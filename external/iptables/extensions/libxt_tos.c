/*
 * Shared library add-on to iptables to add tos match support
 *
 * Copyright © CC Computer Consultants GmbH, 2007
 * Contact: Jan Engelhardt <jengelh@computergmbh.de>
 */
#include <getopt.h>
#include <netdb.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <xtables.h>
#include <linux/netfilter/xt_dscp.h>
#include "tos_values.c"

struct ipt_tos_info {
	u_int8_t tos;
	u_int8_t invert;
};

enum {
	FLAG_TOS = 1 << 0,
};

static const struct option tos_mt_opts[] = {
	{.name = "tos", .has_arg = true, .val = 't'},
	XT_GETOPT_TABLEEND,
};

static void tos_mt_help(void)
{
	const struct tos_symbol_info *symbol;

	printf(
"tos match options:\n"
"[!] --tos value[/mask]    Match Type of Service/Priority field value\n"
"[!] --tos symbol          Match TOS field (IPv4 only) by symbol\n"
"                          Accepted symbolic names for value are:\n");

	for (symbol = tos_symbol_names; symbol->name != NULL; ++symbol)
		printf("                          (0x%02x) %2u %s\n",
		       symbol->value, symbol->value, symbol->name);

	printf("\n");
}

static int tos_mt_parse_v0(int c, char **argv, int invert, unsigned int *flags,
                           const void *entry, struct xt_entry_match **match)
{
	struct ipt_tos_info *info = (void *)(*match)->data;
	struct tos_value_mask tvm;

	switch (c) {
	case 't':
		xtables_param_act(XTF_ONLY_ONCE, "tos", "--tos", *flags & FLAG_TOS);
		if (!tos_parse_symbolic(optarg, &tvm, 0xFF))
			xtables_param_act(XTF_BAD_VALUE, "tos", "--tos", optarg);
		if (tvm.mask != 0xFF)
			xtables_error(PARAMETER_PROBLEM, "tos: Your kernel is "
			           "too old to support anything besides /0xFF "
				   "as a mask.");
		info->tos = tvm.value;
		if (invert)
			info->invert = true;
		*flags |= FLAG_TOS;
		return true;
	}
	return false;
}

static int tos_mt_parse(int c, char **argv, int invert, unsigned int *flags,
                        const void *entry, struct xt_entry_match **match)
{
	struct xt_tos_match_info *info = (void *)(*match)->data;
	struct tos_value_mask tvm = {.mask = 0xFF};

	switch (c) {
	case 't':
		xtables_param_act(XTF_ONLY_ONCE, "tos", "--tos", *flags & FLAG_TOS);
		if (!tos_parse_symbolic(optarg, &tvm, 0x3F))
			xtables_param_act(XTF_BAD_VALUE, "tos", "--tos", optarg);
		info->tos_value = tvm.value;
		info->tos_mask  = tvm.mask;
		if (invert)
			info->invert = true;
		*flags |= FLAG_TOS;
		return true;
	}
	return false;
}

static void tos_mt_check(unsigned int flags)
{
	if (flags == 0)
		xtables_error(PARAMETER_PROBLEM,
		           "tos: --tos parameter required");
}

static void tos_mt_print_v0(const void *ip, const struct xt_entry_match *match,
                            int numeric)
{
	const struct ipt_tos_info *info = (const void *)match->data;

	printf("tos match ");
	if (info->invert)
		printf("!");
	if (numeric || !tos_try_print_symbolic("", info->tos, 0x3F))
		printf("0x%02x ", info->tos);
}

static void tos_mt_print(const void *ip, const struct xt_entry_match *match,
                         int numeric)
{
	const struct xt_tos_match_info *info = (const void *)match->data;

	printf("tos match ");
	if (info->invert)
		printf("!");
	if (numeric ||
	    !tos_try_print_symbolic("", info->tos_value, info->tos_mask))
		printf("0x%02x/0x%02x ", info->tos_value, info->tos_mask);
}

static void tos_mt_save_v0(const void *ip, const struct xt_entry_match *match)
{
	const struct ipt_tos_info *info = (const void *)match->data;

	if (info->invert)
		printf("! ");
	printf("--tos 0x%02x ", info->tos);
}

static void tos_mt_save(const void *ip, const struct xt_entry_match *match)
{
	const struct xt_tos_match_info *info = (const void *)match->data;

	if (info->invert)
		printf("! ");
	printf("--tos 0x%02x/0x%02x ", info->tos_value, info->tos_mask);
}

static struct xtables_match tos_mt_reg[] = {
	{
		.version       = XTABLES_VERSION,
		.name          = "tos",
		.family        = NFPROTO_IPV4,
		.revision      = 0,
		.size          = XT_ALIGN(sizeof(struct ipt_tos_info)),
		.userspacesize = XT_ALIGN(sizeof(struct ipt_tos_info)),
		.help          = tos_mt_help,
		.parse         = tos_mt_parse_v0,
		.final_check   = tos_mt_check,
		.print         = tos_mt_print_v0,
		.save          = tos_mt_save_v0,
		.extra_opts    = tos_mt_opts,
	},
	{
		.version       = XTABLES_VERSION,
		.name          = "tos",
		.family        = NFPROTO_UNSPEC,
		.revision      = 1,
		.size          = XT_ALIGN(sizeof(struct xt_tos_match_info)),
		.userspacesize = XT_ALIGN(sizeof(struct xt_tos_match_info)),
		.help          = tos_mt_help,
		.parse         = tos_mt_parse,
		.final_check   = tos_mt_check,
		.print         = tos_mt_print,
		.save          = tos_mt_save,
		.extra_opts    = tos_mt_opts,
	},
};

void libxt_tos_init(void)
{
	xtables_register_matches(tos_mt_reg, ARRAY_SIZE(tos_mt_reg));
}
