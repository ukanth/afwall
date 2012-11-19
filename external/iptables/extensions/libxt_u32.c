/* Shared library add-on to iptables to add u32 matching,
 * generalized matching on values found at packet offsets
 *
 * Detailed doc is in the kernel module source
 * net/netfilter/xt_u32.c
 *
 * (C) 2002 by Don Cohen <don-netf@isis.cs3-inc.com>
 * Released under the terms of GNU GPL v2
 *
 * Copyright © CC Computer Consultants GmbH, 2007
 * Contact: <jengelh@computergmbh.de>
 */
#include <sys/types.h>
#include <ctype.h>
#include <errno.h>
#include <getopt.h>
#include <netdb.h>
#include <stdbool.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#include <xtables.h>
#include <linux/netfilter/xt_u32.h>

static const struct option u32_opts[] = {
	{.name = "u32", .has_arg = true, .val = 'u'},
	XT_GETOPT_TABLEEND,
};

static void u32_help(void)
{
	printf(
		"u32 match options:\n"
		"[!] --u32 tests\n"
		"\t\t""tests := location \"=\" value | tests \"&&\" location \"=\" value\n"
		"\t\t""value := range | value \",\" range\n"
		"\t\t""range := number | number \":\" number\n"
		"\t\t""location := number | location operator number\n"
		"\t\t""operator := \"&\" | \"<<\" | \">>\" | \"@\"\n");
}

static void u32_dump(const struct xt_u32 *data)
{
	const struct xt_u32_test *ct;
	unsigned int testind, i;

	for (testind = 0; testind < data->ntests; ++testind) {
		ct = &data->tests[testind];

		if (testind > 0)
			printf("&&");

		printf("0x%x", ct->location[0].number);
		for (i = 1; i < ct->nnums; ++i) {
			switch (ct->location[i].nextop) {
			case XT_U32_AND:
				printf("&");
				break;
			case XT_U32_LEFTSH:
				printf("<<");
				break;
			case XT_U32_RIGHTSH:
				printf(">>");
				break;
			case XT_U32_AT:
				printf("@");
				break;
			}
			printf("0x%x", ct->location[i].number);
		}

		printf("=");
		for (i = 0; i < ct->nvalues; ++i) {
			if (i > 0)
				printf(",");
			if (ct->value[i].min == ct->value[i].max)
				printf("0x%x", ct->value[i].min);
			else
				printf("0x%x:0x%x", ct->value[i].min,
				       ct->value[i].max);
		}
	}
	printf(" ");
}

/* string_to_number() is not quite what we need here ... */
static u_int32_t parse_number(char **s, int pos)
{
	u_int32_t number;
	char *end;

	errno  = 0;
	number = strtoul(*s, &end, 0);
	if (end == *s)
		xtables_error(PARAMETER_PROBLEM,
			   "u32: at char %d: expected number", pos);
	if (errno != 0)
		xtables_error(PARAMETER_PROBLEM,
			   "u32: at char %d: error reading number", pos);
	*s = end;
	return number;
}

static int u32_parse(int c, char **argv, int invert, unsigned int *flags,
		     const void *entry, struct xt_entry_match **match)
{
	struct xt_u32 *data = (void *)(*match)->data;
	unsigned int testind = 0, locind = 0, valind = 0;
	struct xt_u32_test *ct = &data->tests[testind]; /* current test */
	char *arg = optarg; /* the argument string */
	char *start = arg;
	int state = 0;

	if (c != 'u')
		return 0;

	data->invert = invert;

	/*
	 * states:
	 * 0 = looking for numbers and operations,
	 * 1 = looking for ranges
	 */
	while (1) {
		/* read next operand/number or range */
		while (isspace(*arg))
			++arg;

		if (*arg == '\0') {
			/* end of argument found */
			if (state == 0)
				xtables_error(PARAMETER_PROBLEM,
					   "u32: abrupt end of input after location specifier");
			if (valind == 0)
				xtables_error(PARAMETER_PROBLEM,
					   "u32: test ended with no value specified");

			ct->nnums    = locind;
			ct->nvalues  = valind;
			data->ntests = ++testind;

			if (testind > XT_U32_MAXSIZE)
				xtables_error(PARAMETER_PROBLEM,
				           "u32: at char %u: too many \"&&\"s",
				           (unsigned int)(arg - start));
			return 1;
		}

		if (state == 0) {
			/*
			 * reading location: read a number if nothing read yet,
			 * otherwise either op number or = to end location spec
			 */
			if (*arg == '=') {
				if (locind == 0) {
					xtables_error(PARAMETER_PROBLEM,
					           "u32: at char %u: "
					           "location spec missing",
					           (unsigned int)(arg - start));
				} else {
					++arg;
					state = 1;
				}
			} else {
				if (locind != 0) {
					/* need op before number */
					if (*arg == '&') {
						ct->location[locind].nextop = XT_U32_AND;
					} else if (*arg == '<') {
						if (*++arg != '<')
							xtables_error(PARAMETER_PROBLEM,
								   "u32: at char %u: a second '<' was expected", (unsigned int)(arg - start));
						ct->location[locind].nextop = XT_U32_LEFTSH;
					} else if (*arg == '>') {
						if (*++arg != '>')
							xtables_error(PARAMETER_PROBLEM,
								   "u32: at char %u: a second '>' was expected", (unsigned int)(arg - start));
						ct->location[locind].nextop = XT_U32_RIGHTSH;
					} else if (*arg == '@') {
						ct->location[locind].nextop = XT_U32_AT;
					} else {
						xtables_error(PARAMETER_PROBLEM,
							"u32: at char %u: operator expected", (unsigned int)(arg - start));
					}
					++arg;
				}
				/* now a number; string_to_number skips white space? */
				ct->location[locind].number =
					parse_number(&arg, arg - start);
				if (++locind > XT_U32_MAXSIZE)
					xtables_error(PARAMETER_PROBLEM,
						   "u32: at char %u: too many operators", (unsigned int)(arg - start));
			}
		} else {
			/*
			 * state 1 - reading values: read a range if nothing
			 * read yet, otherwise either ,range or && to end
			 * test spec
			 */
			if (*arg == '&') {
				if (*++arg != '&')
					xtables_error(PARAMETER_PROBLEM,
						   "u32: at char %u: a second '&' was expected", (unsigned int)(arg - start));
				if (valind == 0) {
					xtables_error(PARAMETER_PROBLEM,
						   "u32: at char %u: value spec missing", (unsigned int)(arg - start));
				} else {
					ct->nnums   = locind;
					ct->nvalues = valind;
					ct = &data->tests[++testind];
					if (testind > XT_U32_MAXSIZE)
						xtables_error(PARAMETER_PROBLEM,
							   "u32: at char %u: too many \"&&\"s", (unsigned int)(arg - start));
					++arg;
					state  = 0;
					locind = 0;
					valind = 0;
				}
			} else { /* read value range */
				if (valind > 0) { /* need , before number */
					if (*arg != ',')
						xtables_error(PARAMETER_PROBLEM,
							   "u32: at char %u: expected \",\" or \"&&\"", (unsigned int)(arg - start));
					++arg;
				}
				ct->value[valind].min =
					parse_number(&arg, arg - start);

				while (isspace(*arg))
					++arg;

				if (*arg == ':') {
					++arg;
					ct->value[valind].max =
						parse_number(&arg, arg-start);
				} else {
					ct->value[valind].max =
						ct->value[valind].min;
				}

				if (++valind > XT_U32_MAXSIZE)
					xtables_error(PARAMETER_PROBLEM,
						   "u32: at char %u: too many \",\"s", (unsigned int)(arg - start));
			}
		}
	}
}

static void u32_print(const void *ip, const struct xt_entry_match *match,
                      int numeric)
{
	const struct xt_u32 *data = (const void *)match->data;
	printf("u32 ");
	if (data->invert)
		printf("! ");
	u32_dump(data);
}

static void u32_save(const void *ip, const struct xt_entry_match *match)
{
	const struct xt_u32 *data = (const void *)match->data;
	if (data->invert)
		printf("! ");
	printf("--u32 ");
	u32_dump(data);
}

static struct xtables_match u32_match = {
	.name          = "u32",
	.family        = NFPROTO_UNSPEC,
	.version       = XTABLES_VERSION,
	.size          = XT_ALIGN(sizeof(struct xt_u32)),
	.userspacesize = XT_ALIGN(sizeof(struct xt_u32)),
	.help          = u32_help,
	.parse         = u32_parse,
	.print         = u32_print,
	.save          = u32_save,
	.extra_opts    = u32_opts,
};

void libxt_u32_init(void)
{
	xtables_register_match(&u32_match);
}
