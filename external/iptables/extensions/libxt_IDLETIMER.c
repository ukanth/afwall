/*
 * Shared library add-on for iptables to add IDLETIMER support.
 *
 * Copyright (C) 2010 Nokia Corporation. All rights reserved.
 *
 * Contact: Luciano Coelho <luciano.coelho@nokia.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	 See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA
 *
 */
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <getopt.h>
#include <stddef.h>

#include <xtables.h>
#include <linux/netfilter/xt_IDLETIMER.h>

enum {
	IDLETIMER_TG_OPT_TIMEOUT = 1 << 0,
	IDLETIMER_TG_OPT_LABEL	 = 1 << 1,
};

static const struct option idletimer_tg_opts[] = {
	{.name = "timeout", .has_arg = true, .val = 't'},
	{.name = "label",   .has_arg = true, .val = 'l'},
	XT_GETOPT_TABLEEND,
};

static void idletimer_tg_help(void)
{
	printf(
"IDLETIMER target options:\n"
" --timeout time	Timeout until the notification is sent (in seconds)\n"
" --label string	Unique rule identifier\n"
"\n");
}

static int idletimer_tg_parse(int c, char **argv, int invert,
			      unsigned int *flags,
			      const void *entry,
			      struct xt_entry_target **target)
{
	struct idletimer_tg_info *info =
		(struct idletimer_tg_info *)(*target)->data;

	switch (c) {
	case 't':
		xtables_param_act(XTF_ONLY_ONCE, "IDLETIMER", "--timeout",
				  *flags & IDLETIMER_TG_OPT_TIMEOUT);

		info->timeout = atoi(optarg);
		*flags |= IDLETIMER_TG_OPT_TIMEOUT;
		break;

	case 'l':
		xtables_param_act(XTF_ONLY_ONCE, "IDLETIMER", "--label",
				  *flags & IDLETIMER_TG_OPT_TIMEOUT);

		if (strlen(optarg) > MAX_IDLETIMER_LABEL_SIZE - 1)
			xtables_param_act(XTF_BAD_VALUE, "IDLETIMER", "--label",
					 optarg);

		strcpy(info->label, optarg);
		*flags |= IDLETIMER_TG_OPT_LABEL;
		break;

	default:
		return false;
	}

	return true;
}

static void idletimer_tg_final_check(unsigned int flags)
{
	if (!(flags & IDLETIMER_TG_OPT_TIMEOUT))
		xtables_error(PARAMETER_PROBLEM, "IDLETIMER target: "
			      "--timeout parameter required");
	if (!(flags & IDLETIMER_TG_OPT_LABEL))
		xtables_error(PARAMETER_PROBLEM, "IDLETIMER target: "
			      "--label parameter required");
}

static void idletimer_tg_print(const void *ip,
			       const struct xt_entry_target *target,
			       int numeric)
{
	struct idletimer_tg_info *info =
		(struct idletimer_tg_info *) target->data;

	printf("timeout:%u ", info->timeout);
	printf("label:%s ", info->label);
}

static void idletimer_tg_save(const void *ip,
			      const struct xt_entry_target *target)
{
	struct idletimer_tg_info *info =
		(struct idletimer_tg_info *) target->data;

	printf("--timeout %u ", info->timeout);
	printf("--label %s ", info->label);
}

static struct xtables_target idletimer_tg_reg = {
	.family	       = NFPROTO_UNSPEC,
	.name	       = "IDLETIMER",
	.version       = XTABLES_VERSION,
	.revision      = 0,
	.size	       = XT_ALIGN(sizeof(struct idletimer_tg_info)),
	.userspacesize = offsetof(struct idletimer_tg_info, timer),
	.help	       = idletimer_tg_help,
	.parse	       = idletimer_tg_parse,
	.final_check   = idletimer_tg_final_check,
	.print	       = idletimer_tg_print,
	.save	       = idletimer_tg_save,
	.extra_opts    = idletimer_tg_opts,
};

void libxt_IDLETIMER_init(void)
{
	xtables_register_target(&idletimer_tg_reg);
}
