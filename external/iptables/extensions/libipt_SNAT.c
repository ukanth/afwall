/* Shared library add-on to iptables to add source-NAT support. */
#include <stdbool.h>
#include <stdio.h>
#include <netdb.h>
#include <string.h>
#include <stdlib.h>
#include <getopt.h>
#include <xtables.h>
#include <iptables.h>
#include <limits.h> /* INT_MAX in ip_tables.h */
#include <linux/netfilter_ipv4/ip_tables.h>
#include <net/netfilter/nf_nat.h>

#define IPT_SNAT_OPT_SOURCE 0x01
#define IPT_SNAT_OPT_RANDOM 0x02

/* Source NAT data consists of a multi-range, indicating where to map
   to. */
struct ipt_natinfo
{
	struct xt_entry_target t;
	struct nf_nat_multi_range mr;
};

static void SNAT_help(void)
{
	printf(
"SNAT target options:\n"
" --to-source <ipaddr>[-<ipaddr>][:port-port]\n"
"				Address to map source to.\n"
"[--random] [--persistent]\n");
}

static const struct option SNAT_opts[] = {
	{.name = "to-source",  .has_arg = true,  .val = '1'},
	{.name = "random",     .has_arg = false, .val = '2'},
	{.name = "persistent", .has_arg = false, .val = '3'},
	XT_GETOPT_TABLEEND,
};

static struct ipt_natinfo *
append_range(struct ipt_natinfo *info, const struct nf_nat_range *range)
{
	unsigned int size;

	/* One rangesize already in struct ipt_natinfo */
	size = XT_ALIGN(sizeof(*info) + info->mr.rangesize * sizeof(*range));

	info = realloc(info, size);
	if (!info)
		xtables_error(OTHER_PROBLEM, "Out of memory\n");

	info->t.u.target_size = size;
	info->mr.range[info->mr.rangesize] = *range;
	info->mr.rangesize++;

	return info;
}

/* Ranges expected in network order. */
static struct xt_entry_target *
parse_to(char *arg, int portok, struct ipt_natinfo *info)
{
	struct nf_nat_range range;
	char *colon, *dash, *error;
	const struct in_addr *ip;

	memset(&range, 0, sizeof(range));
	colon = strchr(arg, ':');

	if (colon) {
		int port;

		if (!portok)
			xtables_error(PARAMETER_PROBLEM,
				   "Need TCP, UDP, SCTP or DCCP with port specification");

		range.flags |= IP_NAT_RANGE_PROTO_SPECIFIED;

		port = atoi(colon+1);
		if (port <= 0 || port > 65535)
			xtables_error(PARAMETER_PROBLEM,
				   "Port `%s' not valid\n", colon+1);

		error = strchr(colon+1, ':');
		if (error)
			xtables_error(PARAMETER_PROBLEM,
				   "Invalid port:port syntax - use dash\n");

		dash = strchr(colon, '-');
		if (!dash) {
			range.min.tcp.port
				= range.max.tcp.port
				= htons(port);
		} else {
			int maxport;

			maxport = atoi(dash + 1);
			if (maxport <= 0 || maxport > 65535)
				xtables_error(PARAMETER_PROBLEM,
					   "Port `%s' not valid\n", dash+1);
			if (maxport < port)
				/* People are stupid. */
				xtables_error(PARAMETER_PROBLEM,
					   "Port range `%s' funky\n", colon+1);
			range.min.tcp.port = htons(port);
			range.max.tcp.port = htons(maxport);
		}
		/* Starts with a colon? No IP info...*/
		if (colon == arg)
			return &(append_range(info, &range)->t);
		*colon = '\0';
	}

	range.flags |= IP_NAT_RANGE_MAP_IPS;
	dash = strchr(arg, '-');
	if (colon && dash && dash > colon)
		dash = NULL;

	if (dash)
		*dash = '\0';

	ip = xtables_numeric_to_ipaddr(arg);
	if (!ip)
		xtables_error(PARAMETER_PROBLEM, "Bad IP address \"%s\"\n",
			   arg);
	range.min_ip = ip->s_addr;
	if (dash) {
		ip = xtables_numeric_to_ipaddr(dash+1);
		if (!ip)
			xtables_error(PARAMETER_PROBLEM, "Bad IP address \"%s\"\n",
				   dash+1);
		range.max_ip = ip->s_addr;
	} else
		range.max_ip = range.min_ip;

	return &(append_range(info, &range)->t);
}

static int SNAT_parse(int c, char **argv, int invert, unsigned int *flags,
                      const void *e, struct xt_entry_target **target)
{
	const struct ipt_entry *entry = e;
	struct ipt_natinfo *info = (void *)*target;
	int portok;

	if (entry->ip.proto == IPPROTO_TCP
	    || entry->ip.proto == IPPROTO_UDP
	    || entry->ip.proto == IPPROTO_SCTP
	    || entry->ip.proto == IPPROTO_DCCP
	    || entry->ip.proto == IPPROTO_ICMP)
		portok = 1;
	else
		portok = 0;

	switch (c) {
	case '1':
		if (xtables_check_inverse(optarg, &invert, NULL, 0, argv))
			xtables_error(PARAMETER_PROBLEM,
				   "Unexpected `!' after --to-source");

		if (*flags & IPT_SNAT_OPT_SOURCE) {
			if (!kernel_version)
				get_kernel_version();
			if (kernel_version > LINUX_VERSION(2, 6, 10))
				xtables_error(PARAMETER_PROBLEM,
					   "Multiple --to-source not supported");
		}
		*target = parse_to(optarg, portok, info);
		/* WTF do we need this for?? */
		if (*flags & IPT_SNAT_OPT_RANDOM)
			info->mr.range[0].flags |= IP_NAT_RANGE_PROTO_RANDOM;
		*flags |= IPT_SNAT_OPT_SOURCE;
		return 1;

	case '2':
		if (*flags & IPT_SNAT_OPT_SOURCE) {
			info->mr.range[0].flags |= IP_NAT_RANGE_PROTO_RANDOM;
			*flags |= IPT_SNAT_OPT_RANDOM;
		} else
			*flags |= IPT_SNAT_OPT_RANDOM;
		return 1;

	case '3':
		info->mr.range[0].flags |= IP_NAT_RANGE_PERSISTENT;
		return 1;

	default:
		return 0;
	}
}

static void SNAT_check(unsigned int flags)
{
	if (!(flags & IPT_SNAT_OPT_SOURCE))
		xtables_error(PARAMETER_PROBLEM,
			   "You must specify --to-source");
}

static void print_range(const struct nf_nat_range *r)
{
	if (r->flags & IP_NAT_RANGE_MAP_IPS) {
		struct in_addr a;

		a.s_addr = r->min_ip;
		printf("%s", xtables_ipaddr_to_numeric(&a));
		if (r->max_ip != r->min_ip) {
			a.s_addr = r->max_ip;
			printf("-%s", xtables_ipaddr_to_numeric(&a));
		}
	}
	if (r->flags & IP_NAT_RANGE_PROTO_SPECIFIED) {
		printf(":");
		printf("%hu", ntohs(r->min.tcp.port));
		if (r->max.tcp.port != r->min.tcp.port)
			printf("-%hu", ntohs(r->max.tcp.port));
	}
}

static void SNAT_print(const void *ip, const struct xt_entry_target *target,
                       int numeric)
{
	const struct ipt_natinfo *info = (const void *)target;
	unsigned int i = 0;

	printf("to:");
	for (i = 0; i < info->mr.rangesize; i++) {
		print_range(&info->mr.range[i]);
		printf(" ");
		if (info->mr.range[i].flags & IP_NAT_RANGE_PROTO_RANDOM)
			printf("random ");
		if (info->mr.range[i].flags & IP_NAT_RANGE_PERSISTENT)
			printf("persistent ");
	}
}

static void SNAT_save(const void *ip, const struct xt_entry_target *target)
{
	const struct ipt_natinfo *info = (const void *)target;
	unsigned int i = 0;

	for (i = 0; i < info->mr.rangesize; i++) {
		printf("--to-source ");
		print_range(&info->mr.range[i]);
		printf(" ");
		if (info->mr.range[i].flags & IP_NAT_RANGE_PROTO_RANDOM)
			printf("--random ");
		if (info->mr.range[i].flags & IP_NAT_RANGE_PERSISTENT)
			printf("--persistent ");
	}
}

static struct xtables_target snat_tg_reg = {
	.name		= "SNAT",
	.version	= XTABLES_VERSION,
	.family		= NFPROTO_IPV4,
	.size		= XT_ALIGN(sizeof(struct nf_nat_multi_range)),
	.userspacesize	= XT_ALIGN(sizeof(struct nf_nat_multi_range)),
	.help		= SNAT_help,
	.parse		= SNAT_parse,
	.final_check	= SNAT_check,
	.print		= SNAT_print,
	.save		= SNAT_save,
	.extra_opts	= SNAT_opts,
};

void libipt_SNAT_init(void)
{
	xtables_register_target(&snat_tg_reg);
}
