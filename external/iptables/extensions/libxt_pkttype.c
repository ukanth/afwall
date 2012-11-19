/* 
 * Shared library add-on to iptables to match 
 * packets by their type (BROADCAST, UNICAST, MULTICAST). 
 *
 * Michal Ludvig <michal@logix.cz>
 */
#include <stdbool.h>
#include <stdio.h>
#include <netdb.h>
#include <string.h>
#include <stdlib.h>
#include <getopt.h>
#if defined(__GLIBC__) && __GLIBC__ == 2
#include <net/ethernet.h>
#else
#include <linux/if_ether.h>
#endif
#include <xtables.h>
#include <linux/if_packet.h>
#include <linux/netfilter/xt_pkttype.h>

#define	PKTTYPE_VERSION	"0.1"

struct pkttypes {
	const char *name;
	unsigned char pkttype;
	unsigned char printhelp;
	const char *help;
};

static const struct pkttypes supported_types[] = {
	{"unicast", PACKET_HOST, 1, "to us"},
	{"broadcast", PACKET_BROADCAST, 1, "to all"},
	{"multicast", PACKET_MULTICAST, 1, "to group"},
/*
	{"otherhost", PACKET_OTHERHOST, 1, "to someone else"},
	{"outgoing", PACKET_OUTGOING, 1, "outgoing of any type"},
*/
	/* aliases */
	{"bcast", PACKET_BROADCAST, 0, NULL},
	{"mcast", PACKET_MULTICAST, 0, NULL},
	{"host", PACKET_HOST, 0, NULL}
};

static void print_types(void)
{
	unsigned int	i;
	
	printf("Valid packet types:\n");
	for (i = 0; i < ARRAY_SIZE(supported_types); ++i)
		if(supported_types[i].printhelp == 1)
			printf("\t%-14s\t\t%s\n", supported_types[i].name, supported_types[i].help);
	printf("\n");
}

static void pkttype_help(void)
{
	printf(
"pkttype match options:\n"
"[!] --pkt-type packettype    match packet type\n");
	print_types();
}

static const struct option pkttype_opts[] = {
	{.name = "pkt-type", .has_arg = true, .val = '1'},
	XT_GETOPT_TABLEEND,
};

static void parse_pkttype(const char *pkttype, struct xt_pkttype_info *info)
{
	unsigned int	i;
	
	for (i = 0; i < ARRAY_SIZE(supported_types); ++i)
		if(strcasecmp(pkttype, supported_types[i].name)==0)
		{
			info->pkttype=supported_types[i].pkttype;
			return;
		}
	
	xtables_error(PARAMETER_PROBLEM, "Bad packet type '%s'", pkttype);
}

static int pkttype_parse(int c, char **argv, int invert, unsigned int *flags,
                         const void *entry, struct xt_entry_match **match)
{
	struct xt_pkttype_info *info = (struct xt_pkttype_info *)(*match)->data;
	
	switch(c)
	{
		case '1':
			xtables_check_inverse(optarg, &invert, &optind, 0, argv);
			parse_pkttype(optarg, info);
			if(invert)
				info->invert=1;
			*flags=1;
			break;

		default: 
			return 0;
	}

	return 1;
}

static void pkttype_check(unsigned int flags)
{
	if (!flags)
		xtables_error(PARAMETER_PROBLEM, "You must specify \"--pkt-type\"");
}

static void print_pkttype(const struct xt_pkttype_info *info)
{
	unsigned int	i;
	
	for (i = 0; i < ARRAY_SIZE(supported_types); ++i)
		if(supported_types[i].pkttype==info->pkttype)
		{
			printf("%s ", supported_types[i].name);
			return;
		}

	printf("%d ", info->pkttype);	/* in case we didn't find an entry in named-packtes */
}

static void pkttype_print(const void *ip, const struct xt_entry_match *match,
                          int numeric)
{
	const struct xt_pkttype_info *info = (const void *)match->data;
	
	printf("PKTTYPE %s= ", info->invert?"!":"");
	print_pkttype(info);
}

static void pkttype_save(const void *ip, const struct xt_entry_match *match)
{
	const struct xt_pkttype_info *info = (const void *)match->data;
	
	printf("%s--pkt-type ", info->invert ? "! " : "");
	print_pkttype(info);
}

static struct xtables_match pkttype_match = {
	.family		= NFPROTO_UNSPEC,
	.name		= "pkttype",
	.version	= XTABLES_VERSION,
	.size		= XT_ALIGN(sizeof(struct xt_pkttype_info)),
	.userspacesize	= XT_ALIGN(sizeof(struct xt_pkttype_info)),
	.help		= pkttype_help,
	.parse		= pkttype_parse,
	.final_check	= pkttype_check,
	.print		= pkttype_print,
	.save		= pkttype_save,
	.extra_opts	= pkttype_opts,
};

void libxt_pkttype_init(void)
{
	xtables_register_match(&pkttype_match);
}
