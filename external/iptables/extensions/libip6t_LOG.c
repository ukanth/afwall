/* Shared library add-on to ip6tables to add LOG support. */
#include <stdbool.h>
#include <stdio.h>
#include <netdb.h>
#include <string.h>
#include <stdlib.h>
#include <syslog.h>
#include <getopt.h>
#include <xtables.h>
#include <linux/netfilter_ipv6/ip6t_LOG.h>

#ifndef IP6T_LOG_UID	/* Old kernel */
#define IP6T_LOG_UID	0x08
#undef  IP6T_LOG_MASK
#define IP6T_LOG_MASK	0x0f
#endif

#define LOG_DEFAULT_LEVEL LOG_WARNING

static void LOG_help(void)
{
	printf(
"LOG target options:\n"
" --log-level level		Level of logging (numeric or see syslog.conf)\n"
" --log-prefix prefix		Prefix log messages with this prefix.\n"
" --log-tcp-sequence		Log TCP sequence numbers.\n"
" --log-tcp-options		Log TCP options.\n"
" --log-ip-options		Log IP options.\n"
" --log-uid			Log UID owning the local socket.\n"
" --log-macdecode		Decode MAC addresses and protocol.\n");
}

static const struct option LOG_opts[] = {
	{.name = "log-level",        .has_arg = true,  .val = '!'},
	{.name = "log-prefix",       .has_arg = true,  .val = '#'},
	{.name = "log-tcp-sequence", .has_arg = false, .val = '1'},
	{.name = "log-tcp-options",  .has_arg = false, .val = '2'},
	{.name = "log-ip-options",   .has_arg = false, .val = '3'},
	{.name = "log-uid",          .has_arg = false, .val = '4'},
	{.name = "log-macdecode",    .has_arg = false, .val = '5'},
	XT_GETOPT_TABLEEND,
};

static void LOG_init(struct xt_entry_target *t)
{
	struct ip6t_log_info *loginfo = (struct ip6t_log_info *)t->data;

	loginfo->level = LOG_DEFAULT_LEVEL;

}

struct ip6t_log_names {
	const char *name;
	unsigned int level;
};

static const struct ip6t_log_names ip6t_log_names[]
= { { .name = "alert",   .level = LOG_ALERT },
    { .name = "crit",    .level = LOG_CRIT },
    { .name = "debug",   .level = LOG_DEBUG },
    { .name = "emerg",   .level = LOG_EMERG },
    { .name = "error",   .level = LOG_ERR },		/* DEPRECATED */
    { .name = "info",    .level = LOG_INFO },
    { .name = "notice",  .level = LOG_NOTICE },
    { .name = "panic",   .level = LOG_EMERG },		/* DEPRECATED */
    { .name = "warning", .level = LOG_WARNING }
};

static u_int8_t
parse_level(const char *level)
{
	unsigned int lev = -1;
	unsigned int set = 0;

	if (!xtables_strtoui(level, NULL, &lev, 0, 7)) {
		unsigned int i = 0;

		for (i = 0; i < ARRAY_SIZE(ip6t_log_names); ++i)
			if (strncasecmp(level, ip6t_log_names[i].name,
					strlen(level)) == 0) {
				if (set++)
					xtables_error(PARAMETER_PROBLEM,
						   "log-level `%s' ambiguous",
						   level);
				lev = ip6t_log_names[i].level;
			}

		if (!set)
			xtables_error(PARAMETER_PROBLEM,
				   "log-level `%s' unknown", level);
	}

	return lev;
}

#define IP6T_LOG_OPT_LEVEL 0x01
#define IP6T_LOG_OPT_PREFIX 0x02
#define IP6T_LOG_OPT_TCPSEQ 0x04
#define IP6T_LOG_OPT_TCPOPT 0x08
#define IP6T_LOG_OPT_IPOPT 0x10
#define IP6T_LOG_OPT_UID 0x20
#define IP6T_LOG_OPT_MACDECODE 0x40

static int LOG_parse(int c, char **argv, int invert, unsigned int *flags,
                     const void *entry, struct xt_entry_target **target)
{
	struct ip6t_log_info *loginfo = (struct ip6t_log_info *)(*target)->data;

	switch (c) {
	case '!':
		if (*flags & IP6T_LOG_OPT_LEVEL)
			xtables_error(PARAMETER_PROBLEM,
				   "Can't specify --log-level twice");

		if (xtables_check_inverse(optarg, &invert, NULL, 0, argv))
			xtables_error(PARAMETER_PROBLEM,
				   "Unexpected `!' after --log-level");

		loginfo->level = parse_level(optarg);
		*flags |= IP6T_LOG_OPT_LEVEL;
		break;

	case '#':
		if (*flags & IP6T_LOG_OPT_PREFIX)
			xtables_error(PARAMETER_PROBLEM,
				   "Can't specify --log-prefix twice");

		if (xtables_check_inverse(optarg, &invert, NULL, 0, argv))
			xtables_error(PARAMETER_PROBLEM,
				   "Unexpected `!' after --log-prefix");

		if (strlen(optarg) > sizeof(loginfo->prefix) - 1)
			xtables_error(PARAMETER_PROBLEM,
				   "Maximum prefix length %u for --log-prefix",
				   (unsigned int)sizeof(loginfo->prefix) - 1);

		if (strlen(optarg) == 0)
			xtables_error(PARAMETER_PROBLEM,
				   "No prefix specified for --log-prefix");

		if (strlen(optarg) != strlen(strtok(optarg, "\n")))
			xtables_error(PARAMETER_PROBLEM,
				   "Newlines not allowed in --log-prefix");

		strcpy(loginfo->prefix, optarg);
		*flags |= IP6T_LOG_OPT_PREFIX;
		break;

	case '1':
		if (*flags & IP6T_LOG_OPT_TCPSEQ)
			xtables_error(PARAMETER_PROBLEM,
				   "Can't specify --log-tcp-sequence "
				   "twice");

		loginfo->logflags |= IP6T_LOG_TCPSEQ;
		*flags |= IP6T_LOG_OPT_TCPSEQ;
		break;

	case '2':
		if (*flags & IP6T_LOG_OPT_TCPOPT)
			xtables_error(PARAMETER_PROBLEM,
				   "Can't specify --log-tcp-options twice");

		loginfo->logflags |= IP6T_LOG_TCPOPT;
		*flags |= IP6T_LOG_OPT_TCPOPT;
		break;

	case '3':
		if (*flags & IP6T_LOG_OPT_IPOPT)
			xtables_error(PARAMETER_PROBLEM,
				   "Can't specify --log-ip-options twice");

		loginfo->logflags |= IP6T_LOG_IPOPT;
		*flags |= IP6T_LOG_OPT_IPOPT;
		break;

	case '4':
		if (*flags & IP6T_LOG_OPT_UID)
			xtables_error(PARAMETER_PROBLEM,
				   "Can't specify --log-uid twice");

		loginfo->logflags |= IP6T_LOG_UID;
		*flags |= IP6T_LOG_OPT_UID;
		break;

	case '5':
		if (*flags & IP6T_LOG_OPT_MACDECODE)
			xtables_error(PARAMETER_PROBLEM,
				      "Can't specify --log-macdecode twice");

		loginfo->logflags |= IP6T_LOG_MACDECODE;
		*flags |= IP6T_LOG_OPT_MACDECODE;
		break;

	default:
		return 0;
	}

	return 1;
}

static void LOG_print(const void *ip, const struct xt_entry_target *target,
                      int numeric)
{
	const struct ip6t_log_info *loginfo
		= (const struct ip6t_log_info *)target->data;
	unsigned int i = 0;

	printf("LOG ");
	if (numeric)
		printf("flags %u level %u ",
		       loginfo->logflags, loginfo->level);
	else {
		for (i = 0; i < ARRAY_SIZE(ip6t_log_names); ++i)
			if (loginfo->level == ip6t_log_names[i].level) {
				printf("level %s ", ip6t_log_names[i].name);
				break;
			}
		if (i == ARRAY_SIZE(ip6t_log_names))
			printf("UNKNOWN level %u ", loginfo->level);
		if (loginfo->logflags & IP6T_LOG_TCPSEQ)
			printf("tcp-sequence ");
		if (loginfo->logflags & IP6T_LOG_TCPOPT)
			printf("tcp-options ");
		if (loginfo->logflags & IP6T_LOG_IPOPT)
			printf("ip-options ");
		if (loginfo->logflags & IP6T_LOG_UID)
			printf("uid ");
		if (loginfo->logflags & IP6T_LOG_MACDECODE)
			printf("macdecode ");
		if (loginfo->logflags & ~(IP6T_LOG_MASK))
			printf("unknown-flags ");
	}

	if (strcmp(loginfo->prefix, "") != 0)
		printf("prefix `%s' ", loginfo->prefix);
}

static void LOG_save(const void *ip, const struct xt_entry_target *target)
{
	const struct ip6t_log_info *loginfo
		= (const struct ip6t_log_info *)target->data;

	if (strcmp(loginfo->prefix, "") != 0)
		printf("--log-prefix \"%s\" ", loginfo->prefix);

	if (loginfo->level != LOG_DEFAULT_LEVEL)
		printf("--log-level %d ", loginfo->level);

	if (loginfo->logflags & IP6T_LOG_TCPSEQ)
		printf("--log-tcp-sequence ");
	if (loginfo->logflags & IP6T_LOG_TCPOPT)
		printf("--log-tcp-options ");
	if (loginfo->logflags & IP6T_LOG_IPOPT)
		printf("--log-ip-options ");
	if (loginfo->logflags & IP6T_LOG_UID)
		printf("--log-uid ");
	if (loginfo->logflags & IP6T_LOG_MACDECODE)
		printf("--log-macdecode ");
}

static struct xtables_target log_tg6_reg = {
    .name          = "LOG",
    .version       = XTABLES_VERSION,
    .family        = NFPROTO_IPV6,
    .size          = XT_ALIGN(sizeof(struct ip6t_log_info)),
    .userspacesize = XT_ALIGN(sizeof(struct ip6t_log_info)),
    .help          = LOG_help,
    .init          = LOG_init,
    .parse         = LOG_parse,
    .print         = LOG_print,
    .save          = LOG_save,
    .extra_opts    = LOG_opts,
};

void _init(void)
{
	xtables_register_target(&log_tg6_reg);
}
