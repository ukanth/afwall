/* Shared library add-on to iptables for DCCP matching
 *
 * (C) 2005 by Harald Welte <laforge@netfilter.org>
 *
 * This program is distributed under the terms of GNU GPL v2, 1991
 *
 */
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <getopt.h>
#include <netdb.h>
#include <ctype.h>

#include <netinet/in.h>
#include <xtables.h>
#include <linux/dccp.h>
#include <linux/netfilter/x_tables.h>
#include <linux/netfilter/xt_dccp.h>

#if 0
#define DEBUGP(format, first...) printf(format, ##first)
#define static
#else
#define DEBUGP(format, fist...) 
#endif

static void dccp_init(struct xt_entry_match *m)
{
	struct xt_dccp_info *einfo = (struct xt_dccp_info *)m->data;

	memset(einfo, 0, sizeof(struct xt_dccp_info));
}

static void dccp_help(void)
{
	printf(
"dccp match options\n"
"[!] --source-port port[:port]                          match source port(s)\n"
" --sport ...\n"
"[!] --destination-port port[:port]                     match destination port(s)\n"
" --dport ...\n");
}

static const struct option dccp_opts[] = {
	{.name = "source-port",      .has_arg = true, .val = '1'},
	{.name = "sport",            .has_arg = true, .val = '1'},
	{.name = "destination-port", .has_arg = true, .val = '2'},
	{.name = "dport",            .has_arg = true, .val = '2'},
	{.name = "dccp-types",       .has_arg = true, .val = '3'},
	{.name = "dccp-option",      .has_arg = true, .val = '4'},
	XT_GETOPT_TABLEEND,
};

static void
parse_dccp_ports(const char *portstring, 
		 u_int16_t *ports)
{
	char *buffer;
	char *cp;

	buffer = strdup(portstring);
	DEBUGP("%s\n", portstring);
	if ((cp = strchr(buffer, ':')) == NULL) {
		ports[0] = ports[1] = xtables_parse_port(buffer, "dccp");
	}
	else {
		*cp = '\0';
		cp++;

		ports[0] = buffer[0] ? xtables_parse_port(buffer, "dccp") : 0;
		ports[1] = cp[0] ? xtables_parse_port(cp, "dccp") : 0xFFFF;

		if (ports[0] > ports[1])
			xtables_error(PARAMETER_PROBLEM,
				   "invalid portrange (min > max)");
	}
	free(buffer);
}

static const char *const dccp_pkt_types[] = {
	[DCCP_PKT_REQUEST] 	= "REQUEST",
	[DCCP_PKT_RESPONSE]	= "RESPONSE",
	[DCCP_PKT_DATA]		= "DATA",
	[DCCP_PKT_ACK]		= "ACK",
	[DCCP_PKT_DATAACK]	= "DATAACK",
	[DCCP_PKT_CLOSEREQ]	= "CLOSEREQ",
	[DCCP_PKT_CLOSE]	= "CLOSE",
	[DCCP_PKT_RESET]	= "RESET",
	[DCCP_PKT_SYNC]		= "SYNC",
	[DCCP_PKT_SYNCACK]	= "SYNCACK",
	[DCCP_PKT_INVALID]	= "INVALID",
};

static u_int16_t
parse_dccp_types(const char *typestring)
{
	u_int16_t typemask = 0;
	char *ptr, *buffer;

	buffer = strdup(typestring);

	for (ptr = strtok(buffer, ","); ptr; ptr = strtok(NULL, ",")) {
		unsigned int i;
		for (i = 0; i < ARRAY_SIZE(dccp_pkt_types); ++i)
			if (!strcasecmp(dccp_pkt_types[i], ptr)) {
				typemask |= (1 << i);
				break;
			}
		if (i == ARRAY_SIZE(dccp_pkt_types))
			xtables_error(PARAMETER_PROBLEM,
				   "Unknown DCCP type `%s'", ptr);
	}

	free(buffer);
	return typemask;
}

static u_int8_t parse_dccp_option(char *optstring)
{
	unsigned int ret;

	if (!xtables_strtoui(optstring, NULL, &ret, 1, UINT8_MAX))
		xtables_error(PARAMETER_PROBLEM, "Bad DCCP option \"%s\"",
			   optstring);

	return ret;
}

static int
dccp_parse(int c, char **argv, int invert, unsigned int *flags,
           const void *entry, struct xt_entry_match **match)
{
	struct xt_dccp_info *einfo
		= (struct xt_dccp_info *)(*match)->data;

	switch (c) {
	case '1':
		if (*flags & XT_DCCP_SRC_PORTS)
			xtables_error(PARAMETER_PROBLEM,
			           "Only one `--source-port' allowed");
		einfo->flags |= XT_DCCP_SRC_PORTS;
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		parse_dccp_ports(optarg, einfo->spts);
		if (invert)
			einfo->invflags |= XT_DCCP_SRC_PORTS;
		*flags |= XT_DCCP_SRC_PORTS;
		break;

	case '2':
		if (*flags & XT_DCCP_DEST_PORTS)
			xtables_error(PARAMETER_PROBLEM,
				   "Only one `--destination-port' allowed");
		einfo->flags |= XT_DCCP_DEST_PORTS;
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		parse_dccp_ports(optarg, einfo->dpts);
		if (invert)
			einfo->invflags |= XT_DCCP_DEST_PORTS;
		*flags |= XT_DCCP_DEST_PORTS;
		break;

	case '3':
		if (*flags & XT_DCCP_TYPE)
			xtables_error(PARAMETER_PROBLEM,
				   "Only one `--dccp-types' allowed");
		einfo->flags |= XT_DCCP_TYPE;
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		einfo->typemask = parse_dccp_types(optarg);
		if (invert)
			einfo->invflags |= XT_DCCP_TYPE;
		*flags |= XT_DCCP_TYPE;
		break;

	case '4':
		if (*flags & XT_DCCP_OPTION)
			xtables_error(PARAMETER_PROBLEM,
				   "Only one `--dccp-option' allowed");
		einfo->flags |= XT_DCCP_OPTION;
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		einfo->option = parse_dccp_option(optarg);
		if (invert)
			einfo->invflags |= XT_DCCP_OPTION;
		*flags |= XT_DCCP_OPTION;
		break;
	default:
		return 0;
	}
	return 1;
}

static char *
port_to_service(int port)
{
	struct servent *service;

	if ((service = getservbyport(htons(port), "dccp")))
		return service->s_name;

	return NULL;
}

static void
print_port(u_int16_t port, int numeric)
{
	char *service;

	if (numeric || (service = port_to_service(port)) == NULL)
		printf("%u", port);
	else
		printf("%s", service);
}

static void
print_ports(const char *name, u_int16_t min, u_int16_t max,
	    int invert, int numeric)
{
	const char *inv = invert ? "!" : "";

	if (min != 0 || max != 0xFFFF || invert) {
		printf("%s", name);
		if (min == max) {
			printf(":%s", inv);
			print_port(min, numeric);
		} else {
			printf("s:%s", inv);
			print_port(min, numeric);
			printf(":");
			print_port(max, numeric);
		}
		printf(" ");
	}
}

static void
print_types(u_int16_t types, int inverted, int numeric)
{
	int have_type = 0;

	if (inverted)
		printf("! ");

	while (types) {
		unsigned int i;

		for (i = 0; !(types & (1 << i)); i++);

		if (have_type)
			printf(",");
		else
			have_type = 1;

		if (numeric)
			printf("%u", i);
		else
			printf("%s", dccp_pkt_types[i]);

		types &= ~(1 << i);
	}
}

static void
print_option(u_int8_t option, int invert, int numeric)
{
	if (option || invert)
		printf("option=%s%u ", invert ? "!" : "", option);
}

static void
dccp_print(const void *ip, const struct xt_entry_match *match, int numeric)
{
	const struct xt_dccp_info *einfo =
		(const struct xt_dccp_info *)match->data;

	printf("dccp ");

	if (einfo->flags & XT_DCCP_SRC_PORTS) {
		print_ports("spt", einfo->spts[0], einfo->spts[1],
			einfo->invflags & XT_DCCP_SRC_PORTS,
			numeric);
	}

	if (einfo->flags & XT_DCCP_DEST_PORTS) {
		print_ports("dpt", einfo->dpts[0], einfo->dpts[1],
			einfo->invflags & XT_DCCP_DEST_PORTS,
			numeric);
	}

	if (einfo->flags & XT_DCCP_TYPE) {
		print_types(einfo->typemask,
			   einfo->invflags & XT_DCCP_TYPE,
			   numeric);
	}

	if (einfo->flags & XT_DCCP_OPTION) {
		print_option(einfo->option,
			     einfo->invflags & XT_DCCP_OPTION, numeric);
	}
}

static void dccp_save(const void *ip, const struct xt_entry_match *match)
{
	const struct xt_dccp_info *einfo =
		(const struct xt_dccp_info *)match->data;

	if (einfo->flags & XT_DCCP_SRC_PORTS) {
		if (einfo->invflags & XT_DCCP_SRC_PORTS)
			printf("! ");
		if (einfo->spts[0] != einfo->spts[1])
			printf("--sport %u:%u ", 
			       einfo->spts[0], einfo->spts[1]);
		else
			printf("--sport %u ", einfo->spts[0]);
	}

	if (einfo->flags & XT_DCCP_DEST_PORTS) {
		if (einfo->invflags & XT_DCCP_DEST_PORTS)
			printf("! ");
		if (einfo->dpts[0] != einfo->dpts[1])
			printf("--dport %u:%u ",
			       einfo->dpts[0], einfo->dpts[1]);
		else
			printf("--dport %u ", einfo->dpts[0]);
	}

	if (einfo->flags & XT_DCCP_TYPE) {
		printf("--dccp-type ");
		print_types(einfo->typemask, einfo->invflags & XT_DCCP_TYPE,0);
	}

	if (einfo->flags & XT_DCCP_OPTION) {
		printf("--dccp-option %s%u ", 
			einfo->typemask & XT_DCCP_OPTION ? "! " : "",
			einfo->option);
	}
}

static struct xtables_match dccp_match = {
	.name		= "dccp",
	.family		= NFPROTO_UNSPEC,
	.version	= XTABLES_VERSION,
	.size		= XT_ALIGN(sizeof(struct xt_dccp_info)),
	.userspacesize	= XT_ALIGN(sizeof(struct xt_dccp_info)),
	.help		= dccp_help,
	.init		= dccp_init,
	.parse		= dccp_parse,
	.print		= dccp_print,
	.save		= dccp_save,
	.extra_opts	= dccp_opts,
};

void libxt_dccp_init(void)
{
	xtables_register_match(&dccp_match);
}
