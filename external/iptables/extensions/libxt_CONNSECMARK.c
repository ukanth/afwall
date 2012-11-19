/*
 * Shared library add-on to iptables to add CONNSECMARK target support.
 *
 * Based on the MARK and CONNMARK targets.
 *
 * Copyright (C) 2006 Red Hat, Inc., James Morris <jmorris@redhat.com>
 */
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <getopt.h>
#include <xtables.h>
#include <linux/netfilter/xt_CONNSECMARK.h>

#define PFX "CONNSECMARK target: "

static void CONNSECMARK_help(void)
{
	printf(
"CONNSECMARK target options:\n"
"  --save                   Copy security mark from packet to conntrack\n"
"  --restore                Copy security mark from connection to packet\n");
}

static const struct option CONNSECMARK_opts[] = {
	{.name = "save",    .has_arg = false, .val = '1'},
	{.name = "restore", .has_arg = false, .val = '2'},
	XT_GETOPT_TABLEEND,
};

static int
CONNSECMARK_parse(int c, char **argv, int invert, unsigned int *flags,
                  const void *entry, struct xt_entry_target **target)
{
	struct xt_connsecmark_target_info *info =
		(struct xt_connsecmark_target_info*)(*target)->data;

	switch (c) {
	case '1':
		if (*flags & CONNSECMARK_SAVE)
			xtables_error(PARAMETER_PROBLEM, PFX
				   "Can't specify --save twice");
		info->mode = CONNSECMARK_SAVE;
		*flags |= CONNSECMARK_SAVE;
		break;

	case '2':
		if (*flags & CONNSECMARK_RESTORE)
			xtables_error(PARAMETER_PROBLEM, PFX
				   "Can't specify --restore twice");
		info->mode = CONNSECMARK_RESTORE;
		*flags |= CONNSECMARK_RESTORE;
		break;

	default:
		return 0;
	}

	return 1;
}

static void CONNSECMARK_check(unsigned int flags)
{
	if (!flags)
		xtables_error(PARAMETER_PROBLEM, PFX "parameter required");

	if (flags == (CONNSECMARK_SAVE|CONNSECMARK_RESTORE))
		xtables_error(PARAMETER_PROBLEM, PFX "only one flag of --save "
		           "or --restore is allowed");
}

static void print_connsecmark(const struct xt_connsecmark_target_info *info)
{
	switch (info->mode) {
	case CONNSECMARK_SAVE:
		printf("save ");
		break;
		
	case CONNSECMARK_RESTORE:
		printf("restore ");
		break;
		
	default:
		xtables_error(OTHER_PROBLEM, PFX "invalid mode %hhu\n", info->mode);
	}
}

static void
CONNSECMARK_print(const void *ip, const struct xt_entry_target *target,
                  int numeric)
{
	const struct xt_connsecmark_target_info *info =
		(struct xt_connsecmark_target_info*)(target)->data;

	printf("CONNSECMARK ");
	print_connsecmark(info);
}

static void
CONNSECMARK_save(const void *ip, const struct xt_entry_target *target)
{
	const struct xt_connsecmark_target_info *info =
		(struct xt_connsecmark_target_info*)target->data;

	printf("--");
	print_connsecmark(info);
}

static struct xtables_target connsecmark_target = {
	.family		= NFPROTO_UNSPEC,
	.name		= "CONNSECMARK",
	.version	= XTABLES_VERSION,
	.revision	= 0,
	.size		= XT_ALIGN(sizeof(struct xt_connsecmark_target_info)),
	.userspacesize	= XT_ALIGN(sizeof(struct xt_connsecmark_target_info)),
	.parse		= CONNSECMARK_parse,
	.help		= CONNSECMARK_help,
	.final_check	= CONNSECMARK_check,
	.print		= CONNSECMARK_print,
	.save		= CONNSECMARK_save,
	.extra_opts	= CONNSECMARK_opts,
};

void libxt_CONNSECMARK_init(void)
{
	xtables_register_target(&connsecmark_target);
}
