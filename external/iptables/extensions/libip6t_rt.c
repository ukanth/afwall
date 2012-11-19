/* Shared library add-on to ip6tables to add Routing header support. */
#include <stdbool.h>
#include <stdio.h>
#include <netdb.h>
#include <string.h>
#include <stdlib.h>
#include <getopt.h>
#include <errno.h>
#include <xtables.h>
/*#include <linux/in6.h>*/
#include <linux/netfilter_ipv6/ip6t_rt.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <arpa/inet.h>

/*#define DEBUG	1*/

static void rt_help(void)
{
	printf(
"rt match options:\n"
"[!] --rt-type type             match the type\n"
"[!] --rt-segsleft num[:num]    match the Segments Left field (range)\n"
"[!] --rt-len length            total length of this header\n"
" --rt-0-res                    check the reserved filed, too (type 0)\n"
" --rt-0-addrs ADDR[,ADDR...]   Type=0 addresses (list, max: %d)\n"
" --rt-0-not-strict             List of Type=0 addresses not a strict list\n",
IP6T_RT_HOPS);
}

static const struct option rt_opts[] = {
	{.name = "rt-type",         .has_arg = true,  .val = '1'},
	{.name = "rt-segsleft",     .has_arg = true,  .val = '2'},
	{.name = "rt-len",          .has_arg = true,  .val = '3'},
	{.name = "rt-0-res",        .has_arg = false, .val = '4'},
	{.name = "rt-0-addrs",      .has_arg = true,  .val = '5'},
	{.name = "rt-0-not-strict", .has_arg = false, .val = '6'},
	XT_GETOPT_TABLEEND,
};

static u_int32_t
parse_rt_num(const char *idstr, const char *typestr)
{
	unsigned long int id;
	char* ep;

	id =  strtoul(idstr,&ep,0) ;

	if ( idstr == ep ) {
		xtables_error(PARAMETER_PROBLEM,
			   "RT no valid digits in %s `%s'", typestr, idstr);
	}
	if ( id == ULONG_MAX  && errno == ERANGE ) {
		xtables_error(PARAMETER_PROBLEM,
			   "%s `%s' specified too big: would overflow",
			   typestr, idstr);
	}	
	if ( *idstr != '\0'  && *ep != '\0' ) {
		xtables_error(PARAMETER_PROBLEM,
			   "RT error parsing %s `%s'", typestr, idstr);
	}
	return id;
}

static void
parse_rt_segsleft(const char *idstring, u_int32_t *ids)
{
	char *buffer;
	char *cp;

	buffer = strdup(idstring);
	if ((cp = strchr(buffer, ':')) == NULL)
		ids[0] = ids[1] = parse_rt_num(buffer,"segsleft");
	else {
		*cp = '\0';
		cp++;

		ids[0] = buffer[0] ? parse_rt_num(buffer,"segsleft") : 0;
		ids[1] = cp[0] ? parse_rt_num(cp,"segsleft") : 0xFFFFFFFF;
	}
	free(buffer);
}

static char *
addr_to_numeric(const struct in6_addr *addrp)
{
	static char buf[50+1];
	return (char *)inet_ntop(AF_INET6, addrp, buf, sizeof(buf));
}

static struct in6_addr *
numeric_to_addr(const char *num)
{
	static struct in6_addr ap;
	int err;

	if ((err=inet_pton(AF_INET6, num, &ap)) == 1)
		return &ap;
#ifdef DEBUG
	fprintf(stderr, "\nnumeric2addr: %d\n", err);
#endif
	xtables_error(PARAMETER_PROBLEM, "bad address: %s", num);

	return (struct in6_addr *)NULL;
}


static int
parse_addresses(const char *addrstr, struct in6_addr *addrp)
{
        char *buffer, *cp, *next;
        unsigned int i;
	
	buffer = strdup(addrstr);
	if (!buffer) xtables_error(OTHER_PROBLEM, "strdup failed");
			
        for (cp=buffer, i=0; cp && i<IP6T_RT_HOPS; cp=next,i++)
        {
                next=strchr(cp, ',');
                if (next) *next++='\0';
		memcpy(&(addrp[i]), numeric_to_addr(cp), sizeof(struct in6_addr));
#if DEBUG
		printf("addr str: %s\n", cp);
		printf("addr ip6: %s\n", addr_to_numeric((numeric_to_addr(cp))));
		printf("addr [%d]: %s\n", i, addr_to_numeric(&(addrp[i])));
#endif
	}
	if (cp) xtables_error(PARAMETER_PROBLEM, "too many addresses specified");

	free(buffer);

#if DEBUG
	printf("addr nr: %d\n", i);
#endif

	return i;
}

static void rt_init(struct xt_entry_match *m)
{
	struct ip6t_rt *rtinfo = (struct ip6t_rt *)m->data;

	rtinfo->rt_type = 0x0L;
	rtinfo->segsleft[0] = 0x0L;
	rtinfo->segsleft[1] = 0xFFFFFFFF;
	rtinfo->hdrlen = 0;
	rtinfo->flags = 0;
	rtinfo->invflags = 0;
	rtinfo->addrnr = 0;
}

static int rt_parse(int c, char **argv, int invert, unsigned int *flags,
                    const void *entry, struct xt_entry_match **match)
{
	struct ip6t_rt *rtinfo = (struct ip6t_rt *)(*match)->data;

	switch (c) {
	case '1':
		if (*flags & IP6T_RT_TYP)
			xtables_error(PARAMETER_PROBLEM,
				   "Only one `--rt-type' allowed");
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		rtinfo->rt_type = parse_rt_num(optarg, "type");
		if (invert)
			rtinfo->invflags |= IP6T_RT_INV_TYP;
		rtinfo->flags |= IP6T_RT_TYP;
		*flags |= IP6T_RT_TYP;
		break;
	case '2':
		if (*flags & IP6T_RT_SGS)
			xtables_error(PARAMETER_PROBLEM,
				   "Only one `--rt-segsleft' allowed");
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		parse_rt_segsleft(optarg, rtinfo->segsleft);
		if (invert)
			rtinfo->invflags |= IP6T_RT_INV_SGS;
		rtinfo->flags |= IP6T_RT_SGS;
		*flags |= IP6T_RT_SGS;
		break;
	case '3':
		if (*flags & IP6T_RT_LEN)
			xtables_error(PARAMETER_PROBLEM,
				   "Only one `--rt-len' allowed");
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		rtinfo->hdrlen = parse_rt_num(optarg, "length");
		if (invert)
			rtinfo->invflags |= IP6T_RT_INV_LEN;
		rtinfo->flags |= IP6T_RT_LEN;
		*flags |= IP6T_RT_LEN;
		break;
	case '4':
		if (*flags & IP6T_RT_RES)
			xtables_error(PARAMETER_PROBLEM,
				   "Only one `--rt-0-res' allowed");
		if ( !(*flags & IP6T_RT_TYP) || (rtinfo->rt_type != 0) || (rtinfo->invflags & IP6T_RT_INV_TYP) )
			xtables_error(PARAMETER_PROBLEM,
				   "`--rt-type 0' required before `--rt-0-res'");
		rtinfo->flags |= IP6T_RT_RES;
		*flags |= IP6T_RT_RES;
		break;
	case '5':
		if (*flags & IP6T_RT_FST)
			xtables_error(PARAMETER_PROBLEM,
				   "Only one `--rt-0-addrs' allowed");
		if ( !(*flags & IP6T_RT_TYP) || (rtinfo->rt_type != 0) || (rtinfo->invflags & IP6T_RT_INV_TYP) )
			xtables_error(PARAMETER_PROBLEM,
				   "`--rt-type 0' required before `--rt-0-addrs'");
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		if (invert)
			xtables_error(PARAMETER_PROBLEM,
				   " '!' not allowed with `--rt-0-addrs'");
		rtinfo->addrnr = parse_addresses(optarg, rtinfo->addrs);
		rtinfo->flags |= IP6T_RT_FST;
		*flags |= IP6T_RT_FST;
		break;
	case '6':
		if (*flags & IP6T_RT_FST_NSTRICT)
			xtables_error(PARAMETER_PROBLEM,
				   "Only one `--rt-0-not-strict' allowed");
		if ( !(*flags & IP6T_RT_FST) )
			xtables_error(PARAMETER_PROBLEM,
				   "`--rt-0-addr ...' required before `--rt-0-not-strict'");
		rtinfo->flags |= IP6T_RT_FST_NSTRICT;
		*flags |= IP6T_RT_FST_NSTRICT;
		break;
	default:
		return 0;
	}

	return 1;
}

static void
print_nums(const char *name, u_int32_t min, u_int32_t max,
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

static void
print_addresses(unsigned int addrnr, struct in6_addr *addrp)
{
	unsigned int i;

	for(i=0; i<addrnr; i++){
		printf("%s%c", addr_to_numeric(&(addrp[i])), (i!=addrnr-1)?',':' ');
	}
}

static void rt_print(const void *ip, const struct xt_entry_match *match,
                     int numeric)
{
	const struct ip6t_rt *rtinfo = (struct ip6t_rt *)match->data;

	printf("rt ");
	if (rtinfo->flags & IP6T_RT_TYP)
	    printf("type:%s%d ", rtinfo->invflags & IP6T_RT_INV_TYP ? "!" : "",
		    rtinfo->rt_type);
	print_nums("segsleft", rtinfo->segsleft[0], rtinfo->segsleft[1],
		    rtinfo->invflags & IP6T_RT_INV_SGS);
	if (rtinfo->flags & IP6T_RT_LEN) {
		printf("length");
		printf(":%s", rtinfo->invflags & IP6T_RT_INV_LEN ? "!" : "");
		printf("%u", rtinfo->hdrlen);
		printf(" ");
	}
	if (rtinfo->flags & IP6T_RT_RES) printf("reserved ");
	if (rtinfo->flags & IP6T_RT_FST) printf("0-addrs ");
	print_addresses(rtinfo->addrnr, (struct in6_addr *)rtinfo->addrs);
	if (rtinfo->flags & IP6T_RT_FST_NSTRICT) printf("0-not-strict ");
	if (rtinfo->invflags & ~IP6T_RT_INV_MASK)
		printf("Unknown invflags: 0x%X ",
		       rtinfo->invflags & ~IP6T_RT_INV_MASK);
}

static void rt_save(const void *ip, const struct xt_entry_match *match)
{
	const struct ip6t_rt *rtinfo = (struct ip6t_rt *)match->data;

	if (rtinfo->flags & IP6T_RT_TYP) {
		printf("%s--rt-type %u ", 
			(rtinfo->invflags & IP6T_RT_INV_TYP) ? "! " : "", 
			rtinfo->rt_type);
	}

	if (!(rtinfo->segsleft[0] == 0
	    && rtinfo->segsleft[1] == 0xFFFFFFFF)) {
		printf("%s--rt-segsleft ",
			(rtinfo->invflags & IP6T_RT_INV_SGS) ? "! " : "");
		if (rtinfo->segsleft[0]
		    != rtinfo->segsleft[1])
			printf("%u:%u ",
			       rtinfo->segsleft[0],
			       rtinfo->segsleft[1]);
		else
			printf("%u ",
			       rtinfo->segsleft[0]);
	}

	if (rtinfo->flags & IP6T_RT_LEN) {
		printf("%s--rt-len %u ",
			(rtinfo->invflags & IP6T_RT_INV_LEN) ? "! " : "", 
			rtinfo->hdrlen);
	}

	if (rtinfo->flags & IP6T_RT_RES) printf("--rt-0-res ");
	if (rtinfo->flags & IP6T_RT_FST) printf("--rt-0-addrs ");
	print_addresses(rtinfo->addrnr, (struct in6_addr *)rtinfo->addrs);
	if (rtinfo->flags & IP6T_RT_FST_NSTRICT) printf("--rt-0-not-strict ");

}

static struct xtables_match rt_mt6_reg = {
	.name		= "rt",
	.version	= XTABLES_VERSION,
	.family		= NFPROTO_IPV6,
	.size		= XT_ALIGN(sizeof(struct ip6t_rt)),
	.userspacesize	= XT_ALIGN(sizeof(struct ip6t_rt)),
	.help		= rt_help,
	.init		= rt_init,
	.parse		= rt_parse,
	.print		= rt_print,
	.save		= rt_save,
	.extra_opts	= rt_opts,
};

void
_init(void)
{
	xtables_register_match(&rt_mt6_reg);
}
