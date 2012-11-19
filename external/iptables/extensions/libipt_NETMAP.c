/* Shared library add-on to iptables to add static NAT support.
   Author: Svenning Soerensen <svenning@post5.tele.dk>
*/
#include <stdbool.h>
#include <stdio.h>
#include <netdb.h>
#include <string.h>
#include <stdlib.h>
#include <getopt.h>
#include <xtables.h>
#include <net/netfilter/nf_nat.h>

#define MODULENAME "NETMAP"

static const struct option NETMAP_opts[] = {
	{.name = "to", .has_arg = true, .val = '1'},
	XT_GETOPT_TABLEEND,
};

static void NETMAP_help(void)
{
	printf(MODULENAME" target options:\n"
	       "  --%s address[/mask]\n"
	       "				Network address to map to.\n\n",
	       NETMAP_opts[0].name);
}

static u_int32_t
bits2netmask(int bits)
{
	u_int32_t netmask, bm;

	if (bits >= 32 || bits < 0)
		return(~0);
	for (netmask = 0, bm = 0x80000000; bits; bits--, bm >>= 1)
		netmask |= bm;
	return htonl(netmask);
}

static int
netmask2bits(u_int32_t netmask)
{
	u_int32_t bm;
	int bits;

	netmask = ntohl(netmask);
	for (bits = 0, bm = 0x80000000; netmask & bm; netmask <<= 1)
		bits++;
	if (netmask)
		return -1; /* holes in netmask */
	return bits;
}

static void NETMAP_init(struct xt_entry_target *t)
{
	struct nf_nat_multi_range *mr = (struct nf_nat_multi_range *)t->data;

	/* Actually, it's 0, but it's ignored at the moment. */
	mr->rangesize = 1;

}

/* Parses network address */
static void
parse_to(char *arg, struct nf_nat_range *range)
{
	char *slash;
	const struct in_addr *ip;
	u_int32_t netmask;
	unsigned int bits;

	range->flags |= IP_NAT_RANGE_MAP_IPS;
	slash = strchr(arg, '/');
	if (slash)
		*slash = '\0';

	ip = xtables_numeric_to_ipaddr(arg);
	if (!ip)
		xtables_error(PARAMETER_PROBLEM, "Bad IP address \"%s\"\n",
			   arg);
	range->min_ip = ip->s_addr;
	if (slash) {
		if (strchr(slash+1, '.')) {
			ip = xtables_numeric_to_ipmask(slash+1);
			if (!ip)
				xtables_error(PARAMETER_PROBLEM, "Bad netmask \"%s\"\n",
					   slash+1);
			netmask = ip->s_addr;
		}
		else {
			if (!xtables_strtoui(slash+1, NULL, &bits, 0, 32))
				xtables_error(PARAMETER_PROBLEM, "Bad netmask \"%s\"\n",
					   slash+1);
			netmask = bits2netmask(bits);
		}
		/* Don't allow /0 (/1 is probably insane, too) */
		if (netmask == 0)
			xtables_error(PARAMETER_PROBLEM, "Netmask needed\n");
	}
	else
		netmask = ~0;

	if (range->min_ip & ~netmask) {
		if (slash)
			*slash = '/';
		xtables_error(PARAMETER_PROBLEM, "Bad network address \"%s\"\n",
			   arg);
	}
	range->max_ip = range->min_ip | ~netmask;
}

static int NETMAP_parse(int c, char **argv, int invert, unsigned int *flags,
                        const void *entry, struct xt_entry_target **target)
{
	struct nf_nat_multi_range *mr
		= (struct nf_nat_multi_range *)(*target)->data;

	switch (c) {
	case '1':
		if (xtables_check_inverse(optarg, &invert, NULL, 0, argv))
			xtables_error(PARAMETER_PROBLEM,
				   "Unexpected `!' after --%s", NETMAP_opts[0].name);

		parse_to(optarg, &mr->range[0]);
		*flags = 1;
		return 1;

	default:
		return 0;
	}
}

static void NETMAP_check(unsigned int flags)
{
	if (!flags)
		xtables_error(PARAMETER_PROBLEM,
			   MODULENAME" needs --%s", NETMAP_opts[0].name);
}

static void NETMAP_print(const void *ip, const struct xt_entry_target *target,
                         int numeric)
{
	const struct nf_nat_multi_range *mr = (const void *)target->data;
	const struct nf_nat_range *r = &mr->range[0];
	struct in_addr a;
	int bits;

	a.s_addr = r->min_ip;
	printf("%s", xtables_ipaddr_to_numeric(&a));
	a.s_addr = ~(r->min_ip ^ r->max_ip);
	bits = netmask2bits(a.s_addr);
	if (bits < 0)
		printf("/%s", xtables_ipaddr_to_numeric(&a));
	else
		printf("/%d", bits);
}

static void NETMAP_save(const void *ip, const struct xt_entry_target *target)
{
	printf("--%s ", NETMAP_opts[0].name);
	NETMAP_print(ip, target, 0);
}

static struct xtables_target netmap_tg_reg = {
	.name		= MODULENAME,
	.version	= XTABLES_VERSION,
	.family		= NFPROTO_IPV4,
	.size		= XT_ALIGN(sizeof(struct nf_nat_multi_range)),
	.userspacesize	= XT_ALIGN(sizeof(struct nf_nat_multi_range)),
	.help		= NETMAP_help,
	.init		= NETMAP_init,
	.parse		= NETMAP_parse,
	.final_check	= NETMAP_check,
	.print		= NETMAP_print,
	.save		= NETMAP_save,
	.extra_opts	= NETMAP_opts,
};

void libipt_NETMAP_init(void)
{
	xtables_register_target(&netmap_tg_reg);
}
