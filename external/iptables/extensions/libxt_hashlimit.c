/* ip6tables match extension for limiting packets per destination
 *
 * (C) 2003-2004 by Harald Welte <laforge@netfilter.org>
 *
 * Development of this code was funded by Astaro AG, http://www.astaro.com/
 *
 * Based on ipt_limit.c by
 * Jérôme de Vivie   <devivie@info.enserb.u-bordeaux.fr>
 * Hervé Eychenne    <rv@wallfire.org>
 * 
 * Error corections by nmalykh@bilim.com (22.01.2005)
 */
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <getopt.h>
#include <xtables.h>
#include <stddef.h>
#include <linux/netfilter/x_tables.h>
#include <linux/netfilter/xt_hashlimit.h>

#define XT_HASHLIMIT_BURST	5

/* miliseconds */
#define XT_HASHLIMIT_GCINTERVAL	1000
#define XT_HASHLIMIT_EXPIRE	10000

static void hashlimit_help(void)
{
	printf(
"hashlimit match options:\n"
"--hashlimit <avg>		max average match rate\n"
"                                [Packets per second unless followed by \n"
"                                /sec /minute /hour /day postfixes]\n"
"--hashlimit-mode <mode>		mode is a comma-separated list of\n"
"					dstip,srcip,dstport,srcport\n"
"--hashlimit-name <name>		name for /proc/net/ipt_hashlimit/\n"
"[--hashlimit-burst <num>]	number to match in a burst, default %u\n"
"[--hashlimit-htable-size <num>]	number of hashtable buckets\n"
"[--hashlimit-htable-max <num>]	number of hashtable entries\n"
"[--hashlimit-htable-gcinterval]	interval between garbage collection runs\n"
"[--hashlimit-htable-expire]	after which time are idle entries expired?\n",
XT_HASHLIMIT_BURST);
}

static void hashlimit_mt_help(void)
{
	printf(
"hashlimit match options:\n"
"  --hashlimit-upto <avg>           max average match rate\n"
"                                   [Packets per second unless followed by \n"
"                                   /sec /minute /hour /day postfixes]\n"
"  --hashlimit-above <avg>          min average match rate\n"
"  --hashlimit-mode <mode>          mode is a comma-separated list of\n"
"                                   dstip,srcip,dstport,srcport (or none)\n"
"  --hashlimit-srcmask <length>     source address grouping prefix length\n"
"  --hashlimit-dstmask <length>     destination address grouping prefix length\n"
"  --hashlimit-name <name>          name for /proc/net/ipt_hashlimit\n"
"  --hashlimit-burst <num>	    number to match in a burst, default %u\n"
"  --hashlimit-htable-size <num>    number of hashtable buckets\n"
"  --hashlimit-htable-max <num>     number of hashtable entries\n"
"  --hashlimit-htable-gcinterval    interval between garbage collection runs\n"
"  --hashlimit-htable-expire        after which time are idle entries expired?\n"
"\n", XT_HASHLIMIT_BURST);
}

static const struct option hashlimit_opts[] = {
	{.name = "hashlimit",                   .has_arg = true, .val = '%'},
	{.name = "hashlimit-burst",             .has_arg = true, .val = '$'},
	{.name = "hashlimit-htable-size",       .has_arg = true, .val = '&'},
	{.name = "hashlimit-htable-max",        .has_arg = true, .val = '*'},
	{.name = "hashlimit-htable-gcinterval", .has_arg = true, .val = '('},
	{.name = "hashlimit-htable-expire",     .has_arg = true, .val = ')'},
	{.name = "hashlimit-mode",              .has_arg = true, .val = '_'},
	{.name = "hashlimit-name",              .has_arg = true, .val = '"'},
	XT_GETOPT_TABLEEND,
};

static const struct option hashlimit_mt_opts[] = {
	{.name = "hashlimit-upto",              .has_arg = true, .val = '%'},
	{.name = "hashlimit-above",             .has_arg = true, .val = '^'},
	{.name = "hashlimit",                   .has_arg = true, .val = '%'},
	{.name = "hashlimit-srcmask",           .has_arg = true, .val = '<'},
	{.name = "hashlimit-dstmask",           .has_arg = true, .val = '>'},
	{.name = "hashlimit-burst",             .has_arg = true, .val = '$'},
	{.name = "hashlimit-htable-size",       .has_arg = true, .val = '&'},
	{.name = "hashlimit-htable-max",        .has_arg = true, .val = '*'},
	{.name = "hashlimit-htable-gcinterval", .has_arg = true, .val = '('},
	{.name = "hashlimit-htable-expire",     .has_arg = true, .val = ')'},
	{.name = "hashlimit-mode",              .has_arg = true, .val = '_'},
	{.name = "hashlimit-name",              .has_arg = true, .val = '"'},
	XT_GETOPT_TABLEEND,
};

static
int parse_rate(const char *rate, u_int32_t *val)
{
	const char *delim;
	u_int32_t r;
	u_int32_t mult = 1;  /* Seconds by default. */

	delim = strchr(rate, '/');
	if (delim) {
		if (strlen(delim+1) == 0)
			return 0;

		if (strncasecmp(delim+1, "second", strlen(delim+1)) == 0)
			mult = 1;
		else if (strncasecmp(delim+1, "minute", strlen(delim+1)) == 0)
			mult = 60;
		else if (strncasecmp(delim+1, "hour", strlen(delim+1)) == 0)
			mult = 60*60;
		else if (strncasecmp(delim+1, "day", strlen(delim+1)) == 0)
			mult = 24*60*60;
		else
			return 0;
	}
	r = atoi(rate);
	if (!r)
		return 0;

	/* This would get mapped to infinite (1/day is minimum they
           can specify, so we're ok at that end). */
	if (r / mult > XT_HASHLIMIT_SCALE)
		xtables_error(PARAMETER_PROBLEM, "Rate too fast \"%s\"\n", rate);

	*val = XT_HASHLIMIT_SCALE * mult / r;
	return 1;
}

static void hashlimit_init(struct xt_entry_match *m)
{
	struct xt_hashlimit_info *r = (struct xt_hashlimit_info *)m->data;

	r->cfg.mode = 0;
	r->cfg.burst = XT_HASHLIMIT_BURST;
	r->cfg.gc_interval = XT_HASHLIMIT_GCINTERVAL;
	r->cfg.expire = XT_HASHLIMIT_EXPIRE;

}

static void hashlimit_mt4_init(struct xt_entry_match *match)
{
	struct xt_hashlimit_mtinfo1 *info = (void *)match->data;

	info->cfg.mode        = 0;
	info->cfg.burst       = XT_HASHLIMIT_BURST;
	info->cfg.gc_interval = XT_HASHLIMIT_GCINTERVAL;
	info->cfg.expire      = XT_HASHLIMIT_EXPIRE;
	info->cfg.srcmask     = 32;
	info->cfg.dstmask     = 32;
}

static void hashlimit_mt6_init(struct xt_entry_match *match)
{
	struct xt_hashlimit_mtinfo1 *info = (void *)match->data;

	info->cfg.mode        = 0;
	info->cfg.burst       = XT_HASHLIMIT_BURST;
	info->cfg.gc_interval = XT_HASHLIMIT_GCINTERVAL;
	info->cfg.expire      = XT_HASHLIMIT_EXPIRE;
	info->cfg.srcmask     = 128;
	info->cfg.dstmask     = 128;
}

/* Parse a 'mode' parameter into the required bitmask */
static int parse_mode(uint32_t *mode, char *option_arg)
{
	char *tok;
	char *arg = strdup(option_arg);

	if (!arg)
		return -1;

	for (tok = strtok(arg, ",|");
	     tok;
	     tok = strtok(NULL, ",|")) {
		if (!strcmp(tok, "dstip"))
			*mode |= XT_HASHLIMIT_HASH_DIP;
		else if (!strcmp(tok, "srcip"))
			*mode |= XT_HASHLIMIT_HASH_SIP;
		else if (!strcmp(tok, "srcport"))
			*mode |= XT_HASHLIMIT_HASH_SPT;
		else if (!strcmp(tok, "dstport"))
			*mode |= XT_HASHLIMIT_HASH_DPT;
		else {
			free(arg);
			return -1;
		}
	}
	free(arg);
	return 0;
}

enum {
	PARAM_LIMIT      = 1 << 0,
	PARAM_BURST      = 1 << 1,
	PARAM_MODE       = 1 << 2,
	PARAM_NAME       = 1 << 3,
	PARAM_SIZE       = 1 << 4,
	PARAM_MAX        = 1 << 5,
	PARAM_GCINTERVAL = 1 << 6,
	PARAM_EXPIRE     = 1 << 7,
	PARAM_SRCMASK    = 1 << 8,
	PARAM_DSTMASK    = 1 << 9,
};

static int
hashlimit_parse(int c, char **argv, int invert, unsigned int *flags,
                const void *entry, struct xt_entry_match **match)
{
	struct xt_hashlimit_info *r = 
			(struct xt_hashlimit_info *)(*match)->data;
	unsigned int num;

	switch(c) {
	case '%':
		xtables_param_act(XTF_ONLY_ONCE, "hashlimit", "--hashlimit",
		          *flags & PARAM_LIMIT);
		if (xtables_check_inverse(optarg, &invert, &optind, 0, argv)) break;
		if (!parse_rate(optarg, &r->cfg.avg))
			xtables_error(PARAMETER_PROBLEM,
				   "bad rate `%s'", optarg);
		*flags |= PARAM_LIMIT;
		break;

	case '$':
		xtables_param_act(XTF_ONLY_ONCE, "hashlimit", "--hashlimit-burst",
		          *flags & PARAM_BURST);
		if (xtables_check_inverse(optarg, &invert, &optind, 0, argv)) break;
		if (!xtables_strtoui(optarg, NULL, &num, 0, 10000))
			xtables_error(PARAMETER_PROBLEM,
				   "bad --hashlimit-burst `%s'", optarg);
		r->cfg.burst = num;
		*flags |= PARAM_BURST;
		break;
	case '&':
		xtables_param_act(XTF_ONLY_ONCE, "hashlimit", "--hashlimit-htable-size",
		          *flags & PARAM_SIZE);
		if (xtables_check_inverse(optarg, &invert, &optind, 0, argv)) break;
		if (!xtables_strtoui(optarg, NULL, &num, 0, UINT32_MAX))
			xtables_error(PARAMETER_PROBLEM,
				"bad --hashlimit-htable-size: `%s'", optarg);
		r->cfg.size = num;
		*flags |= PARAM_SIZE;
		break;
	case '*':
		xtables_param_act(XTF_ONLY_ONCE, "hashlimit", "--hashlimit-htable-max",
		          *flags & PARAM_MAX);
		if (xtables_check_inverse(optarg, &invert, &optind, 0, argv)) break;
		if (!xtables_strtoui(optarg, NULL, &num, 0, UINT32_MAX))
			xtables_error(PARAMETER_PROBLEM,
				"bad --hashlimit-htable-max: `%s'", optarg);
		r->cfg.max = num;
		*flags |= PARAM_MAX;
		break;
	case '(':
		xtables_param_act(XTF_ONLY_ONCE, "hashlimit",
		          "--hashlimit-htable-gcinterval",
		          *flags & PARAM_GCINTERVAL);
		if (xtables_check_inverse(optarg, &invert, &optind, 0, argv)) break;
		if (!xtables_strtoui(optarg, NULL, &num, 0, UINT32_MAX))
			xtables_error(PARAMETER_PROBLEM,
				"bad --hashlimit-htable-gcinterval: `%s'", 
				optarg);
		/* FIXME: not HZ dependent!! */
		r->cfg.gc_interval = num;
		*flags |= PARAM_GCINTERVAL;
		break;
	case ')':
		xtables_param_act(XTF_ONLY_ONCE, "hashlimit",
		          "--hashlimit-htable-expire", *flags & PARAM_EXPIRE);
		if (xtables_check_inverse(optarg, &invert, &optind, 0, argv)) break;
		if (!xtables_strtoui(optarg, NULL, &num, 0, UINT32_MAX))
			xtables_error(PARAMETER_PROBLEM,
				"bad --hashlimit-htable-expire: `%s'", optarg);
		/* FIXME: not HZ dependent */
		r->cfg.expire = num;
		*flags |= PARAM_EXPIRE;
		break;
	case '_':
		xtables_param_act(XTF_ONLY_ONCE, "hashlimit", "--hashlimit-mode",
		          *flags & PARAM_MODE);
		if (xtables_check_inverse(optarg, &invert, &optind, 0, argv)) break;
		if (parse_mode(&r->cfg.mode, optarg) < 0)
			xtables_error(PARAMETER_PROBLEM,
				   "bad --hashlimit-mode: `%s'\n", optarg);
		*flags |= PARAM_MODE;
		break;
	case '"':
		xtables_param_act(XTF_ONLY_ONCE, "hashlimit", "--hashlimit-name",
		          *flags & PARAM_NAME);
		if (xtables_check_inverse(optarg, &invert, &optind, 0, argv)) break;
		if (strlen(optarg) == 0)
			xtables_error(PARAMETER_PROBLEM, "Zero-length name?");
		strncpy(r->name, optarg, sizeof(r->name));
		*flags |= PARAM_NAME;
		break;
	default:
		return 0;
	}

	if (invert)
		xtables_error(PARAMETER_PROBLEM,
			   "hashlimit does not support invert");

	return 1;
}

static int
hashlimit_mt_parse(struct xt_hashlimit_mtinfo1 *info, unsigned int *flags,
                   int c, int invert, unsigned int maxmask)
{
	unsigned int num;

	switch(c) {
	case '%': /* --hashlimit / --hashlimit-below */
		xtables_param_act(XTF_ONLY_ONCE, "hashlimit", "--hashlimit-upto",
		          *flags & PARAM_LIMIT);
		if (invert)
			info->cfg.mode |= XT_HASHLIMIT_INVERT;
		if (!parse_rate(optarg, &info->cfg.avg))
			xtables_param_act(XTF_BAD_VALUE, "hashlimit",
			          "--hashlimit-upto", optarg);
		*flags |= PARAM_LIMIT;
		return true;

	case '^': /* --hashlimit-above == !--hashlimit-below */
		xtables_param_act(XTF_ONLY_ONCE, "hashlimit", "--hashlimit-above",
		          *flags & PARAM_LIMIT);
		if (!invert)
			info->cfg.mode |= XT_HASHLIMIT_INVERT;
		if (!parse_rate(optarg, &info->cfg.avg))
			xtables_param_act(XTF_BAD_VALUE, "hashlimit",
			          "--hashlimit-above", optarg);
		*flags |= PARAM_LIMIT;
		return true;

	case '$': /* --hashlimit-burst */
		xtables_param_act(XTF_ONLY_ONCE, "hashlimit", "--hashlimit-burst",
		          *flags & PARAM_BURST);
		if (!xtables_strtoui(optarg, NULL, &num, 0, 10000))
			xtables_param_act(XTF_BAD_VALUE, "hashlimit",
			          "--hashlimit-burst", optarg);
		info->cfg.burst = num;
		*flags |= PARAM_BURST;
		return true;

	case '&': /* --hashlimit-htable-size */
		xtables_param_act(XTF_ONLY_ONCE, "hashlimit", "--hashlimit-htable-size",
		          *flags & PARAM_SIZE);
		if (!xtables_strtoui(optarg, NULL, &num, 0, UINT32_MAX))
			xtables_param_act(XTF_BAD_VALUE, "hashlimit",
			          "--hashlimit-htable-size", optarg);
		info->cfg.size = num;
		*flags |= PARAM_SIZE;
		return true;

	case '*': /* --hashlimit-htable-max */
		xtables_param_act(XTF_ONLY_ONCE, "hashlimit", "--hashlimit-htable-max",
		          *flags & PARAM_MAX);
		if (!xtables_strtoui(optarg, NULL, &num, 0, UINT32_MAX))
			xtables_param_act(XTF_BAD_VALUE, "hashlimit",
			          "--hashlimit-htable-max", optarg);
		info->cfg.max = num;
		*flags |= PARAM_MAX;
		return true;

	case '(': /* --hashlimit-htable-gcinterval */
		xtables_param_act(XTF_ONLY_ONCE, "hashlimit",
		          "--hashlimit-htable-gcinterval",
		          *flags & PARAM_GCINTERVAL);
		if (!xtables_strtoui(optarg, NULL, &num, 0, UINT32_MAX))
			xtables_param_act(XTF_BAD_VALUE, "hashlimit",
			          "--hashlimit-htable-gcinterval", optarg);
		/* FIXME: not HZ dependent!! */
		info->cfg.gc_interval = num;
		*flags |= PARAM_GCINTERVAL;
		return true;

	case ')': /* --hashlimit-htable-expire */
		xtables_param_act(XTF_ONLY_ONCE, "hashlimit",
		          "--hashlimit-htable-expire", *flags & PARAM_EXPIRE);
		if (!xtables_strtoui(optarg, NULL, &num, 0, UINT32_MAX))
			xtables_param_act(XTF_BAD_VALUE, "hashlimit",
			          "--hashlimit-htable-expire", optarg);
		/* FIXME: not HZ dependent */
		info->cfg.expire = num;
		*flags |= PARAM_EXPIRE;
		return true;

	case '_':
		xtables_param_act(XTF_ONLY_ONCE, "hashlimit", "--hashlimit-mode",
		          *flags & PARAM_MODE);
		if (parse_mode(&info->cfg.mode, optarg) < 0)
			xtables_param_act(XTF_BAD_VALUE, "hashlimit",
			          "--hashlimit-mode", optarg);
		*flags |= PARAM_MODE;
		return true;

	case '"': /* --hashlimit-name */
		xtables_param_act(XTF_ONLY_ONCE, "hashlimit", "--hashlimit-name",
		          *flags & PARAM_NAME);
		if (strlen(optarg) == 0)
			xtables_error(PARAMETER_PROBLEM, "Zero-length name?");
		strncpy(info->name, optarg, sizeof(info->name));
		info->name[sizeof(info->name)-1] = '\0';
		*flags |= PARAM_NAME;
		return true;

	case '<': /* --hashlimit-srcmask */
		xtables_param_act(XTF_ONLY_ONCE, "hashlimit", "--hashlimit-srcmask",
		          *flags & PARAM_SRCMASK);
		if (!xtables_strtoui(optarg, NULL, &num, 0, maxmask))
			xtables_param_act(XTF_BAD_VALUE, "hashlimit",
			          "--hashlimit-srcmask", optarg);
		info->cfg.srcmask = num;
		*flags |= PARAM_SRCMASK;
		return true;

	case '>': /* --hashlimit-dstmask */
		xtables_param_act(XTF_ONLY_ONCE, "hashlimit", "--hashlimit-dstmask",
		          *flags & PARAM_DSTMASK);
		if (!xtables_strtoui(optarg, NULL, &num, 0, maxmask))
			xtables_param_act(XTF_BAD_VALUE, "hashlimit",
			          "--hashlimit-dstmask", optarg);
		info->cfg.dstmask = num;
		*flags |= PARAM_DSTMASK;
		return true;
	}
	return false;
}

static int
hashlimit_mt4_parse(int c, char **argv, int invert, unsigned int *flags,
                    const void *entry, struct xt_entry_match **match)
{
	return hashlimit_mt_parse((void *)(*match)->data,
	       flags, c, invert, 32);
}

static int
hashlimit_mt6_parse(int c, char **argv, int invert, unsigned int *flags,
                    const void *entry, struct xt_entry_match **match)
{
	return hashlimit_mt_parse((void *)(*match)->data,
	       flags, c, invert, 128);
}

static void hashlimit_check(unsigned int flags)
{
	if (!(flags & PARAM_LIMIT))
		xtables_error(PARAMETER_PROBLEM,
				"You have to specify --hashlimit");
	if (!(flags & PARAM_MODE))
		xtables_error(PARAMETER_PROBLEM,
				"You have to specify --hashlimit-mode");
	if (!(flags & PARAM_NAME))
		xtables_error(PARAMETER_PROBLEM,
				"You have to specify --hashlimit-name");
}

static void hashlimit_mt_check(unsigned int flags)
{
	if (!(flags & PARAM_LIMIT))
		xtables_error(PARAMETER_PROBLEM, "You have to specify "
		           "--hashlimit-upto or --hashlimit-above");
	if (!(flags & PARAM_NAME))
		xtables_error(PARAMETER_PROBLEM,
		           "You have to specify --hashlimit-name");
}

static const struct rates
{
	const char *name;
	u_int32_t mult;
} rates[] = { { "day", XT_HASHLIMIT_SCALE*24*60*60 },
	      { "hour", XT_HASHLIMIT_SCALE*60*60 },
	      { "min", XT_HASHLIMIT_SCALE*60 },
	      { "sec", XT_HASHLIMIT_SCALE } };

static void print_rate(u_int32_t period)
{
	unsigned int i;

	for (i = 1; i < ARRAY_SIZE(rates); ++i)
		if (period > rates[i].mult
            || rates[i].mult/period < rates[i].mult%period)
			break;

	printf("%u/%s ", rates[i-1].mult / period, rates[i-1].name);
}

static void print_mode(unsigned int mode, char separator)
{
	bool prevmode = false;

	if (mode & XT_HASHLIMIT_HASH_SIP) {
		fputs("srcip", stdout);
		prevmode = 1;
	}
	if (mode & XT_HASHLIMIT_HASH_SPT) {
		if (prevmode)
			putchar(separator);
		fputs("srcport", stdout);
		prevmode = 1;
	}
	if (mode & XT_HASHLIMIT_HASH_DIP) {
		if (prevmode)
			putchar(separator);
		fputs("dstip", stdout);
		prevmode = 1;
	}
	if (mode & XT_HASHLIMIT_HASH_DPT) {
		if (prevmode)
			putchar(separator);
		fputs("dstport", stdout);
	}
	putchar(' ');
}

static void hashlimit_print(const void *ip,
                            const struct xt_entry_match *match, int numeric)
{
	const struct xt_hashlimit_info *r = (const void *)match->data;
	fputs("limit: avg ", stdout); print_rate(r->cfg.avg);
	printf("burst %u ", r->cfg.burst);
	fputs("mode ", stdout);
	print_mode(r->cfg.mode, '-');
	if (r->cfg.size)
		printf("htable-size %u ", r->cfg.size);
	if (r->cfg.max)
		printf("htable-max %u ", r->cfg.max);
	if (r->cfg.gc_interval != XT_HASHLIMIT_GCINTERVAL)
		printf("htable-gcinterval %u ", r->cfg.gc_interval);
	if (r->cfg.expire != XT_HASHLIMIT_EXPIRE)
		printf("htable-expire %u ", r->cfg.expire);
}

static void
hashlimit_mt_print(const struct xt_hashlimit_mtinfo1 *info, unsigned int dmask)
{
	if (info->cfg.mode & XT_HASHLIMIT_INVERT)
		fputs("limit: above ", stdout);
	else
		fputs("limit: up to ", stdout);
	print_rate(info->cfg.avg);
	printf("burst %u ", info->cfg.burst);
	if (info->cfg.mode & (XT_HASHLIMIT_HASH_SIP | XT_HASHLIMIT_HASH_SPT |
	    XT_HASHLIMIT_HASH_DIP | XT_HASHLIMIT_HASH_DPT)) {
		fputs("mode ", stdout);
		print_mode(info->cfg.mode, '-');
	}
	if (info->cfg.size != 0)
		printf("htable-size %u ", info->cfg.size);
	if (info->cfg.max != 0)
		printf("htable-max %u ", info->cfg.max);
	if (info->cfg.gc_interval != XT_HASHLIMIT_GCINTERVAL)
		printf("htable-gcinterval %u ", info->cfg.gc_interval);
	if (info->cfg.expire != XT_HASHLIMIT_EXPIRE)
		printf("htable-expire %u ", info->cfg.expire);

	if (info->cfg.srcmask != dmask)
		printf("srcmask %u ", info->cfg.srcmask);
	if (info->cfg.dstmask != dmask)
		printf("dstmask %u ", info->cfg.dstmask);
}

static void
hashlimit_mt4_print(const void *ip, const struct xt_entry_match *match,
                   int numeric)
{
	const struct xt_hashlimit_mtinfo1 *info = (const void *)match->data;

	hashlimit_mt_print(info, 32);
}

static void
hashlimit_mt6_print(const void *ip, const struct xt_entry_match *match,
                   int numeric)
{
	const struct xt_hashlimit_mtinfo1 *info = (const void *)match->data;

	hashlimit_mt_print(info, 128);
}

static void hashlimit_save(const void *ip, const struct xt_entry_match *match)
{
	const struct xt_hashlimit_info *r = (const void *)match->data;

	fputs("--hashlimit ", stdout); print_rate(r->cfg.avg);
	printf("--hashlimit-burst %u ", r->cfg.burst);

	fputs("--hashlimit-mode ", stdout);
	print_mode(r->cfg.mode, ',');
	
	printf("--hashlimit-name %s ", r->name);

	if (r->cfg.size)
		printf("--hashlimit-htable-size %u ", r->cfg.size);
	if (r->cfg.max)
		printf("--hashlimit-htable-max %u ", r->cfg.max);
	if (r->cfg.gc_interval != XT_HASHLIMIT_GCINTERVAL)
		printf("--hashlimit-htable-gcinterval %u ", r->cfg.gc_interval);
	if (r->cfg.expire != XT_HASHLIMIT_EXPIRE)
		printf("--hashlimit-htable-expire %u ", r->cfg.expire);
}

static void
hashlimit_mt_save(const struct xt_hashlimit_mtinfo1 *info, unsigned int dmask)
{
	if (info->cfg.mode & XT_HASHLIMIT_INVERT)
		fputs("--hashlimit-above ", stdout);
	else
		fputs("--hashlimit-upto ", stdout);
	print_rate(info->cfg.avg);
	printf("--hashlimit-burst %u ", info->cfg.burst);

	if (info->cfg.mode & (XT_HASHLIMIT_HASH_SIP | XT_HASHLIMIT_HASH_SPT |
	    XT_HASHLIMIT_HASH_DIP | XT_HASHLIMIT_HASH_DPT)) {
		fputs("--hashlimit-mode ", stdout);
		print_mode(info->cfg.mode, ',');
	}

	printf("--hashlimit-name %s ", info->name);

	if (info->cfg.size != 0)
		printf("--hashlimit-htable-size %u ", info->cfg.size);
	if (info->cfg.max != 0)
		printf("--hashlimit-htable-max %u ", info->cfg.max);
	if (info->cfg.gc_interval != XT_HASHLIMIT_GCINTERVAL)
		printf("--hashlimit-htable-gcinterval %u ", info->cfg.gc_interval);
	if (info->cfg.expire != XT_HASHLIMIT_EXPIRE)
		printf("--hashlimit-htable-expire %u ", info->cfg.expire);

	if (info->cfg.srcmask != dmask)
		printf("--hashlimit-srcmask %u ", info->cfg.srcmask);
	if (info->cfg.dstmask != dmask)
		printf("--hashlimit-dstmask %u ", info->cfg.dstmask);
}

static void
hashlimit_mt4_save(const void *ip, const struct xt_entry_match *match)
{
	const struct xt_hashlimit_mtinfo1 *info = (const void *)match->data;

	hashlimit_mt_save(info, 32);
}

static void
hashlimit_mt6_save(const void *ip, const struct xt_entry_match *match)
{
	const struct xt_hashlimit_mtinfo1 *info = (const void *)match->data;

	hashlimit_mt_save(info, 128);
}

static struct xtables_match hashlimit_mt_reg[] = {
	{
		.family        = NFPROTO_UNSPEC,
		.name          = "hashlimit",
		.version       = XTABLES_VERSION,
		.revision      = 0,
		.size          = XT_ALIGN(sizeof(struct xt_hashlimit_info)),
		.userspacesize = offsetof(struct xt_hashlimit_info, hinfo),
		.help          = hashlimit_help,
		.init          = hashlimit_init,
		.parse         = hashlimit_parse,
		.final_check   = hashlimit_check,
		.print         = hashlimit_print,
		.save          = hashlimit_save,
		.extra_opts    = hashlimit_opts,
	},
	{
		.version       = XTABLES_VERSION,
		.name          = "hashlimit",
		.revision      = 1,
		.family        = NFPROTO_IPV4,
		.size          = XT_ALIGN(sizeof(struct xt_hashlimit_mtinfo1)),
		.userspacesize = offsetof(struct xt_hashlimit_mtinfo1, hinfo),
		.help          = hashlimit_mt_help,
		.init          = hashlimit_mt4_init,
		.parse         = hashlimit_mt4_parse,
		.final_check   = hashlimit_mt_check,
		.print         = hashlimit_mt4_print,
		.save          = hashlimit_mt4_save,
		.extra_opts    = hashlimit_mt_opts,
	},
	{
		.version       = XTABLES_VERSION,
		.name          = "hashlimit",
		.revision      = 1,
		.family        = NFPROTO_IPV6,
		.size          = XT_ALIGN(sizeof(struct xt_hashlimit_mtinfo1)),
		.userspacesize = offsetof(struct xt_hashlimit_mtinfo1, hinfo),
		.help          = hashlimit_mt_help,
		.init          = hashlimit_mt6_init,
		.parse         = hashlimit_mt6_parse,
		.final_check   = hashlimit_mt_check,
		.print         = hashlimit_mt6_print,
		.save          = hashlimit_mt6_save,
		.extra_opts    = hashlimit_mt_opts,
	},
};

void libxt_hashlimit_init(void)
{
	xtables_register_matches(hashlimit_mt_reg, ARRAY_SIZE(hashlimit_mt_reg));
}
