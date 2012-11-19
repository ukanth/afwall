/* Shared library add-on to iptables to add string matching support. 
 * 
 * Copyright (C) 2000 Emmanuel Roger  <winfield@freegates.be>
 *
 * 2005-08-05 Pablo Neira Ayuso <pablo@eurodev.net>
 * 	- reimplemented to use new string matching iptables match
 * 	- add functionality to match packets by using window offsets
 * 	- add functionality to select the string matching algorithm
 *
 * ChangeLog
 *     29.12.2003: Michael Rash <mbr@cipherdyne.org>
 *             Fixed iptables save/restore for ascii strings
 *             that contain space chars, and hex strings that
 *             contain embedded NULL chars.  Updated to print
 *             strings in hex mode if any non-printable char
 *             is contained within the string.
 *
 *     27.01.2001: Gianni Tedesco <gianni@ecsc.co.uk>
 *             Changed --tos to --string in save(). Also
 *             updated to work with slightly modified
 *             ipt_string_info.
 */
#define _GNU_SOURCE 1
#include <stdbool.h>
#include <stdio.h>
#include <netdb.h>
#include <string.h>
#include <stdlib.h>
#include <getopt.h>
#include <ctype.h>
#include <xtables.h>
#include <stddef.h>
#include <linux/netfilter/xt_string.h>

static void string_help(void)
{
	printf(
"string match options:\n"
"--from                       Offset to start searching from\n"
"--to                         Offset to stop searching\n"
"--algo                       Algorithm\n"
"--icase                      Ignore case (default: 0)\n"
"[!] --string string          Match a string in a packet\n"
"[!] --hex-string string      Match a hex string in a packet\n");
}

static const struct option string_opts[] = {
	{.name = "from",       .has_arg = true,  .val = '1'},
	{.name = "to",         .has_arg = true,  .val = '2'},
	{.name = "algo",       .has_arg = true,  .val = '3'},
	{.name = "string",     .has_arg = true,  .val = '4'},
	{.name = "hex-string", .has_arg = true,  .val = '5'},
	{.name = "icase",      .has_arg = false, .val = '6'},
	XT_GETOPT_TABLEEND,
};

static void string_init(struct xt_entry_match *m)
{
	struct xt_string_info *i = (struct xt_string_info *) m->data;

	if (i->to_offset == 0)
		i->to_offset = UINT16_MAX;
}

static void
parse_string(const char *s, struct xt_string_info *info)
{	
	/* xt_string does not need \0 at the end of the pattern */
	if (strlen(s) <= XT_STRING_MAX_PATTERN_SIZE) {
		strncpy(info->pattern, s, XT_STRING_MAX_PATTERN_SIZE);
		info->patlen = strnlen(s, XT_STRING_MAX_PATTERN_SIZE);
		return;
	}
	xtables_error(PARAMETER_PROBLEM, "STRING too long \"%s\"", s);
}

static void
parse_algo(const char *s, struct xt_string_info *info)
{
	/* xt_string needs \0 for algo name */
	if (strlen(s) < XT_STRING_MAX_ALGO_NAME_SIZE) {
		strncpy(info->algo, s, XT_STRING_MAX_ALGO_NAME_SIZE);
		return;
	}
	xtables_error(PARAMETER_PROBLEM, "ALGO too long \"%s\"", s);
}

static void
parse_hex_string(const char *s, struct xt_string_info *info)
{
	int i=0, slen, sindex=0, schar;
	short hex_f = 0, literal_f = 0;
	char hextmp[3];

	slen = strlen(s);

	if (slen == 0) {
		xtables_error(PARAMETER_PROBLEM,
			"STRING must contain at least one char");
	}

	while (i < slen) {
		if (s[i] == '\\' && !hex_f) {
			literal_f = 1;
		} else if (s[i] == '\\') {
			xtables_error(PARAMETER_PROBLEM,
				"Cannot include literals in hex data");
		} else if (s[i] == '|') {
			if (hex_f)
				hex_f = 0;
			else {
				hex_f = 1;
				/* get past any initial whitespace just after the '|' */
				while (s[i+1] == ' ')
					i++;
			}
			if (i+1 >= slen)
				break;
			else
				i++;  /* advance to the next character */
		}

		if (literal_f) {
			if (i+1 >= slen) {
				xtables_error(PARAMETER_PROBLEM,
					"Bad literal placement at end of string");
			}
			info->pattern[sindex] = s[i+1];
			i += 2;  /* skip over literal char */
			literal_f = 0;
		} else if (hex_f) {
			if (i+1 >= slen) {
				xtables_error(PARAMETER_PROBLEM,
					"Odd number of hex digits");
			}
			if (i+2 >= slen) {
				/* must end with a "|" */
				xtables_error(PARAMETER_PROBLEM, "Invalid hex block");
			}
			if (! isxdigit(s[i])) /* check for valid hex char */
				xtables_error(PARAMETER_PROBLEM, "Invalid hex char '%c'", s[i]);
			if (! isxdigit(s[i+1])) /* check for valid hex char */
				xtables_error(PARAMETER_PROBLEM, "Invalid hex char '%c'", s[i+1]);
			hextmp[0] = s[i];
			hextmp[1] = s[i+1];
			hextmp[2] = '\0';
			if (! sscanf(hextmp, "%x", &schar))
				xtables_error(PARAMETER_PROBLEM,
					"Invalid hex char `%c'", s[i]);
			info->pattern[sindex] = (char) schar;
			if (s[i+2] == ' ')
				i += 3;  /* spaces included in the hex block */
			else
				i += 2;
		} else {  /* the char is not part of hex data, so just copy */
			info->pattern[sindex] = s[i];
			i++;
		}
		if (sindex > XT_STRING_MAX_PATTERN_SIZE)
			xtables_error(PARAMETER_PROBLEM, "STRING too long \"%s\"", s);
		sindex++;
	}
	info->patlen = sindex;
}

#define STRING 0x1
#define ALGO   0x2
#define FROM   0x4
#define TO     0x8
#define ICASE  0x10

static int
string_parse(int c, char **argv, int invert, unsigned int *flags,
             const void *entry, struct xt_entry_match **match)
{
	struct xt_string_info *stringinfo =
	    (struct xt_string_info *)(*match)->data;
	const int revision = (*match)->u.user.revision;

	switch (c) {
	case '1':
		if (*flags & FROM)
			xtables_error(PARAMETER_PROBLEM,
				   "Can't specify multiple --from");
		stringinfo->from_offset = atoi(optarg);
		*flags |= FROM;
		break;
	case '2':
		if (*flags & TO)
			xtables_error(PARAMETER_PROBLEM,
				   "Can't specify multiple --to");
		stringinfo->to_offset = atoi(optarg);
		*flags |= TO;
		break;
	case '3':
		if (*flags & ALGO)
			xtables_error(PARAMETER_PROBLEM,
				   "Can't specify multiple --algo");
		parse_algo(optarg, stringinfo);
		*flags |= ALGO;
		break;
	case '4':
		if (*flags & STRING)
			xtables_error(PARAMETER_PROBLEM,
				   "Can't specify multiple --string");
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		parse_string(optarg, stringinfo);
		if (invert) {
			if (revision == 0)
				stringinfo->u.v0.invert = 1;
			else
				stringinfo->u.v1.flags |= XT_STRING_FLAG_INVERT;
		}
		*flags |= STRING;
		break;

	case '5':
		if (*flags & STRING)
			xtables_error(PARAMETER_PROBLEM,
				   "Can't specify multiple --hex-string");

		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		parse_hex_string(optarg, stringinfo);  /* sets length */
		if (invert) {
			if (revision == 0)
				stringinfo->u.v0.invert = 1;
			else
				stringinfo->u.v1.flags |= XT_STRING_FLAG_INVERT;
		}
		*flags |= STRING;
		break;

	case '6':
		if (revision == 0)
			xtables_error(VERSION_PROBLEM,
				   "Kernel doesn't support --icase");

		stringinfo->u.v1.flags |= XT_STRING_FLAG_IGNORECASE;
		*flags |= ICASE;
		break;

	default:
		return 0;
	}
	return 1;
}

static void string_check(unsigned int flags)
{
	if (!(flags & STRING))
		xtables_error(PARAMETER_PROBLEM,
			   "STRING match: You must specify `--string' or "
			   "`--hex-string'");
	if (!(flags & ALGO))
		xtables_error(PARAMETER_PROBLEM,
			   "STRING match: You must specify `--algo'");
}

/* Test to see if the string contains non-printable chars or quotes */
static unsigned short int
is_hex_string(const char *str, const unsigned short int len)
{
	unsigned int i;
	for (i=0; i < len; i++)
		if (! isprint(str[i]))
			return 1;  /* string contains at least one non-printable char */
	/* use hex output if the last char is a "\" */
	if ((unsigned char) str[len-1] == 0x5c)
		return 1;
	return 0;
}

/* Print string with "|" chars included as one would pass to --hex-string */
static void
print_hex_string(const char *str, const unsigned short int len)
{
	unsigned int i;
	/* start hex block */
	printf("\"|");
	for (i=0; i < len; i++) {
		/* see if we need to prepend a zero */
		if ((unsigned char) str[i] <= 0x0F)
			printf("0%x", (unsigned char) str[i]);
		else
			printf("%x", (unsigned char) str[i]);
	}
	/* close hex block */
	printf("|\" ");
}

static void
print_string(const char *str, const unsigned short int len)
{
	unsigned int i;
	printf("\"");
	for (i=0; i < len; i++) {
		if ((unsigned char) str[i] == 0x22)  /* escape any embedded quotes */
			printf("%c", 0x5c);
		printf("%c", (unsigned char) str[i]);
	}
	printf("\" ");  /* closing space and quote */
}

static void
string_print(const void *ip, const struct xt_entry_match *match, int numeric)
{
	const struct xt_string_info *info =
	    (const struct xt_string_info*) match->data;
	const int revision = match->u.user.revision;
	int invert = (revision == 0 ? info->u.v0.invert :
				    info->u.v1.flags & XT_STRING_FLAG_INVERT);

	if (is_hex_string(info->pattern, info->patlen)) {
		printf("STRING match %s", invert ? "!" : "");
		print_hex_string(info->pattern, info->patlen);
	} else {
		printf("STRING match %s", invert ? "!" : "");
		print_string(info->pattern, info->patlen);
	}
	printf("ALGO name %s ", info->algo);
	if (info->from_offset != 0)
		printf("FROM %u ", info->from_offset);
	if (info->to_offset != 0)
		printf("TO %u ", info->to_offset);
	if (revision > 0 && info->u.v1.flags & XT_STRING_FLAG_IGNORECASE)
		printf("ICASE ");
}

static void string_save(const void *ip, const struct xt_entry_match *match)
{
	const struct xt_string_info *info =
	    (const struct xt_string_info*) match->data;
	const int revision = match->u.user.revision;
	int invert = (revision == 0 ? info->u.v0.invert :
				    info->u.v1.flags & XT_STRING_FLAG_INVERT);

	if (is_hex_string(info->pattern, info->patlen)) {
		printf("%s--hex-string ", (invert) ? "! ": "");
		print_hex_string(info->pattern, info->patlen);
	} else {
		printf("%s--string ", (invert) ? "! ": "");
		print_string(info->pattern, info->patlen);
	}
	printf("--algo %s ", info->algo);
	if (info->from_offset != 0)
		printf("--from %u ", info->from_offset);
	if (info->to_offset != 0)
		printf("--to %u ", info->to_offset);
	if (revision > 0 && info->u.v1.flags & XT_STRING_FLAG_IGNORECASE)
		printf("--icase ");
}


static struct xtables_match string_mt_reg[] = {
	{
		.name          = "string",
		.revision      = 0,
		.family        = NFPROTO_UNSPEC,
		.version       = XTABLES_VERSION,
		.size          = XT_ALIGN(sizeof(struct xt_string_info)),
		.userspacesize = offsetof(struct xt_string_info, config),
		.help          = string_help,
		.init          = string_init,
		.parse         = string_parse,
		.final_check   = string_check,
		.print         = string_print,
		.save          = string_save,
		.extra_opts    = string_opts,
	},
	{
		.name          = "string",
		.revision      = 1,
		.family        = NFPROTO_UNSPEC,
		.version       = XTABLES_VERSION,
		.size          = XT_ALIGN(sizeof(struct xt_string_info)),
		.userspacesize = offsetof(struct xt_string_info, config),
		.help          = string_help,
		.init          = string_init,
		.parse         = string_parse,
		.final_check   = string_check,
		.print         = string_print,
		.save          = string_save,
		.extra_opts    = string_opts,
	},
};

void libxt_string_init(void)
{
	xtables_register_matches(string_mt_reg, ARRAY_SIZE(string_mt_reg));
}
