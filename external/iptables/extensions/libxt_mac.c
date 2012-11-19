/* Shared library add-on to iptables to add MAC address support. */
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
#include <linux/netfilter/xt_mac.h>

static void mac_help(void)
{
	printf(
"mac match options:\n"
"[!] --mac-source XX:XX:XX:XX:XX:XX\n"
"				Match source MAC address\n");
}

static const struct option mac_opts[] = {
	{.name = "mac-source", .has_arg = true, .val = '1'},
	XT_GETOPT_TABLEEND,
};

static void
parse_mac(const char *mac, struct xt_mac_info *info)
{
	unsigned int i = 0;

	if (strlen(mac) != ETH_ALEN*3-1)
		xtables_error(PARAMETER_PROBLEM, "Bad mac address \"%s\"", mac);

	for (i = 0; i < ETH_ALEN; i++) {
		long number;
		char *end;

		number = strtol(mac + i*3, &end, 16);

		if (end == mac + i*3 + 2
		    && number >= 0
		    && number <= 255)
			info->srcaddr[i] = number;
		else
			xtables_error(PARAMETER_PROBLEM,
				   "Bad mac address `%s'", mac);
	}
}

static int
mac_parse(int c, char **argv, int invert, unsigned int *flags,
          const void *entry, struct xt_entry_match **match)
{
	struct xt_mac_info *macinfo = (struct xt_mac_info *)(*match)->data;

	switch (c) {
	case '1':
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		parse_mac(optarg, macinfo);
		if (invert)
			macinfo->invert = 1;
		*flags = 1;
		break;

	default:
		return 0;
	}

	return 1;
}

static void print_mac(const unsigned char macaddress[ETH_ALEN])
{
	unsigned int i;

	printf("%02X", macaddress[0]);
	for (i = 1; i < ETH_ALEN; i++)
		printf(":%02X", macaddress[i]);
	printf(" ");
}

static void mac_check(unsigned int flags)
{
	if (!flags)
		xtables_error(PARAMETER_PROBLEM,
			   "You must specify `--mac-source'");
}

static void
mac_print(const void *ip, const struct xt_entry_match *match, int numeric)
{
	const struct xt_mac_info *info = (void *)match->data;
	printf("MAC ");

	if (info->invert)
		printf("! ");
	
	print_mac(info->srcaddr);
}

static void mac_save(const void *ip, const struct xt_entry_match *match)
{
	const struct xt_mac_info *info = (void *)match->data;

	if (info->invert)
		printf("! ");

	printf("--mac-source ");
	print_mac(info->srcaddr);
}

static struct xtables_match mac_match = {
	.family		= NFPROTO_UNSPEC,
 	.name		= "mac",
	.version	= XTABLES_VERSION,
	.size		= XT_ALIGN(sizeof(struct xt_mac_info)),
	.userspacesize	= XT_ALIGN(sizeof(struct xt_mac_info)),
	.help		= mac_help,
	.parse		= mac_parse,
	.final_check	= mac_check,
	.print		= mac_print,
	.save		= mac_save,
	.extra_opts	= mac_opts,
};

void libxt_mac_init(void)
{
	xtables_register_match(&mac_match);
}
