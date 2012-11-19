/* Shared library add-on to iptables for NFQ
 *
 * (C) 2005 by Harald Welte <laforge@netfilter.org>
 *
 * This program is distributed under the terms of GNU GPL v2, 1991
 *
 */
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <getopt.h>

#include <xtables.h>
#include <linux/netfilter/x_tables.h>
#include <linux/netfilter/xt_NFQUEUE.h>

static void NFQUEUE_help(void)
{
	printf(
"NFQUEUE target options\n"
"  --queue-num value		Send packet to QUEUE number <value>.\n"
"  		                Valid queue numbers are 0-65535\n"
);
}

static void NFQUEUE_help_v1(void)
{
	NFQUEUE_help();
	printf(
"  --queue-balance first:last	Balance flows between queues <value> to <value>.\n");
}

static const struct option NFQUEUE_opts[] = {
	{.name = "queue-num",     .has_arg = true, .val = 'F'},
	{.name = "queue-balance", .has_arg = true, .val = 'B'},
	XT_GETOPT_TABLEEND,
};

static void exit_badqueue(const char *s)
{
	xtables_error(PARAMETER_PROBLEM, "Invalid queue number `%s'\n", s);
}

static void
parse_num(const char *s, struct xt_NFQ_info *tinfo)
{
	unsigned int num;

	if (!xtables_strtoui(s, NULL, &num, 0, UINT16_MAX))
		exit_badqueue(s);

	tinfo->queuenum = num;
}

static int
NFQUEUE_parse(int c, char **argv, int invert, unsigned int *flags,
              const void *entry, struct xt_entry_target **target)
{
	struct xt_NFQ_info *tinfo
		= (struct xt_NFQ_info *)(*target)->data;

	switch (c) {
	case 'F':
		if (*flags)
			xtables_error(PARAMETER_PROBLEM, "NFQUEUE target: "
				   "Only use --queue-num ONCE!");
		parse_num(optarg, tinfo);
		break;
	case 'B':
		xtables_error(PARAMETER_PROBLEM, "NFQUEUE target: "
				   "--queue-balance not supported (kernel too old?)");
	default:
		return 0;
	}

	return 1;
}

static int
NFQUEUE_parse_v1(int c, char **argv, int invert, unsigned int *flags,
                 const void *entry, struct xt_entry_target **target)
{
	struct xt_NFQ_info_v1 *info = (void *)(*target)->data;
	char *colon;
	unsigned int firstqueue, lastqueue;

	switch (c) {
	case 'F': /* fallthrough */
	case 'B':
		if (*flags)
			xtables_error(PARAMETER_PROBLEM, "NFQUEUE target: "
				   "Only use --queue-num ONCE!");

		if (!xtables_strtoui(optarg, &colon, &firstqueue, 0, UINT16_MAX))
			exit_badqueue(optarg);

		info->queuenum = firstqueue;

		if (c == 'F') {
			if (*colon)
				exit_badqueue(optarg);
			break;
		}

		if (*colon != ':')
			xtables_error(PARAMETER_PROBLEM, "Bad range \"%s\"", optarg);

		if (!xtables_strtoui(colon + 1, NULL, &lastqueue, 1, UINT16_MAX))
			exit_badqueue(optarg);

		if (firstqueue >= lastqueue)
			xtables_error(PARAMETER_PROBLEM, "%u should be less than %u",
							firstqueue, lastqueue);
		info->queues_total = lastqueue - firstqueue + 1;
		break;
	default:
		return 0;
	}

	return 1;
}

static void NFQUEUE_print(const void *ip,
                          const struct xt_entry_target *target, int numeric)
{
	const struct xt_NFQ_info *tinfo =
		(const struct xt_NFQ_info *)target->data;
	printf("NFQUEUE num %u", tinfo->queuenum);
}

static void NFQUEUE_print_v1(const void *ip,
                             const struct xt_entry_target *target, int numeric)
{
	const struct xt_NFQ_info_v1 *tinfo = (const void *)target->data;
	unsigned int last = tinfo->queues_total;

	if (last > 1) {
		last += tinfo->queuenum - 1;
		printf("NFQUEUE balance %u:%u", tinfo->queuenum, last);
	} else {
		printf("NFQUEUE num %u", tinfo->queuenum);
	}
}

static void NFQUEUE_save(const void *ip, const struct xt_entry_target *target)
{
	const struct xt_NFQ_info *tinfo =
		(const struct xt_NFQ_info *)target->data;

	printf("--queue-num %u ", tinfo->queuenum);
}

static void NFQUEUE_save_v1(const void *ip, const struct xt_entry_target *target)
{
	const struct xt_NFQ_info_v1 *tinfo = (const void *)target->data;
	unsigned int last = tinfo->queues_total;

	if (last > 1) {
		last += tinfo->queuenum - 1;
		printf("--queue-balance %u:%u ", tinfo->queuenum, last);
	} else {
		printf("--queue-num %u ", tinfo->queuenum);
	}
}

static void NFQUEUE_init_v1(struct xt_entry_target *t)
{
	struct xt_NFQ_info_v1 *tinfo = (void *)t->data;
	tinfo->queues_total = 1;
}

static struct xtables_target nfqueue_target = {
	.family		= NFPROTO_UNSPEC,
	.name		= "NFQUEUE",
	.version	= XTABLES_VERSION,
	.size		= XT_ALIGN(sizeof(struct xt_NFQ_info)),
	.userspacesize	= XT_ALIGN(sizeof(struct xt_NFQ_info)),
	.help		= NFQUEUE_help,
	.parse		= NFQUEUE_parse,
	.print		= NFQUEUE_print,
	.save		= NFQUEUE_save,
	.extra_opts	= NFQUEUE_opts
};

static struct xtables_target nfqueue_target_v1 = {
	.family		= NFPROTO_UNSPEC,
	.revision	= 1,
	.name		= "NFQUEUE",
	.version	= XTABLES_VERSION,
	.size		= XT_ALIGN(sizeof(struct xt_NFQ_info_v1)),
	.userspacesize	= XT_ALIGN(sizeof(struct xt_NFQ_info_v1)),
	.help		= NFQUEUE_help_v1,
	.init		= NFQUEUE_init_v1,
	.parse		= NFQUEUE_parse_v1,
	.print		= NFQUEUE_print_v1,
	.save		= NFQUEUE_save_v1,
	.extra_opts	= NFQUEUE_opts,
};

void libxt_NFQUEUE_init(void)
{
	xtables_register_target(&nfqueue_target);
	xtables_register_target(&nfqueue_target_v1);
}
