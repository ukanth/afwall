/* Shared library add-on to ip6tables to add Dst header support. */
#include <stdbool.h>
#include <stdio.h>
#include <netdb.h>
#include <string.h>
#include <stdlib.h>
#include <getopt.h>
#include <errno.h>
#include <xtables.h>
#include <linux/netfilter_ipv6/ip6t_opts.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <arpa/inet.h>

static void dst_help(void)
{
	printf(
"dst match options:\n"
"[!] --dst-len length            total length of this header\n"
"  --dst-opts TYPE[:LEN][,TYPE[:LEN]...]\n"
"                                Options and its length (list, max: %d)\n",
IP6T_OPTS_OPTSNR);
}

static const struct option dst_opts[] = {
	{.name = "dst-len",        .has_arg = true, .val = '1'},
	{.name = "dst-opts",       .has_arg = true, .val = '2'},
	{.name = "dst-not-strict", .has_arg = true, .val = '3'},
	XT_GETOPT_TABLEEND,
};

static u_int32_t
parse_opts_num(const char *idstr, const char *typestr)
{
	unsigned long int id;
	char* ep;

	id = strtoul(idstr, &ep, 0);

	if ( idstr == ep ) {
		xtables_error(PARAMETER_PROBLEM,
		           "dst: no valid digits in %s `%s'", typestr, idstr);
	}
	if ( id == ULONG_MAX  && errno == ERANGE ) {
		xtables_error(PARAMETER_PROBLEM,
			   "%s `%s' specified too big: would overflow",
			   typestr, idstr);
	}
	if ( *idstr != '\0'  && *ep != '\0' ) {
		xtables_error(PARAMETER_PROBLEM,
		           "dst: error parsing %s `%s'", typestr, idstr);
	}
	return id;
}

static int
parse_options(const char *optsstr, u_int16_t *opts)
{
        char *buffer, *cp, *next, *range;
        unsigned int i;
	
	buffer = strdup(optsstr);
        if (!buffer)
		xtables_error(OTHER_PROBLEM, "strdup failed");
			
        for (cp = buffer, i = 0; cp && i < IP6T_OPTS_OPTSNR; cp = next, i++)
        {
                next = strchr(cp, ',');

                if (next)
			*next++='\0';

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
				xtables_error(PARAMETER_PROBLEM,
					"PAD0 hasn't got length");
			opts[i] |= parse_opts_num(range, "length") & 0xFF;
                } else
                        opts[i] |= (0x00FF);

#ifdef DEBUG
		printf("opts str: %s %s\n", cp, range);
		printf("opts opt: %04X\n", opts[i]);
#endif
	}

        if (cp)
		xtables_error(PARAMETER_PROBLEM, "too many addresses specified");

	free(buffer);

#ifdef DEBUG
	printf("addr nr: %d\n", i);
#endif

	return i;
}

static void dst_init(struct xt_entry_match *m)
{
	struct ip6t_opts *optinfo = (struct ip6t_opts *)m->data;

	optinfo->hdrlen = 0;
	optinfo->flags = 0;
	optinfo->invflags = 0;
	optinfo->optsnr = 0;
}

static int dst_parse(int c, char **argv, int invert, unsigned int *flags,
                     const void *entry, struct xt_entry_match **match)
{
	struct ip6t_opts *optinfo = (struct ip6t_opts *)(*match)->data;

	switch (c) {
	case '1':
		if (*flags & IP6T_OPTS_LEN)
			xtables_error(PARAMETER_PROBLEM,
				   "Only one `--dst-len' allowed");
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
				   "Only one `--dst-opts' allowed");
                xtables_check_inverse(optarg, &invert, &optind, 0, argv);
                if (invert)
			xtables_error(PARAMETER_PROBLEM,
				" '!' not allowed with `--dst-opts'");
		optinfo->optsnr = parse_options(optarg, optinfo->opts);
		optinfo->flags |= IP6T_OPTS_OPTS;
		*flags |= IP6T_OPTS_OPTS;
		break;
	case '3':
		if (*flags & IP6T_OPTS_NSTRICT)
			xtables_error(PARAMETER_PROBLEM,
				   "Only one `--dst-not-strict' allowed");
		if ( !(*flags & IP6T_OPTS_OPTS) )
			xtables_error(PARAMETER_PROBLEM,
				   "`--dst-opts ...' required before "
				   "`--dst-not-strict'");
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

	for(i = 0; i < optsnr; i++) {
		printf("%d", (optsp[i] & 0xFF00) >> 8);

		if ((optsp[i] & 0x00FF) != 0x00FF)
			printf(":%d", (optsp[i] & 0x00FF));

		printf("%c", (i != optsnr - 1) ? ',' : ' ');
	}
}

static void dst_print(const void *ip, const struct xt_entry_match *match,
                      int numeric)
{
	const struct ip6t_opts *optinfo = (struct ip6t_opts *)match->data;

	printf("dst ");
	if (optinfo->flags & IP6T_OPTS_LEN)
		printf("length:%s%u ",
			optinfo->invflags & IP6T_OPTS_INV_LEN ? "!" : "",
			optinfo->hdrlen);

	if (optinfo->flags & IP6T_OPTS_OPTS)
		printf("opts ");

	print_options(optinfo->optsnr, (u_int16_t *)optinfo->opts);

	if (optinfo->flags & IP6T_OPTS_NSTRICT)
		printf("not-strict ");

	if (optinfo->invflags & ~IP6T_OPTS_INV_MASK)
		printf("Unknown invflags: 0x%X ",
		       optinfo->invflags & ~IP6T_OPTS_INV_MASK);
}

static void dst_save(const void *ip, const struct xt_entry_match *match)
{
	const struct ip6t_opts *optinfo = (struct ip6t_opts *)match->data;

	if (optinfo->flags & IP6T_OPTS_LEN) {
		printf("%s--dst-len %u ",
			(optinfo->invflags & IP6T_OPTS_INV_LEN) ? "! " : "", 
			optinfo->hdrlen);
	}

	if (optinfo->flags & IP6T_OPTS_OPTS)
		printf("--dst-opts ");

	print_options(optinfo->optsnr, (u_int16_t *)optinfo->opts);

	if (optinfo->flags & IP6T_OPTS_NSTRICT)
		printf("--dst-not-strict ");
}

static struct xtables_match dst_mt6_reg = {
	.name          = "dst",
	.version       = XTABLES_VERSION,
	.family        = NFPROTO_IPV6,
	.size          = XT_ALIGN(sizeof(struct ip6t_opts)),
	.userspacesize = XT_ALIGN(sizeof(struct ip6t_opts)),
	.help          = dst_help,
	.init          = dst_init,
	.parse         = dst_parse,
	.print         = dst_print,
	.save          = dst_save,
	.extra_opts    = dst_opts,
};

void
_init(void)
{
	xtables_register_match(&dst_mt6_reg);
}
