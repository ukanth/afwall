/* Shared library add-on to iptables to add tcp MSS matching support. */
#include <stdbool.h>
#include <stdio.h>
#include <netdb.h>
#include <string.h>
#include <stdlib.h>
#include <getopt.h>

#include <xtables.h>
#include <linux/netfilter/xt_tcpmss.h>

static void tcpmss_help(void)
{
	printf(
"tcpmss match options:\n"
"[!] --mss value[:value]	Match TCP MSS range.\n"
"				(only valid for TCP SYN or SYN/ACK packets)\n");
}

static const struct option tcpmss_opts[] = {
	{.name = "mss", .has_arg = true, .val = '1'},
	XT_GETOPT_TABLEEND,
};

static u_int16_t
parse_tcp_mssvalue(const char *mssvalue)
{
	unsigned int mssvaluenum;

	if (xtables_strtoui(mssvalue, NULL, &mssvaluenum, 0, UINT16_MAX))
		return mssvaluenum;

	xtables_error(PARAMETER_PROBLEM,
		   "Invalid mss `%s' specified", mssvalue);
}

static void
parse_tcp_mssvalues(const char *mssvaluestring,
		    u_int16_t *mss_min, u_int16_t *mss_max)
{
	char *buffer;
	char *cp;

	buffer = strdup(mssvaluestring);
	if ((cp = strchr(buffer, ':')) == NULL)
		*mss_min = *mss_max = parse_tcp_mssvalue(buffer);
	else {
		*cp = '\0';
		cp++;

		*mss_min = buffer[0] ? parse_tcp_mssvalue(buffer) : 0;
		*mss_max = cp[0] ? parse_tcp_mssvalue(cp) : 0xFFFF;
	}
	free(buffer);
}

static int
tcpmss_parse(int c, char **argv, int invert, unsigned int *flags,
             const void *entry, struct xt_entry_match **match)
{
	struct xt_tcpmss_match_info *mssinfo =
		(struct xt_tcpmss_match_info *)(*match)->data;

	switch (c) {
	case '1':
		if (*flags)
			xtables_error(PARAMETER_PROBLEM,
				   "Only one `--mss' allowed");
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		parse_tcp_mssvalues(optarg,
				    &mssinfo->mss_min, &mssinfo->mss_max);
		if (invert)
			mssinfo->invert = 1;
		*flags = 1;
		break;
	default:
		return 0;
	}
	return 1;
}

static void tcpmss_check(unsigned int flags)
{
	if (!flags)
		xtables_error(PARAMETER_PROBLEM,
			   "tcpmss match: You must specify `--mss'");
}

static void
tcpmss_print(const void *ip, const struct xt_entry_match *match, int numeric)
{
	const struct xt_tcpmss_match_info *info = (void *)match->data;

	printf("tcpmss match %s", info->invert ? "!" : "");
	if (info->mss_min == info->mss_max)
		printf("%u ", info->mss_min);
	else
		printf("%u:%u ", info->mss_min, info->mss_max);
}

static void tcpmss_save(const void *ip, const struct xt_entry_match *match)
{
	const struct xt_tcpmss_match_info *info = (void *)match->data;

	printf("%s--mss ", info->invert ? "! " : "");
	if (info->mss_min == info->mss_max)
		printf("%u ", info->mss_min);
	else
		printf("%u:%u ", info->mss_min, info->mss_max);
}

static struct xtables_match tcpmss_match = {
	.family		= NFPROTO_UNSPEC,
	.name		= "tcpmss",
	.version	= XTABLES_VERSION,
	.size		= XT_ALIGN(sizeof(struct xt_tcpmss_match_info)),
	.userspacesize	= XT_ALIGN(sizeof(struct xt_tcpmss_match_info)),
	.help		= tcpmss_help,
	.parse		= tcpmss_parse,
	.final_check	= tcpmss_check,
	.print		= tcpmss_print,
	.save		= tcpmss_save,
	.extra_opts	= tcpmss_opts,
};

void libxt_tcpmss_init(void)
{
	xtables_register_match(&tcpmss_match);
}
