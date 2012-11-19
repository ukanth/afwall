/* Shared library add-on to ip6tables to add Hop-by-Hop header support. */
#include <stdio.h>
#include <netdb.h>
#include <string.h>
#include <stdlib.h>
#include <getopt.h>
#include <errno.h>
#include <xtables.h>
/*#include <linux/in6.h>*/
#include <linux/netfilter_ipv6/ip6t_opts.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <arpa/inet.h>

#define DEBUG		0

static void hbh_help(void)
{
	printf(
"hbh match options:\n"
"[!] --hbh-len length            total length of this header\n"
"  --hbh-opts TYPE[:LEN][,TYPE[:LEN]...] \n"
"                                Options and its length (list, max: %d)\n",
IP6T_OPTS_OPTSNR);
}

static const struct option hbh_opts[] = {
	{.name = "hbh-len",        .has_arg = true, .val = '1'},
	{.name = "hbh-opts",       .has_arg = true, .val = '2'},
	{.name = "hbh-not-strict", .has_arg = true, .val = '3'},
	XT_GETOPT_TABLEEND,
};

static u_int32_t
parse_opts_num(const char *idstr, const char *typestr)
{
	unsigned long int id;
	char* ep;

	id =  strtoul(idstr,&ep,0) ;

	if ( idstr == ep ) {
		xtables_error(PARAMETER_PROBLEM,
			   "hbh: no valid digits in %s `%s'", typestr, idstr);
	}
	if ( id == ULONG_MAX  && errno == ERANGE ) {
		xtables_error(PARAMETER_PROBLEM,
			   "%s `%s' specified too big: would overflow",
			   typestr, idstr);
	}	
	if ( *idstr != '\0'  && *ep != '\0' ) {
		xtables_error(PARAMETER_PROBLEM,
			   "hbh: error parsing %s `%s'", typestr, idstr);
	}
	return id;
}

static int
parse_options(const char *optsstr, u_int16_t *opts)
{
        char *buffer, *cp, *next, *range;
        unsigned int i;
	
	buffer = strdup(optsstr);
	if (!buffer) xtables_error(OTHER_PROBLEM, "strdup failed");
			
        for (cp=buffer, i=0; cp && i<IP6T_OPTS_OPTSNR; cp=next,i++)
        {
                next=strchr(cp, ',');
                if (next) *next++='\0';
                range = strchr(cp, ':');
                if (range) {
                        if (i == IP6T_OPTS_OPTSNR-1)
				xtables_error(PARAMETER_PROBLEM,
                                           "too many ports specified");
                        *range++ = '\0';
                }
		opts[i] = (parse_opts_num(cp, "opt") & 0xFF) << 8;
                if (range) {
			if (opts[i] == 0)
				xtables_error(PARAMETER_PROBLEM, "PAD0 has not got length");
			opts[i] |= parse_opts_num(range, "length") & 0xFF;
                } else {
                        opts[i] |= (0x00FF);
		}

#if DEBUG
		printf("opts str: %s %s\n", cp, range);
		printf("opts opt: %04X\n", opts[i]);
#endif
	}
	if (cp) xtables_error(PARAMETER_PROBLEM, "too many addresses specified");

	free(buffer);

#if DEBUG
	printf("addr nr: %d\n", i);
#endif

	return i;
}

static void hbh_init(struct xt_entry_match *m)
{
	struct ip6t_opts *optinfo = (struct ip6t_opts *)m->data;

	optinfo->hdrlen = 0;
	optinfo->flags = 0;
	optinfo->invflags = 0;
	optinfo->optsnr = 0;
}

static int hbh_parse(int c, char **argv, int invert, unsigned int *flags,
                     const void *entry, struct xt_entry_match **match)
{
	struct ip6t_opts *optinfo = (struct ip6t_opts *)(*match)->data;

	switch (c) {
	case '1':
		if (*flags & IP6T_OPTS_LEN)
			xtables_error(PARAMETER_PROBLEM,
				   "Only one `--hbh-len' allowed");
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		optinfo->hdrlen = parse_opts_num(optarg, "length");
		if (invert)
			optinfo->invflags |= IP6T_OPTS_INV_LEN;
		optinfo->flags |= IP6T_OPTS_LEN;
		*flags |= IP6T_OPTS_LEN;
		break;
	case '2':
		if (*flags & IP6T_OPTS_OPTS)
			xtables_error(PARAMETER_PROBLEM,
				   "Only one `--hbh-opts' allowed");
                xtables_check_inverse(optarg, &invert, &optind, 0, argv);
                if (invert)
			xtables_error(PARAMETER_PROBLEM,
				" '!' not allowed with `--hbh-opts'");
		optinfo->optsnr = parse_options(optarg, optinfo->opts);
		optinfo->flags |= IP6T_OPTS_OPTS;
		*flags |= IP6T_OPTS_OPTS;
		break;
	case '3':
		if (*flags & IP6T_OPTS_NSTRICT)
			xtables_error(PARAMETER_PROBLEM,
				   "Only one `--hbh-not-strict' allowed");
		if ( !(*flags & IP6T_OPTS_OPTS) )
			xtables_error(PARAMETER_PROBLEM,
				   "`--hbh-opts ...' required before `--hbh-not-strict'");
		optinfo->flags |= IP6T_OPTS_NSTRICT;
		*flags |= IP6T_OPTS_NSTRICT;
		break;
	default:
		return 0;
	}

	return 1;
}

static void
print_options(unsigned int optsnr, u_int16_t *optsp)
{
	unsigned int i;

	for(i=0; i<optsnr; i++){
		printf("%d", (optsp[i] & 0xFF00)>>8);
		if ((optsp[i] & 0x00FF) != 0x00FF){
			printf(":%d", (optsp[i] & 0x00FF));
		} 
		printf("%c", (i!=optsnr-1)?',':' ');
	}
}

static void hbh_print(const void *ip, const struct xt_entry_match *match,
                      int numeric)
{
	const struct ip6t_opts *optinfo = (struct ip6t_opts *)match->data;

	printf("hbh ");
	if (optinfo->flags & IP6T_OPTS_LEN) {
		printf("length");
		printf(":%s", optinfo->invflags & IP6T_OPTS_INV_LEN ? "!" : "");
		printf("%u", optinfo->hdrlen);
		printf(" ");
	}
	if (optinfo->flags & IP6T_OPTS_OPTS) printf("opts ");
	print_options(optinfo->optsnr, (u_int16_t *)optinfo->opts);
	if (optinfo->flags & IP6T_OPTS_NSTRICT) printf("not-strict ");
	if (optinfo->invflags & ~IP6T_OPTS_INV_MASK)
		printf("Unknown invflags: 0x%X ",
		       optinfo->invflags & ~IP6T_OPTS_INV_MASK);
}

static void hbh_save(const void *ip, const struct xt_entry_match *match)
{
	const struct ip6t_opts *optinfo = (struct ip6t_opts *)match->data;

	if (optinfo->flags & IP6T_OPTS_LEN) {
		printf("%s--hbh-len %u ",
			(optinfo->invflags & IP6T_OPTS_INV_LEN) ? "! " : "", 
			optinfo->hdrlen);
	}

	if (optinfo->flags & IP6T_OPTS_OPTS)
		printf("--hbh-opts ");
	print_options(optinfo->optsnr, (u_int16_t *)optinfo->opts);
	if (optinfo->flags & IP6T_OPTS_NSTRICT)
		printf("--hbh-not-strict ");
}

static struct xtables_match hbh_mt6_reg = {
	.name 		= "hbh",
	.version	= XTABLES_VERSION,
	.family		= NFPROTO_IPV6,
	.size		= XT_ALIGN(sizeof(struct ip6t_opts)),
	.userspacesize	= XT_ALIGN(sizeof(struct ip6t_opts)),
	.help		= hbh_help,
	.init		= hbh_init,
	.parse		= hbh_parse,
	.print		= hbh_print,
	.save		= hbh_save,
	.extra_opts	= hbh_opts,
};

void
_init(void)
{
	xtables_register_match(&hbh_mt6_reg);
}
