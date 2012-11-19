/* Shared library add-on to iptables to add IP range matching support. */
#include <stdbool.h>
#include <stdio.h>
#include <netdb.h>
#include <string.h>
#include <stdlib.h>
#include <getopt.h>

#include <netinet/in.h>
#include <xtables.h>
#include <linux/netfilter.h>
#include <linux/netfilter/xt_iprange.h>

struct ipt_iprange {
	/* Inclusive: network order. */
	__be32 min_ip, max_ip;
};

struct ipt_iprange_info {
	struct ipt_iprange src;
	struct ipt_iprange dst;

	/* Flags from above */
	u_int8_t flags;
};

enum {
	F_SRCIP = 1 << 0,
	F_DSTIP = 1 << 1,
};

static void iprange_mt_help(void)
{
	printf(
"iprange match options:\n"
"[!] --src-range ip[-ip]    Match source IP in the specified range\n"
"[!] --dst-range ip[-ip]    Match destination IP in the specified range\n");
}

static const struct option iprange_mt_opts[] = {
	{.name = "src-range", .has_arg = true, .val = '1'},
	{.name = "dst-range", .has_arg = true, .val = '2'},
	XT_GETOPT_TABLEEND,
};

static void
iprange_parse_spec(const char *from, const char *to, union nf_inet_addr *range,
		   uint8_t family, const char *optname)
{
	const char *spec[2] = {from, to};
	struct in6_addr *ia6;
	struct in_addr *ia4;
	unsigned int i;

	memset(range, 0, sizeof(union nf_inet_addr) * 2);

	if (family == NFPROTO_IPV6) {
		for (i = 0; i < ARRAY_SIZE(spec); ++i) {
			ia6 = xtables_numeric_to_ip6addr(spec[i]);
			if (ia6 == NULL)
				xtables_param_act(XTF_BAD_VALUE, "iprange",
					optname, spec[i]);
			range[i].in6 = *ia6;
		}
	} else {
		for (i = 0; i < ARRAY_SIZE(spec); ++i) {
			ia4 = xtables_numeric_to_ipaddr(spec[i]);
			if (ia4 == NULL)
				xtables_param_act(XTF_BAD_VALUE, "iprange",
					optname, spec[i]);
			range[i].in = *ia4;
		}
	}
}

static void iprange_parse_range(char *arg, union nf_inet_addr *range,
				u_int8_t family, const char *optname)
{
	char *dash;

	dash = strchr(arg, '-');
	if (dash == NULL) {
		iprange_parse_spec(arg, arg, range, family, optname);
		return;
	}

	*dash = '\0';
	iprange_parse_spec(arg, dash + 1, range, family, optname);
	if (memcmp(&range[0], &range[1], sizeof(*range)) > 0)
		fprintf(stderr, "xt_iprange: range %s-%s is reversed and "
			"will never match\n", arg, dash + 1);
}

static int iprange_parse(int c, char **argv, int invert, unsigned int *flags,
                         const void *entry, struct xt_entry_match **match)
{
	struct ipt_iprange_info *info = (struct ipt_iprange_info *)(*match)->data;
	union nf_inet_addr range[2];

	switch (c) {
	case '1':
		if (*flags & IPRANGE_SRC)
			xtables_error(PARAMETER_PROBLEM,
				   "iprange match: Only use --src-range ONCE!");
		*flags |= IPRANGE_SRC;

		info->flags |= IPRANGE_SRC;
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		if (invert)
			info->flags |= IPRANGE_SRC_INV;
		iprange_parse_range(optarg, range, NFPROTO_IPV4, "--src-range");
		info->src.min_ip = range[0].ip;
		info->src.max_ip = range[1].ip;
		break;

	case '2':
		if (*flags & IPRANGE_DST)
			xtables_error(PARAMETER_PROBLEM,
				   "iprange match: Only use --dst-range ONCE!");
		*flags |= IPRANGE_DST;

		info->flags |= IPRANGE_DST;
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		if (invert)
			info->flags |= IPRANGE_DST_INV;

		iprange_parse_range(optarg, range, NFPROTO_IPV4, "--dst-range");
		info->dst.min_ip = range[0].ip;
		info->dst.max_ip = range[1].ip;
		break;

	default:
		return 0;
	}
	return 1;
}

static int
iprange_mt4_parse(int c, char **argv, int invert, unsigned int *flags,
                  const void *entry, struct xt_entry_match **match)
{
	struct xt_iprange_mtinfo *info = (void *)(*match)->data;

	switch (c) {
	case '1': /* --src-range */
		iprange_parse_range(optarg, &info->src_min, NFPROTO_IPV4,
			"--src-range");
		info->flags |= IPRANGE_SRC;
		if (invert)
			info->flags |= IPRANGE_SRC_INV;
		*flags |= F_SRCIP;
		return true;

	case '2': /* --dst-range */
		iprange_parse_range(optarg, &info->dst_min, NFPROTO_IPV4,
			"--dst-range");
		info->flags |= IPRANGE_DST;
		if (invert)
			info->flags |= IPRANGE_DST_INV;
		*flags |= F_DSTIP;
		return true;
	}
	return false;
}

static int
iprange_mt6_parse(int c, char **argv, int invert, unsigned int *flags,
                  const void *entry, struct xt_entry_match **match)
{
	struct xt_iprange_mtinfo *info = (void *)(*match)->data;

	switch (c) {
	case '1': /* --src-range */
		iprange_parse_range(optarg, &info->src_min, NFPROTO_IPV6,
			"--src-range");
		info->flags |= IPRANGE_SRC;
		if (invert)
			info->flags |= IPRANGE_SRC_INV;
		*flags |= F_SRCIP;
		return true;

	case '2': /* --dst-range */
		iprange_parse_range(optarg, &info->dst_min, NFPROTO_IPV6,
			"--dst-range");
		info->flags |= IPRANGE_DST;
		if (invert)
			info->flags |= IPRANGE_DST_INV;
		*flags |= F_DSTIP;
		return true;
	}
	return false;
}

static void iprange_mt_check(unsigned int flags)
{
	if (flags == 0)
		xtables_error(PARAMETER_PROBLEM,
			   "iprange match: You must specify `--src-range' or `--dst-range'");
}

static void
print_iprange(const struct ipt_iprange *range)
{
	const unsigned char *byte_min, *byte_max;

	byte_min = (const unsigned char *)&range->min_ip;
	byte_max = (const unsigned char *)&range->max_ip;
	printf("%u.%u.%u.%u-%u.%u.%u.%u ",
		byte_min[0], byte_min[1], byte_min[2], byte_min[3],
		byte_max[0], byte_max[1], byte_max[2], byte_max[3]);
}

static void iprange_print(const void *ip, const struct xt_entry_match *match,
                          int numeric)
{
	const struct ipt_iprange_info *info = (const void *)match->data;

	if (info->flags & IPRANGE_SRC) {
		printf("source IP range ");
		if (info->flags & IPRANGE_SRC_INV)
			printf("! ");
		print_iprange(&info->src);
	}
	if (info->flags & IPRANGE_DST) {
		printf("destination IP range ");
		if (info->flags & IPRANGE_DST_INV)
			printf("! ");
		print_iprange(&info->dst);
	}
}

static void
iprange_mt4_print(const void *ip, const struct xt_entry_match *match,
                  int numeric)
{
	const struct xt_iprange_mtinfo *info = (const void *)match->data;

	if (info->flags & IPRANGE_SRC) {
		printf("source IP range ");
		if (info->flags & IPRANGE_SRC_INV)
			printf("! ");
		/*
		 * ipaddr_to_numeric() uses a static buffer, so cannot
		 * combine the printf() calls.
		 */
		printf("%s", xtables_ipaddr_to_numeric(&info->src_min.in));
		printf("-%s ", xtables_ipaddr_to_numeric(&info->src_max.in));
	}
	if (info->flags & IPRANGE_DST) {
		printf("destination IP range ");
		if (info->flags & IPRANGE_DST_INV)
			printf("! ");
		printf("%s", xtables_ipaddr_to_numeric(&info->dst_min.in));
		printf("-%s ", xtables_ipaddr_to_numeric(&info->dst_max.in));
	}
}

static void
iprange_mt6_print(const void *ip, const struct xt_entry_match *match,
                  int numeric)
{
	const struct xt_iprange_mtinfo *info = (const void *)match->data;

	if (info->flags & IPRANGE_SRC) {
		printf("source IP range ");
		if (info->flags & IPRANGE_SRC_INV)
			printf("! ");
		/*
		 * ipaddr_to_numeric() uses a static buffer, so cannot
		 * combine the printf() calls.
		 */
		printf("%s", xtables_ip6addr_to_numeric(&info->src_min.in6));
		printf("-%s ", xtables_ip6addr_to_numeric(&info->src_max.in6));
	}
	if (info->flags & IPRANGE_DST) {
		printf("destination IP range ");
		if (info->flags & IPRANGE_DST_INV)
			printf("! ");
		printf("%s", xtables_ip6addr_to_numeric(&info->dst_min.in6));
		printf("-%s ", xtables_ip6addr_to_numeric(&info->dst_max.in6));
	}
}

static void iprange_save(const void *ip, const struct xt_entry_match *match)
{
	const struct ipt_iprange_info *info = (const void *)match->data;

	if (info->flags & IPRANGE_SRC) {
		if (info->flags & IPRANGE_SRC_INV)
			printf("! ");
		printf("--src-range ");
		print_iprange(&info->src);
		if (info->flags & IPRANGE_DST)
			fputc(' ', stdout);
	}
	if (info->flags & IPRANGE_DST) {
		if (info->flags & IPRANGE_DST_INV)
			printf("! ");
		printf("--dst-range ");
		print_iprange(&info->dst);
	}
}

static void iprange_mt4_save(const void *ip, const struct xt_entry_match *match)
{
	const struct xt_iprange_mtinfo *info = (const void *)match->data;

	if (info->flags & IPRANGE_SRC) {
		if (info->flags & IPRANGE_SRC_INV)
			printf("! ");
		printf("--src-range %s", xtables_ipaddr_to_numeric(&info->src_min.in));
		printf("-%s ", xtables_ipaddr_to_numeric(&info->src_max.in));
	}
	if (info->flags & IPRANGE_DST) {
		if (info->flags & IPRANGE_DST_INV)
			printf("! ");
		printf("--dst-range %s", xtables_ipaddr_to_numeric(&info->dst_min.in));
		printf("-%s ", xtables_ipaddr_to_numeric(&info->dst_max.in));
	}
}

static void iprange_mt6_save(const void *ip, const struct xt_entry_match *match)
{
	const struct xt_iprange_mtinfo *info = (const void *)match->data;

	if (info->flags & IPRANGE_SRC) {
		if (info->flags & IPRANGE_SRC_INV)
			printf("! ");
		printf("--src-range %s", xtables_ip6addr_to_numeric(&info->src_min.in6));
		printf("-%s ", xtables_ip6addr_to_numeric(&info->src_max.in6));
	}
	if (info->flags & IPRANGE_DST) {
		if (info->flags & IPRANGE_DST_INV)
			printf("! ");
		printf("--dst-range %s", xtables_ip6addr_to_numeric(&info->dst_min.in6));
		printf("-%s ", xtables_ip6addr_to_numeric(&info->dst_max.in6));
	}
}

static struct xtables_match iprange_mt_reg[] = {
	{
		.version       = XTABLES_VERSION,
		.name          = "iprange",
		.revision      = 0,
		.family        = NFPROTO_IPV4,
		.size          = XT_ALIGN(sizeof(struct ipt_iprange_info)),
		.userspacesize = XT_ALIGN(sizeof(struct ipt_iprange_info)),
		.help          = iprange_mt_help,
		.parse         = iprange_parse,
		.final_check   = iprange_mt_check,
		.print         = iprange_print,
		.save          = iprange_save,
		.extra_opts    = iprange_mt_opts,
	},
	{
		.version       = XTABLES_VERSION,
		.name          = "iprange",
		.revision      = 1,
		.family        = NFPROTO_IPV4,
		.size          = XT_ALIGN(sizeof(struct xt_iprange_mtinfo)),
		.userspacesize = XT_ALIGN(sizeof(struct xt_iprange_mtinfo)),
		.help          = iprange_mt_help,
		.parse         = iprange_mt4_parse,
		.final_check   = iprange_mt_check,
		.print         = iprange_mt4_print,
		.save          = iprange_mt4_save,
		.extra_opts    = iprange_mt_opts,
	},
	{
		.version       = XTABLES_VERSION,
		.name          = "iprange",
		.revision      = 1,
		.family        = NFPROTO_IPV6,
		.size          = XT_ALIGN(sizeof(struct xt_iprange_mtinfo)),
		.userspacesize = XT_ALIGN(sizeof(struct xt_iprange_mtinfo)),
		.help          = iprange_mt_help,
		.parse         = iprange_mt6_parse,
		.final_check   = iprange_mt_check,
		.print         = iprange_mt6_print,
		.save          = iprange_mt6_save,
		.extra_opts    = iprange_mt_opts,
	},
};

void libxt_iprange_init(void)
{
	xtables_register_matches(iprange_mt_reg, ARRAY_SIZE(iprange_mt_reg));
}
