/* Shared library add-on to iptables to add NFMARK matching support. */
#include <stdbool.h>
#include <stdio.h>
#include <netdb.h>
#include <string.h>
#include <stdlib.h>
#include <getopt.h>

#include <xtables.h>
#include <linux/netfilter/xt_mark.h>

struct xt_mark_info {
	unsigned long mark, mask;
	u_int8_t invert;
};

enum {
	F_MARK = 1 << 0,
};

static void mark_mt_help(void)
{
	printf(
"mark match options:\n"
"[!] --mark value[/mask]    Match nfmark value with optional mask\n");
}

static const struct option mark_mt_opts[] = {
	{.name = "mark", .has_arg = true, .val = '1'},
	XT_GETOPT_TABLEEND,
};

static int mark_mt_parse(int c, char **argv, int invert, unsigned int *flags,
                         const void *entry, struct xt_entry_match **match)
{
	struct xt_mark_mtinfo1 *info = (void *)(*match)->data;
	unsigned int mark, mask = UINT32_MAX;
	char *end;

	switch (c) {
	case '1': /* --mark */
		xtables_param_act(XTF_ONLY_ONCE, "mark", "--mark", *flags & F_MARK);
		if (!xtables_strtoui(optarg, &end, &mark, 0, UINT32_MAX))
			xtables_param_act(XTF_BAD_VALUE, "mark", "--mark", optarg);
		if (*end == '/')
			if (!xtables_strtoui(end + 1, &end, &mask, 0, UINT32_MAX))
				xtables_param_act(XTF_BAD_VALUE, "mark", "--mark", optarg);
		if (*end != '\0')
			xtables_param_act(XTF_BAD_VALUE, "mark", "--mark", optarg);

		if (invert)
			info->invert = true;
		info->mark = mark;
		info->mask = mask;
		*flags    |= F_MARK;
		return true;
	}
	return false;
}

static int
mark_parse(int c, char **argv, int invert, unsigned int *flags,
           const void *entry, struct xt_entry_match **match)
{
	struct xt_mark_info *markinfo = (struct xt_mark_info *)(*match)->data;

	switch (c) {
		char *end;
	case '1':
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		markinfo->mark = strtoul(optarg, &end, 0);
		if (*end == '/') {
			markinfo->mask = strtoul(end+1, &end, 0);
		} else
			markinfo->mask = 0xffffffff;
		if (*end != '\0' || end == optarg)
			xtables_error(PARAMETER_PROBLEM, "Bad MARK value \"%s\"", optarg);
		if (invert)
			markinfo->invert = 1;
		*flags = 1;
		break;

	default:
		return 0;
	}
	return 1;
}

static void print_mark(unsigned int mark, unsigned int mask)
{
	if (mask != 0xffffffffU)
		printf("0x%x/0x%x ", mark, mask);
	else
		printf("0x%x ", mark);
}

static void mark_mt_check(unsigned int flags)
{
	if (flags == 0)
		xtables_error(PARAMETER_PROBLEM,
			   "mark match: The --mark option is required");
}

static void
mark_mt_print(const void *ip, const struct xt_entry_match *match, int numeric)
{
	const struct xt_mark_mtinfo1 *info = (const void *)match->data;

	printf("mark match ");
	if (info->invert)
		printf("!");
	print_mark(info->mark, info->mask);
}

static void
mark_print(const void *ip, const struct xt_entry_match *match, int numeric)
{
	const struct xt_mark_info *info = (const void *)match->data;

	printf("MARK match ");

	if (info->invert)
		printf("!");
	
	print_mark(info->mark, info->mask);
}

static void mark_mt_save(const void *ip, const struct xt_entry_match *match)
{
	const struct xt_mark_mtinfo1 *info = (const void *)match->data;

	if (info->invert)
		printf("! ");

	printf("--mark ");
	print_mark(info->mark, info->mask);
}

static void
mark_save(const void *ip, const struct xt_entry_match *match)
{
	const struct xt_mark_info *info = (const void *)match->data;

	if (info->invert)
		printf("! ");
	
	printf("--mark ");
	print_mark(info->mark, info->mask);
}

static struct xtables_match mark_mt_reg[] = {
	{
		.family        = NFPROTO_UNSPEC,
		.name          = "mark",
		.revision      = 0,
		.version       = XTABLES_VERSION,
		.size          = XT_ALIGN(sizeof(struct xt_mark_info)),
		.userspacesize = XT_ALIGN(sizeof(struct xt_mark_info)),
		.help          = mark_mt_help,
		.parse         = mark_parse,
		.final_check   = mark_mt_check,
		.print         = mark_print,
		.save          = mark_save,
		.extra_opts    = mark_mt_opts,
	},
	{
		.version       = XTABLES_VERSION,
		.name          = "mark",
		.revision      = 1,
		.family        = NFPROTO_UNSPEC,
		.size          = XT_ALIGN(sizeof(struct xt_mark_mtinfo1)),
		.userspacesize = XT_ALIGN(sizeof(struct xt_mark_mtinfo1)),
		.help          = mark_mt_help,
		.parse         = mark_mt_parse,
		.final_check   = mark_mt_check,
		.print         = mark_mt_print,
		.save          = mark_mt_save,
		.extra_opts    = mark_mt_opts,
	},
};

void libxt_mark_init(void)
{
	xtables_register_matches(mark_mt_reg, ARRAY_SIZE(mark_mt_reg));
}
