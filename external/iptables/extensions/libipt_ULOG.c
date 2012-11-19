/* Shared library add-on to iptables to add ULOG support.
 * 
 * (C) 2000 by Harald Welte <laforge@gnumonks.org>
 *
 * multipart netlink support based on ideas by Sebastian Zander 
 * 						<zander@fokus.gmd.de>
 *
 * This software is released under the terms of GNU GPL
 * 
 * libipt_ULOG.c,v 1.7 2001/01/30 11:55:02 laforge Exp
 */
#include <stdbool.h>
#include <stdio.h>
#include <netdb.h>
#include <string.h>
#include <stdlib.h>
#include <syslog.h>
#include <getopt.h>
#include <xtables.h>
/* For 64bit kernel / 32bit userspace */
#include <linux/netfilter_ipv4/ipt_ULOG.h>


static void print_groups(unsigned int gmask)
{
	int b;
	unsigned int test;

	for (b = 31; b >= 0; b--) {
		test = (1 << b);
		if (gmask & test)
			printf("%d ", b + 1);
	}
}

static void ULOG_help(void)
{
	printf("ULOG target options:\n"
	       " --ulog-nlgroup nlgroup		NETLINK group used for logging\n"
	       " --ulog-cprange size		Bytes of each packet to be passed\n"
	       " --ulog-qthreshold		Threshold of in-kernel queue\n"
	       " --ulog-prefix prefix		Prefix log messages with this prefix.\n");
}

static const struct option ULOG_opts[] = {
	{.name = "ulog-nlgroup",    .has_arg = true, .val = '!'},
	{.name = "ulog-prefix",     .has_arg = true, .val = '#'},
	{.name = "ulog-cprange",    .has_arg = true, .val = 'A'},
	{.name = "ulog-qthreshold", .has_arg = true, .val = 'B'},
	XT_GETOPT_TABLEEND,
};

static void ULOG_init(struct xt_entry_target *t)
{
	struct ipt_ulog_info *loginfo = (struct ipt_ulog_info *) t->data;

	loginfo->nl_group = ULOG_DEFAULT_NLGROUP;
	loginfo->qthreshold = ULOG_DEFAULT_QTHRESHOLD;

}

#define IPT_LOG_OPT_NLGROUP 0x01
#define IPT_LOG_OPT_PREFIX 0x02
#define IPT_LOG_OPT_CPRANGE 0x04
#define IPT_LOG_OPT_QTHRESHOLD 0x08

static int ULOG_parse(int c, char **argv, int invert, unsigned int *flags,
                      const void *entry, struct xt_entry_target **target)
{
	struct ipt_ulog_info *loginfo =
	    (struct ipt_ulog_info *) (*target)->data;
	int group_d;

	switch (c) {
	case '!':
		if (*flags & IPT_LOG_OPT_NLGROUP)
			xtables_error(PARAMETER_PROBLEM,
				   "Can't specify --ulog-nlgroup twice");

		if (xtables_check_inverse(optarg, &invert, NULL, 0, argv))
			xtables_error(PARAMETER_PROBLEM,
				   "Unexpected `!' after --ulog-nlgroup");
		group_d = atoi(optarg);
		if (group_d > 32 || group_d < 1)
			xtables_error(PARAMETER_PROBLEM,
				   "--ulog-nlgroup has to be between 1 and 32");

		loginfo->nl_group = (1 << (group_d - 1));

		*flags |= IPT_LOG_OPT_NLGROUP;
		break;

	case '#':
		if (*flags & IPT_LOG_OPT_PREFIX)
			xtables_error(PARAMETER_PROBLEM,
				   "Can't specify --ulog-prefix twice");

		if (xtables_check_inverse(optarg, &invert, NULL, 0, argv))
			xtables_error(PARAMETER_PROBLEM,
				   "Unexpected `!' after --ulog-prefix");

		if (strlen(optarg) > sizeof(loginfo->prefix) - 1)
			xtables_error(PARAMETER_PROBLEM,
				   "Maximum prefix length %u for --ulog-prefix",
				   (unsigned int)sizeof(loginfo->prefix) - 1);

		if (strlen(optarg) == 0)
			xtables_error(PARAMETER_PROBLEM,
				   "No prefix specified for --ulog-prefix");

		if (strlen(optarg) != strlen(strtok(optarg, "\n")))
			xtables_error(PARAMETER_PROBLEM,
				   "Newlines not allowed in --ulog-prefix");

		strcpy(loginfo->prefix, optarg);
		*flags |= IPT_LOG_OPT_PREFIX;
		break;
	case 'A':
		if (*flags & IPT_LOG_OPT_CPRANGE)
			xtables_error(PARAMETER_PROBLEM,
				   "Can't specify --ulog-cprange twice");
		if (atoi(optarg) < 0)
			xtables_error(PARAMETER_PROBLEM,
				   "Negative copy range?");
		loginfo->copy_range = atoi(optarg);
		*flags |= IPT_LOG_OPT_CPRANGE;
		break;
	case 'B':
		if (*flags & IPT_LOG_OPT_QTHRESHOLD)
			xtables_error(PARAMETER_PROBLEM,
				   "Can't specify --ulog-qthreshold twice");
		if (atoi(optarg) < 1)
			xtables_error(PARAMETER_PROBLEM,
				   "Negative or zero queue threshold ?");
		if (atoi(optarg) > ULOG_MAX_QLEN)
			xtables_error(PARAMETER_PROBLEM,
				   "Maximum queue length exceeded");
		loginfo->qthreshold = atoi(optarg);
		*flags |= IPT_LOG_OPT_QTHRESHOLD;
		break;
	default:
		return 0;
	}
	return 1;
}

static void ULOG_save(const void *ip, const struct xt_entry_target *target)
{
	const struct ipt_ulog_info *loginfo
	    = (const struct ipt_ulog_info *) target->data;

	if (strcmp(loginfo->prefix, "") != 0) {
		fputs("--ulog-prefix ", stdout);
		xtables_save_string(loginfo->prefix);
	}

	if (loginfo->nl_group != ULOG_DEFAULT_NLGROUP) {
		printf("--ulog-nlgroup ");
		print_groups(loginfo->nl_group);
	}
	if (loginfo->copy_range)
		printf("--ulog-cprange %u ", (unsigned int)loginfo->copy_range);

	if (loginfo->qthreshold != ULOG_DEFAULT_QTHRESHOLD)
		printf("--ulog-qthreshold %u ", (unsigned int)loginfo->qthreshold);
}

static void ULOG_print(const void *ip, const struct xt_entry_target *target,
                       int numeric)
{
	const struct ipt_ulog_info *loginfo
	    = (const struct ipt_ulog_info *) target->data;

	printf("ULOG ");
	printf("copy_range %u nlgroup ", (unsigned int)loginfo->copy_range);
	print_groups(loginfo->nl_group);
	if (strcmp(loginfo->prefix, "") != 0)
		printf("prefix `%s' ", loginfo->prefix);
	printf("queue_threshold %u ", (unsigned int)loginfo->qthreshold);
}

static struct xtables_target ulog_tg_reg = {
	.name		= "ULOG",
	.version	= XTABLES_VERSION,
	.family		= NFPROTO_IPV4,
	.size		= XT_ALIGN(sizeof(struct ipt_ulog_info)),
	.userspacesize	= XT_ALIGN(sizeof(struct ipt_ulog_info)),
	.help		= ULOG_help,
	.init		= ULOG_init,
	.parse		= ULOG_parse,
	.print		= ULOG_print,
	.save		= ULOG_save,
	.extra_opts	= ULOG_opts,
};

void libipt_ULOG_init(void)
{
	xtables_register_target(&ulog_tg_reg);
}
