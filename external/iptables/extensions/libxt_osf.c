/*
 * Copyright (c) 2003+ Evgeniy Polyakov <zbr@ioremap.net>
 *
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

/*
 * xtables interface for OS fingerprint matching module.
 */
#include <stdbool.h>
#include <stdio.h>
#include <netdb.h>
#include <string.h>
#include <stdlib.h>
#include <getopt.h>
#include <ctype.h>

#include <linux/types.h>

#include <xtables.h>

#include <netinet/ip.h>
#include <netinet/tcp.h>

#include <linux/netfilter/xt_osf.h>

static void osf_help(void)
{
	printf("OS fingerprint match options:\n"
		"[!] --genre string     Match a OS genre by passive fingerprinting.\n"
		"--ttl level            Use some TTL check extensions to determine OS:\n"
		"       0                       true ip and fingerprint TTL comparison. Works for LAN.\n"
		"       1                       check if ip TTL is less than fingerprint one. Works for global addresses.\n"
		"       2                       do not compare TTL at all. Allows to detect NMAP, but can produce false results.\n"
		"--log level            Log determined genres into dmesg even if they do not match desired one:\n"
		"       0                       log all matched or unknown signatures.\n"
		"       1                       log only first one.\n"
		"       2                       log all known matched signatures.\n"
		);
}


static const struct option osf_opts[] = {
	{.name = "genre", .has_arg = true, .val = '1'},
	{.name = "ttl",   .has_arg = true, .val = '2'},
	{.name = "log",   .has_arg = true, .val = '3'},
	XT_GETOPT_TABLEEND,
};


static void osf_parse_string(const char *s, struct xt_osf_info *info)
{
	if (strlen(s) < MAXGENRELEN)
		strcpy(info->genre, s);
	else
		xtables_error(PARAMETER_PROBLEM,
			      "Genre string too long `%s' [%zd], max=%d",
			      s, strlen(s), MAXGENRELEN);
}

static int osf_parse(int c, char **argv, int invert, unsigned int *flags,
      			const void *entry,
      			struct xt_entry_match **match)
{
	struct xt_osf_info *info = (struct xt_osf_info *)(*match)->data;

	switch(c) {
		case '1': /* --genre */
			if (*flags & XT_OSF_GENRE)
				xtables_error(PARAMETER_PROBLEM,
					      "Can't specify multiple genre parameter");
			xtables_check_inverse(optarg, &invert, &optind, 0, argv);
			osf_parse_string(argv[optind-1], info);
			if (invert)
				info->flags |= XT_OSF_INVERT;
			info->len=strlen(info->genre);
			*flags |= XT_OSF_GENRE;
			break;
		case '2': /* --ttl */
			if (*flags & XT_OSF_TTL)
				xtables_error(PARAMETER_PROBLEM,
					      "Can't specify multiple ttl parameter");
			*flags |= XT_OSF_TTL;
			info->flags |= XT_OSF_TTL;
			if (!xtables_strtoui(argv[optind-1], NULL, &info->ttl, 0, 2))
				xtables_error(PARAMETER_PROBLEM, "TTL parameter is too big");
			break;
		case '3': /* --log */
			if (*flags & XT_OSF_LOG)
				xtables_error(PARAMETER_PROBLEM,
					      "Can't specify multiple log parameter");
			*flags |= XT_OSF_LOG;
			if (!xtables_strtoui(argv[optind-1], NULL, &info->loglevel, 0, 2))
				xtables_error(PARAMETER_PROBLEM, "Log level parameter is too big");
			info->flags |= XT_OSF_LOG;
			break;
		default:
			return 0;
	}

	return 1;
}

static void osf_final_check(unsigned int flags)
{
	if (!flags)
		xtables_error(PARAMETER_PROBLEM,
			      "OS fingerprint match: You must specify `--genre'");
}

static void osf_print(const void *ip, const struct xt_entry_match *match, int numeric)
{
	const struct xt_osf_info *info = (const struct xt_osf_info*) match->data;

	printf("OS fingerprint match %s%s ", (info->flags & XT_OSF_INVERT) ? "! " : "", info->genre);
}

static void osf_save(const void *ip, const struct xt_entry_match *match)
{
	const struct xt_osf_info *info = (const struct xt_osf_info*) match->data;

	printf("--genre %s%s ", (info->flags & XT_OSF_INVERT) ? "! ": "", info->genre);
}

static struct xtables_match osf_match = {
	.name		= "osf",
	.version	= XTABLES_VERSION,
	.size		= XT_ALIGN(sizeof(struct xt_osf_info)),
	.userspacesize	= XT_ALIGN(sizeof(struct xt_osf_info)),
	.help		= osf_help,
	.parse		= osf_parse,
	.print		= osf_print,
	.final_check	= osf_final_check,
	.save		= osf_save,
	.extra_opts	= osf_opts,
	.family		= NFPROTO_IPV4
};

void libxt_osf_init(void)
{
	xtables_register_match(&osf_match);
}
