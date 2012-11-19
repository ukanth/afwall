/*
 * IPv6 Hop Limit matching module
 * Maciej Soltysiak <solt@dns.toxicfilms.tv>
 * Based on HW's ttl match
 * This program is released under the terms of GNU GPL
 * Cleanups by Stephane Ouellette <ouellettes@videotron.ca>
 */
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <getopt.h>
#include <xtables.h>

#include <linux/netfilter_ipv6/ip6t_hl.h>

static void hl_help(void)
{
	printf(
"hl match options:\n"
"[!] --hl-eq value	Match hop limit value\n"
"  --hl-lt value	Match HL < value\n"
"  --hl-gt value	Match HL > value\n");
}

static int hl_parse(int c, char **argv, int invert, unsigned int *flags,
                    const void *entry, struct xt_entry_match **match)
{
	struct ip6t_hl_info *info = (struct ip6t_hl_info *) (*match)->data;
	u_int8_t value;

	xtables_check_inverse(optarg, &invert, &optind, 0, argv);
	value = atoi(optarg);

	if (*flags) 
		xtables_error(PARAMETER_PROBLEM,
				"Can't specify HL option twice");

	if (!optarg)
		xtables_error(PARAMETER_PROBLEM,
				"hl: You must specify a value");
	switch (c) {
		case '2':
			if (invert)
				info->mode = IP6T_HL_NE;
			else
				info->mode = IP6T_HL_EQ;

			/* is 0 allowed? */
			info->hop_limit = value;
			*flags = 1;

			break;
		case '3':
			if (invert) 
				xtables_error(PARAMETER_PROBLEM,
						"hl: unexpected `!'");

			info->mode = IP6T_HL_LT;
			info->hop_limit = value;
			*flags = 1;

			break;
		case '4':
			if (invert)
				xtables_error(PARAMETER_PROBLEM,
						"hl: unexpected `!'");

			info->mode = IP6T_HL_GT;
			info->hop_limit = value;
			*flags = 1;

			break;
		default:
			return 0;
	}

	return 1;
}

static void hl_check(unsigned int flags)
{
	if (!flags) 
		xtables_error(PARAMETER_PROBLEM,
			"HL match: You must specify one of "
			"`--hl-eq', `--hl-lt', `--hl-gt'");
}

static void hl_print(const void *ip, const struct xt_entry_match *match,
                     int numeric)
{
	static const char *const op[] = {
		[IP6T_HL_EQ] = "==",
		[IP6T_HL_NE] = "!=",
		[IP6T_HL_LT] = "<",
		[IP6T_HL_GT] = ">" };

	const struct ip6t_hl_info *info = 
		(struct ip6t_hl_info *) match->data;

	printf("HL match HL %s %u ", op[info->mode], info->hop_limit);
}

static void hl_save(const void *ip, const struct xt_entry_match *match)
{
	static const char *const op[] = {
		[IP6T_HL_EQ] = "--hl-eq",
		[IP6T_HL_NE] = "! --hl-eq",
		[IP6T_HL_LT] = "--hl-lt",
		[IP6T_HL_GT] = "--hl-gt" };

	const struct ip6t_hl_info *info =
		(struct ip6t_hl_info *) match->data;

	printf("%s %u ", op[info->mode], info->hop_limit);
}

static const struct option hl_opts[] = {
	{.name = "hl",    .has_arg = true, .val = '2'},
	{.name = "hl-eq", .has_arg = true, .val = '2'},
	{.name = "hl-lt", .has_arg = true, .val = '3'},
	{.name = "hl-gt", .has_arg = true, .val = '4'},
	XT_GETOPT_TABLEEND,
};

static struct xtables_match hl_mt6_reg = {
	.name          = "hl",
	.version       = XTABLES_VERSION,
	.family        = NFPROTO_IPV6,
	.size          = XT_ALIGN(sizeof(struct ip6t_hl_info)),
	.userspacesize = XT_ALIGN(sizeof(struct ip6t_hl_info)),
	.help          = hl_help,
	.parse         = hl_parse,
	.final_check   = hl_check,
	.print         = hl_print,
	.save          = hl_save,
	.extra_opts    = hl_opts,
};


void _init(void) 
{
	xtables_register_match(&hl_mt6_reg);
}
