/* Shared library add-on to iptables to add packet length matching support. */
#include <stdbool.h>
#include <stdio.h>
#include <netdb.h>
#include <string.h>
#include <stdlib.h>
#include <getopt.h>

#include <xtables.h>
#include <linux/netfilter/xt_length.h>

static void length_help(void)
{
	printf(
"length match options:\n"
"[!] --length length[:length]    Match packet length against value or range\n"
"                                of values (inclusive)\n");
}
  
static const struct option length_opts[] = {
	{.name = "length", .has_arg = true, .val = '1'},
	XT_GETOPT_TABLEEND,
};

static u_int16_t
parse_length(const char *s)
{
	unsigned int len;
	
	if (!xtables_strtoui(s, NULL, &len, 0, UINT32_MAX))
		xtables_error(PARAMETER_PROBLEM, "length invalid: \"%s\"\n", s);
	else
		return len;
}

/* If a single value is provided, min and max are both set to the value */
static void
parse_lengths(const char *s, struct xt_length_info *info)
{
	char *buffer;
	char *cp;

	buffer = strdup(s);
	if ((cp = strchr(buffer, ':')) == NULL)
		info->min = info->max = parse_length(buffer);
	else {
		*cp = '\0';
		cp++;

		info->min = buffer[0] ? parse_length(buffer) : 0;
		info->max = cp[0] ? parse_length(cp) : 0xFFFF;
	}
	free(buffer);
	
	if (info->min > info->max)
		xtables_error(PARAMETER_PROBLEM,
		           "length min. range value `%u' greater than max. "
		           "range value `%u'", info->min, info->max);
	
}

static int
length_parse(int c, char **argv, int invert, unsigned int *flags,
             const void *entry, struct xt_entry_match **match)
{
	struct xt_length_info *info = (struct xt_length_info *)(*match)->data;

	switch (c) {
		case '1':
			if (*flags)
				xtables_error(PARAMETER_PROBLEM,
				           "length: `--length' may only be "
				           "specified once");
			xtables_check_inverse(optarg, &invert, &optind, 0, argv);
			parse_lengths(optarg, info);
			if (invert)
				info->invert = 1;
			*flags = 1;
			break;
			
		default:
			return 0;
	}
	return 1;
}

static void length_check(unsigned int flags)
{
	if (!flags)
		xtables_error(PARAMETER_PROBLEM,
			   "length: You must specify `--length'");
}

static void
length_print(const void *ip, const struct xt_entry_match *match, int numeric)
{
	const struct xt_length_info *info = (void *)match->data;

	printf("length %s", info->invert ? "!" : "");
	if (info->min == info->max)
		printf("%u ", info->min);
	else
		printf("%u:%u ", info->min, info->max);
}

static void length_save(const void *ip, const struct xt_entry_match *match)
{
	const struct xt_length_info *info = (void *)match->data;

	printf("%s--length ", info->invert ? "! " : "");
	if (info->min == info->max)
		printf("%u ", info->min);
	else
		printf("%u:%u ", info->min, info->max);
}

static struct xtables_match length_match = {
	.family		= NFPROTO_UNSPEC,
	.name		= "length",
	.version	= XTABLES_VERSION,
	.size		= XT_ALIGN(sizeof(struct xt_length_info)),
	.userspacesize	= XT_ALIGN(sizeof(struct xt_length_info)),
	.help		= length_help,
	.parse		= length_parse,
	.final_check	= length_check,
	.print		= length_print,
	.save		= length_save,
	.extra_opts	= length_opts,
};

void libxt_length_init(void)
{
	xtables_register_match(&length_match);
}
