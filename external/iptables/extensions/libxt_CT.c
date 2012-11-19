#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <stddef.h>
#include <getopt.h>
#include <xtables.h>
#include <linux/netfilter/nf_conntrack_common.h>
#include <linux/netfilter/xt_CT.h>

static void ct_help(void)
{
	printf(
"CT target options:\n"
" --notrack			Don't track connection\n"
" --helper name			Use conntrack helper 'name' for connection\n"
" --ctevents event[,event...]	Generate specified conntrack events for connection\n"
" --expevents event[,event...]	Generate specified expectation events for connection\n"
" --zone ID			Assign/Lookup connection in zone ID\n"
	);
}

enum ct_options {
	CT_OPT_NOTRACK		= 0x1,
	CT_OPT_HELPER		= 0x2,
	CT_OPT_CTEVENTS		= 0x4,
	CT_OPT_EXPEVENTS	= 0x8,
	CT_OPT_ZONE		= 0x10,
};

static const struct option ct_opts[] = {
	{.name = "notrack",	.has_arg = false, .val = CT_OPT_NOTRACK},
	{.name = "helper",	.has_arg = true,  .val = CT_OPT_HELPER},
	{.name = "ctevents",	.has_arg = true,  .val = CT_OPT_CTEVENTS},
	{.name = "expevents",	.has_arg = true,  .val = CT_OPT_EXPEVENTS},
	{.name = "zone",	.has_arg = true,  .val = CT_OPT_ZONE},
	XT_GETOPT_TABLEEND,
};

struct event_tbl {
	const char	*name;
	unsigned int	event;
};

static const struct event_tbl ct_event_tbl[] = {
	{ "new",		IPCT_NEW },
	{ "related",		IPCT_RELATED },
	{ "destroy",		IPCT_DESTROY },
	{ "reply",		IPCT_REPLY },
	{ "assured",		IPCT_ASSURED },
	{ "protoinfo",		IPCT_PROTOINFO },
	{ "helper",		IPCT_HELPER },
	{ "mark",		IPCT_MARK },
	{ "natseqinfo",		IPCT_NATSEQADJ },
	{ "secmark",		IPCT_SECMARK },
};

static const struct event_tbl exp_event_tbl[] = {
	{ "new",		IPEXP_NEW },
};

static uint32_t ct_parse_events(const struct event_tbl *tbl, unsigned int size,
				const char *events)
{
	char str[strlen(events) + 1], *e = str, *t;
	unsigned int mask = 0, i;

	strcpy(str, events);
	while ((t = strsep(&e, ","))) {
		for (i = 0; i < size; i++) {
			if (strcmp(t, tbl[i].name))
				continue;
			mask |= 1 << tbl[i].event;
			break;
		}

		if (i == size)
			xtables_error(PARAMETER_PROBLEM, "Unknown event type \"%s\"", t);
	}

	return mask;
}

static void ct_print_events(const char *pfx, const struct event_tbl *tbl,
			    unsigned int size, uint32_t mask)
{
	const char *sep = "";
	unsigned int i;

	printf("%s ", pfx);
	for (i = 0; i < size; i++) {
		if (mask & (1 << tbl[i].event)) {
			printf("%s%s", sep, tbl[i].name);
			sep = ",";
		}
	}
	printf(" ");
}

static int ct_parse(int c, char **argv, int invert, unsigned int *flags,
		    const void *entry, struct xt_entry_target **target)
{
	struct xt_ct_target_info *info = (struct xt_ct_target_info *)(*target)->data;
	unsigned int zone;

	switch (c) {
	case CT_OPT_NOTRACK:
		xtables_param_act(XTF_ONLY_ONCE, "CT", "--notrack", *flags & CT_OPT_NOTRACK);
		info->flags |= XT_CT_NOTRACK;
		break;
	case CT_OPT_HELPER:
		xtables_param_act(XTF_ONLY_ONCE, "CT", "--helper", *flags & CT_OPT_HELPER);
		strncpy(info->helper, optarg, sizeof(info->helper));
		info->helper[sizeof(info->helper) - 1] = '\0';
		break;
	case CT_OPT_CTEVENTS:
		xtables_param_act(XTF_ONLY_ONCE, "CT", "--ctevents", *flags & CT_OPT_CTEVENTS);
		info->ct_events = ct_parse_events(ct_event_tbl, ARRAY_SIZE(ct_event_tbl), optarg);
		break;
	case CT_OPT_EXPEVENTS:
		xtables_param_act(XTF_ONLY_ONCE, "CT", "--expevents", *flags & CT_OPT_EXPEVENTS);
		info->exp_events = ct_parse_events(exp_event_tbl, ARRAY_SIZE(exp_event_tbl), optarg);
		break;
	case CT_OPT_ZONE:
		xtables_param_act(XTF_ONLY_ONCE, "CT", "--zone", *flags & CT_OPT_ZONE);
		if (!xtables_strtoui(optarg, NULL, &zone, 0, UINT16_MAX))
			xtables_error(PARAMETER_PROBLEM, "Bad zone value \"%s\"", optarg);
		info->zone = zone;
		break;
	default:
		return 0;
	}

	*flags |= c;
	return 1;
}

static void ct_print(const void *ip, const struct xt_entry_target *target, int numeric)
{
	const struct xt_ct_target_info *info =
		(const struct xt_ct_target_info *)target->data;

	printf("CT ");
	if (info->flags & XT_CT_NOTRACK)
		printf("notrack ");
	if (info->helper[0])
		printf("helper %s ", info->helper);
	if (info->ct_events)
		ct_print_events("ctevents", ct_event_tbl,
				ARRAY_SIZE(ct_event_tbl), info->ct_events);
	if (info->exp_events)
		ct_print_events("expevents", exp_event_tbl,
				ARRAY_SIZE(exp_event_tbl), info->exp_events);
	if (info->zone)
		printf("zone %u ", info->zone);
}

static void ct_save(const void *ip, const struct xt_entry_target *target)
{
	const struct xt_ct_target_info *info =
		(const struct xt_ct_target_info *)target->data;

	if (info->flags & XT_CT_NOTRACK)
		printf("--notrack ");
	if (info->helper[0])
		printf("--helper %s ", info->helper);
	if (info->ct_events)
		ct_print_events("--ctevents", ct_event_tbl,
				ARRAY_SIZE(ct_event_tbl), info->ct_events);
	if (info->exp_events)
		ct_print_events("--expevents", exp_event_tbl,
				ARRAY_SIZE(exp_event_tbl), info->exp_events);
	if (info->zone)
		printf("--zone %u ", info->zone);
}

static struct xtables_target ct_target = {
	.family		= NFPROTO_UNSPEC,
	.name		= "CT",
	.version	= XTABLES_VERSION,
	.size		= XT_ALIGN(sizeof(struct xt_ct_target_info)),
	.userspacesize	= offsetof(struct xt_ct_target_info, ct),
	.help		= ct_help,
	.parse		= ct_parse,
	.print		= ct_print,
	.save		= ct_save,
	.extra_opts	= ct_opts,
};

void libxt_CT_init(void)
{
	xtables_register_target(&ct_target);
}
