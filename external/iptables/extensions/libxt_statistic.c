#include <stdbool.h>
#include <stdio.h>
#include <netdb.h>
#include <string.h>
#include <stdlib.h>
#include <stddef.h>
#include <getopt.h>

#include <xtables.h>
#include <linux/netfilter/xt_statistic.h>

static void statistic_help(void)
{
	printf(
"statistic match options:\n"
" --mode mode                    Match mode (random, nth)\n"
" random mode:\n"
" --probability p		 Probability\n"
" nth mode:\n"
" --every n			 Match every nth packet\n"
" --packet p			 Initial counter value (0 <= p <= n-1, default 0)\n");
}

static const struct option statistic_opts[] = {
	{.name = "mode",        .has_arg = true, .val = '1'},
	{.name = "probability", .has_arg = true, .val = '2'},
	{.name = "every",       .has_arg = true, .val = '3'},
	{.name = "packet",      .has_arg = true, .val = '4'},
	XT_GETOPT_TABLEEND,
};

static struct xt_statistic_info *global_info;

static void statistic_mt_init(struct xt_entry_match *match)
{
	global_info = (void *)match->data;
}

static int
statistic_parse(int c, char **argv, int invert, unsigned int *flags,
                const void *entry, struct xt_entry_match **match)
{
	struct xt_statistic_info *info = (void *)(*match)->data;
	unsigned int val;
	double prob;

	if (invert)
		info->flags |= XT_STATISTIC_INVERT;

	switch (c) {
	case '1':
		if (*flags & 0x1)
			xtables_error(PARAMETER_PROBLEM, "double --mode");
		if (!strcmp(optarg, "random"))
			info->mode = XT_STATISTIC_MODE_RANDOM;
		else if (!strcmp(optarg, "nth"))
			info->mode = XT_STATISTIC_MODE_NTH;
		else
			xtables_error(PARAMETER_PROBLEM, "Bad mode \"%s\"", optarg);
		*flags |= 0x1;
		break;
	case '2':
		if (*flags & 0x2)
			xtables_error(PARAMETER_PROBLEM, "double --probability");
		prob = atof(optarg);
		if (prob < 0 || prob > 1)
			xtables_error(PARAMETER_PROBLEM,
				   "--probability must be between 0 and 1");
		info->u.random.probability = 0x80000000 * prob;
		*flags |= 0x2;
		break;
	case '3':
		if (*flags & 0x4)
			xtables_error(PARAMETER_PROBLEM, "double --every");
		if (!xtables_strtoui(optarg, NULL, &val, 0, UINT32_MAX))
			xtables_error(PARAMETER_PROBLEM,
				   "cannot parse --every `%s'", optarg);
		info->u.nth.every = val;
		if (info->u.nth.every == 0)
			xtables_error(PARAMETER_PROBLEM, "--every cannot be 0");
		info->u.nth.every--;
		*flags |= 0x4;
		break;
	case '4':
		if (*flags & 0x8)
			xtables_error(PARAMETER_PROBLEM, "double --packet");
		if (!xtables_strtoui(optarg, NULL, &val, 0, UINT32_MAX))
			xtables_error(PARAMETER_PROBLEM,
				   "cannot parse --packet `%s'", optarg);
		info->u.nth.packet = val;
		*flags |= 0x8;
		break;
	default:
		return 0;
	}
	return 1;
}

static void statistic_check(unsigned int flags)
{
	if (!(flags & 0x1))
		xtables_error(PARAMETER_PROBLEM, "no mode specified");
	if ((flags & 0x2) && (flags & (0x4 | 0x8)))
		xtables_error(PARAMETER_PROBLEM,
			   "both nth and random parameters given");
	if (flags & 0x2 && global_info->mode != XT_STATISTIC_MODE_RANDOM)
		xtables_error(PARAMETER_PROBLEM,
			   "--probability can only be used in random mode");
	if (flags & 0x4 && global_info->mode != XT_STATISTIC_MODE_NTH)
		xtables_error(PARAMETER_PROBLEM,
			   "--every can only be used in nth mode");
	if (flags & 0x8 && global_info->mode != XT_STATISTIC_MODE_NTH)
		xtables_error(PARAMETER_PROBLEM,
			   "--packet can only be used in nth mode");
	if ((flags & 0x8) && !(flags & 0x4))
		xtables_error(PARAMETER_PROBLEM,
			   "--packet can only be used with --every");
	/* at this point, info->u.nth.every have been decreased. */
	if (global_info->u.nth.packet > global_info->u.nth.every)
		xtables_error(PARAMETER_PROBLEM,
			  "the --packet p must be 0 <= p <= n-1");


	global_info->u.nth.count = global_info->u.nth.every -
	                           global_info->u.nth.packet;
}

static void print_match(const struct xt_statistic_info *info, char *prefix)
{
	if (info->flags & XT_STATISTIC_INVERT)
		printf("! ");

	switch (info->mode) {
	case XT_STATISTIC_MODE_RANDOM:
		printf("%smode random %sprobability %f ", prefix, prefix,
		       1.0 * info->u.random.probability / 0x80000000);
		break;
	case XT_STATISTIC_MODE_NTH:
		printf("%smode nth %severy %u ", prefix, prefix,
		       info->u.nth.every + 1);
		if (info->u.nth.packet)
			printf("%spacket %u ", prefix, info->u.nth.packet);
		break;
	}
}

static void
statistic_print(const void *ip, const struct xt_entry_match *match, int numeric)
{
	const struct xt_statistic_info *info = (const void *)match->data;

	printf("statistic ");
	print_match(info, "");
}

static void statistic_save(const void *ip, const struct xt_entry_match *match)
{
	const struct xt_statistic_info *info = (const void *)match->data;

	print_match(info, "--");
}

static struct xtables_match statistic_match = {
	.family		= NFPROTO_UNSPEC,
	.name		= "statistic",
	.version	= XTABLES_VERSION,
	.size		= XT_ALIGN(sizeof(struct xt_statistic_info)),
	.userspacesize	= offsetof(struct xt_statistic_info, u.nth.count),
	.init		= statistic_mt_init,
	.help		= statistic_help,
	.parse		= statistic_parse,
	.final_check	= statistic_check,
	.print		= statistic_print,
	.save		= statistic_save,
	.extra_opts	= statistic_opts,
};

void libxt_statistic_init(void)
{
	xtables_register_match(&statistic_match);
}
