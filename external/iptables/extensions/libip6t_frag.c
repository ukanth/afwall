/* Shared library add-on to ip6tables to add Fragmentation header support. */
#include <stdbool.h>
#include <stdio.h>
#include <netdb.h>
#include <string.h>
#include <stdlib.h>
#include <getopt.h>
#include <errno.h>
#include <xtables.h>
#include <linux/netfilter_ipv6/ip6t_frag.h>

static void frag_help(void)
{
	printf(
"frag match options:\n"
"[!] --fragid id[:id]           match the id (range)\n"
"[!] --fraglen length           total length of this header\n"
" --fragres                     check the reserved filed, too\n"
" --fragfirst                   matches on the first fragment\n"
" [--fragmore|--fraglast]       there are more fragments or this\n"
"                               is the last one\n");
}

static const struct option frag_opts[] = {
	{.name = "fragid",    .has_arg = true,  .val = '1'},
	{.name = "fraglen",   .has_arg = true,  .val = '2'},
	{.name = "fragres",   .has_arg = false, .val = '3'},
	{.name = "fragfirst", .has_arg = false, .val = '4'},
	{.name = "fragmore",  .has_arg = false, .val = '5'},
	{.name = "fraglast",  .has_arg = false, .val = '6'},
	XT_GETOPT_TABLEEND,
};

static u_int32_t
parse_frag_id(const char *idstr, const char *typestr)
{
	unsigned long int id;
	char* ep;

	id = strtoul(idstr, &ep, 0);

	if ( idstr == ep ) {
		xtables_error(PARAMETER_PROBLEM,
			   "FRAG no valid digits in %s `%s'", typestr, idstr);
	}
	if ( id == ULONG_MAX  && errno == ERANGE ) {
		xtables_error(PARAMETER_PROBLEM,
			   "%s `%s' specified too big: would overflow",
			   typestr, idstr);
	}	
	if ( *idstr != '\0'  && *ep != '\0' ) {
		xtables_error(PARAMETER_PROBLEM,
			   "FRAG error parsing %s `%s'", typestr, idstr);
	}
	return id;
}

static void
parse_frag_ids(const char *idstring, u_int32_t *ids)
{
	char *buffer;
	char *cp;

	buffer = strdup(idstring);
	if ((cp = strchr(buffer, ':')) == NULL)
		ids[0] = ids[1] = parse_frag_id(buffer,"id");
	else {
		*cp = '\0';
		cp++;

		ids[0] = buffer[0] ? parse_frag_id(buffer,"id") : 0;
		ids[1] = cp[0] ? parse_frag_id(cp,"id") : 0xFFFFFFFF;
	}
	free(buffer);
}

static void frag_init(struct xt_entry_match *m)
{
	struct ip6t_frag *fraginfo = (struct ip6t_frag *)m->data;

	fraginfo->ids[0] = 0x0L;
	fraginfo->ids[1] = 0xFFFFFFFF;
	fraginfo->hdrlen = 0;
	fraginfo->flags = 0;
	fraginfo->invflags = 0;
}

static int frag_parse(int c, char **argv, int invert, unsigned int *flags,
                      const void *entry, struct xt_entry_match **match)
{
	struct ip6t_frag *fraginfo = (struct ip6t_frag *)(*match)->data;

	switch (c) {
	case '1':
		if (*flags & IP6T_FRAG_IDS)
			xtables_error(PARAMETER_PROBLEM,
				   "Only one `--fragid' allowed");
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		parse_frag_ids(optarg, fraginfo->ids);
		if (invert)
			fraginfo->invflags |= IP6T_FRAG_INV_IDS;
		fraginfo->flags |= IP6T_FRAG_IDS;
		*flags |= IP6T_FRAG_IDS;
		break;
	case '2':
		if (*flags & IP6T_FRAG_LEN)
			xtables_error(PARAMETER_PROBLEM,
				   "Only one `--fraglen' allowed");
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		fraginfo->hdrlen = parse_frag_id(optarg, "length");
		if (invert)
			fraginfo->invflags |= IP6T_FRAG_INV_LEN;
		fraginfo->flags |= IP6T_FRAG_LEN;
		*flags |= IP6T_FRAG_LEN;
		break;
	case '3':
		if (*flags & IP6T_FRAG_RES)
			xtables_error(PARAMETER_PROBLEM,
				   "Only one `--fragres' allowed");
		fraginfo->flags |= IP6T_FRAG_RES;
		*flags |= IP6T_FRAG_RES;
		break;
	case '4':
		if (*flags & IP6T_FRAG_FST)
			xtables_error(PARAMETER_PROBLEM,
				   "Only one `--fragfirst' allowed");
		fraginfo->flags |= IP6T_FRAG_FST;
		*flags |= IP6T_FRAG_FST;
		break;
	case '5':
		if (*flags & (IP6T_FRAG_MF|IP6T_FRAG_NMF)) 
			xtables_error(PARAMETER_PROBLEM,
			   "Only one `--fragmore' or `--fraglast' allowed");
		fraginfo->flags |= IP6T_FRAG_MF;
		*flags |= IP6T_FRAG_MF;
		break;
	case '6':
		if (*flags & (IP6T_FRAG_MF|IP6T_FRAG_NMF)) 
			xtables_error(PARAMETER_PROBLEM,
			   "Only one `--fragmore' or `--fraglast' allowed");
		fraginfo->flags |= IP6T_FRAG_NMF;
		*flags |= IP6T_FRAG_NMF;
		break;
	default:
		return 0;
	}

	return 1;
}

static void
print_ids(const char *name, u_int32_t min, u_int32_t max,
	    int invert)
{
	const char *inv = invert ? "!" : "";

	if (min != 0 || max != 0xFFFFFFFF || invert) {
		printf("%s", name);
		if (min == max)
			printf(":%s%u ", inv, min);
		else
			printf("s:%s%u:%u ", inv, min, max);
	}
}

static void frag_print(const void *ip, const struct xt_entry_match *match,
                       int numeric)
{
	const struct ip6t_frag *frag = (struct ip6t_frag *)match->data;

	printf("frag ");
	print_ids("id", frag->ids[0], frag->ids[1],
		    frag->invflags & IP6T_FRAG_INV_IDS);

	if (frag->flags & IP6T_FRAG_LEN) {
		printf("length:%s%u ",
			frag->invflags & IP6T_FRAG_INV_LEN ? "!" : "",
			frag->hdrlen);
	}

	if (frag->flags & IP6T_FRAG_RES)
		printf("reserved ");

	if (frag->flags & IP6T_FRAG_FST)
		printf("first ");

	if (frag->flags & IP6T_FRAG_MF)
		printf("more ");

	if (frag->flags & IP6T_FRAG_NMF)
		printf("last ");

	if (frag->invflags & ~IP6T_FRAG_INV_MASK)
		printf("Unknown invflags: 0x%X ",
		       frag->invflags & ~IP6T_FRAG_INV_MASK);
}

static void frag_save(const void *ip, const struct xt_entry_match *match)
{
	const struct ip6t_frag *fraginfo = (struct ip6t_frag *)match->data;

	if (!(fraginfo->ids[0] == 0
	    && fraginfo->ids[1] == 0xFFFFFFFF)) {
		printf("%s--fragid ", 
			(fraginfo->invflags & IP6T_FRAG_INV_IDS) ? "! " : "");
		if (fraginfo->ids[0]
		    != fraginfo->ids[1])
			printf("%u:%u ",
			       fraginfo->ids[0],
			       fraginfo->ids[1]);
		else
			printf("%u ",
			       fraginfo->ids[0]);
	}

	if (fraginfo->flags & IP6T_FRAG_LEN) {
		printf("%s--fraglen %u ", 
			(fraginfo->invflags & IP6T_FRAG_INV_LEN) ? "! " : "", 
			fraginfo->hdrlen);
	}

	if (fraginfo->flags & IP6T_FRAG_RES)
		printf("--fragres ");

	if (fraginfo->flags & IP6T_FRAG_FST)
		printf("--fragfirst ");

	if (fraginfo->flags & IP6T_FRAG_MF)
		printf("--fragmore ");

	if (fraginfo->flags & IP6T_FRAG_NMF)
		printf("--fraglast ");
}

static struct xtables_match frag_mt6_reg = {
	.name          = "frag",
	.version       = XTABLES_VERSION,
	.family        = NFPROTO_IPV6,
	.size          = XT_ALIGN(sizeof(struct ip6t_frag)),
	.userspacesize = XT_ALIGN(sizeof(struct ip6t_frag)),
	.help          = frag_help,
	.init          = frag_init,
	.parse         = frag_parse,
	.print         = frag_print,
	.save          = frag_save,
	.extra_opts    = frag_opts,
};

void
_init(void)
{
	xtables_register_match(&frag_mt6_reg);
}
