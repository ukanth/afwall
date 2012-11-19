/* Shared library add-on to iptables to add addrtype matching support 
 * 
 * This program is released under the terms of GNU GPL */
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <getopt.h>
#include <xtables.h>

#include <linux/netfilter_ipv4/ipt_addrtype.h>

/* from linux/rtnetlink.h, must match order of enumeration */
static const char *const rtn_names[] = {
	"UNSPEC",
	"UNICAST",
	"LOCAL",
	"BROADCAST",
	"ANYCAST",
	"MULTICAST",
	"BLACKHOLE",
	"UNREACHABLE",
	"PROHIBIT",
	"THROW",
	"NAT",
	"XRESOLVE",
	NULL
};

static void addrtype_help_types(void)
{
	int i;

	for (i = 0; rtn_names[i]; i++)
		printf("                                %s\n", rtn_names[i]);
}

static void addrtype_help_v0(void)
{
	printf(
"Address type match options:\n"
" [!] --src-type type[,...]      Match source address type\n"
" [!] --dst-type type[,...]      Match destination address type\n"
"\n"
"Valid types:           \n");
	addrtype_help_types();
}

static void addrtype_help_v1(void)
{
	printf(
"Address type match options:\n"
" [!] --src-type type[,...]      Match source address type\n"
" [!] --dst-type type[,...]      Match destination address type\n"
"     --limit-iface-in           Match only on the packet's incoming device\n"
"     --limit-iface-out          Match only on the packet's incoming device\n"
"\n"
"Valid types:           \n");
	addrtype_help_types();
}

static int
parse_type(const char *name, size_t len, u_int16_t *mask)
{
	int i;

	for (i = 0; rtn_names[i]; i++)
		if (strncasecmp(name, rtn_names[i], len) == 0) {
			/* build up bitmask for kernel module */
			*mask |= (1 << i);
			return 1;
		}

	return 0;
}

static void parse_types(const char *arg, u_int16_t *mask)
{
	const char *comma;

	while ((comma = strchr(arg, ',')) != NULL) {
		if (comma == arg || !parse_type(arg, comma-arg, mask))
			xtables_error(PARAMETER_PROBLEM,
			           "addrtype: bad type `%s'", arg);
		arg = comma + 1;
	}

	if (strlen(arg) == 0 || !parse_type(arg, strlen(arg), mask))
		xtables_error(PARAMETER_PROBLEM, "addrtype: bad type \"%s\"", arg);
}
	
#define IPT_ADDRTYPE_OPT_SRCTYPE	0x1
#define IPT_ADDRTYPE_OPT_DSTTYPE	0x2
#define IPT_ADDRTYPE_OPT_LIMIT_IFACE_IN		0x4
#define IPT_ADDRTYPE_OPT_LIMIT_IFACE_OUT	0x8

static int
addrtype_parse_v0(int c, char **argv, int invert, unsigned int *flags,
                  const void *entry, struct xt_entry_match **match)
{
	struct ipt_addrtype_info *info =
		(struct ipt_addrtype_info *) (*match)->data;

	switch (c) {
	case '1':
		if (*flags&IPT_ADDRTYPE_OPT_SRCTYPE)
			xtables_error(PARAMETER_PROBLEM,
			           "addrtype: can't specify src-type twice");
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		parse_types(optarg, &info->source);
		if (invert)
			info->invert_source = 1;
		*flags |= IPT_ADDRTYPE_OPT_SRCTYPE;
		break;
	case '2':
		if (*flags&IPT_ADDRTYPE_OPT_DSTTYPE)
			xtables_error(PARAMETER_PROBLEM,
			           "addrtype: can't specify dst-type twice");
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		parse_types(optarg, &info->dest);
		if (invert)
			info->invert_dest = 1;
		*flags |= IPT_ADDRTYPE_OPT_DSTTYPE;
		break;
	default:
		return 0;
	}
	
	return 1;
}

static int
addrtype_parse_v1(int c, char **argv, int invert, unsigned int *flags,
                  const void *entry, struct xt_entry_match **match)
{
	struct ipt_addrtype_info_v1 *info =
		(struct ipt_addrtype_info_v1 *) (*match)->data;

	switch (c) {
	case '1':
		if (*flags & IPT_ADDRTYPE_OPT_SRCTYPE)
			xtables_error(PARAMETER_PROBLEM,
			           "addrtype: can't specify src-type twice");
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		parse_types(optarg, &info->source);
		if (invert)
			info->flags |= IPT_ADDRTYPE_INVERT_SOURCE;
		*flags |= IPT_ADDRTYPE_OPT_SRCTYPE;
		break;
	case '2':
		if (*flags & IPT_ADDRTYPE_OPT_DSTTYPE)
			xtables_error(PARAMETER_PROBLEM,
			           "addrtype: can't specify dst-type twice");
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		parse_types(optarg, &info->dest);
		if (invert)
			info->flags |= IPT_ADDRTYPE_INVERT_DEST;
		*flags |= IPT_ADDRTYPE_OPT_DSTTYPE;
		break;
	case '3':
		if (*flags & IPT_ADDRTYPE_OPT_LIMIT_IFACE_IN)
			xtables_error(PARAMETER_PROBLEM,
			           "addrtype: can't specify limit-iface-in twice");
		info->flags |= IPT_ADDRTYPE_LIMIT_IFACE_IN;
		*flags |= IPT_ADDRTYPE_OPT_LIMIT_IFACE_IN;
		break;
	case '4':
		if (*flags & IPT_ADDRTYPE_OPT_LIMIT_IFACE_OUT)
			xtables_error(PARAMETER_PROBLEM,
			           "addrtype: can't specify limit-iface-out twice");
		info->flags |= IPT_ADDRTYPE_LIMIT_IFACE_OUT;
		*flags |= IPT_ADDRTYPE_OPT_LIMIT_IFACE_OUT;
		break;
	default:
		return 0;
	}
	
	return 1;
}

static void addrtype_check_v0(unsigned int flags)
{
	if (!(flags & (IPT_ADDRTYPE_OPT_SRCTYPE|IPT_ADDRTYPE_OPT_DSTTYPE)))
		xtables_error(PARAMETER_PROBLEM,
			   "addrtype: you must specify --src-type or --dst-type");
}

static void addrtype_check_v1(unsigned int flags)
{
	if (!(flags & (IPT_ADDRTYPE_OPT_SRCTYPE|IPT_ADDRTYPE_OPT_DSTTYPE)))
		xtables_error(PARAMETER_PROBLEM,
			   "addrtype: you must specify --src-type or --dst-type");
	if (flags & IPT_ADDRTYPE_OPT_LIMIT_IFACE_IN &&
	    flags & IPT_ADDRTYPE_OPT_LIMIT_IFACE_OUT)
		xtables_error(PARAMETER_PROBLEM,
			   "addrtype: you can't specify both --limit-iface-in "
			   "and --limit-iface-out");
}

static void print_types(u_int16_t mask)
{
	const char *sep = "";
	int i;

	for (i = 0; rtn_names[i]; i++)
		if (mask & (1 << i)) {
			printf("%s%s", sep, rtn_names[i]);
			sep = ",";
		}

	printf(" ");
}

static void addrtype_print_v0(const void *ip, const struct xt_entry_match *match,
                              int numeric)
{
	const struct ipt_addrtype_info *info = 
		(struct ipt_addrtype_info *) match->data;

	printf("ADDRTYPE match ");
	if (info->source) {
		printf("src-type ");
		if (info->invert_source)
			printf("!");
		print_types(info->source);
	}
	if (info->dest) {
		printf("dst-type ");
		if (info->invert_dest)
			printf("!");
		print_types(info->dest);
	}
}

static void addrtype_print_v1(const void *ip, const struct xt_entry_match *match,
                              int numeric)
{
	const struct ipt_addrtype_info_v1 *info = 
		(struct ipt_addrtype_info_v1 *) match->data;

	printf("ADDRTYPE match ");
	if (info->source) {
		printf("src-type ");
		if (info->flags & IPT_ADDRTYPE_INVERT_SOURCE)
			printf("!");
		print_types(info->source);
	}
	if (info->dest) {
		printf("dst-type ");
		if (info->flags & IPT_ADDRTYPE_INVERT_DEST)
			printf("!");
		print_types(info->dest);
	}
	if (info->flags & IPT_ADDRTYPE_LIMIT_IFACE_IN) {
		printf("limit-in ");
	}
	if (info->flags & IPT_ADDRTYPE_LIMIT_IFACE_OUT) {
		printf("limit-out ");
	}
}

static void addrtype_save_v0(const void *ip, const struct xt_entry_match *match)
{
	const struct ipt_addrtype_info *info =
		(struct ipt_addrtype_info *) match->data;

	if (info->source) {
		if (info->invert_source)
			printf("! ");
		printf("--src-type ");
		print_types(info->source);
	}
	if (info->dest) {
		if (info->invert_dest)
			printf("! ");
		printf("--dst-type ");
		print_types(info->dest);
	}
}

static void addrtype_save_v1(const void *ip, const struct xt_entry_match *match)
{
	const struct ipt_addrtype_info_v1 *info =
		(struct ipt_addrtype_info_v1 *) match->data;

	if (info->source) {
		if (info->flags & IPT_ADDRTYPE_INVERT_SOURCE)
			printf("! ");
		printf("--src-type ");
		print_types(info->source);
	}
	if (info->dest) {
		if (info->flags & IPT_ADDRTYPE_INVERT_DEST)
			printf("! ");
		printf("--dst-type ");
		print_types(info->dest);
	}
	if (info->flags & IPT_ADDRTYPE_LIMIT_IFACE_IN) {
		printf("--limit-iface-in ");
	}
	if (info->flags & IPT_ADDRTYPE_LIMIT_IFACE_OUT) {
		printf("--limit-iface-out ");
	}
}

static const struct option addrtype_opts[] = {
	{.name = "src-type", .has_arg = true, .val = '1'},
	{.name = "dst-type", .has_arg = true, .val = '2'},
	XT_GETOPT_TABLEEND,
};

static const struct option addrtype_opts_v0[] = {
	{.name = "src-type", .has_arg = true, .val = '1'},
	{.name = "dst-type", .has_arg = true, .val = '2'},
	XT_GETOPT_TABLEEND,
};

static const struct option addrtype_opts_v1[] = {
	{.name = "src-type",        .has_arg = true,  .val = '1'},
	{.name = "dst-type",        .has_arg = true,  .val = '2'},
	{.name = "limit-iface-in",  .has_arg = false, .val = '3'},
	{.name = "limit-iface-out", .has_arg = false, .val = '4'},
	XT_GETOPT_TABLEEND,
};

static struct xtables_match addrtype_mt_reg[] = {
	{
		.name          = "addrtype",
		.version       = XTABLES_VERSION,
		.family        = NFPROTO_IPV4,
		.size          = XT_ALIGN(sizeof(struct ipt_addrtype_info)),
		.userspacesize = XT_ALIGN(sizeof(struct ipt_addrtype_info)),
		.help          = addrtype_help_v0,
		.parse         = addrtype_parse_v0,
		.final_check   = addrtype_check_v0,
		.print         = addrtype_print_v0,
		.save          = addrtype_save_v0,
		.extra_opts    = addrtype_opts_v0,
	},
	{
		.name          = "addrtype",
		.revision      = 1,
		.version       = XTABLES_VERSION,
		.family        = NFPROTO_IPV4,
		.size          = XT_ALIGN(sizeof(struct ipt_addrtype_info_v1)),
		.userspacesize = XT_ALIGN(sizeof(struct ipt_addrtype_info_v1)),
		.help          = addrtype_help_v1,
		.parse         = addrtype_parse_v1,
		.final_check   = addrtype_check_v1,
		.print         = addrtype_print_v1,
		.save          = addrtype_save_v1,
		.extra_opts    = addrtype_opts_v1,
	},
};


void libipt_addrtype_init(void) 
{
	xtables_register_matches(addrtype_mt_reg, ARRAY_SIZE(addrtype_mt_reg));
}
