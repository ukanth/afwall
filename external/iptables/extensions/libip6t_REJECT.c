/* Shared library add-on to ip6tables to add customized REJECT support.
 *
 * (C) 2000 Jozsef Kadlecsik <kadlec@blackhole.kfki.hu>
 * 
 * ported to IPv6 by Harald Welte <laforge@gnumonks.org>
 *
 */
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <getopt.h>
#include <xtables.h>
#include <linux/netfilter_ipv6/ip6t_REJECT.h>

struct reject_names {
	const char *name;
	const char *alias;
	enum ip6t_reject_with with;
	const char *desc;
};

static const struct reject_names reject_table[] = {
	{"icmp6-no-route", "no-route",
		IP6T_ICMP6_NO_ROUTE, "ICMPv6 no route"},
	{"icmp6-adm-prohibited", "adm-prohibited",
		IP6T_ICMP6_ADM_PROHIBITED, "ICMPv6 administratively prohibited"},
#if 0
	{"icmp6-not-neighbor", "not-neighbor"},
		IP6T_ICMP6_NOT_NEIGHBOR, "ICMPv6 not a neighbor"},
#endif
	{"icmp6-addr-unreachable", "addr-unreach",
		IP6T_ICMP6_ADDR_UNREACH, "ICMPv6 address unreachable"},
	{"icmp6-port-unreachable", "port-unreach",
		IP6T_ICMP6_PORT_UNREACH, "ICMPv6 port unreachable"},
	{"tcp-reset", "tcp-reset",
		IP6T_TCP_RESET, "TCP RST packet"}
};

static void
print_reject_types(void)
{
	unsigned int i;

	printf("Valid reject types:\n");

	for (i = 0; i < ARRAY_SIZE(reject_table); ++i) {
		printf("    %-25s\t%s\n", reject_table[i].name, reject_table[i].desc);
		printf("    %-25s\talias\n", reject_table[i].alias);
	}
	printf("\n");
}

static void REJECT_help(void)
{
	printf(
"REJECT target options:\n"
"--reject-with type              drop input packet and send back\n"
"                                a reply packet according to type:\n");

	print_reject_types();
}

static const struct option REJECT_opts[] = {
	{.name = "reject-with", .has_arg = true, .val = '1'},
	XT_GETOPT_TABLEEND,
};

static void REJECT_init(struct xt_entry_target *t)
{
	struct ip6t_reject_info *reject = (struct ip6t_reject_info *)t->data;

	/* default */
	reject->with = IP6T_ICMP6_PORT_UNREACH;

}

static int REJECT_parse(int c, char **argv, int invert, unsigned int *flags,
                        const void *entry, struct xt_entry_target **target)
{
	struct ip6t_reject_info *reject = 
		(struct ip6t_reject_info *)(*target)->data;
	unsigned int i;

	switch(c) {
	case '1':
		if (xtables_check_inverse(optarg, &invert, NULL, 0, argv))
			xtables_error(PARAMETER_PROBLEM,
				   "Unexpected `!' after --reject-with");
		for (i = 0; i < ARRAY_SIZE(reject_table); ++i)
			if ((strncasecmp(reject_table[i].name, optarg, strlen(optarg)) == 0)
			    || (strncasecmp(reject_table[i].alias, optarg, strlen(optarg)) == 0)) {
				reject->with = reject_table[i].with;
				return 1;
			}
		xtables_error(PARAMETER_PROBLEM, "unknown reject type \"%s\"", optarg);
	default:
		/* Fall through */
		break;
	}
	return 0;
}

static void REJECT_print(const void *ip, const struct xt_entry_target *target,
                         int numeric)
{
	const struct ip6t_reject_info *reject
		= (const struct ip6t_reject_info *)target->data;
	unsigned int i;

	for (i = 0; i < ARRAY_SIZE(reject_table); ++i)
		if (reject_table[i].with == reject->with)
			break;
	printf("reject-with %s ", reject_table[i].name);
}

static void REJECT_save(const void *ip, const struct xt_entry_target *target)
{
	const struct ip6t_reject_info *reject
		= (const struct ip6t_reject_info *)target->data;
	unsigned int i;

	for (i = 0; i < ARRAY_SIZE(reject_table); ++i)
		if (reject_table[i].with == reject->with)
			break;

	printf("--reject-with %s ", reject_table[i].name);
}

static struct xtables_target reject_tg6_reg = {
	.name = "REJECT",
	.version	= XTABLES_VERSION,
	.family		= NFPROTO_IPV6,
	.size 		= XT_ALIGN(sizeof(struct ip6t_reject_info)),
	.userspacesize 	= XT_ALIGN(sizeof(struct ip6t_reject_info)),
	.help		= REJECT_help,
	.init		= REJECT_init,
	.parse		= REJECT_parse,
	.print		= REJECT_print,
	.save		= REJECT_save,
	.extra_opts	= REJECT_opts,
};

void _init(void)
{
	xtables_register_target(&reject_tg6_reg);
}
