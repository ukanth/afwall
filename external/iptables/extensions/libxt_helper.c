/* Shared library add-on to iptables to add related packet matching support. */
#include <stdbool.h>
#include <stdio.h>
#include <netdb.h>
#include <string.h>
#include <stdlib.h>
#include <getopt.h>

#include <xtables.h>
#include <linux/netfilter/xt_helper.h>

static void helper_help(void)
{
	printf(
"helper match options:\n"
"[!] --helper string        Match helper identified by string\n");
}

static const struct option helper_opts[] = {
	{.name = "helper", .has_arg = true, .val = '1'},
	XT_GETOPT_TABLEEND,
};

static int
helper_parse(int c, char **argv, int invert, unsigned int *flags,
             const void *entry, struct xt_entry_match **match)
{
	struct xt_helper_info *info = (struct xt_helper_info *)(*match)->data;

	switch (c) {
	case '1':
		if (*flags)
			xtables_error(PARAMETER_PROBLEM,
					"helper match: Only use --helper ONCE!");
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		strncpy(info->name, optarg, 29);
		info->name[29] = '\0';
		if (invert)
			info->invert = 1;
		*flags = 1;
		break;

	default:
		return 0;
	}
	return 1;
}

static void helper_check(unsigned int flags)
{
	if (!flags)
		xtables_error(PARAMETER_PROBLEM,
			   "helper match: You must specify `--helper'");
}

static void
helper_print(const void *ip, const struct xt_entry_match *match, int numeric)
{
	const struct xt_helper_info *info = (const void *)match->data;

	printf("helper match %s\"%s\" ", info->invert ? "! " : "", info->name);
}

static void helper_save(const void *ip, const struct xt_entry_match *match)
{
	const struct xt_helper_info *info = (const void *)match->data;

	printf("%s--helper ",info->invert ? "! " : "");
	xtables_save_string(info->name);
}

static struct xtables_match helper_match = {
	.family		= NFPROTO_UNSPEC,
	.name		= "helper",
	.version	= XTABLES_VERSION,
	.size		= XT_ALIGN(sizeof(struct xt_helper_info)),
	.help		= helper_help,
	.parse		= helper_parse,
	.final_check	= helper_check,
	.print		= helper_print,
	.save		= helper_save,
	.extra_opts	= helper_opts,
};

void libxt_helper_init(void)
{
	xtables_register_match(&helper_match);
}
