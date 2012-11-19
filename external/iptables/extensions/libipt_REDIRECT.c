/* Shared library add-on to iptables to add redirect support. */
#include <stdbool.h>
#include <stdio.h>
#include <netdb.h>
#include <string.h>
#include <stdlib.h>
#include <getopt.h>
#include <xtables.h>
#include <limits.h> /* INT_MAX in ip_tables.h */
#include <linux/netfilter_ipv4/ip_tables.h>
#include <net/netfilter/nf_nat.h>

#define IPT_REDIRECT_OPT_DEST	0x01
#define IPT_REDIRECT_OPT_RANDOM	0x02

static void REDIRECT_help(void)
{
	printf(
"REDIRECT target options:\n"
" --to-ports <port>[-<port>]\n"
"				Port (range) to map to.\n"
" [--random]\n");
}

static const struct option REDIRECT_opts[] = {
	{.name = "to-ports", .has_arg = true,  .val = '1'},
	{.name = "random",   .has_arg = false, .val = '2'},
	XT_GETOPT_TABLEEND,
};

static void REDIRECT_init(struct xt_entry_target *t)
{
	struct nf_nat_multi_range *mr = (struct nf_nat_multi_range *)t->data;

	/* Actually, it's 0, but it's ignored at the moment. */
	mr->rangesize = 1;

}

/* Parses ports */
static void
parse_ports(const char *arg, struct nf_nat_multi_range *mr)
{
	char *end;
	unsigned int port, maxport;

	mr->range[0].flags |= IP_NAT_RANGE_PROTO_SPECIFIED;

	if (!xtables_strtoui(arg, &end, &port, 0, UINT16_MAX) &&
	    (port = xtables_service_to_port(arg, NULL)) == (unsigned)-1)
		xtables_param_act(XTF_BAD_VALUE, "REDIRECT", "--to-ports", arg);

	switch (*end) {
	case '\0':
		mr->range[0].min.tcp.port
			= mr->range[0].max.tcp.port
			= htons(port);
		return;
	case '-':
		if (!xtables_strtoui(end + 1, NULL, &maxport, 0, UINT16_MAX) &&
		    (maxport = xtables_service_to_port(end + 1, NULL)) == (unsigned)-1)
			break;

		if (maxport < port)
			break;

		mr->range[0].min.tcp.port = htons(port);
		mr->range[0].max.tcp.port = htons(maxport);
		return;
	default:
		break;
	}
	xtables_param_act(XTF_BAD_VALUE, "REDIRECT", "--to-ports", arg);
}

static int REDIRECT_parse(int c, char **argv, int invert, unsigned int *flags,
                          const void *e, struct xt_entry_target **target)
{
	const struct ipt_entry *entry = e;
	struct nf_nat_multi_range *mr
		= (struct nf_nat_multi_range *)(*target)->data;
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
		if (!portok)
			xtables_error(PARAMETER_PROBLEM,
				   "Need TCP, UDP, SCTP or DCCP with port specification");

		if (xtables_check_inverse(optarg, &invert, NULL, 0, argv))
			xtables_error(PARAMETER_PROBLEM,
				   "Unexpected `!' after --to-ports");

		parse_ports(optarg, mr);
		if (*flags & IPT_REDIRECT_OPT_RANDOM)
			mr->range[0].flags |= IP_NAT_RANGE_PROTO_RANDOM;
		*flags |= IPT_REDIRECT_OPT_DEST;
		return 1;

	case '2':
		if (*flags & IPT_REDIRECT_OPT_DEST) {
			mr->range[0].flags |= IP_NAT_RANGE_PROTO_RANDOM;
			*flags |= IPT_REDIRECT_OPT_RANDOM;
		} else
			*flags |= IPT_REDIRECT_OPT_RANDOM;
		return 1;

	default:
		return 0;
	}
}

static void REDIRECT_print(const void *ip, const struct xt_entry_target *target,
                           int numeric)
{
	const struct nf_nat_multi_range *mr = (const void *)target->data;
	const struct nf_nat_range *r = &mr->range[0];

	if (r->flags & IP_NAT_RANGE_PROTO_SPECIFIED) {
		printf("redir ports ");
		printf("%hu", ntohs(r->min.tcp.port));
		if (r->max.tcp.port != r->min.tcp.port)
			printf("-%hu", ntohs(r->max.tcp.port));
		printf(" ");
		if (mr->range[0].flags & IP_NAT_RANGE_PROTO_RANDOM)
			printf("random ");
	}
}

static void REDIRECT_save(const void *ip, const struct xt_entry_target *target)
{
	const struct nf_nat_multi_range *mr = (const void *)target->data;
	const struct nf_nat_range *r = &mr->range[0];

	if (r->flags & IP_NAT_RANGE_PROTO_SPECIFIED) {
		printf("--to-ports ");
		printf("%hu", ntohs(r->min.tcp.port));
		if (r->max.tcp.port != r->min.tcp.port)
			printf("-%hu", ntohs(r->max.tcp.port));
		printf(" ");
		if (mr->range[0].flags & IP_NAT_RANGE_PROTO_RANDOM)
			printf("--random ");
	}
}

static struct xtables_target redirect_tg_reg = {
	.name		= "REDIRECT",
	.version	= XTABLES_VERSION,
	.family		= NFPROTO_IPV4,
	.size		= XT_ALIGN(sizeof(struct nf_nat_multi_range)),
	.userspacesize	= XT_ALIGN(sizeof(struct nf_nat_multi_range)),
	.help		= REDIRECT_help,
	.init		= REDIRECT_init,
 	.parse		= REDIRECT_parse,
	.print		= REDIRECT_print,
	.save		= REDIRECT_save,
	.extra_opts	= REDIRECT_opts,
};

void libipt_REDIRECT_init(void)
{
	xtables_register_target(&redirect_tg_reg);
}
