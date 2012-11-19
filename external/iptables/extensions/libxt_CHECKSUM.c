/* Shared library add-on to xtables for CHECKSUM
 *
 * (C) 2002 by Harald Welte <laforge@gnumonks.org>
 * (C) 2010 by Red Hat, Inc
 * Author: Michael S. Tsirkin <mst@redhat.com>
 *
 * This program is distributed under the terms of GNU GPL v2, 1991
 *
 * libxt_CHECKSUM.c borrowed some bits from libipt_ECN.c
 */
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <getopt.h>

#include <xtables.h>
#include <linux/netfilter/xt_CHECKSUM.h>

static void CHECKSUM_help(void)
{
	printf(
"CHECKSUM target options\n"
"  --checksum-fill			Fill in packet checksum.\n");
}

static const struct option CHECKSUM_opts[] = {
	{.name = "checksum-fill", .has_arg = false, .val = 'F'},
	XT_GETOPT_TABLEEND,
};

static int CHECKSUM_parse(int c, char **argv, int invert, unsigned int *flags,
                     const void *entry, struct xt_entry_target **target)
{
	struct xt_CHECKSUM_info *einfo
		= (struct xt_CHECKSUM_info *)(*target)->data;

	switch (c) {
	case 'F':
		xtables_param_act(XTF_ONLY_ONCE, "CHECKSUM", "--checksum-fill",
			*flags & XT_CHECKSUM_OP_FILL);
		einfo->operation = XT_CHECKSUM_OP_FILL;
		*flags |= XT_CHECKSUM_OP_FILL;
		break;
	default:
		return 0;
	}

	return 1;
}

static void CHECKSUM_check(unsigned int flags)
{
	if (!flags)
		xtables_error(PARAMETER_PROBLEM,
		           "CHECKSUM target: Parameter --checksum-fill is required");
}

static void CHECKSUM_print(const void *ip, const struct xt_entry_target *target,
                      int numeric)
{
	const struct xt_CHECKSUM_info *einfo =
		(const struct xt_CHECKSUM_info *)target->data;

	printf("CHECKSUM ");

	if (einfo->operation & XT_CHECKSUM_OP_FILL)
		printf("fill ");
}

static void CHECKSUM_save(const void *ip, const struct xt_entry_target *target)
{
	const struct xt_CHECKSUM_info *einfo =
		(const struct xt_CHECKSUM_info *)target->data;

	if (einfo->operation & XT_CHECKSUM_OP_FILL)
		printf("--checksum-fill ");
}

static struct xtables_target checksum_tg_reg = {
	.name		= "CHECKSUM",
	.version	= XTABLES_VERSION,
	.family		= NFPROTO_UNSPEC,
	.size		= XT_ALIGN(sizeof(struct xt_CHECKSUM_info)),
	.userspacesize	= XT_ALIGN(sizeof(struct xt_CHECKSUM_info)),
	.help		= CHECKSUM_help,
	.parse		= CHECKSUM_parse,
	.final_check	= CHECKSUM_check,
	.print		= CHECKSUM_print,
	.save		= CHECKSUM_save,
	.extra_opts	= CHECKSUM_opts,
};

void libxt_CHECKSUM_init(void)
{
	xtables_register_target(&checksum_tg_reg);
}
