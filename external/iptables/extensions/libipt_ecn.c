/* Shared library add-on to iptables for ECN matching
 *
 * (C) 2002 by Harald Welte <laforge@gnumonks.org>
 *
 * This program is distributed under the terms of GNU GPL v2, 1991
 *
 * libipt_ecn.c borrowed heavily from libipt_dscp.c
 *
 */
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <getopt.h>

#include <xtables.h>
#include <linux/netfilter_ipv4/ipt_ecn.h>

static void ecn_help(void)
{
	printf(
"ECN match options\n"
"[!] --ecn-tcp-cwr 		Match CWR bit of TCP header\n"
"[!] --ecn-tcp-ece		Match ECE bit of TCP header\n"
"[!] --ecn-ip-ect [0..3]	Match ECN codepoint in IPv4 header\n");
}

static const struct option ecn_opts[] = {
	{.name = "ecn-tcp-cwr", .has_arg = false, .val = 'F'},
	{.name = "ecn-tcp-ece", .has_arg = false, .val = 'G'},
	{.name = "ecn-ip-ect",  .has_arg = true,  .val = 'H'},
	XT_GETOPT_TABLEEND,
};

static int ecn_parse(int c, char **argv, int invert, unsigned int *flags,
                     const void *entry, struct xt_entry_match **match)
{
	unsigned int result;
	struct ipt_ecn_info *einfo
		= (struct ipt_ecn_info *)(*match)->data;

	switch (c) {
	case 'F':
		if (*flags & IPT_ECN_OP_MATCH_CWR)
			xtables_error(PARAMETER_PROBLEM,
			           "ECN match: can only use parameter ONCE!");
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		einfo->operation |= IPT_ECN_OP_MATCH_CWR;
		if (invert)
			einfo->invert |= IPT_ECN_OP_MATCH_CWR;
		*flags |= IPT_ECN_OP_MATCH_CWR;
		break;

	case 'G':
		if (*flags & IPT_ECN_OP_MATCH_ECE)
			xtables_error(PARAMETER_PROBLEM,
				   "ECN match: can only use parameter ONCE!");
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		einfo->operation |= IPT_ECN_OP_MATCH_ECE;
		if (invert)
			einfo->invert |= IPT_ECN_OP_MATCH_ECE;
		*flags |= IPT_ECN_OP_MATCH_ECE;
		break;

	case 'H':
		if (*flags & IPT_ECN_OP_MATCH_IP)
			xtables_error(PARAMETER_PROBLEM,
				   "ECN match: can only use parameter ONCE!");
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		if (invert)
			einfo->invert |= IPT_ECN_OP_MATCH_IP;
		*flags |= IPT_ECN_OP_MATCH_IP;
		einfo->operation |= IPT_ECN_OP_MATCH_IP;
		if (!xtables_strtoui(optarg, NULL, &result, 0, 3))
			xtables_error(PARAMETER_PROBLEM,
				   "ECN match: Value out of range");
		einfo->ip_ect = result;
		break;
	default:
		return 0;
	}

	return 1;
}

static void ecn_check(unsigned int flags)
{
	if (!flags)
		xtables_error(PARAMETER_PROBLEM,
		           "ECN match: some option required");
}

static void ecn_print(const void *ip, const struct xt_entry_match *match,
                      int numeric)
{
	const struct ipt_ecn_info *einfo =
		(const struct ipt_ecn_info *)match->data;

	printf("ECN match ");

	if (einfo->operation & IPT_ECN_OP_MATCH_ECE) {
		if (einfo->invert & IPT_ECN_OP_MATCH_ECE)
			fputc('!', stdout);
		printf("ECE ");
	}

	if (einfo->operation & IPT_ECN_OP_MATCH_CWR) {
		if (einfo->invert & IPT_ECN_OP_MATCH_CWR)
			fputc('!', stdout);
		printf("CWR ");
	}

	if (einfo->operation & IPT_ECN_OP_MATCH_IP) {
		if (einfo->invert & IPT_ECN_OP_MATCH_IP)
			fputc('!', stdout);
		printf("ECT=%d ", einfo->ip_ect);
	}
}

static void ecn_save(const void *ip, const struct xt_entry_match *match)
{
	const struct ipt_ecn_info *einfo =
		(const struct ipt_ecn_info *)match->data;
	
	if (einfo->operation & IPT_ECN_OP_MATCH_ECE) {
		if (einfo->invert & IPT_ECN_OP_MATCH_ECE)
			printf("! ");
		printf("--ecn-tcp-ece ");
	}

	if (einfo->operation & IPT_ECN_OP_MATCH_CWR) {
		if (einfo->invert & IPT_ECN_OP_MATCH_CWR)
			printf("! ");
		printf("--ecn-tcp-cwr ");
	}

	if (einfo->operation & IPT_ECN_OP_MATCH_IP) {
		if (einfo->invert & IPT_ECN_OP_MATCH_IP)
			printf("! ");
		printf("--ecn-ip-ect %d", einfo->ip_ect);
	}
}

static struct xtables_match ecn_mt_reg = {
    .name          = "ecn",
    .version       = XTABLES_VERSION,
    .family        = NFPROTO_IPV4,
    .size          = XT_ALIGN(sizeof(struct ipt_ecn_info)),
    .userspacesize = XT_ALIGN(sizeof(struct ipt_ecn_info)),
    .help          = ecn_help,
    .parse         = ecn_parse,
    .final_check   = ecn_check,
    .print         = ecn_print,
    .save          = ecn_save,
    .extra_opts    = ecn_opts,
};

void libipt_ecn_init(void)
{
	xtables_register_match(&ecn_mt_reg);
}
