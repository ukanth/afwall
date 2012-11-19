/* Shared library add-on to iptables to add recent matching support. */
#include <stdbool.h>
#include <stdio.h>
#include <netdb.h>
#include <string.h>
#include <stdlib.h>
#include <getopt.h>

#include <xtables.h>
#include <linux/netfilter/xt_recent.h>

static const struct option recent_opts[] = {
	{.name = "set",      .has_arg = false, .val = 201},
	{.name = "rcheck",   .has_arg = false, .val = 202},
	{.name = "update",   .has_arg = false, .val = 203},
	{.name = "seconds",  .has_arg = true,  .val = 204},
	{.name = "hitcount", .has_arg = true,  .val = 205},
	{.name = "remove",   .has_arg = false, .val = 206},
	{.name = "rttl",     .has_arg = false, .val = 207},
	{.name = "name",     .has_arg = true,  .val = 208},
	{.name = "rsource",  .has_arg = false, .val = 209},
	{.name = "rdest",    .has_arg = false, .val = 210},
	XT_GETOPT_TABLEEND,
};

static void recent_help(void)
{
	printf(
"recent match options:\n"
"[!] --set                       Add source address to list, always matches.\n"
"[!] --rcheck                    Match if source address in list.\n"
"[!] --update                    Match if source address in list, also update last-seen time.\n"
"[!] --remove                    Match if source address in list, also removes that address from list.\n"
"    --seconds seconds           For check and update commands above.\n"
"                                Specifies that the match will only occur if source address last seen within\n"
"                                the last 'seconds' seconds.\n"
"    --hitcount hits             For check and update commands above.\n"
"                                Specifies that the match will only occur if source address seen hits times.\n"
"                                May be used in conjunction with the seconds option.\n"
"    --rttl                      For check and update commands above.\n"
"                                Specifies that the match will only occur if the source address and the TTL\n"
"                                match between this packet and the one which was set.\n"
"                                Useful if you have problems with people spoofing their source address in order\n"
"                                to DoS you via this module.\n"
"    --name name                 Name of the recent list to be used.  DEFAULT used if none given.\n"
"    --rsource                   Match/Save the source address of each packet in the recent list table (default).\n"
"    --rdest                     Match/Save the destination address of each packet in the recent list table.\n"
"xt_recent by: Stephen Frost <sfrost@snowman.net>.  http://snowman.net/projects/ipt_recent/\n");
}

static void recent_init(struct xt_entry_match *match)
{
	struct xt_recent_mtinfo *info = (void *)(match)->data;

	strncpy(info->name,"DEFAULT", XT_RECENT_NAME_LEN);
	/* even though XT_RECENT_NAME_LEN is currently defined as 200,
	 * better be safe, than sorry */
	info->name[XT_RECENT_NAME_LEN-1] = '\0';
	info->side = XT_RECENT_SOURCE;
}

#define RECENT_CMDS \
	(XT_RECENT_SET | XT_RECENT_CHECK | \
	XT_RECENT_UPDATE | XT_RECENT_REMOVE)

static int recent_parse(int c, char **argv, int invert, unsigned int *flags,
                        const void *entry, struct xt_entry_match **match)
{
	struct xt_recent_mtinfo *info = (void *)(*match)->data;

	switch (c) {
		case 201:
			if (*flags & RECENT_CMDS)
				xtables_error(PARAMETER_PROBLEM,
					"recent: only one of `--set', `--rcheck' "
					"`--update' or `--remove' may be set");
			xtables_check_inverse(optarg, &invert, &optind, 0, argv);
			info->check_set |= XT_RECENT_SET;
			if (invert) info->invert = 1;
			*flags |= XT_RECENT_SET;
			break;

		case 202:
			if (*flags & RECENT_CMDS)
				xtables_error(PARAMETER_PROBLEM,
					"recent: only one of `--set', `--rcheck' "
					"`--update' or `--remove' may be set");
			xtables_check_inverse(optarg, &invert, &optind, 0, argv);
			info->check_set |= XT_RECENT_CHECK;
			if(invert) info->invert = 1;
			*flags |= XT_RECENT_CHECK;
			break;

		case 203:
			if (*flags & RECENT_CMDS)
				xtables_error(PARAMETER_PROBLEM,
					"recent: only one of `--set', `--rcheck' "
					"`--update' or `--remove' may be set");
			xtables_check_inverse(optarg, &invert, &optind, 0, argv);
			info->check_set |= XT_RECENT_UPDATE;
			if (invert) info->invert = 1;
			*flags |= XT_RECENT_UPDATE;
			break;

		case 204:
			info->seconds = atoi(optarg);
			break;

		case 205:
			info->hit_count = atoi(optarg);
			break;

		case 206:
			if (*flags & RECENT_CMDS)
				xtables_error(PARAMETER_PROBLEM,
					"recent: only one of `--set', `--rcheck' "
					"`--update' or `--remove' may be set");
			xtables_check_inverse(optarg, &invert, &optind, 0, argv);
			info->check_set |= XT_RECENT_REMOVE;
			if (invert) info->invert = 1;
			*flags |= XT_RECENT_REMOVE;
			break;

		case 207:
			info->check_set |= XT_RECENT_TTL;
			*flags |= XT_RECENT_TTL;
			break;

		case 208:
			strncpy(info->name,optarg, XT_RECENT_NAME_LEN);
			info->name[XT_RECENT_NAME_LEN-1] = '\0';
			break;

		case 209:
			info->side = XT_RECENT_SOURCE;
			break;

		case 210:
			info->side = XT_RECENT_DEST;
			break;

		default:
			return 0;
	}

	return 1;
}

static void recent_check(unsigned int flags)
{
	if (!(flags & RECENT_CMDS))
		xtables_error(PARAMETER_PROBLEM,
			"recent: you must specify one of `--set', `--rcheck' "
			"`--update' or `--remove'");
	if ((flags & XT_RECENT_TTL) &&
	    (flags & (XT_RECENT_SET | XT_RECENT_REMOVE)))
		xtables_error(PARAMETER_PROBLEM,
		           "recent: --rttl may only be used with --rcheck or "
		           "--update");
}

static void recent_print(const void *ip, const struct xt_entry_match *match,
                         int numeric)
{
	const struct xt_recent_mtinfo *info = (const void *)match->data;

	if (info->invert)
		fputc('!', stdout);

	printf("recent: ");
	if (info->check_set & XT_RECENT_SET)
		printf("SET ");
	if (info->check_set & XT_RECENT_CHECK)
		printf("CHECK ");
	if (info->check_set & XT_RECENT_UPDATE)
		printf("UPDATE ");
	if (info->check_set & XT_RECENT_REMOVE)
		printf("REMOVE ");
	if(info->seconds) printf("seconds: %d ",info->seconds);
	if(info->hit_count) printf("hit_count: %d ",info->hit_count);
	if (info->check_set & XT_RECENT_TTL)
		printf("TTL-Match ");
	if(info->name) printf("name: %s ",info->name);
	if (info->side == XT_RECENT_SOURCE)
		printf("side: source ");
	if (info->side == XT_RECENT_DEST)
		printf("side: dest ");
}

static void recent_save(const void *ip, const struct xt_entry_match *match)
{
	const struct xt_recent_mtinfo *info = (const void *)match->data;

	if (info->invert)
		printf("! ");

	if (info->check_set & XT_RECENT_SET)
		printf("--set ");
	if (info->check_set & XT_RECENT_CHECK)
		printf("--rcheck ");
	if (info->check_set & XT_RECENT_UPDATE)
		printf("--update ");
	if (info->check_set & XT_RECENT_REMOVE)
		printf("--remove ");
	if(info->seconds) printf("--seconds %d ",info->seconds);
	if(info->hit_count) printf("--hitcount %d ",info->hit_count);
	if (info->check_set & XT_RECENT_TTL)
		printf("--rttl ");
	if(info->name) printf("--name %s ",info->name);
	if (info->side == XT_RECENT_SOURCE)
		printf("--rsource ");
	if (info->side == XT_RECENT_DEST)
		printf("--rdest ");
}

static struct xtables_match recent_mt_reg = {
    .name          = "recent",
    .version       = XTABLES_VERSION,
    .family        = NFPROTO_UNSPEC,
    .size          = XT_ALIGN(sizeof(struct xt_recent_mtinfo)),
    .userspacesize = XT_ALIGN(sizeof(struct xt_recent_mtinfo)),
    .help          = recent_help,
    .init          = recent_init,
    .parse         = recent_parse,
    .final_check   = recent_check,
    .print         = recent_print,
    .save          = recent_save,
    .extra_opts    = recent_opts,
};

void libxt_recent_init(void)
{
	xtables_register_match(&recent_mt_reg);
}
