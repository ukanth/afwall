/* Shared library add-on to ip6tables to add AH support. */
#include <stdbool.h>
#include <stdio.h>
#include <netdb.h>
#include <string.h>
#include <stdlib.h>
#include <getopt.h>
#include <errno.h>
#include <xtables.h>
#include <linux/netfilter_ipv6/ip6t_ah.h>

static void ah_help(void)
{
	printf(
"ah match options:\n"
"[!] --ahspi spi[:spi]          match spi (range)\n"
"[!] --ahlen length             total length of this header\n"
" --ahres                       check the reserved filed, too\n");
}

static const struct option ah_opts[] = {
	{.name = "ahspi", .has_arg = true,  .val = '1'},
	{.name = "ahlen", .has_arg = true,  .val = '2'},
	{.name = "ahres", .has_arg = false, .val = '3'},
	XT_GETOPT_TABLEEND,
};

static u_int32_t
parse_ah_spi(const char *spistr, const char *typestr)
{
	unsigned long int spi;
	char* ep;

	spi = strtoul(spistr, &ep, 0);

	if ( spistr == ep )
		xtables_error(PARAMETER_PROBLEM,
			   "AH no valid digits in %s `%s'", typestr, spistr);

	if ( spi == ULONG_MAX  && errno == ERANGE )
		xtables_error(PARAMETER_PROBLEM,
			   "%s `%s' specified too big: would overflow",
			   typestr, spistr);

	if ( *spistr != '\0'  && *ep != '\0' )
		xtables_error(PARAMETER_PROBLEM,
			   "AH error parsing %s `%s'", typestr, spistr);

	return spi;
}

static void
parse_ah_spis(const char *spistring, u_int32_t *spis)
{
	char *buffer;
	char *cp;

	buffer = strdup(spistring);
	if ((cp = strchr(buffer, ':')) == NULL)
		spis[0] = spis[1] = parse_ah_spi(buffer, "spi");
	else {
		*cp = '\0';
		cp++;

		spis[0] = buffer[0] ? parse_ah_spi(buffer, "spi") : 0;
		spis[1] = cp[0] ? parse_ah_spi(cp, "spi") : 0xFFFFFFFF;
	}
	free(buffer);
}

static void ah_init(struct xt_entry_match *m)
{
	struct ip6t_ah *ahinfo = (struct ip6t_ah *)m->data;

	ahinfo->spis[1] = 0xFFFFFFFF;
	ahinfo->hdrlen = 0;
	ahinfo->hdrres = 0;
}

static int ah_parse(int c, char **argv, int invert, unsigned int *flags,
                    const void *entry, struct xt_entry_match **match)
{
	struct ip6t_ah *ahinfo = (struct ip6t_ah *)(*match)->data;

	switch (c) {
	case '1':
		if (*flags & IP6T_AH_SPI)
			xtables_error(PARAMETER_PROBLEM,
				   "Only one `--ahspi' allowed");
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		parse_ah_spis(optarg, ahinfo->spis);
		if (invert)
			ahinfo->invflags |= IP6T_AH_INV_SPI;
		*flags |= IP6T_AH_SPI;
		break;
	case '2':
		if (*flags & IP6T_AH_LEN)
			xtables_error(PARAMETER_PROBLEM,
				   "Only one `--ahlen' allowed");
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		ahinfo->hdrlen = parse_ah_spi(optarg, "length");
		if (invert)
			ahinfo->invflags |= IP6T_AH_INV_LEN;
		*flags |= IP6T_AH_LEN;
		break;
	case '3':
		if (*flags & IP6T_AH_RES)
			xtables_error(PARAMETER_PROBLEM,
				   "Only one `--ahres' allowed");
		ahinfo->hdrres = 1;
		*flags |= IP6T_AH_RES;
		break;
	default:
		return 0;
	}

	return 1;
}

static void
print_spis(const char *name, u_int32_t min, u_int32_t max,
	    int invert)
{
	const char *inv = invert ? "!" : "";

	if (min != 0 || max != 0xFFFFFFFF || invert) {
		if (min == max)
			printf("%s:%s%u ", name, inv, min);
		else
			printf("%ss:%s%u:%u ", name, inv, min, max);
	}
}

static void
print_len(const char *name, u_int32_t len, int invert)
{
	const char *inv = invert ? "!" : "";

	if (len != 0 || invert)
		printf("%s:%s%u ", name, inv, len);
}

static void ah_print(const void *ip, const struct xt_entry_match *match,
                     int numeric)
{
	const struct ip6t_ah *ah = (struct ip6t_ah *)match->data;

	printf("ah ");
	print_spis("spi", ah->spis[0], ah->spis[1],
		    ah->invflags & IP6T_AH_INV_SPI);
	print_len("length", ah->hdrlen, 
		    ah->invflags & IP6T_AH_INV_LEN);

	if (ah->hdrres)
		printf("reserved ");

	if (ah->invflags & ~IP6T_AH_INV_MASK)
		printf("Unknown invflags: 0x%X ",
		       ah->invflags & ~IP6T_AH_INV_MASK);
}

static void ah_save(const void *ip, const struct xt_entry_match *match)
{
	const struct ip6t_ah *ahinfo = (struct ip6t_ah *)match->data;

	if (!(ahinfo->spis[0] == 0
	    && ahinfo->spis[1] == 0xFFFFFFFF)) {
		printf("%s--ahspi ",
			(ahinfo->invflags & IP6T_AH_INV_SPI) ? "! " : "");
		if (ahinfo->spis[0]
		    != ahinfo->spis[1])
			printf("%u:%u ",
			       ahinfo->spis[0],
			       ahinfo->spis[1]);
		else
			printf("%u ",
			       ahinfo->spis[0]);
	}

	if (ahinfo->hdrlen != 0 || (ahinfo->invflags & IP6T_AH_INV_LEN) ) {
		printf("%s--ahlen %u ", 
			(ahinfo->invflags & IP6T_AH_INV_LEN) ? "! " : "", 
			ahinfo->hdrlen);
	}

	if (ahinfo->hdrres != 0 )
		printf("--ahres ");
}

static struct xtables_match ah_mt6_reg = {
	.name          = "ah",
	.version       = XTABLES_VERSION,
	.family        = NFPROTO_IPV6,
	.size          = XT_ALIGN(sizeof(struct ip6t_ah)),
	.userspacesize = XT_ALIGN(sizeof(struct ip6t_ah)),
	.help          = ah_help,
	.init          = ah_init,
	.parse         = ah_parse,
	.print         = ah_print,
	.save          = ah_save,
	.extra_opts    = ah_opts,
};

void
_init(void)
{
	xtables_register_match(&ah_mt6_reg);
}
