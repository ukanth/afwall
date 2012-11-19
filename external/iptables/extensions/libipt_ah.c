/* Shared library add-on to iptables to add AH support. */
#include <stdbool.h>
#include <stdio.h>
#include <netdb.h>
#include <string.h>
#include <stdlib.h>
#include <getopt.h>
#include <errno.h>
#include <xtables.h>
#include <linux/netfilter_ipv4/ipt_ah.h>

static void ah_help(void)
{
	printf(
"ah match options:\n"
"[!] --ahspi spi[:spi]\n"
"				match spi (range)\n");
}

static const struct option ah_opts[] = {
	{.name = "ahspi", .has_arg = true, .val = '1'},
	XT_GETOPT_TABLEEND,
};

static u_int32_t
parse_ah_spi(const char *spistr)
{
	unsigned long int spi;
	char* ep;

	spi =  strtoul(spistr,&ep,0) ;

	if ( spistr == ep ) {
		xtables_error(PARAMETER_PROBLEM,
			   "AH no valid digits in spi `%s'", spistr);
	}
	if ( spi == ULONG_MAX  && errno == ERANGE ) {
		xtables_error(PARAMETER_PROBLEM,
			   "spi `%s' specified too big: would overflow", spistr);
	}	
	if ( *spistr != '\0'  && *ep != '\0' ) {
		xtables_error(PARAMETER_PROBLEM,
			   "AH error parsing spi `%s'", spistr);
	}
	return spi;
}

static void
parse_ah_spis(const char *spistring, u_int32_t *spis)
{
	char *buffer;
	char *cp;

	buffer = strdup(spistring);
	if ((cp = strchr(buffer, ':')) == NULL)
		spis[0] = spis[1] = parse_ah_spi(buffer);
	else {
		*cp = '\0';
		cp++;

		spis[0] = buffer[0] ? parse_ah_spi(buffer) : 0;
		spis[1] = cp[0] ? parse_ah_spi(cp) : 0xFFFFFFFF;
	}
	free(buffer);
}

static void ah_init(struct xt_entry_match *m)
{
	struct ipt_ah *ahinfo = (struct ipt_ah *)m->data;

	ahinfo->spis[1] = 0xFFFFFFFF;
}

#define AH_SPI 0x01

static int ah_parse(int c, char **argv, int invert, unsigned int *flags,
                    const void *entry, struct xt_entry_match **match)
{
	struct ipt_ah *ahinfo = (struct ipt_ah *)(*match)->data;

	switch (c) {
	case '1':
		if (*flags & AH_SPI)
			xtables_error(PARAMETER_PROBLEM,
				   "Only one `--ahspi' allowed");
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		parse_ah_spis(optarg, ahinfo->spis);
		if (invert)
			ahinfo->invflags |= IPT_AH_INV_SPI;
		*flags |= AH_SPI;
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
		printf("%s", name);
		if (min == max) {
			printf(":%s", inv);
			printf("%u", min);
		} else {
			printf("s:%s", inv);
			printf("%u",min);
			printf(":");
			printf("%u",max);
		}
		printf(" ");
	}
}

static void ah_print(const void *ip, const struct xt_entry_match *match,
                     int numeric)
{
	const struct ipt_ah *ah = (struct ipt_ah *)match->data;

	printf("ah ");
	print_spis("spi", ah->spis[0], ah->spis[1],
		    ah->invflags & IPT_AH_INV_SPI);
	if (ah->invflags & ~IPT_AH_INV_MASK)
		printf("Unknown invflags: 0x%X ",
		       ah->invflags & ~IPT_AH_INV_MASK);
}

static void ah_save(const void *ip, const struct xt_entry_match *match)
{
	const struct ipt_ah *ahinfo = (struct ipt_ah *)match->data;

	if (!(ahinfo->spis[0] == 0
	    && ahinfo->spis[1] == 0xFFFFFFFF)) {
		printf("%s--ahspi ",
			(ahinfo->invflags & IPT_AH_INV_SPI) ? "! " : "");
		if (ahinfo->spis[0]
		    != ahinfo->spis[1])
			printf("%u:%u ",
			       ahinfo->spis[0],
			       ahinfo->spis[1]);
		else
			printf("%u ",
			       ahinfo->spis[0]);
	}

}

static struct xtables_match ah_mt_reg = {
	.name 		= "ah",
	.version 	= XTABLES_VERSION,
	.family		= NFPROTO_IPV4,
	.size		= XT_ALIGN(sizeof(struct ipt_ah)),
	.userspacesize 	= XT_ALIGN(sizeof(struct ipt_ah)),
	.help 		= ah_help,
	.init 		= ah_init,
	.parse 		= ah_parse,
	.print 		= ah_print,
	.save 		= ah_save,
	.extra_opts 	= ah_opts,
};

void
libipt_ah_init(void)
{
	xtables_register_match(&ah_mt_reg);
}
