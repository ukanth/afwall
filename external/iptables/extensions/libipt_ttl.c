/* Shared library add-on to iptables to add TTL matching support 
 * (C) 2000 by Harald Welte <laforge@gnumonks.org>
 *
 * $Id: //atg/packetfilter/tagging/platform_passion/external/iptables/extensions/libipt_ttl.c#1 $
 *
 * This program is released under the terms of GNU GPL */
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <getopt.h>
#include <xtables.h>

#include <linux/netfilter_ipv4/ipt_ttl.h>

static void ttl_help(void)
{
	printf(
"ttl match options:\n"
"  --ttl-eq value	Match time to live value\n"
"  --ttl-lt value	Match TTL < value\n"
"  --ttl-gt value	Match TTL > value\n");
}

static int ttl_parse(int c, char **argv, int invert, unsigned int *flags,
                     const void *entry, struct xt_entry_match **match)
{
	struct ipt_ttl_info *info = (struct ipt_ttl_info *) (*match)->data;
	unsigned int value;

	xtables_check_inverse(optarg, &invert, &optind, 0, argv);

	switch (c) {
		case '2':
			if (!xtables_strtoui(optarg, NULL, &value, 0, UINT8_MAX))
				xtables_error(PARAMETER_PROBLEM,
				           "ttl: Expected value between 0 and 255");

			if (invert)
				info->mode = IPT_TTL_NE;
			else
				info->mode = IPT_TTL_EQ;

			/* is 0 allowed? */
			info->ttl = value;
			break;
		case '3':
			if (!xtables_strtoui(optarg, NULL, &value, 0, UINT8_MAX))
				xtables_error(PARAMETER_PROBLEM,
				           "ttl: Expected value between 0 and 255");

			if (invert) 
				xtables_error(PARAMETER_PROBLEM,
						"ttl: unexpected `!'");

			info->mode = IPT_TTL_LT;
			info->ttl = value;
			break;
		case '4':
			if (!xtables_strtoui(optarg, NULL, &value, 0, UINT8_MAX))
				xtables_error(PARAMETER_PROBLEM,
				           "ttl: Expected value between 0 and 255");

			if (invert)
				xtables_error(PARAMETER_PROBLEM,
						"ttl: unexpected `!'");

			info->mode = IPT_TTL_GT;
			info->ttl = value;
			break;
		default:
			return 0;

	}

	if (*flags) 
		xtables_error(PARAMETER_PROBLEM,
				"Can't specify TTL option twice");
	*flags = 1;

	return 1;
}

static void ttl_check(unsigned int flags)
{
	if (!flags) 
		xtables_error(PARAMETER_PROBLEM,
			"TTL match: You must specify one of "
			"`--ttl-eq', `--ttl-lt', `--ttl-gt");
}

static void ttl_print(const void *ip, const struct xt_entry_match *match,
                      int numeric)
{
	const struct ipt_ttl_info *info = 
		(struct ipt_ttl_info *) match->data;

	printf("TTL match ");
	switch (info->mode) {
		case IPT_TTL_EQ:
			printf("TTL == ");
			break;
		case IPT_TTL_NE:
			printf("TTL != ");
			break;
		case IPT_TTL_LT:
			printf("TTL < ");
			break;
		case IPT_TTL_GT:
			printf("TTL > ");
			break;
	}
	printf("%u ", info->ttl);
}

static void ttl_save(const void *ip, const struct xt_entry_match *match)
{
	const struct ipt_ttl_info *info =
		(struct ipt_ttl_info *) match->data;

	switch (info->mode) {
		case IPT_TTL_EQ:
			printf("--ttl-eq ");
			break;
		case IPT_TTL_NE:
			printf("! --ttl-eq ");
			break;
		case IPT_TTL_LT:
			printf("--ttl-lt ");
			break;
		case IPT_TTL_GT:
			printf("--ttl-gt ");
			break;
		default:
			/* error */
			break;
	}
	printf("%u ", info->ttl);
}

static const struct option ttl_opts[] = {
	{.name = "ttl",    .has_arg = true, .val = '2'},
	{.name = "ttl-eq", .has_arg = true, .val = '2'},
	{.name = "ttl-lt", .has_arg = true, .val = '3'},
	{.name = "ttl-gt", .has_arg = true, .val = '4'},
	XT_GETOPT_TABLEEND,
};

static struct xtables_match ttl_mt_reg = {
	.name		= "ttl",
	.version	= XTABLES_VERSION,
	.family		= NFPROTO_IPV4,
	.size		= XT_ALIGN(sizeof(struct ipt_ttl_info)),
	.userspacesize	= XT_ALIGN(sizeof(struct ipt_ttl_info)),
	.help		= ttl_help,
	.parse		= ttl_parse,
	.final_check	= ttl_check,
	.print		= ttl_print,
	.save		= ttl_save,
	.extra_opts	= ttl_opts,
};


void libipt_ttl_init(void) 
{
	xtables_register_match(&ttl_mt_reg);
}
