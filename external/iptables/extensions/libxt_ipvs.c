/*
 * Shared library add-on to iptables to add IPVS matching.
 *
 * Detailed doc is in the kernel module source net/netfilter/xt_ipvs.c
 *
 * Author: Hannes Eder <heder@google.com>
 */
#include <sys/types.h>
#include <assert.h>
#include <ctype.h>
#include <errno.h>
#include <getopt.h>
#include <netdb.h>
#include <stdbool.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <xtables.h>
#include <linux/ip_vs.h>
#include <linux/netfilter/xt_ipvs.h>

static const struct option ipvs_mt_opts[] = {
	{ .name = "ipvs",     .has_arg = false, .val = '0' },
	{ .name = "vproto",   .has_arg = true,  .val = '1' },
	{ .name = "vaddr",    .has_arg = true,  .val = '2' },
	{ .name = "vport",    .has_arg = true,  .val = '3' },
	{ .name = "vdir",     .has_arg = true,  .val = '4' },
	{ .name = "vmethod",  .has_arg = true,  .val = '5' },
	{ .name = "vportctl", .has_arg = true,  .val = '6' },
	XT_GETOPT_TABLEEND,
};

static void ipvs_mt_help(void)
{
	printf(
"IPVS match options:\n"
"[!] --ipvs                      packet belongs to an IPVS connection\n"
"\n"
"Any of the following options implies --ipvs (even negated)\n"
"[!] --vproto protocol           VIP protocol to match; by number or name,\n"
"                                e.g. \"tcp\"\n"
"[!] --vaddr address[/mask]      VIP address to match\n"
"[!] --vport port                VIP port to match; by number or name,\n"
"                                e.g. \"http\"\n"
"    --vdir {ORIGINAL|REPLY}     flow direction of packet\n"
"[!] --vmethod {GATE|IPIP|MASQ}  IPVS forwarding method used\n"
"[!] --vportctl port             VIP port of the controlling connection to\n"
"                                match, e.g. 21 for FTP\n"
		);
}

static void ipvs_mt_parse_addr_and_mask(const char *arg,
					union nf_inet_addr *address,
					union nf_inet_addr *mask,
					unsigned int family)
{
	struct in_addr *addr = NULL;
	struct in6_addr *addr6 = NULL;
	unsigned int naddrs = 0;

	if (family == NFPROTO_IPV4) {
		xtables_ipparse_any(arg, &addr, &mask->in, &naddrs);
		if (naddrs > 1)
			xtables_error(PARAMETER_PROBLEM,
				      "multiple IP addresses not allowed");
		if (naddrs == 1)
			memcpy(&address->in, addr, sizeof(*addr));
	} else if (family == NFPROTO_IPV6) {
		xtables_ip6parse_any(arg, &addr6, &mask->in6, &naddrs);
		if (naddrs > 1)
			xtables_error(PARAMETER_PROBLEM,
				      "multiple IP addresses not allowed");
		if (naddrs == 1)
			memcpy(&address->in6, addr6, sizeof(*addr6));
	} else {
		/* Hu? */
		assert(false);
	}
}

/* Function which parses command options; returns true if it ate an option */
static int ipvs_mt_parse(int c, char **argv, int invert, unsigned int *flags,
			 const void *entry, struct xt_entry_match **match,
			 unsigned int family)
{
	struct xt_ipvs_mtinfo *data = (void *)(*match)->data;
	char *p = NULL;
	u_int8_t op = 0;

	if ('0' <= c && c <= '6') {
		static const int ops[] = {
			XT_IPVS_IPVS_PROPERTY,
			XT_IPVS_PROTO,
			XT_IPVS_VADDR,
			XT_IPVS_VPORT,
			XT_IPVS_DIR,
			XT_IPVS_METHOD,
			XT_IPVS_VPORTCTL
		};
		op = ops[c - '0'];
	} else
		return 0;

	if (*flags & op & XT_IPVS_ONCE_MASK)
		goto multiple_use;

	switch (c) {
	case '0': /* --ipvs */
		/* Nothing to do here. */
		break;

	case '1': /* --vproto */
		/* Canonicalize into lower case */
		for (p = optarg; *p != '\0'; ++p)
			*p = tolower(*p);

		data->l4proto = xtables_parse_protocol(optarg);
		break;

	case '2': /* --vaddr */
		ipvs_mt_parse_addr_and_mask(optarg, &data->vaddr,
					    &data->vmask, family);
		break;

	case '3': /* --vport */
		data->vport = htons(xtables_parse_port(optarg, "tcp"));
		break;

	case '4': /* --vdir */
		xtables_param_act(XTF_NO_INVERT, "ipvs", "--vdir", invert);
		if (strcasecmp(optarg, "ORIGINAL") == 0) {
			data->bitmask |= XT_IPVS_DIR;
			data->invert   &= ~XT_IPVS_DIR;
		} else if (strcasecmp(optarg, "REPLY") == 0) {
			data->bitmask |= XT_IPVS_DIR;
			data->invert  |= XT_IPVS_DIR;
		} else {
			xtables_param_act(XTF_BAD_VALUE,
					  "ipvs", "--vdir", optarg);
		}
		break;

	case '5': /* --vmethod */
		if (strcasecmp(optarg, "GATE") == 0)
			data->fwd_method = IP_VS_CONN_F_DROUTE;
		else if (strcasecmp(optarg, "IPIP") == 0)
			data->fwd_method = IP_VS_CONN_F_TUNNEL;
		else if (strcasecmp(optarg, "MASQ") == 0)
			data->fwd_method = IP_VS_CONN_F_MASQ;
		else
			xtables_param_act(XTF_BAD_VALUE,
					  "ipvs", "--vmethod", optarg);
		break;

	case '6': /* --vportctl */
		data->vportctl = htons(xtables_parse_port(optarg, "tcp"));
		break;

	default:
		/* Hu? How did we come here? */
		assert(false);
		return 0;
	}

	if (op & XT_IPVS_ONCE_MASK) {
		if (data->invert & XT_IPVS_IPVS_PROPERTY)
			xtables_error(PARAMETER_PROBLEM,
				      "! --ipvs cannot be together with"
				      " other options");
		data->bitmask |= XT_IPVS_IPVS_PROPERTY;
	}

	data->bitmask |= op;
	if (invert)
		data->invert |= op;
	*flags |= op;
	return 1;

multiple_use:
	xtables_error(PARAMETER_PROBLEM,
		      "multiple use of the same IPVS option is not allowed");
}

static int ipvs_mt4_parse(int c, char **argv, int invert, unsigned int *flags,
			  const void *entry, struct xt_entry_match **match)
{
	return ipvs_mt_parse(c, argv, invert, flags, entry, match,
			     NFPROTO_IPV4);
}

static int ipvs_mt6_parse(int c, char **argv, int invert, unsigned int *flags,
			  const void *entry, struct xt_entry_match **match)
{
	return ipvs_mt_parse(c, argv, invert, flags, entry, match,
			     NFPROTO_IPV6);
}

static void ipvs_mt_check(unsigned int flags)
{
	if (flags == 0)
		xtables_error(PARAMETER_PROBLEM,
			      "IPVS: At least one option is required");
}

/* Shamelessly copied from libxt_conntrack.c */
static void ipvs_mt_dump_addr(const union nf_inet_addr *addr,
			      const union nf_inet_addr *mask,
			      unsigned int family, bool numeric)
{
	char buf[BUFSIZ];

	if (family == NFPROTO_IPV4) {
		if (!numeric && addr->ip == 0) {
			printf("anywhere ");
			return;
		}
		if (numeric)
			strcpy(buf, xtables_ipaddr_to_numeric(&addr->in));
		else
			strcpy(buf, xtables_ipaddr_to_anyname(&addr->in));
		strcat(buf, xtables_ipmask_to_numeric(&mask->in));
		printf("%s ", buf);
	} else if (family == NFPROTO_IPV6) {
		if (!numeric && addr->ip6[0] == 0 && addr->ip6[1] == 0 &&
		    addr->ip6[2] == 0 && addr->ip6[3] == 0) {
			printf("anywhere ");
			return;
		}
		if (numeric)
			strcpy(buf, xtables_ip6addr_to_numeric(&addr->in6));
		else
			strcpy(buf, xtables_ip6addr_to_anyname(&addr->in6));
		strcat(buf, xtables_ip6mask_to_numeric(&mask->in6));
		printf("%s ", buf);
	}
}

static void ipvs_mt_dump(const void *ip, const struct xt_ipvs_mtinfo *data,
			 unsigned int family, bool numeric, const char *prefix)
{
	if (data->bitmask == XT_IPVS_IPVS_PROPERTY) {
		if (data->invert & XT_IPVS_IPVS_PROPERTY)
			printf("! ");
		printf("%sipvs ", prefix);
	}

	if (data->bitmask & XT_IPVS_PROTO) {
		if (data->invert & XT_IPVS_PROTO)
			printf("! ");
		printf("%sproto %u ", prefix, data->l4proto);
	}

	if (data->bitmask & XT_IPVS_VADDR) {
		if (data->invert & XT_IPVS_VADDR)
			printf("! ");

		printf("%svaddr ", prefix);
		ipvs_mt_dump_addr(&data->vaddr, &data->vmask, family, numeric);
	}

	if (data->bitmask & XT_IPVS_VPORT) {
		if (data->invert & XT_IPVS_VPORT)
			printf("! ");

		printf("%svport %u ", prefix, ntohs(data->vport));
	}

	if (data->bitmask & XT_IPVS_DIR) {
		if (data->invert & XT_IPVS_DIR)
			printf("%svdir REPLY ", prefix);
		else
			printf("%svdir ORIGINAL ", prefix);
	}

	if (data->bitmask & XT_IPVS_METHOD) {
		if (data->invert & XT_IPVS_METHOD)
			printf("! ");

		printf("%svmethod ", prefix);
		switch (data->fwd_method) {
		case IP_VS_CONN_F_DROUTE:
			printf("GATE ");
			break;
		case IP_VS_CONN_F_TUNNEL:
			printf("IPIP ");
			break;
		case IP_VS_CONN_F_MASQ:
			printf("MASQ ");
			break;
		default:
			/* Hu? */
			printf("UNKNOWN ");
			break;
		}
	}

	if (data->bitmask & XT_IPVS_VPORTCTL) {
		if (data->invert & XT_IPVS_VPORTCTL)
			printf("! ");

		printf("%svportctl %u ", prefix, ntohs(data->vportctl));
	}
}

static void ipvs_mt4_print(const void *ip, const struct xt_entry_match *match,
			   int numeric)
{
	const struct xt_ipvs_mtinfo *data = (const void *)match->data;
	ipvs_mt_dump(ip, data, NFPROTO_IPV4, numeric, "");
}

static void ipvs_mt6_print(const void *ip, const struct xt_entry_match *match,
			   int numeric)
{
	const struct xt_ipvs_mtinfo *data = (const void *)match->data;
	ipvs_mt_dump(ip, data, NFPROTO_IPV6, numeric, "");
}

static void ipvs_mt4_save(const void *ip, const struct xt_entry_match *match)
{
	const struct xt_ipvs_mtinfo *data = (const void *)match->data;
	ipvs_mt_dump(ip, data, NFPROTO_IPV4, true, "--");
}

static void ipvs_mt6_save(const void *ip, const struct xt_entry_match *match)
{
	const struct xt_ipvs_mtinfo *data = (const void *)match->data;
	ipvs_mt_dump(ip, data, NFPROTO_IPV6, true, "--");
}

static struct xtables_match ipvs_matches_reg[] = {
	{
		.version       = XTABLES_VERSION,
		.name          = "ipvs",
		.revision      = 0,
		.family        = NFPROTO_IPV4,
		.size          = XT_ALIGN(sizeof(struct xt_ipvs_mtinfo)),
		.userspacesize = XT_ALIGN(sizeof(struct xt_ipvs_mtinfo)),
		.help          = ipvs_mt_help,
		.parse         = ipvs_mt4_parse,
		.final_check   = ipvs_mt_check,
		.print         = ipvs_mt4_print,
		.save          = ipvs_mt4_save,
		.extra_opts    = ipvs_mt_opts,
	},
	{
		.version       = XTABLES_VERSION,
		.name          = "ipvs",
		.revision      = 0,
		.family        = NFPROTO_IPV6,
		.size          = XT_ALIGN(sizeof(struct xt_ipvs_mtinfo)),
		.userspacesize = XT_ALIGN(sizeof(struct xt_ipvs_mtinfo)),
		.help          = ipvs_mt_help,
		.parse         = ipvs_mt6_parse,
		.final_check   = ipvs_mt_check,
		.print         = ipvs_mt6_print,
		.save          = ipvs_mt6_save,
		.extra_opts    = ipvs_mt_opts,
	},
};

void libxt_ipvs_init(void)
{
	xtables_register_matches(ipvs_matches_reg,
				 ARRAY_SIZE(ipvs_matches_reg));
}
