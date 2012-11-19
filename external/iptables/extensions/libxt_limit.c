/* Shared library add-on to iptables to add limit support.
 *
 * Jérôme de Vivie   <devivie@info.enserb.u-bordeaux.fr>
 * Hervé Eychenne    <rv@wallfire.org>
 */
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <getopt.h>
#include <xtables.h>
#include <stddef.h>
#include <linux/netfilter/x_tables.h>
/* For 64bit kernel / 32bit userspace */
#include <linux/netfilter/xt_limit.h>

#define XT_LIMIT_AVG	"3/hour"
#define XT_LIMIT_BURST	5

static void limit_help(void)
{
	printf(
"limit match options:\n"
"--limit avg			max average match rate: default "XT_LIMIT_AVG"\n"
"                                [Packets per second unless followed by \n"
"                                /sec /minute /hour /day postfixes]\n"
"--limit-burst number		number to match in a burst, default %u\n",
XT_LIMIT_BURST);
}

static const struct option limit_opts[] = {
	{.name = "limit",       .has_arg = true, .val = '%'},
	{.name = "limit-burst", .has_arg = true, .val = '$'},
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
	if (r / mult > XT_LIMIT_SCALE)
		xtables_error(PARAMETER_PROBLEM, "Rate too fast \"%s\"\n", rate);

	*val = XT_LIMIT_SCALE * mult / r;
	return 1;
}

static void limit_init(struct xt_entry_match *m)
{
	struct xt_rateinfo *r = (struct xt_rateinfo *)m->data;

	parse_rate(XT_LIMIT_AVG, &r->avg);
	r->burst = XT_LIMIT_BURST;

}

/* FIXME: handle overflow:
	if (r->avg*r->burst/r->burst != r->avg)
		xtables_error(PARAMETER_PROBLEM,
			   "Sorry: burst too large for that avg rate.\n");
*/

static int
limit_parse(int c, char **argv, int invert, unsigned int *flags,
            const void *entry, struct xt_entry_match **match)
{
	struct xt_rateinfo *r = (struct xt_rateinfo *)(*match)->data;
	unsigned int num;

	switch(c) {
	case '%':
		if (xtables_check_inverse(optarg, &invert, &optind, 0, argv)) break;
		if (!parse_rate(optarg, &r->avg))
			xtables_error(PARAMETER_PROBLEM,
				   "bad rate `%s'", optarg);
		break;

	case '$':
		if (xtables_check_inverse(optarg, &invert, &optind, 0, argv)) break;
		if (!xtables_strtoui(optarg, NULL, &num, 0, 10000))
			xtables_error(PARAMETER_PROBLEM,
				   "bad --limit-burst `%s'", optarg);
		r->burst = num;
		break;

	default:
		return 0;
	}

	if (invert)
		xtables_error(PARAMETER_PROBLEM,
			   "limit does not support invert");

	return 1;
}

static const struct rates
{
	const char *name;
	u_int32_t mult;
} rates[] = { { "day", XT_LIMIT_SCALE*24*60*60 },
	      { "hour", XT_LIMIT_SCALE*60*60 },
	      { "min", XT_LIMIT_SCALE*60 },
	      { "sec", XT_LIMIT_SCALE } };

static void print_rate(u_int32_t period)
{
	unsigned int i;

	for (i = 1; i < ARRAY_SIZE(rates); ++i)
		if (period > rates[i].mult
            || rates[i].mult/period < rates[i].mult%period)
			break;

	printf("%u/%s ", rates[i-1].mult / period, rates[i-1].name);
}

static void
limit_print(const void *ip, const struct xt_entry_match *match, int numeric)
{
	const struct xt_rateinfo *r = (const void *)match->data;
	printf("limit: avg "); print_rate(r->avg);
	printf("burst %u ", r->burst);
}

static void limit_save(const void *ip, const struct xt_entry_match *match)
{
	const struct xt_rateinfo *r = (const void *)match->data;

	printf("--limit "); print_rate(r->avg);
	if (r->burst != XT_LIMIT_BURST)
		printf("--limit-burst %u ", r->burst);
}

static struct xtables_match limit_match = {
	.family		= NFPROTO_UNSPEC,
	.name		= "limit",
	.version	= XTABLES_VERSION,
	.size		= XT_ALIGN(sizeof(struct xt_rateinfo)),
	.userspacesize	= offsetof(struct xt_rateinfo, prev),
	.help		= limit_help,
	.init		= limit_init,
	.parse		= limit_parse,
	.print		= limit_print,
	.save		= limit_save,
	.extra_opts	= limit_opts,
};

void libxt_limit_init(void)
{
	xtables_register_match(&limit_match);
}
