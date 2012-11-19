/*
 *	libxt_conntrack
 *	Shared library add-on to iptables for conntrack matching support.
 *
 *	GPL (C) 2001  Marc Boucher (marc@mbsi.ca).
 *	Copyright © CC Computer Consultants GmbH, 2007 - 2008
 *	Jan Engelhardt <jengelh@computergmbh.de>
 */
#include <sys/socket.h>
#include <sys/types.h>
#include <ctype.h>
#include <getopt.h>
#include <netdb.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <xtables.h>
#include <linux/netfilter.h>
#include <linux/netfilter/xt_conntrack.h>
#include <linux/netfilter/nf_conntrack_common.h>
#include <arpa/inet.h>

struct ip_conntrack_old_tuple {
	struct {
		__be32 ip;
		union {
			__u16 all;
		} u;
	} src;

	struct {
		__be32 ip;
		union {
			__u16 all;
		} u;

		/* The protocol. */
		__u16 protonum;
	} dst;
};

struct xt_conntrack_info {
	unsigned int statemask, statusmask;

	struct ip_conntrack_old_tuple tuple[IP_CT_DIR_MAX];
	struct in_addr sipmsk[IP_CT_DIR_MAX], dipmsk[IP_CT_DIR_MAX];

	unsigned long expires_min, expires_max;

	/* Flags word */
	u_int8_t flags;
	/* Inverse flags */
	u_int8_t invflags;
};

static void conntrack_mt_help(void)
{
	printf(
"conntrack match options:\n"
"[!] --ctstate {INVALID|ESTABLISHED|NEW|RELATED|UNTRACKED|SNAT|DNAT}[,...]\n"
"                               State(s) to match\n"
"[!] --ctproto proto            Protocol to match; by number or name, e.g. \"tcp\"\n"
"[!] --ctorigsrc address[/mask]\n"
"[!] --ctorigdst address[/mask]\n"
"[!] --ctreplsrc address[/mask]\n"
"[!] --ctrepldst address[/mask]\n"
"                               Original/Reply source/destination address\n"
"[!] --ctorigsrcport port\n"
"[!] --ctorigdstport port\n"
"[!] --ctreplsrcport port\n"
"[!] --ctrepldstport port\n"
"                               TCP/UDP/SCTP orig./reply source/destination port\n"
"[!] --ctstatus {NONE|EXPECTED|SEEN_REPLY|ASSURED|CONFIRMED}[,...]\n"
"                               Status(es) to match\n"
"[!] --ctexpire time[:time]     Match remaining lifetime in seconds against\n"
"                               value or range of values (inclusive)\n"
"    --ctdir {ORIGINAL|REPLY}   Flow direction of packet\n");
}

static const struct option conntrack_mt_opts_v0[] = {
	{.name = "ctstate",   .has_arg = true, .val = '1'},
	{.name = "ctproto",   .has_arg = true, .val = '2'},
	{.name = "ctorigsrc", .has_arg = true, .val = '3'},
	{.name = "ctorigdst", .has_arg = true, .val = '4'},
	{.name = "ctreplsrc", .has_arg = true, .val = '5'},
	{.name = "ctrepldst", .has_arg = true, .val = '6'},
	{.name = "ctstatus",  .has_arg = true, .val = '7'},
	{.name = "ctexpire",  .has_arg = true, .val = '8'},
	XT_GETOPT_TABLEEND,
};

static const struct option conntrack_mt_opts[] = {
	{.name = "ctstate",       .has_arg = true, .val = '1'},
	{.name = "ctproto",       .has_arg = true, .val = '2'},
	{.name = "ctorigsrc",     .has_arg = true, .val = '3'},
	{.name = "ctorigdst",     .has_arg = true, .val = '4'},
	{.name = "ctreplsrc",     .has_arg = true, .val = '5'},
	{.name = "ctrepldst",     .has_arg = true, .val = '6'},
	{.name = "ctstatus",      .has_arg = true, .val = '7'},
	{.name = "ctexpire",      .has_arg = true, .val = '8'},
	{.name = "ctorigsrcport", .has_arg = true, .val = 'a'},
	{.name = "ctorigdstport", .has_arg = true, .val = 'b'},
	{.name = "ctreplsrcport", .has_arg = true, .val = 'c'},
	{.name = "ctrepldstport", .has_arg = true, .val = 'd'},
	{.name = "ctdir",         .has_arg = true, .val = 'e'},
	{.name = NULL},
};

static int
parse_state(const char *state, size_t len, struct xt_conntrack_info *sinfo)
{
	if (strncasecmp(state, "INVALID", len) == 0)
		sinfo->statemask |= XT_CONNTRACK_STATE_INVALID;
	else if (strncasecmp(state, "NEW", len) == 0)
		sinfo->statemask |= XT_CONNTRACK_STATE_BIT(IP_CT_NEW);
	else if (strncasecmp(state, "ESTABLISHED", len) == 0)
		sinfo->statemask |= XT_CONNTRACK_STATE_BIT(IP_CT_ESTABLISHED);
	else if (strncasecmp(state, "RELATED", len) == 0)
		sinfo->statemask |= XT_CONNTRACK_STATE_BIT(IP_CT_RELATED);
	else if (strncasecmp(state, "UNTRACKED", len) == 0)
		sinfo->statemask |= XT_CONNTRACK_STATE_UNTRACKED;
	else if (strncasecmp(state, "SNAT", len) == 0)
		sinfo->statemask |= XT_CONNTRACK_STATE_SNAT;
	else if (strncasecmp(state, "DNAT", len) == 0)
		sinfo->statemask |= XT_CONNTRACK_STATE_DNAT;
	else
		return 0;
	return 1;
}

static void
parse_states(const char *arg, struct xt_conntrack_info *sinfo)
{
	const char *comma;

	while ((comma = strchr(arg, ',')) != NULL) {
		if (comma == arg || !parse_state(arg, comma-arg, sinfo))
			xtables_error(PARAMETER_PROBLEM, "Bad ctstate \"%s\"", arg);
		arg = comma+1;
	}
	if (!*arg)
		xtables_error(PARAMETER_PROBLEM, "\"--ctstate\" requires a list of "
					      "states with no spaces, e.g. "
					      "ESTABLISHED,RELATED");
	if (strlen(arg) == 0 || !parse_state(arg, strlen(arg), sinfo))
		xtables_error(PARAMETER_PROBLEM, "Bad ctstate \"%s\"", arg);
}

static bool
conntrack_ps_state(struct xt_conntrack_mtinfo2 *info, const char *state,
                   size_t z)
{
	if (strncasecmp(state, "INVALID", z) == 0)
		info->state_mask |= XT_CONNTRACK_STATE_INVALID;
	else if (strncasecmp(state, "NEW", z) == 0)
		info->state_mask |= XT_CONNTRACK_STATE_BIT(IP_CT_NEW);
	else if (strncasecmp(state, "ESTABLISHED", z) == 0)
		info->state_mask |= XT_CONNTRACK_STATE_BIT(IP_CT_ESTABLISHED);
	else if (strncasecmp(state, "RELATED", z) == 0)
		info->state_mask |= XT_CONNTRACK_STATE_BIT(IP_CT_RELATED);
	else if (strncasecmp(state, "UNTRACKED", z) == 0)
		info->state_mask |= XT_CONNTRACK_STATE_UNTRACKED;
	else if (strncasecmp(state, "SNAT", z) == 0)
		info->state_mask |= XT_CONNTRACK_STATE_SNAT;
	else if (strncasecmp(state, "DNAT", z) == 0)
		info->state_mask |= XT_CONNTRACK_STATE_DNAT;
	else
		return false;
	return true;
}

static void
conntrack_ps_states(struct xt_conntrack_mtinfo2 *info, const char *arg)
{
	const char *comma;

	while ((comma = strchr(arg, ',')) != NULL) {
		if (comma == arg || !conntrack_ps_state(info, arg, comma - arg))
			xtables_error(PARAMETER_PROBLEM,
			           "Bad ctstate \"%s\"", arg);
		arg = comma + 1;
	}

	if (strlen(arg) == 0 || !conntrack_ps_state(info, arg, strlen(arg)))
		xtables_error(PARAMETER_PROBLEM, "Bad ctstate \"%s\"", arg);
}

static int
parse_status(const char *status, size_t len, struct xt_conntrack_info *sinfo)
{
	if (strncasecmp(status, "NONE", len) == 0)
		sinfo->statusmask |= 0;
	else if (strncasecmp(status, "EXPECTED", len) == 0)
		sinfo->statusmask |= IPS_EXPECTED;
	else if (strncasecmp(status, "SEEN_REPLY", len) == 0)
		sinfo->statusmask |= IPS_SEEN_REPLY;
	else if (strncasecmp(status, "ASSURED", len) == 0)
		sinfo->statusmask |= IPS_ASSURED;
#ifdef IPS_CONFIRMED
	else if (strncasecmp(status, "CONFIRMED", len) == 0)
		sinfo->statusmask |= IPS_CONFIRMED;
#endif
	else
		return 0;
	return 1;
}

static void
parse_statuses(const char *arg, struct xt_conntrack_info *sinfo)
{
	const char *comma;

	while ((comma = strchr(arg, ',')) != NULL) {
		if (comma == arg || !parse_status(arg, comma-arg, sinfo))
			xtables_error(PARAMETER_PROBLEM, "Bad ctstatus \"%s\"", arg);
		arg = comma+1;
	}

	if (strlen(arg) == 0 || !parse_status(arg, strlen(arg), sinfo))
		xtables_error(PARAMETER_PROBLEM, "Bad ctstatus \"%s\"", arg);
}

static bool
conntrack_ps_status(struct xt_conntrack_mtinfo2 *info, const char *status,
                    size_t z)
{
	if (strncasecmp(status, "NONE", z) == 0)
		info->status_mask |= 0;
	else if (strncasecmp(status, "EXPECTED", z) == 0)
		info->status_mask |= IPS_EXPECTED;
	else if (strncasecmp(status, "SEEN_REPLY", z) == 0)
		info->status_mask |= IPS_SEEN_REPLY;
	else if (strncasecmp(status, "ASSURED", z) == 0)
		info->status_mask |= IPS_ASSURED;
	else if (strncasecmp(status, "CONFIRMED", z) == 0)
		info->status_mask |= IPS_CONFIRMED;
	else
		return false;
	return true;
}

static void
conntrack_ps_statuses(struct xt_conntrack_mtinfo2 *info, const char *arg)
{
	const char *comma;

	while ((comma = strchr(arg, ',')) != NULL) {
		if (comma == arg || !conntrack_ps_status(info, arg, comma - arg))
			xtables_error(PARAMETER_PROBLEM,
			           "Bad ctstatus \"%s\"", arg);
		arg = comma + 1;
	}

	if (strlen(arg) == 0 || !conntrack_ps_status(info, arg, strlen(arg)))
		xtables_error(PARAMETER_PROBLEM, "Bad ctstatus \"%s\"", arg);
}

static unsigned long
parse_expire(const char *s)
{
	unsigned int len;

	if (!xtables_strtoui(s, NULL, &len, 0, UINT32_MAX))
		xtables_error(PARAMETER_PROBLEM, "expire value invalid: \"%s\"\n", s);
	else
		return len;
}

/* If a single value is provided, min and max are both set to the value */
static void
parse_expires(const char *s, struct xt_conntrack_info *sinfo)
{
	char *buffer;
	char *cp;

	buffer = strdup(s);
	if ((cp = strchr(buffer, ':')) == NULL)
		sinfo->expires_min = sinfo->expires_max =
			parse_expire(buffer);
	else {
		*cp = '\0';
		cp++;

		sinfo->expires_min = buffer[0] ? parse_expire(buffer) : 0;
		sinfo->expires_max = cp[0]
			? parse_expire(cp)
			: (unsigned long)-1;
	}
	free(buffer);

	if (sinfo->expires_min > sinfo->expires_max)
		xtables_error(PARAMETER_PROBLEM,
		           "expire min. range value `%lu' greater than max. "
		           "range value `%lu'", sinfo->expires_min, sinfo->expires_max);
}

static void
conntrack_ps_expires(struct xt_conntrack_mtinfo2 *info, const char *s)
{
	unsigned int min, max;
	char *end;

	if (!xtables_strtoui(s, &end, &min, 0, UINT32_MAX))
		xtables_param_act(XTF_BAD_VALUE, "conntrack", "--expires", s);
	max = min;
	if (*end == ':')
		if (!xtables_strtoui(end + 1, &end, &max, 0, UINT32_MAX))
			xtables_param_act(XTF_BAD_VALUE, "conntrack", "--expires", s);
	if (*end != '\0')
		xtables_param_act(XTF_BAD_VALUE, "conntrack", "--expires", s);

	if (min > max)
		xtables_error(PARAMETER_PROBLEM,
		           "expire min. range value \"%u\" greater than max. "
		           "range value \"%u\"", min, max);

	info->expires_min = min;
	info->expires_max = max;
}

static int conntrack_parse(int c, char **argv, int invert, unsigned int *flags,
                           const void *entry, struct xt_entry_match **match)
{
	struct xt_conntrack_info *sinfo = (void *)(*match)->data;
	char *protocol = NULL;
	unsigned int naddrs = 0;
	struct in_addr *addrs = NULL;


	switch (c) {
	case '1':
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);

		parse_states(optarg, sinfo);
		if (invert) {
			sinfo->invflags |= XT_CONNTRACK_STATE;
		}
		sinfo->flags |= XT_CONNTRACK_STATE;
		break;

	case '2':
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);

		if(invert)
			sinfo->invflags |= XT_CONNTRACK_PROTO;

		/* Canonicalize into lower case */
		for (protocol = optarg; *protocol; protocol++)
			*protocol = tolower(*protocol);

		protocol = optarg;
		sinfo->tuple[IP_CT_DIR_ORIGINAL].dst.protonum =
			xtables_parse_protocol(protocol);

		if (sinfo->tuple[IP_CT_DIR_ORIGINAL].dst.protonum == 0
		    && (sinfo->invflags & XT_INV_PROTO))
			xtables_error(PARAMETER_PROBLEM,
				   "rule would never match protocol");

		sinfo->flags |= XT_CONNTRACK_PROTO;
		break;

	case '3':
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);

		if (invert)
			sinfo->invflags |= XT_CONNTRACK_ORIGSRC;

		xtables_ipparse_any(optarg, &addrs,
					&sinfo->sipmsk[IP_CT_DIR_ORIGINAL],
					&naddrs);
		if(naddrs > 1)
			xtables_error(PARAMETER_PROBLEM,
				"multiple IP addresses not allowed");

		if(naddrs == 1) {
			sinfo->tuple[IP_CT_DIR_ORIGINAL].src.ip = addrs[0].s_addr;
		}

		sinfo->flags |= XT_CONNTRACK_ORIGSRC;
		break;

	case '4':
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);

		if (invert)
			sinfo->invflags |= XT_CONNTRACK_ORIGDST;

		xtables_ipparse_any(optarg, &addrs,
					&sinfo->dipmsk[IP_CT_DIR_ORIGINAL],
					&naddrs);
		if(naddrs > 1)
			xtables_error(PARAMETER_PROBLEM,
				"multiple IP addresses not allowed");

		if(naddrs == 1) {
			sinfo->tuple[IP_CT_DIR_ORIGINAL].dst.ip = addrs[0].s_addr;
		}

		sinfo->flags |= XT_CONNTRACK_ORIGDST;
		break;

	case '5':
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);

		if (invert)
			sinfo->invflags |= XT_CONNTRACK_REPLSRC;

		xtables_ipparse_any(optarg, &addrs,
					&sinfo->sipmsk[IP_CT_DIR_REPLY],
					&naddrs);
		if(naddrs > 1)
			xtables_error(PARAMETER_PROBLEM,
				"multiple IP addresses not allowed");

		if(naddrs == 1) {
			sinfo->tuple[IP_CT_DIR_REPLY].src.ip = addrs[0].s_addr;
		}

		sinfo->flags |= XT_CONNTRACK_REPLSRC;
		break;

	case '6':
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);

		if (invert)
			sinfo->invflags |= XT_CONNTRACK_REPLDST;

		xtables_ipparse_any(optarg, &addrs,
					&sinfo->dipmsk[IP_CT_DIR_REPLY],
					&naddrs);
		if(naddrs > 1)
			xtables_error(PARAMETER_PROBLEM,
				"multiple IP addresses not allowed");

		if(naddrs == 1) {
			sinfo->tuple[IP_CT_DIR_REPLY].dst.ip = addrs[0].s_addr;
		}

		sinfo->flags |= XT_CONNTRACK_REPLDST;
		break;

	case '7':
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);

		parse_statuses(optarg, sinfo);
		if (invert) {
			sinfo->invflags |= XT_CONNTRACK_STATUS;
		}
		sinfo->flags |= XT_CONNTRACK_STATUS;
		break;

	case '8':
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);

		parse_expires(optarg, sinfo);
		if (invert) {
			sinfo->invflags |= XT_CONNTRACK_EXPIRES;
		}
		sinfo->flags |= XT_CONNTRACK_EXPIRES;
		break;

	default:
		return 0;
	}

	*flags = sinfo->flags;
	return 1;
}

static int
conntrack_mt_parse(int c, bool invert, unsigned int *flags,
                   struct xt_conntrack_mtinfo2 *info)
{
	unsigned int port;
	char *p;

	switch (c) {
	case '1': /* --ctstate */
		conntrack_ps_states(info, optarg);
		info->match_flags |= XT_CONNTRACK_STATE;
		if (invert)
			info->invert_flags |= XT_CONNTRACK_STATE;
		break;

	case '2': /* --ctproto */
		/* Canonicalize into lower case */
		for (p = optarg; *p != '\0'; ++p)
			*p = tolower(*p);
		info->l4proto = xtables_parse_protocol(optarg);

		if (info->l4proto == 0 && (info->invert_flags & XT_INV_PROTO))
			xtables_error(PARAMETER_PROBLEM, "conntrack: rule would "
			           "never match protocol");

		info->match_flags |= XT_CONNTRACK_PROTO;
		if (invert)
			info->invert_flags |= XT_CONNTRACK_PROTO;
		break;

	case '7': /* --ctstatus */
		conntrack_ps_statuses(info, optarg);
		info->match_flags |= XT_CONNTRACK_STATUS;
		if (invert)
			info->invert_flags |= XT_CONNTRACK_STATUS;
		break;

	case '8': /* --ctexpire */
		conntrack_ps_expires(info, optarg);
		info->match_flags |= XT_CONNTRACK_EXPIRES;
		if (invert)
			info->invert_flags |= XT_CONNTRACK_EXPIRES;
		break;

	case 'a': /* --ctorigsrcport */
		if (!xtables_strtoui(optarg, NULL, &port, 0, UINT16_MAX))
			xtables_param_act(XTF_BAD_VALUE, "conntrack",
			          "--ctorigsrcport", optarg);
		info->match_flags |= XT_CONNTRACK_ORIGSRC_PORT;
		info->origsrc_port = htons(port);
		if (invert)
			info->invert_flags |= XT_CONNTRACK_ORIGSRC_PORT;
		break;

	case 'b': /* --ctorigdstport */
		if (!xtables_strtoui(optarg, NULL, &port, 0, UINT16_MAX))
			xtables_param_act(XTF_BAD_VALUE, "conntrack",
			          "--ctorigdstport", optarg);
		info->match_flags |= XT_CONNTRACK_ORIGDST_PORT;
		info->origdst_port = htons(port);
		if (invert)
			info->invert_flags |= XT_CONNTRACK_ORIGDST_PORT;
		break;

	case 'c': /* --ctreplsrcport */
		if (!xtables_strtoui(optarg, NULL, &port, 0, UINT16_MAX))
			xtables_param_act(XTF_BAD_VALUE, "conntrack",
			          "--ctreplsrcport", optarg);
		info->match_flags |= XT_CONNTRACK_REPLSRC_PORT;
		info->replsrc_port = htons(port);
		if (invert)
			info->invert_flags |= XT_CONNTRACK_REPLSRC_PORT;
		break;

	case 'd': /* --ctrepldstport */
		if (!xtables_strtoui(optarg, NULL, &port, 0, UINT16_MAX))
			xtables_param_act(XTF_BAD_VALUE, "conntrack",
			          "--ctrepldstport", optarg);
		info->match_flags |= XT_CONNTRACK_REPLDST_PORT;
		info->repldst_port = htons(port);
		if (invert)
			info->invert_flags |= XT_CONNTRACK_REPLDST_PORT;
		break;

	case 'e': /* --ctdir */
		xtables_param_act(XTF_NO_INVERT, "conntrack", "--ctdir", invert);
		if (strcasecmp(optarg, "ORIGINAL") == 0) {
			info->match_flags  |= XT_CONNTRACK_DIRECTION;
			info->invert_flags &= ~XT_CONNTRACK_DIRECTION;
		} else if (strcasecmp(optarg, "REPLY") == 0) {
			info->match_flags  |= XT_CONNTRACK_DIRECTION;
			info->invert_flags |= XT_CONNTRACK_DIRECTION;
		} else {
			xtables_param_act(XTF_BAD_VALUE, "conntrack", "--ctdir", optarg);
		}
		break;

	default:
		return false;
	}

	*flags = info->match_flags;
	return true;
}

static int
conntrack_mt4_parse(int c, bool invert, unsigned int *flags,
                    struct xt_conntrack_mtinfo2 *info)
{
	struct in_addr *addr = NULL;
	unsigned int naddrs = 0;

	switch (c) {
	case '3': /* --ctorigsrc */
		xtables_ipparse_any(optarg, &addr, &info->origsrc_mask.in,
		                        &naddrs);
		if (naddrs > 1)
			xtables_error(PARAMETER_PROBLEM,
			           "multiple IP addresses not allowed");
		if (naddrs == 1)
			memcpy(&info->origsrc_addr.in, addr, sizeof(*addr));
		info->match_flags |= XT_CONNTRACK_ORIGSRC;
		if (invert)
			info->invert_flags |= XT_CONNTRACK_ORIGSRC;
		break;

	case '4': /* --ctorigdst */
		xtables_ipparse_any(optarg, &addr, &info->origdst_mask.in,
		                        &naddrs);
		if (naddrs > 1)
			xtables_error(PARAMETER_PROBLEM,
			           "multiple IP addresses not allowed");
		if (naddrs == 1)
			memcpy(&info->origdst_addr.in, addr, sizeof(*addr));
		info->match_flags |= XT_CONNTRACK_ORIGDST;
		if (invert)
			info->invert_flags |= XT_CONNTRACK_ORIGDST;
		break;

	case '5': /* --ctreplsrc */
		xtables_ipparse_any(optarg, &addr, &info->replsrc_mask.in,
		                        &naddrs);
		if (naddrs > 1)
			xtables_error(PARAMETER_PROBLEM,
			           "multiple IP addresses not allowed");
		if (naddrs == 1)
			memcpy(&info->replsrc_addr.in, addr, sizeof(*addr));
		info->match_flags |= XT_CONNTRACK_REPLSRC;
		if (invert)
			info->invert_flags |= XT_CONNTRACK_REPLSRC;
		break;

	case '6': /* --ctrepldst */
		xtables_ipparse_any(optarg, &addr, &info->repldst_mask.in,
		                        &naddrs);
		if (naddrs > 1)
			xtables_error(PARAMETER_PROBLEM,
			           "multiple IP addresses not allowed");
		if (naddrs == 1)
			memcpy(&info->repldst_addr.in, addr, sizeof(*addr));
		info->match_flags |= XT_CONNTRACK_REPLDST;
		if (invert)
			info->invert_flags |= XT_CONNTRACK_REPLDST;
		break;


	default:
		return conntrack_mt_parse(c, invert, flags, info);
	}

	*flags = info->match_flags;
	return true;
}

static int
conntrack_mt6_parse(int c, bool invert, unsigned int *flags,
                    struct xt_conntrack_mtinfo2 *info)
{
	struct in6_addr *addr = NULL;
	unsigned int naddrs = 0;

	switch (c) {
	case '3': /* --ctorigsrc */
		xtables_ip6parse_any(optarg, &addr,
		                         &info->origsrc_mask.in6, &naddrs);
		if (naddrs > 1)
			xtables_error(PARAMETER_PROBLEM,
			           "multiple IP addresses not allowed");
		if (naddrs == 1)
			memcpy(&info->origsrc_addr.in6, addr, sizeof(*addr));
		info->match_flags |= XT_CONNTRACK_ORIGSRC;
		if (invert)
			info->invert_flags |= XT_CONNTRACK_ORIGSRC;
		break;

	case '4': /* --ctorigdst */
		xtables_ip6parse_any(optarg, &addr,
		                         &info->origdst_mask.in6, &naddrs);
		if (naddrs > 1)
			xtables_error(PARAMETER_PROBLEM,
			           "multiple IP addresses not allowed");
		if (naddrs == 1)
			memcpy(&info->origdst_addr.in, addr, sizeof(*addr));
		info->match_flags |= XT_CONNTRACK_ORIGDST;
		if (invert)
			info->invert_flags |= XT_CONNTRACK_ORIGDST;
		break;

	case '5': /* --ctreplsrc */
		xtables_ip6parse_any(optarg, &addr,
		                         &info->replsrc_mask.in6, &naddrs);
		if (naddrs > 1)
			xtables_error(PARAMETER_PROBLEM,
			           "multiple IP addresses not allowed");
		if (naddrs == 1)
			memcpy(&info->replsrc_addr.in, addr, sizeof(*addr));
		info->match_flags |= XT_CONNTRACK_REPLSRC;
		if (invert)
			info->invert_flags |= XT_CONNTRACK_REPLSRC;
		break;

	case '6': /* --ctrepldst */
		xtables_ip6parse_any(optarg, &addr,
		                         &info->repldst_mask.in6, &naddrs);
		if (naddrs > 1)
			xtables_error(PARAMETER_PROBLEM,
			           "multiple IP addresses not allowed");
		if (naddrs == 1)
			memcpy(&info->repldst_addr.in, addr, sizeof(*addr));
		info->match_flags |= XT_CONNTRACK_REPLDST;
		if (invert)
			info->invert_flags |= XT_CONNTRACK_REPLDST;
		break;


	default:
		return conntrack_mt_parse(c, invert, flags, info);
	}

	*flags = info->match_flags;
	return true;
}

#define cinfo_transform(r, l) \
	do { \
		memcpy((r), (l), offsetof(typeof(*(l)), state_mask)); \
		(r)->state_mask  = (l)->state_mask; \
		(r)->status_mask = (l)->status_mask; \
	} while (false);

static int
conntrack1_mt4_parse(int c, char **argv, int invert, unsigned int *flags,
                     const void *entry, struct xt_entry_match **match)
{
	struct xt_conntrack_mtinfo1 *info = (void *)(*match)->data;
	struct xt_conntrack_mtinfo2 up;

	cinfo_transform(&up, info);
	if (!conntrack_mt4_parse(c, invert, flags, &up))
		return false;
	cinfo_transform(info, &up);
	return true;
}

static int
conntrack1_mt6_parse(int c, char **argv, int invert, unsigned int *flags,
                     const void *entry, struct xt_entry_match **match)
{
	struct xt_conntrack_mtinfo1 *info = (void *)(*match)->data;
	struct xt_conntrack_mtinfo2 up;

	cinfo_transform(&up, info);
	if (!conntrack_mt6_parse(c, invert, flags, &up))
		return false;
	cinfo_transform(info, &up);
	return true;
}

static int
conntrack2_mt4_parse(int c, char **argv, int invert, unsigned int *flags,
                     const void *entry, struct xt_entry_match **match)
{
	return conntrack_mt4_parse(c, invert, flags, (void *)(*match)->data);
}

static int
conntrack2_mt6_parse(int c, char **argv, int invert, unsigned int *flags,
                     const void *entry, struct xt_entry_match **match)
{
	return conntrack_mt6_parse(c, invert, flags, (void *)(*match)->data);
}

static void conntrack_mt_check(unsigned int flags)
{
	if (flags == 0)
		xtables_error(PARAMETER_PROBLEM, "conntrack: At least one option "
		           "is required");
}

static void
print_state(unsigned int statemask)
{
	const char *sep = "";

	if (statemask & XT_CONNTRACK_STATE_INVALID) {
		printf("%sINVALID", sep);
		sep = ",";
	}
	if (statemask & XT_CONNTRACK_STATE_BIT(IP_CT_NEW)) {
		printf("%sNEW", sep);
		sep = ",";
	}
	if (statemask & XT_CONNTRACK_STATE_BIT(IP_CT_RELATED)) {
		printf("%sRELATED", sep);
		sep = ",";
	}
	if (statemask & XT_CONNTRACK_STATE_BIT(IP_CT_ESTABLISHED)) {
		printf("%sESTABLISHED", sep);
		sep = ",";
	}
	if (statemask & XT_CONNTRACK_STATE_UNTRACKED) {
		printf("%sUNTRACKED", sep);
		sep = ",";
	}
	if (statemask & XT_CONNTRACK_STATE_SNAT) {
		printf("%sSNAT", sep);
		sep = ",";
	}
	if (statemask & XT_CONNTRACK_STATE_DNAT) {
		printf("%sDNAT", sep);
		sep = ",";
	}
	printf(" ");
}

static void
print_status(unsigned int statusmask)
{
	const char *sep = "";

	if (statusmask & IPS_EXPECTED) {
		printf("%sEXPECTED", sep);
		sep = ",";
	}
	if (statusmask & IPS_SEEN_REPLY) {
		printf("%sSEEN_REPLY", sep);
		sep = ",";
	}
	if (statusmask & IPS_ASSURED) {
		printf("%sASSURED", sep);
		sep = ",";
	}
	if (statusmask & IPS_CONFIRMED) {
		printf("%sCONFIRMED", sep);
		sep = ",";
	}
	if (statusmask == 0)
		printf("%sNONE", sep);
	printf(" ");
}

static void
conntrack_dump_addr(const union nf_inet_addr *addr,
                    const union nf_inet_addr *mask,
                    unsigned int family, bool numeric)
{
	if (family == NFPROTO_IPV4) {
		if (!numeric && addr->ip == 0) {
			printf("anywhere ");
			return;
		}
		if (numeric)
			printf("%s%s ",
			       xtables_ipaddr_to_numeric(&addr->in),
			       xtables_ipmask_to_numeric(&mask->in));
		else
			printf("%s%s ",
			       xtables_ipaddr_to_anyname(&addr->in),
			       xtables_ipmask_to_numeric(&mask->in));
	} else if (family == NFPROTO_IPV6) {
		if (!numeric && addr->ip6[0] == 0 && addr->ip6[1] == 0 &&
		    addr->ip6[2] == 0 && addr->ip6[3] == 0) {
			printf("anywhere ");
			return;
		}
		if (numeric)
			printf("%s%s ",
			       xtables_ip6addr_to_numeric(&addr->in6),
			       xtables_ip6mask_to_numeric(&mask->in6));
		else
			printf("%s%s ",
			       xtables_ip6addr_to_anyname(&addr->in6),
			       xtables_ip6mask_to_numeric(&mask->in6));
	}
}

static void
print_addr(const struct in_addr *addr, const struct in_addr *mask,
           int inv, int numeric)
{
	char buf[BUFSIZ];

	if (inv)
	       	printf("! ");

	if (mask->s_addr == 0L && !numeric)
		printf("%s ", "anywhere");
	else {
		if (numeric)
			strcpy(buf, xtables_ipaddr_to_numeric(addr));
		else
			strcpy(buf, xtables_ipaddr_to_anyname(addr));
		strcat(buf, xtables_ipmask_to_numeric(mask));
		printf("%s ", buf);
	}
}

static void
matchinfo_print(const void *ip, const struct xt_entry_match *match, int numeric, const char *optpfx)
{
	const struct xt_conntrack_info *sinfo = (const void *)match->data;

	if(sinfo->flags & XT_CONNTRACK_STATE) {
        	if (sinfo->invflags & XT_CONNTRACK_STATE)
                	printf("! ");
		printf("%sctstate ", optpfx);
		print_state(sinfo->statemask);
	}

	if(sinfo->flags & XT_CONNTRACK_PROTO) {
        	if (sinfo->invflags & XT_CONNTRACK_PROTO)
                	printf("! ");
		printf("%sctproto ", optpfx);
		printf("%u ", sinfo->tuple[IP_CT_DIR_ORIGINAL].dst.protonum);
	}

	if(sinfo->flags & XT_CONNTRACK_ORIGSRC) {
		if (sinfo->invflags & XT_CONNTRACK_ORIGSRC)
			printf("! ");
		printf("%sctorigsrc ", optpfx);

		print_addr(
		    (struct in_addr *)&sinfo->tuple[IP_CT_DIR_ORIGINAL].src.ip,
		    &sinfo->sipmsk[IP_CT_DIR_ORIGINAL],
		    false,
		    numeric);
	}

	if(sinfo->flags & XT_CONNTRACK_ORIGDST) {
		if (sinfo->invflags & XT_CONNTRACK_ORIGDST)
			printf("! ");
		printf("%sctorigdst ", optpfx);

		print_addr(
		    (struct in_addr *)&sinfo->tuple[IP_CT_DIR_ORIGINAL].dst.ip,
		    &sinfo->dipmsk[IP_CT_DIR_ORIGINAL],
		    false,
		    numeric);
	}

	if(sinfo->flags & XT_CONNTRACK_REPLSRC) {
		if (sinfo->invflags & XT_CONNTRACK_REPLSRC)
			printf("! ");
		printf("%sctreplsrc ", optpfx);

		print_addr(
		    (struct in_addr *)&sinfo->tuple[IP_CT_DIR_REPLY].src.ip,
		    &sinfo->sipmsk[IP_CT_DIR_REPLY],
		    false,
		    numeric);
	}

	if(sinfo->flags & XT_CONNTRACK_REPLDST) {
		if (sinfo->invflags & XT_CONNTRACK_REPLDST)
			printf("! ");
		printf("%sctrepldst ", optpfx);

		print_addr(
		    (struct in_addr *)&sinfo->tuple[IP_CT_DIR_REPLY].dst.ip,
		    &sinfo->dipmsk[IP_CT_DIR_REPLY],
		    false,
		    numeric);
	}

	if(sinfo->flags & XT_CONNTRACK_STATUS) {
        	if (sinfo->invflags & XT_CONNTRACK_STATUS)
                	printf("! ");
		printf("%sctstatus ", optpfx);
		print_status(sinfo->statusmask);
	}

	if(sinfo->flags & XT_CONNTRACK_EXPIRES) {
        	if (sinfo->invflags & XT_CONNTRACK_EXPIRES)
                	printf("! ");
		printf("%sctexpire ", optpfx);

        	if (sinfo->expires_max == sinfo->expires_min)
                	printf("%lu ", sinfo->expires_min);
        	else
                	printf("%lu:%lu ", sinfo->expires_min, sinfo->expires_max);
	}

	if (sinfo->flags & XT_CONNTRACK_DIRECTION) {
		if (sinfo->invflags & XT_CONNTRACK_DIRECTION)
			printf("%sctdir REPLY", optpfx);
		else
			printf("%sctdir ORIGINAL", optpfx);
	}

}

static void
conntrack_dump(const struct xt_conntrack_mtinfo2 *info, const char *prefix,
               unsigned int family, bool numeric)
{
	if (info->match_flags & XT_CONNTRACK_STATE) {
		if (info->invert_flags & XT_CONNTRACK_STATE)
			printf("! ");
		printf("%sctstate ", prefix);
		print_state(info->state_mask);
	}

	if (info->match_flags & XT_CONNTRACK_PROTO) {
		if (info->invert_flags & XT_CONNTRACK_PROTO)
			printf("! ");
		printf("%sctproto %u ", prefix, info->l4proto);
	}

	if (info->match_flags & XT_CONNTRACK_ORIGSRC) {
		if (info->invert_flags & XT_CONNTRACK_ORIGSRC)
			printf("! ");
		printf("%sctorigsrc ", prefix);
		conntrack_dump_addr(&info->origsrc_addr, &info->origsrc_mask,
		                    family, numeric);
	}

	if (info->match_flags & XT_CONNTRACK_ORIGDST) {
		if (info->invert_flags & XT_CONNTRACK_ORIGDST)
			printf("! ");
		printf("%sctorigdst ", prefix);
		conntrack_dump_addr(&info->origdst_addr, &info->origdst_mask,
		                    family, numeric);
	}

	if (info->match_flags & XT_CONNTRACK_REPLSRC) {
		if (info->invert_flags & XT_CONNTRACK_REPLSRC)
			printf("! ");
		printf("%sctreplsrc ", prefix);
		conntrack_dump_addr(&info->replsrc_addr, &info->replsrc_mask,
		                    family, numeric);
	}

	if (info->match_flags & XT_CONNTRACK_REPLDST) {
		if (info->invert_flags & XT_CONNTRACK_REPLDST)
			printf("! ");
		printf("%sctrepldst ", prefix);
		conntrack_dump_addr(&info->repldst_addr, &info->repldst_mask,
		                    family, numeric);
	}

	if (info->match_flags & XT_CONNTRACK_ORIGSRC_PORT) {
		if (info->invert_flags & XT_CONNTRACK_ORIGSRC_PORT)
			printf("! ");
		printf("%sctorigsrcport %u ", prefix,
		       ntohs(info->origsrc_port));
	}

	if (info->match_flags & XT_CONNTRACK_ORIGDST_PORT) {
		if (info->invert_flags & XT_CONNTRACK_ORIGDST_PORT)
			printf("! ");
		printf("%sctorigdstport %u ", prefix,
		       ntohs(info->origdst_port));
	}

	if (info->match_flags & XT_CONNTRACK_REPLSRC_PORT) {
		if (info->invert_flags & XT_CONNTRACK_REPLSRC_PORT)
			printf("! ");
		printf("%sctreplsrcport %u ", prefix,
		       ntohs(info->replsrc_port));
	}

	if (info->match_flags & XT_CONNTRACK_REPLDST_PORT) {
		if (info->invert_flags & XT_CONNTRACK_REPLDST_PORT)
			printf("! ");
		printf("%sctrepldstport %u ", prefix,
		       ntohs(info->repldst_port));
	}

	if (info->match_flags & XT_CONNTRACK_STATUS) {
		if (info->invert_flags & XT_CONNTRACK_STATUS)
			printf("! ");
		printf("%sctstatus ", prefix);
		print_status(info->status_mask);
	}

	if (info->match_flags & XT_CONNTRACK_EXPIRES) {
		if (info->invert_flags & XT_CONNTRACK_EXPIRES)
			printf("! ");
		printf("%sctexpire ", prefix);

		if (info->expires_max == info->expires_min)
			printf("%u ", (unsigned int)info->expires_min);
		else
			printf("%u:%u ", (unsigned int)info->expires_min,
			       (unsigned int)info->expires_max);
	}

	if (info->match_flags & XT_CONNTRACK_DIRECTION) {
		if (info->invert_flags & XT_CONNTRACK_DIRECTION)
			printf("%sctdir REPLY", prefix);
		else
			printf("%sctdir ORIGINAL", prefix);
	}
}

static void conntrack_print(const void *ip, const struct xt_entry_match *match,
                            int numeric)
{
	matchinfo_print(ip, match, numeric, "");
}

static void
conntrack1_mt4_print(const void *ip, const struct xt_entry_match *match,
                     int numeric)
{
	const struct xt_conntrack_mtinfo1 *info = (void *)match->data;
	struct xt_conntrack_mtinfo2 up;

	cinfo_transform(&up, info);
	conntrack_dump(&up, "", NFPROTO_IPV4, numeric);
}

static void
conntrack1_mt6_print(const void *ip, const struct xt_entry_match *match,
                     int numeric)
{
	const struct xt_conntrack_mtinfo1 *info = (void *)match->data;
	struct xt_conntrack_mtinfo2 up;

	cinfo_transform(&up, info);
	conntrack_dump(&up, "", NFPROTO_IPV6, numeric);
}

static void
conntrack_mt_print(const void *ip, const struct xt_entry_match *match,
                   int numeric)
{
	conntrack_dump((const void *)match->data, "", NFPROTO_IPV4, numeric);
}

static void
conntrack_mt6_print(const void *ip, const struct xt_entry_match *match,
                    int numeric)
{
	conntrack_dump((const void *)match->data, "", NFPROTO_IPV6, numeric);
}

static void conntrack_save(const void *ip, const struct xt_entry_match *match)
{
	matchinfo_print(ip, match, 1, "--");
}

static void conntrack_mt_save(const void *ip,
                              const struct xt_entry_match *match)
{
	conntrack_dump((const void *)match->data, "--", NFPROTO_IPV4, true);
}

static void conntrack_mt6_save(const void *ip,
                               const struct xt_entry_match *match)
{
	conntrack_dump((const void *)match->data, "--", NFPROTO_IPV6, true);
}

static void
conntrack1_mt4_save(const void *ip, const struct xt_entry_match *match)
{
	const struct xt_conntrack_mtinfo1 *info = (void *)match->data;
	struct xt_conntrack_mtinfo2 up;

	cinfo_transform(&up, info);
	conntrack_dump(&up, "--", NFPROTO_IPV4, true);
}

static void
conntrack1_mt6_save(const void *ip, const struct xt_entry_match *match)
{
	const struct xt_conntrack_mtinfo1 *info = (void *)match->data;
	struct xt_conntrack_mtinfo2 up;

	cinfo_transform(&up, info);
	conntrack_dump(&up, "--", NFPROTO_IPV6, true);
}

static struct xtables_match conntrack_mt_reg[] = {
	{
		.version       = XTABLES_VERSION,
		.name          = "conntrack",
		.revision      = 0,
		.family        = NFPROTO_IPV4,
		.size          = XT_ALIGN(sizeof(struct xt_conntrack_info)),
		.userspacesize = XT_ALIGN(sizeof(struct xt_conntrack_info)),
		.help          = conntrack_mt_help,
		.parse         = conntrack_parse,
		.final_check   = conntrack_mt_check,
		.print         = conntrack_print,
		.save          = conntrack_save,
		.extra_opts    = conntrack_mt_opts_v0,
	},
	{
		.version       = XTABLES_VERSION,
		.name          = "conntrack",
		.revision      = 1,
		.family        = NFPROTO_IPV4,
		.size          = XT_ALIGN(sizeof(struct xt_conntrack_mtinfo1)),
		.userspacesize = XT_ALIGN(sizeof(struct xt_conntrack_mtinfo1)),
		.help          = conntrack_mt_help,
		.parse         = conntrack1_mt4_parse,
		.final_check   = conntrack_mt_check,
		.print         = conntrack1_mt4_print,
		.save          = conntrack1_mt4_save,
		.extra_opts    = conntrack_mt_opts,
	},
	{
		.version       = XTABLES_VERSION,
		.name          = "conntrack",
		.revision      = 1,
		.family        = NFPROTO_IPV6,
		.size          = XT_ALIGN(sizeof(struct xt_conntrack_mtinfo1)),
		.userspacesize = XT_ALIGN(sizeof(struct xt_conntrack_mtinfo1)),
		.help          = conntrack_mt_help,
		.parse         = conntrack1_mt6_parse,
		.final_check   = conntrack_mt_check,
		.print         = conntrack1_mt6_print,
		.save          = conntrack1_mt6_save,
		.extra_opts    = conntrack_mt_opts,
	},
	{
		.version       = XTABLES_VERSION,
		.name          = "conntrack",
		.revision      = 2,
		.family        = NFPROTO_IPV4,
		.size          = XT_ALIGN(sizeof(struct xt_conntrack_mtinfo2)),
		.userspacesize = XT_ALIGN(sizeof(struct xt_conntrack_mtinfo2)),
		.help          = conntrack_mt_help,
		.parse         = conntrack2_mt4_parse,
		.final_check   = conntrack_mt_check,
		.print         = conntrack_mt_print,
		.save          = conntrack_mt_save,
		.extra_opts    = conntrack_mt_opts,
	},
	{
		.version       = XTABLES_VERSION,
		.name          = "conntrack",
		.revision      = 2,
		.family        = NFPROTO_IPV6,
		.size          = XT_ALIGN(sizeof(struct xt_conntrack_mtinfo2)),
		.userspacesize = XT_ALIGN(sizeof(struct xt_conntrack_mtinfo2)),
		.help          = conntrack_mt_help,
		.parse         = conntrack2_mt6_parse,
		.final_check   = conntrack_mt_check,
		.print         = conntrack_mt6_print,
		.save          = conntrack_mt6_save,
		.extra_opts    = conntrack_mt_opts,
	},
};

void libxt_conntrack_init(void)
{
	xtables_register_matches(conntrack_mt_reg, ARRAY_SIZE(conntrack_mt_reg));
}
