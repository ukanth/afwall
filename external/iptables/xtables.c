/*
 * (C) 2000-2006 by the netfilter coreteam <coreteam@netfilter.org>:
 *
 *	This program is free software; you can redistribute it and/or modify
 *	it under the terms of the GNU General Public License as published by
 *	the Free Software Foundation; either version 2 of the License, or
 *	(at your option) any later version.
 *
 *	This program is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *	GNU General Public License for more details.
 *
 *	You should have received a copy of the GNU General Public License
 *	along with this program; if not, write to the Free Software
 *	Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

#include <errno.h>
#include <fcntl.h>
#include <netdb.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <arpa/inet.h>

#include <xtables.h>
#include <limits.h> /* INT_MAX in ip_tables.h/ip6_tables.h */
#include <linux/netfilter_ipv4/ip_tables.h>
#include <linux/netfilter_ipv6/ip6_tables.h>
#include <libiptc/libxtc.h>

#ifndef NO_SHARED_LIBS
#include <dlfcn.h>
#endif
#ifndef IPT_SO_GET_REVISION_MATCH /* Old kernel source. */
#	define IPT_SO_GET_REVISION_MATCH	(IPT_BASE_CTL + 2)
#	define IPT_SO_GET_REVISION_TARGET	(IPT_BASE_CTL + 3)
#endif
#ifndef IP6T_SO_GET_REVISION_MATCH /* Old kernel source. */
#	define IP6T_SO_GET_REVISION_MATCH	68
#	define IP6T_SO_GET_REVISION_TARGET	69
#endif
#include <getopt.h>


#define NPROTO	255

#ifndef PROC_SYS_MODPROBE
#define PROC_SYS_MODPROBE "/proc/sys/kernel/modprobe"
#endif

void basic_exit_err(enum xtables_exittype status, const char *msg, ...) __attribute__((noreturn, format(printf,2,3)));

struct xtables_globals *xt_params = NULL;

void basic_exit_err(enum xtables_exittype status, const char *msg, ...)
{
	va_list args;

	va_start(args, msg);
	fprintf(stderr, "%s v%s: ", xt_params->program_name, xt_params->program_version);
	vfprintf(stderr, msg, args);
	va_end(args);
	fprintf(stderr, "\n");
	exit(status);
}


void xtables_free_opts(int reset_offset)
{
	if (xt_params->opts != xt_params->orig_opts) {
		free(xt_params->opts);
		xt_params->opts = xt_params->orig_opts;
		if (reset_offset)
			xt_params->option_offset = 0;
	}
}

struct option *xtables_merge_options(struct option *oldopts,
				     const struct option *newopts,
				     unsigned int *option_offset)
{
	unsigned int num_old, num_new, i;
	struct option *merge;

	if (newopts == NULL)
		return oldopts;

	for (num_old = 0; oldopts[num_old].name; num_old++) ;
	for (num_new = 0; newopts[num_new].name; num_new++) ;

	xt_params->option_offset += 256;
	*option_offset = xt_params->option_offset;

	merge = malloc(sizeof(struct option) * (num_new + num_old + 1));
	if (merge == NULL)
		return NULL;
	memcpy(merge, oldopts, num_old * sizeof(struct option));
	xtables_free_opts(0);	/* Release any old options merged  */
	for (i = 0; i < num_new; i++) {
		merge[num_old + i] = newopts[i];
		merge[num_old + i].val += *option_offset;
	}
	memset(merge + num_old + num_new, 0, sizeof(struct option));

	return merge;
}

/**
 * xtables_afinfo - protocol family dependent information
 * @kmod:		kernel module basename (e.g. "ip_tables")
 * @libprefix:		prefix of .so library name (e.g. "libipt_")
 * @family:		nfproto family
 * @ipproto:		used by setsockopt (e.g. IPPROTO_IP)
 * @so_rev_match:	optname to check revision support of match
 * @so_rev_target:	optname to check revision support of target
 */
struct xtables_afinfo {
	const char *kmod;
	const char *libprefix;
	uint8_t family;
	uint8_t ipproto;
	int so_rev_match;
	int so_rev_target;
};

static const struct xtables_afinfo afinfo_ipv4 = {
	.kmod          = "ip_tables",
	.libprefix     = "libipt_",
	.family	       = NFPROTO_IPV4,
	.ipproto       = IPPROTO_IP,
	.so_rev_match  = IPT_SO_GET_REVISION_MATCH,
	.so_rev_target = IPT_SO_GET_REVISION_TARGET,
};

static const struct xtables_afinfo afinfo_ipv6 = {
	.kmod          = "ip6_tables",
	.libprefix     = "libip6t_",
	.family        = NFPROTO_IPV6,
	.ipproto       = IPPROTO_IPV6,
	.so_rev_match  = IP6T_SO_GET_REVISION_MATCH,
	.so_rev_target = IP6T_SO_GET_REVISION_TARGET,
};

static const struct xtables_afinfo *afinfo;

/* Search path for Xtables .so files */
static const char *xtables_libdir;

/* the path to command to load kernel module */
const char *xtables_modprobe_program;

/* Keeping track of external matches and targets: linked lists.  */
struct xtables_match *xtables_matches;
struct xtables_target *xtables_targets;

void xtables_init(void)
{
	xtables_libdir = getenv("XTABLES_LIBDIR");
	if (xtables_libdir != NULL)
		return;
	xtables_libdir = getenv("IPTABLES_LIB_DIR");
	if (xtables_libdir != NULL) {
		fprintf(stderr, "IPTABLES_LIB_DIR is deprecated, "
		        "use XTABLES_LIBDIR.\n");
		return;
	}
	/*
	 * Well yes, IP6TABLES_LIB_DIR is of lower priority over
	 * IPTABLES_LIB_DIR since this moved to libxtables; I think that is ok
	 * for these env vars are deprecated anyhow, and in light of the
	 * (shared) libxt_*.so files, makes less sense to have
	 * IPTABLES_LIB_DIR != IP6TABLES_LIB_DIR.
	 */
	xtables_libdir = getenv("IP6TABLES_LIB_DIR");
	if (xtables_libdir != NULL) {
		fprintf(stderr, "IP6TABLES_LIB_DIR is deprecated, "
		        "use XTABLES_LIBDIR.\n");
		return;
	}
	xtables_libdir = XTABLES_LIBDIR;
}

void xtables_set_nfproto(uint8_t nfproto)
{
	switch (nfproto) {
	case NFPROTO_IPV4:
		afinfo = &afinfo_ipv4;
		break;
	case NFPROTO_IPV6:
		afinfo = &afinfo_ipv6;
		break;
	default:
		fprintf(stderr, "libxtables: unhandled NFPROTO in %s\n",
		        __func__);
	}
}

/**
 * xtables_set_params - set the global parameters used by xtables
 * @xtp:	input xtables_globals structure
 *
 * The app is expected to pass a valid xtables_globals data-filled
 * with proper values
 * @xtp cannot be NULL
 *
 * Returns -1 on failure to set and 0 on success
 */
int xtables_set_params(struct xtables_globals *xtp)
{
	if (!xtp) {
		fprintf(stderr, "%s: Illegal global params\n",__func__);
		return -1;
	}

	xt_params = xtp;

	if (!xt_params->exit_err)
		xt_params->exit_err = basic_exit_err;

	return 0;
}

int xtables_init_all(struct xtables_globals *xtp, uint8_t nfproto)
{
	xtables_init();
	xtables_set_nfproto(nfproto);
	return xtables_set_params(xtp);
}

/**
 * xtables_*alloc - wrappers that exit on failure
 */
void *xtables_calloc(size_t count, size_t size)
{
	void *p;

	if ((p = calloc(count, size)) == NULL) {
		perror("ip[6]tables: calloc failed");
		exit(1);
	}

	return p;
}

void *xtables_malloc(size_t size)
{
	void *p;

	if ((p = malloc(size)) == NULL) {
		perror("ip[6]tables: malloc failed");
		exit(1);
	}

	return p;
}

void *xtables_realloc(void *ptr, size_t size)
{
	void *p;

	if ((p = realloc(ptr, size)) == NULL) {
		perror("ip[6]tables: realloc failed");
		exit(1);
	}

	return p;
}

static char *get_modprobe(void)
{
	int procfile;
	char *ret;

#define PROCFILE_BUFSIZ	1024
	procfile = open(PROC_SYS_MODPROBE, O_RDONLY);
	if (procfile < 0)
		return NULL;

	ret = malloc(PROCFILE_BUFSIZ);
	if (ret) {
		memset(ret, 0, PROCFILE_BUFSIZ);
		switch (read(procfile, ret, PROCFILE_BUFSIZ)) {
		case -1: goto fail;
		case PROCFILE_BUFSIZ: goto fail; /* Partial read.  Wierd */
		}
		if (ret[strlen(ret)-1]=='\n') 
			ret[strlen(ret)-1]=0;
		close(procfile);
		return ret;
	}
 fail:
	free(ret);
	close(procfile);
	return NULL;
}

int xtables_insmod(const char *modname, const char *modprobe, bool quiet)
{
	char *buf = NULL;
	char *argv[4];
	int status;

	/* If they don't explicitly set it, read out of kernel */
	if (!modprobe) {
		buf = get_modprobe();
		if (!buf)
			return -1;
		modprobe = buf;
	}

	/*
	 * Need to flush the buffer, or the child may output it again
	 * when switching the program thru execv.
	 */
	fflush(stdout);

	switch (vfork()) {
	case 0:
		argv[0] = (char *)modprobe;
		argv[1] = (char *)modname;
		if (quiet) {
			argv[2] = "-q";
			argv[3] = NULL;
		} else {
			argv[2] = NULL;
			argv[3] = NULL;
		}
		execv(argv[0], argv);

		/* not usually reached */
		exit(1);
	case -1:
		return -1;

	default: /* parent */
		wait(&status);
	}

	free(buf);
	if (WIFEXITED(status) && WEXITSTATUS(status) == 0)
		return 0;
	return -1;
}

int xtables_load_ko(const char *modprobe, bool quiet)
{
	static bool loaded = false;
	static int ret = -1;

	if (!loaded) {
		ret = xtables_insmod(afinfo->kmod, modprobe, quiet);
		loaded = (ret == 0);
	}

	return ret;
}

/**
 * xtables_strtou{i,l} - string to number conversion
 * @s:	input string
 * @end:	like strtoul's "end" pointer
 * @value:	pointer for result
 * @min:	minimum accepted value
 * @max:	maximum accepted value
 *
 * If @end is NULL, we assume the caller wants a "strict strtoul", and hence
 * "15a" is rejected.
 * In either case, the value obtained is compared for min-max compliance.
 * Base is always 0, i.e. autodetect depending on @s.
 *
 * Returns true/false whether number was accepted. On failure, *value has
 * undefined contents.
 */
bool xtables_strtoul(const char *s, char **end, unsigned long *value,
                     unsigned long min, unsigned long max)
{
	unsigned long v;
	char *my_end;

	errno = 0;
	v = strtoul(s, &my_end, 0);

	if (my_end == s)
		return false;
	if (end != NULL)
		*end = my_end;

	if (errno != ERANGE && min <= v && (max == 0 || v <= max)) {
		if (value != NULL)
			*value = v;
		if (end == NULL)
			return *my_end == '\0';
		return true;
	}

	return false;
}

bool xtables_strtoui(const char *s, char **end, unsigned int *value,
                     unsigned int min, unsigned int max)
{
	unsigned long v;
	bool ret;

	ret = xtables_strtoul(s, end, &v, min, max);
	if (value != NULL)
		*value = v;
	return ret;
}

int xtables_service_to_port(const char *name, const char *proto)
{
	struct servent *service;

	if ((service = getservbyname(name, proto)) != NULL)
		return ntohs((unsigned short) service->s_port);

	return -1;
}

u_int16_t xtables_parse_port(const char *port, const char *proto)
{
	unsigned int portnum;

	if (xtables_strtoui(port, NULL, &portnum, 0, UINT16_MAX) ||
	    (portnum = xtables_service_to_port(port, proto)) != (unsigned)-1)
		return portnum;

	xt_params->exit_err(PARAMETER_PROBLEM,
		   "invalid port/service `%s' specified", port);
}

void xtables_parse_interface(const char *arg, char *vianame,
			     unsigned char *mask)
{
	unsigned int vialen = strlen(arg);
	unsigned int i;

	memset(mask, 0, IFNAMSIZ);
	memset(vianame, 0, IFNAMSIZ);

	if (vialen + 1 > IFNAMSIZ)
		xt_params->exit_err(PARAMETER_PROBLEM,
			   "interface name `%s' must be shorter than IFNAMSIZ"
			   " (%i)", arg, IFNAMSIZ-1);

	strcpy(vianame, arg);
	if (vialen == 0)
		memset(mask, 0, IFNAMSIZ);
	else if (vianame[vialen - 1] == '+') {
		memset(mask, 0xFF, vialen - 1);
		memset(mask + vialen - 1, 0, IFNAMSIZ - vialen + 1);
		/* Don't remove `+' here! -HW */
	} else {
		/* Include nul-terminator in match */
		memset(mask, 0xFF, vialen + 1);
		memset(mask + vialen + 1, 0, IFNAMSIZ - vialen - 1);
		for (i = 0; vianame[i]; i++) {
			if (vianame[i] == '/' ||
			    vianame[i] == ' ') {
				fprintf(stderr,
					"Warning: weird character in interface"
					" `%s' ('/' and ' ' are not allowed by the kernel).\n",
					vianame);
				break;
			}
		}
	}
}

#ifndef NO_SHARED_LIBS
static void *load_extension(const char *search_path, const char *prefix,
    const char *name, bool is_target)
{
	const char *dir = search_path, *next;
	void *ptr = NULL;
	struct stat sb;
	char path[256];

	do {
		next = strchr(dir, ':');
		if (next == NULL)
			next = dir + strlen(dir);
		snprintf(path, sizeof(path), "%.*s/libxt_%s.so",
		         (unsigned int)(next - dir), dir, name);

		if (dlopen(path, RTLD_NOW) != NULL) {
			/* Found library.  If it didn't register itself,
			   maybe they specified target as match. */
			if (is_target)
				ptr = xtables_find_target(name, XTF_DONT_LOAD);
			else
				ptr = xtables_find_match(name,
				      XTF_DONT_LOAD, NULL);
		} else if (stat(path, &sb) == 0) {
			fprintf(stderr, "%s: %s\n", path, dlerror());
		}

		if (ptr != NULL)
			return ptr;

		snprintf(path, sizeof(path), "%.*s/%s%s.so",
		         (unsigned int)(next - dir), dir, prefix, name);
		if (dlopen(path, RTLD_NOW) != NULL) {
			if (is_target)
				ptr = xtables_find_target(name, XTF_DONT_LOAD);
			else
				ptr = xtables_find_match(name,
				      XTF_DONT_LOAD, NULL);
		} else if (stat(path, &sb) == 0) {
			fprintf(stderr, "%s: %s\n", path, dlerror());
		}

		if (ptr != NULL)
			return ptr;

		dir = next + 1;
	} while (*next != '\0');

	return NULL;
}
#endif

struct xtables_match *
xtables_find_match(const char *name, enum xtables_tryload tryload,
		   struct xtables_rule_match **matches)
{
	struct xtables_match *ptr;
	const char *icmp6 = "icmp6";

	if (strlen(name) >= XT_EXTENSION_MAXNAMELEN)
		xtables_error(PARAMETER_PROBLEM,
			   "Invalid match name \"%s\" (%u chars max)",
			   name, XT_EXTENSION_MAXNAMELEN - 1);

	/* This is ugly as hell. Nonetheless, there is no way of changing
	 * this without hurting backwards compatibility */
	if ( (strcmp(name,"icmpv6") == 0) ||
	     (strcmp(name,"ipv6-icmp") == 0) ||
	     (strcmp(name,"icmp6") == 0) )
		name = icmp6;

	for (ptr = xtables_matches; ptr; ptr = ptr->next) {
		if (strcmp(name, ptr->name) == 0) {
			struct xtables_match *clone;

			/* First match of this type: */
			if (ptr->m == NULL)
				break;

			/* Second and subsequent clones */
			clone = xtables_malloc(sizeof(struct xtables_match));
			memcpy(clone, ptr, sizeof(struct xtables_match));
			clone->mflags = 0;
			/* This is a clone: */
			clone->next = clone;

			ptr = clone;
			break;
		}
	}

#ifndef NO_SHARED_LIBS
	if (!ptr && tryload != XTF_DONT_LOAD && tryload != XTF_DURING_LOAD) {
		ptr = load_extension(xtables_libdir, afinfo->libprefix,
		      name, false);

		if (ptr == NULL && tryload == XTF_LOAD_MUST_SUCCEED)
			xt_params->exit_err(PARAMETER_PROBLEM,
				   "Couldn't load match `%s':%s\n",
				   name, dlerror());
	}
#else
	if (ptr && !ptr->loaded) {
		if (tryload != XTF_DONT_LOAD)
			ptr->loaded = 1;
		else
			ptr = NULL;
	}
	if(!ptr && (tryload == XTF_LOAD_MUST_SUCCEED)) {
		xt_params->exit_err(PARAMETER_PROBLEM,
			   "Couldn't find match `%s'\n", name);
	}
#endif

	if (ptr && matches) {
		struct xtables_rule_match **i;
		struct xtables_rule_match *newentry;

		newentry = xtables_malloc(sizeof(struct xtables_rule_match));

		for (i = matches; *i; i = &(*i)->next) {
			if (strcmp(name, (*i)->match->name) == 0)
				(*i)->completed = true;
		}
		newentry->match = ptr;
		newentry->completed = false;
		newentry->next = NULL;
		*i = newentry;
	}

	return ptr;
}

struct xtables_target *
xtables_find_target(const char *name, enum xtables_tryload tryload)
{
	struct xtables_target *ptr;

	/* Standard target? */
	if (strcmp(name, "") == 0
	    || strcmp(name, XTC_LABEL_ACCEPT) == 0
	    || strcmp(name, XTC_LABEL_DROP) == 0
	    || strcmp(name, XTC_LABEL_QUEUE) == 0
	    || strcmp(name, XTC_LABEL_RETURN) == 0)
		name = "standard";

	for (ptr = xtables_targets; ptr; ptr = ptr->next) {
		if (strcmp(name, ptr->name) == 0)
			break;
	}

#ifndef NO_SHARED_LIBS
	if (!ptr && tryload != XTF_DONT_LOAD && tryload != XTF_DURING_LOAD) {
		ptr = load_extension(xtables_libdir, afinfo->libprefix,
		      name, true);

		if (ptr == NULL && tryload == XTF_LOAD_MUST_SUCCEED)
			xt_params->exit_err(PARAMETER_PROBLEM,
				   "Couldn't load target `%s':%s\n",
				   name, dlerror());
	}
#else
	if (ptr && !ptr->loaded) {
		if (tryload != XTF_DONT_LOAD)
			ptr->loaded = 1;
		else
			ptr = NULL;
	}
	if (ptr == NULL && tryload == XTF_LOAD_MUST_SUCCEED) {
		xt_params->exit_err(PARAMETER_PROBLEM,
			   "Couldn't find target `%s'\n", name);
	}
#endif

	if (ptr)
		ptr->used = 1;

	return ptr;
}

static int compatible_revision(const char *name, u_int8_t revision, int opt)
{
	struct xt_get_revision rev;
	socklen_t s = sizeof(rev);
	int max_rev, sockfd;

	sockfd = socket(afinfo->family, SOCK_RAW, IPPROTO_RAW);
	if (sockfd < 0) {
		if (errno == EPERM) {
			/* revision 0 is always supported. */
			if (revision != 0)
				fprintf(stderr, "Could not determine whether "
						"revision %u is supported, "
						"assuming it is.\n",
					revision);
			return 1;
		}
		fprintf(stderr, "Could not open socket to kernel: %s\n",
			strerror(errno));
		exit(1);
	}

	xtables_load_ko(xtables_modprobe_program, true);

	strcpy(rev.name, name);
	rev.revision = revision;

	max_rev = getsockopt(sockfd, afinfo->ipproto, opt, &rev, &s);
	if (max_rev < 0) {
		/* Definitely don't support this? */
		if (errno == ENOENT || errno == EPROTONOSUPPORT) {
			close(sockfd);
			return 0;
		} else if (errno == ENOPROTOOPT) {
			close(sockfd);
			/* Assume only revision 0 support (old kernel) */
			return (revision == 0);
		} else {
			fprintf(stderr, "getsockopt failed strangely: %s\n",
				strerror(errno));
			exit(1);
		}
	}
	close(sockfd);
	return 1;
}


static int compatible_match_revision(const char *name, u_int8_t revision)
{
	return compatible_revision(name, revision, afinfo->so_rev_match);
}

static int compatible_target_revision(const char *name, u_int8_t revision)
{
	return compatible_revision(name, revision, afinfo->so_rev_target);
}

void xtables_register_match(struct xtables_match *me)
{
	struct xtables_match **i, *old;

	if (me->version == NULL) {
		fprintf(stderr, "%s: match %s<%u> is missing a version\n",
		        xt_params->program_name, me->name, me->revision);
		exit(1);
	}
	if (strcmp(me->version, XTABLES_VERSION) != 0) {
		fprintf(stderr, "%s: match \"%s\" has version \"%s\", "
		        "but \"%s\" is required.\n",
			xt_params->program_name, me->name,
			me->version, XTABLES_VERSION);
		exit(1);
	}

	if (strlen(me->name) >= XT_EXTENSION_MAXNAMELEN) {
		fprintf(stderr, "%s: target `%s' has invalid name\n",
			xt_params->program_name, me->name);
		exit(1);
	}

	if (me->family >= NPROTO) {
		fprintf(stderr,
			"%s: BUG: match %s has invalid protocol family\n",
			xt_params->program_name, me->name);
		exit(1);
	}

	/* ignore not interested match */
	if (me->family != afinfo->family && me->family != AF_UNSPEC)
		return;

	old = xtables_find_match(me->name, XTF_DURING_LOAD, NULL);
	if (old) {
		if (old->revision == me->revision &&
		    old->family == me->family) {
			fprintf(stderr,
				"%s: match `%s' already registered.\n",
				xt_params->program_name, me->name);
			exit(1);
		}

		/* Now we have two (or more) options, check compatibility. */
		if (compatible_match_revision(old->name, old->revision)
		    && old->revision > me->revision)
			return;

		/* See if new match can be used. */
		if (!compatible_match_revision(me->name, me->revision))
			return;

		/* Prefer !AF_UNSPEC over AF_UNSPEC for same revision. */
		if (old->revision == me->revision && me->family == AF_UNSPEC)
			return;

		/* Delete old one. */
		for (i = &xtables_matches; *i!=old; i = &(*i)->next);
		*i = old->next;
	}

	if (me->size != XT_ALIGN(me->size)) {
		fprintf(stderr, "%s: match `%s' has invalid size %u.\n",
		        xt_params->program_name, me->name,
		        (unsigned int)me->size);
		exit(1);
	}

	/* Append to list. */
	for (i = &xtables_matches; *i; i = &(*i)->next);
	me->next = NULL;
	*i = me;

	me->m = NULL;
	me->mflags = 0;
}

void xtables_register_matches(struct xtables_match *match, unsigned int n)
{
	do {
		xtables_register_match(&match[--n]);
	} while (n > 0);
}

void xtables_register_target(struct xtables_target *me)
{
	struct xtables_target *old;

	if (me->version == NULL) {
		fprintf(stderr, "%s: target %s<%u> is missing a version\n",
		        xt_params->program_name, me->name, me->revision);
		exit(1);
	}
	if (strcmp(me->version, XTABLES_VERSION) != 0) {
		fprintf(stderr, "%s: target \"%s\" has version \"%s\", "
		        "but \"%s\" is required.\n",
			xt_params->program_name, me->name,
			me->version, XTABLES_VERSION);
		exit(1);
	}

	if (strlen(me->name) >= XT_EXTENSION_MAXNAMELEN) {
		fprintf(stderr, "%s: target `%s' has invalid name\n",
			xt_params->program_name, me->name);
		exit(1);
	}

	if (me->family >= NPROTO) {
		fprintf(stderr,
			"%s: BUG: target %s has invalid protocol family\n",
			xt_params->program_name, me->name);
		exit(1);
	}

	/* ignore not interested target */
	if (me->family != afinfo->family && me->family != AF_UNSPEC)
		return;

	old = xtables_find_target(me->name, XTF_DURING_LOAD);
	if (old) {
		struct xtables_target **i;

		if (old->revision == me->revision &&
		    old->family == me->family) {
			fprintf(stderr,
				"%s: target `%s' already registered.\n",
				xt_params->program_name, me->name);
			exit(1);
		}

		/* Now we have two (or more) options, check compatibility. */
		if (compatible_target_revision(old->name, old->revision)
		    && old->revision > me->revision)
			return;

		/* See if new target can be used. */
		if (!compatible_target_revision(me->name, me->revision))
			return;

		/* Prefer !AF_UNSPEC over AF_UNSPEC for same revision. */
		if (old->revision == me->revision && me->family == AF_UNSPEC)
			return;

		/* Delete old one. */
		for (i = &xtables_targets; *i!=old; i = &(*i)->next);
		*i = old->next;
	}

	if (me->size != XT_ALIGN(me->size)) {
		fprintf(stderr, "%s: target `%s' has invalid size %u.\n",
		        xt_params->program_name, me->name,
		        (unsigned int)me->size);
		exit(1);
	}

	/* Prepend to list. */
	me->next = xtables_targets;
	xtables_targets = me;
	me->t = NULL;
	me->tflags = 0;
}

void xtables_register_targets(struct xtables_target *target, unsigned int n)
{
	do {
		xtables_register_target(&target[--n]);
	} while (n > 0);
}

/**
 * xtables_param_act - act on condition
 * @status:	a constant from enum xtables_exittype
 *
 * %XTF_ONLY_ONCE: print error message that option may only be used once.
 * @p1:		module name (e.g. "mark")
 * @p2(...):	option in conflict (e.g. "--mark")
 * @p3(...):	condition to match on (see extensions/ for examples)
 *
 * %XTF_NO_INVERT: option does not support inversion
 * @p1:		module name
 * @p2:		option in conflict
 * @p3:		condition to match on
 *
 * %XTF_BAD_VALUE: bad value for option
 * @p1:		module name
 * @p2:		option with which the problem occured (e.g. "--mark")
 * @p3:		string the user passed in (e.g. "99999999999999")
 *
 * %XTF_ONE_ACTION: two mutually exclusive actions have been specified
 * @p1:		module name
 *
 * Displays an error message and exits the program.
 */
void xtables_param_act(unsigned int status, const char *p1, ...)
{
	const char *p2, *p3;
	va_list args;
	bool b;

	va_start(args, p1);

	switch (status) {
	case XTF_ONLY_ONCE:
		p2 = va_arg(args, const char *);
		b  = va_arg(args, unsigned int);
		if (!b)
			return;
		xt_params->exit_err(PARAMETER_PROBLEM,
		           "%s: \"%s\" option may only be specified once",
		           p1, p2);
		break;
	case XTF_NO_INVERT:
		p2 = va_arg(args, const char *);
		b  = va_arg(args, unsigned int);
		if (!b)
			return;
		xt_params->exit_err(PARAMETER_PROBLEM,
		           "%s: \"%s\" option cannot be inverted", p1, p2);
		break;
	case XTF_BAD_VALUE:
		p2 = va_arg(args, const char *);
		p3 = va_arg(args, const char *);
		xt_params->exit_err(PARAMETER_PROBLEM,
		           "%s: Bad value for \"%s\" option: \"%s\"",
		           p1, p2, p3);
		break;
	case XTF_ONE_ACTION:
		b = va_arg(args, unsigned int);
		if (!b)
			return;
		xt_params->exit_err(PARAMETER_PROBLEM,
		           "%s: At most one action is possible", p1);
		break;
	default:
		xt_params->exit_err(status, p1, args);
		break;
	}

	va_end(args);
}

const char *xtables_ipaddr_to_numeric(const struct in_addr *addrp)
{
	static char buf[20];
	const unsigned char *bytep = (const void *)&addrp->s_addr;

	sprintf(buf, "%u.%u.%u.%u", bytep[0], bytep[1], bytep[2], bytep[3]);
	return buf;
}

static const char *ipaddr_to_host(const struct in_addr *addr)
{
	struct hostent *host;

	host = gethostbyaddr(addr, sizeof(struct in_addr), AF_INET);
	if (host == NULL)
		return NULL;

	return host->h_name;
}

static const char *ipaddr_to_network(const struct in_addr *addr)
{
	struct netent *net;

	if ((net = getnetbyaddr(ntohl(addr->s_addr), AF_INET)) != NULL)
		return net->n_name;

	return NULL;
}

const char *xtables_ipaddr_to_anyname(const struct in_addr *addr)
{
	const char *name;

	if ((name = ipaddr_to_host(addr)) != NULL ||
	    (name = ipaddr_to_network(addr)) != NULL)
		return name;

	return xtables_ipaddr_to_numeric(addr);
}

const char *xtables_ipmask_to_numeric(const struct in_addr *mask)
{
	static char buf[20];
	uint32_t maskaddr, bits;
	int i;

	maskaddr = ntohl(mask->s_addr);

	if (maskaddr == 0xFFFFFFFFL)
		/* we don't want to see "/32" */
		return "";

	i = 32;
	bits = 0xFFFFFFFEL;
	while (--i >= 0 && maskaddr != bits)
		bits <<= 1;
	if (i >= 0)
		sprintf(buf, "/%d", i);
	else
		/* mask was not a decent combination of 1's and 0's */
		sprintf(buf, "/%s", xtables_ipaddr_to_numeric(mask));

	return buf;
}

static struct in_addr *__numeric_to_ipaddr(const char *dotted, bool is_mask)
{
	static struct in_addr addr;
	unsigned char *addrp;
	unsigned int onebyte;
	char buf[20], *p, *q;
	int i;

	/* copy dotted string, because we need to modify it */
	strncpy(buf, dotted, sizeof(buf) - 1);
	buf[sizeof(buf) - 1] = '\0';
	addrp = (void *)&addr.s_addr;

	p = buf;
	for (i = 0; i < 3; ++i) {
		if ((q = strchr(p, '.')) == NULL) {
			if (is_mask)
				return NULL;

			/* autocomplete, this is a network address */
			if (!xtables_strtoui(p, NULL, &onebyte, 0, UINT8_MAX))
				return NULL;

			addrp[i] = onebyte;
			while (i < 3)
				addrp[++i] = 0;

			return &addr;
		}

		*q = '\0';
		if (!xtables_strtoui(p, NULL, &onebyte, 0, UINT8_MAX))
			return NULL;

		addrp[i] = onebyte;
		p = q + 1;
	}

	/* we have checked 3 bytes, now we check the last one */
	if (!xtables_strtoui(p, NULL, &onebyte, 0, UINT8_MAX))
		return NULL;

	addrp[3] = onebyte;
	return &addr;
}

struct in_addr *xtables_numeric_to_ipaddr(const char *dotted)
{
	return __numeric_to_ipaddr(dotted, false);
}

struct in_addr *xtables_numeric_to_ipmask(const char *dotted)
{
	return __numeric_to_ipaddr(dotted, true);
}

static struct in_addr *network_to_ipaddr(const char *name)
{
	static struct in_addr addr;
	struct netent *net;

	if ((net = getnetbyname(name)) != NULL) {
		if (net->n_addrtype != AF_INET)
			return NULL;
		addr.s_addr = htonl(net->n_net);
		return &addr;
	}

	return NULL;
}

static struct in_addr *host_to_ipaddr(const char *name, unsigned int *naddr)
{
	struct hostent *host;
	struct in_addr *addr;
	unsigned int i;

	*naddr = 0;
	if ((host = gethostbyname(name)) != NULL) {
		if (host->h_addrtype != AF_INET ||
		    host->h_length != sizeof(struct in_addr))
			return NULL;

		while (host->h_addr_list[*naddr] != NULL)
			++*naddr;
		addr = xtables_calloc(*naddr, sizeof(struct in_addr) * *naddr);
		for (i = 0; i < *naddr; i++)
			memcpy(&addr[i], host->h_addr_list[i],
			       sizeof(struct in_addr));
		return addr;
	}

	return NULL;
}

static struct in_addr *
ipparse_hostnetwork(const char *name, unsigned int *naddrs)
{
	struct in_addr *addrptmp, *addrp;

	if ((addrptmp = xtables_numeric_to_ipaddr(name)) != NULL ||
	    (addrptmp = network_to_ipaddr(name)) != NULL) {
		addrp = xtables_malloc(sizeof(struct in_addr));
		memcpy(addrp, addrptmp, sizeof(*addrp));
		*naddrs = 1;
		return addrp;
	}
	if ((addrptmp = host_to_ipaddr(name, naddrs)) != NULL)
		return addrptmp;

	xt_params->exit_err(PARAMETER_PROBLEM, "host/network `%s' not found", name);
}

static struct in_addr *parse_ipmask(const char *mask)
{
	static struct in_addr maskaddr;
	struct in_addr *addrp;
	unsigned int bits;

	if (mask == NULL) {
		/* no mask at all defaults to 32 bits */
		maskaddr.s_addr = 0xFFFFFFFF;
		return &maskaddr;
	}
	if ((addrp = xtables_numeric_to_ipmask(mask)) != NULL)
		/* dotted_to_addr already returns a network byte order addr */
		return addrp;
	if (!xtables_strtoui(mask, NULL, &bits, 0, 32))
		xt_params->exit_err(PARAMETER_PROBLEM,
			   "invalid mask `%s' specified", mask);
	if (bits != 0) {
		maskaddr.s_addr = htonl(0xFFFFFFFF << (32 - bits));
		return &maskaddr;
	}

	maskaddr.s_addr = 0U;
	return &maskaddr;
}

void xtables_ipparse_multiple(const char *name, struct in_addr **addrpp,
                              struct in_addr **maskpp, unsigned int *naddrs)
{
	struct in_addr *addrp;
	char buf[256], *p;
	unsigned int len, i, j, n, count = 1;
	const char *loop = name;

	while ((loop = strchr(loop, ',')) != NULL) {
		++count;
		++loop; /* skip ',' */
	}

	*addrpp = xtables_malloc(sizeof(struct in_addr) * count);
	*maskpp = xtables_malloc(sizeof(struct in_addr) * count);

	loop = name;

	for (i = 0; i < count; ++i) {
		if (loop == NULL)
			break;
		if (*loop == ',')
			++loop;
		if (*loop == '\0')
			break;
		p = strchr(loop, ',');
		if (p != NULL)
			len = p - loop;
		else
			len = strlen(loop);
		if (len == 0 || sizeof(buf) - 1 < len)
			break;

		strncpy(buf, loop, len);
		buf[len] = '\0';
		loop += len;
		if ((p = strrchr(buf, '/')) != NULL) {
			*p = '\0';
			addrp = parse_ipmask(p + 1);
		} else {
			addrp = parse_ipmask(NULL);
		}
		memcpy(*maskpp + i, addrp, sizeof(*addrp));

		/* if a null mask is given, the name is ignored, like in "any/0" */
		if ((*maskpp + i)->s_addr == 0)
			/*
			 * A bit pointless to process multiple addresses
			 * in this case...
			 */
			strcpy(buf, "0.0.0.0");

		addrp = ipparse_hostnetwork(buf, &n);
		if (n > 1) {
			count += n - 1;
			*addrpp = xtables_realloc(*addrpp,
			          sizeof(struct in_addr) * count);
			*maskpp = xtables_realloc(*maskpp,
			          sizeof(struct in_addr) * count);
			for (j = 0; j < n; ++j)
				/* for each new addr */
				memcpy(*addrpp + i + j, addrp + j,
				       sizeof(*addrp));
			for (j = 1; j < n; ++j)
				/* for each new mask */
				memcpy(*maskpp + i + j, *maskpp + i,
				       sizeof(*addrp));
			i += n - 1;
		} else {
			memcpy(*addrpp + i, addrp, sizeof(*addrp));
		}
		/* free what ipparse_hostnetwork had allocated: */
		free(addrp);
	}
	*naddrs = count;
	for (i = 0; i < n; ++i)
		(*addrpp+i)->s_addr &= (*maskpp+i)->s_addr;
}


/**
 * xtables_ipparse_any - transform arbitrary name to in_addr
 *
 * Possible inputs (pseudo regex):
 * 	m{^($hostname|$networkname|$ipaddr)(/$mask)?}
 * "1.2.3.4/5", "1.2.3.4", "hostname", "networkname"
 */
void xtables_ipparse_any(const char *name, struct in_addr **addrpp,
                         struct in_addr *maskp, unsigned int *naddrs)
{
	unsigned int i, j, k, n;
	struct in_addr *addrp;
	char buf[256], *p;

	strncpy(buf, name, sizeof(buf) - 1);
	buf[sizeof(buf) - 1] = '\0';
	if ((p = strrchr(buf, '/')) != NULL) {
		*p = '\0';
		addrp = parse_ipmask(p + 1);
	} else {
		addrp = parse_ipmask(NULL);
	}
	memcpy(maskp, addrp, sizeof(*maskp));

	/* if a null mask is given, the name is ignored, like in "any/0" */
	if (maskp->s_addr == 0U)
		strcpy(buf, "0.0.0.0");

	addrp = *addrpp = ipparse_hostnetwork(buf, naddrs);
	n = *naddrs;
	for (i = 0, j = 0; i < n; ++i) {
		addrp[j++].s_addr &= maskp->s_addr;
		for (k = 0; k < j - 1; ++k)
			if (addrp[k].s_addr == addrp[j-1].s_addr) {
				--*naddrs;
				--j;
				break;
			}
	}
}

const char *xtables_ip6addr_to_numeric(const struct in6_addr *addrp)
{
	/* 0000:0000:0000:0000:0000:000.000.000.000
	 * 0000:0000:0000:0000:0000:0000:0000:0000 */
	static char buf[50+1];
	return inet_ntop(AF_INET6, addrp, buf, sizeof(buf));
}

static const char *ip6addr_to_host(const struct in6_addr *addr)
{
	static char hostname[NI_MAXHOST];
	struct sockaddr_in6 saddr;
	int err;

	memset(&saddr, 0, sizeof(struct sockaddr_in6));
	memcpy(&saddr.sin6_addr, addr, sizeof(*addr));
	saddr.sin6_family = AF_INET6;

	err = getnameinfo((const void *)&saddr, sizeof(struct sockaddr_in6),
	      hostname, sizeof(hostname) - 1, NULL, 0, 0);
	if (err != 0) {
#ifdef DEBUG
		fprintf(stderr,"IP2Name: %s\n",gai_strerror(err));
#endif
		return NULL;
	}

#ifdef DEBUG
	fprintf (stderr, "\naddr2host: %s\n", hostname);
#endif
	return hostname;
}

const char *xtables_ip6addr_to_anyname(const struct in6_addr *addr)
{
	const char *name;

	if ((name = ip6addr_to_host(addr)) != NULL)
		return name;

	return xtables_ip6addr_to_numeric(addr);
}

static int ip6addr_prefix_length(const struct in6_addr *k)
{
	unsigned int bits = 0;
	uint32_t a, b, c, d;

	a = ntohl(k->s6_addr32[0]);
	b = ntohl(k->s6_addr32[1]);
	c = ntohl(k->s6_addr32[2]);
	d = ntohl(k->s6_addr32[3]);
	while (a & 0x80000000U) {
		++bits;
		a <<= 1;
		a  |= (b >> 31) & 1;
		b <<= 1;
		b  |= (c >> 31) & 1;
		c <<= 1;
		c  |= (d >> 31) & 1;
		d <<= 1;
	}
	if (a != 0 || b != 0 || c != 0 || d != 0)
		return -1;
	return bits;
}

const char *xtables_ip6mask_to_numeric(const struct in6_addr *addrp)
{
	static char buf[50+2];
	int l = ip6addr_prefix_length(addrp);

	if (l == -1) {
		strcpy(buf, "/");
		strcat(buf, xtables_ip6addr_to_numeric(addrp));
		return buf;
	}
	sprintf(buf, "/%d", l);
	return buf;
}

struct in6_addr *xtables_numeric_to_ip6addr(const char *num)
{
	static struct in6_addr ap;
	int err;

	if ((err = inet_pton(AF_INET6, num, &ap)) == 1)
		return &ap;
#ifdef DEBUG
	fprintf(stderr, "\nnumeric2addr: %d\n", err);
#endif
	return NULL;
}

static struct in6_addr *
host_to_ip6addr(const char *name, unsigned int *naddr)
{
	static struct in6_addr *addr;
	struct addrinfo hints;
	struct addrinfo *res;
	int err;

	memset(&hints, 0, sizeof(hints));
	hints.ai_flags    = AI_CANONNAME;
	hints.ai_family   = AF_INET6;
	hints.ai_socktype = SOCK_RAW;
	hints.ai_protocol = IPPROTO_IPV6;
	hints.ai_next     = NULL;

	*naddr = 0;
	if ((err = getaddrinfo(name, NULL, &hints, &res)) != 0) {
#ifdef DEBUG
		fprintf(stderr,"Name2IP: %s\n",gai_strerror(err));
#endif
		return NULL;
	} else {
		if (res->ai_family != AF_INET6 ||
		    res->ai_addrlen != sizeof(struct sockaddr_in6))
			return NULL;

#ifdef DEBUG
		fprintf(stderr, "resolved: len=%d  %s ", res->ai_addrlen,
		        xtables_ip6addr_to_numeric(&((struct sockaddr_in6 *)res->ai_addr)->sin6_addr));
#endif
		/* Get the first element of the address-chain */
		addr = xtables_malloc(sizeof(struct in6_addr));
		memcpy(addr, &((const struct sockaddr_in6 *)res->ai_addr)->sin6_addr,
		       sizeof(struct in6_addr));
		freeaddrinfo(res);
		*naddr = 1;
		return addr;
	}

	return NULL;
}

static struct in6_addr *network_to_ip6addr(const char *name)
{
	/*	abort();*/
	/* TODO: not implemented yet, but the exception breaks the
	 *       name resolvation */
	return NULL;
}

static struct in6_addr *
ip6parse_hostnetwork(const char *name, unsigned int *naddrs)
{
	struct in6_addr *addrp, *addrptmp;

	if ((addrptmp = xtables_numeric_to_ip6addr(name)) != NULL ||
	    (addrptmp = network_to_ip6addr(name)) != NULL) {
		addrp = xtables_malloc(sizeof(struct in6_addr));
		memcpy(addrp, addrptmp, sizeof(*addrp));
		*naddrs = 1;
		return addrp;
	}
	if ((addrp = host_to_ip6addr(name, naddrs)) != NULL)
		return addrp;

	xt_params->exit_err(PARAMETER_PROBLEM, "host/network `%s' not found", name);
}

static struct in6_addr *parse_ip6mask(char *mask)
{
	static struct in6_addr maskaddr;
	struct in6_addr *addrp;
	unsigned int bits;

	if (mask == NULL) {
		/* no mask at all defaults to 128 bits */
		memset(&maskaddr, 0xff, sizeof maskaddr);
		return &maskaddr;
	}
	if ((addrp = xtables_numeric_to_ip6addr(mask)) != NULL)
		return addrp;
	if (!xtables_strtoui(mask, NULL, &bits, 0, 128))
		xt_params->exit_err(PARAMETER_PROBLEM,
			   "invalid mask `%s' specified", mask);
	if (bits != 0) {
		char *p = (void *)&maskaddr;
		memset(p, 0xff, bits / 8);
		memset(p + (bits / 8) + 1, 0, (128 - bits) / 8);
		p[bits/8] = 0xff << (8 - (bits & 7));
		return &maskaddr;
	}

	memset(&maskaddr, 0, sizeof(maskaddr));
	return &maskaddr;
}

void
xtables_ip6parse_multiple(const char *name, struct in6_addr **addrpp,
		      struct in6_addr **maskpp, unsigned int *naddrs)
{
	static const struct in6_addr zero_addr;
	struct in6_addr *addrp;
	char buf[256], *p;
	unsigned int len, i, j, n, count = 1;
	const char *loop = name;

	while ((loop = strchr(loop, ',')) != NULL) {
		++count;
		++loop; /* skip ',' */
	}

	*addrpp = xtables_malloc(sizeof(struct in6_addr) * count);
	*maskpp = xtables_malloc(sizeof(struct in6_addr) * count);

	loop = name;

	for (i = 0; i < count /*NB: count can grow*/; ++i) {
		if (loop == NULL)
			break;
		if (*loop == ',')
			++loop;
		if (*loop == '\0')
			break;
		p = strchr(loop, ',');
		if (p != NULL)
			len = p - loop;
		else
			len = strlen(loop);
		if (len == 0 || sizeof(buf) - 1 < len)
			break;

		strncpy(buf, loop, len);
		buf[len] = '\0';
		loop += len;
		if ((p = strrchr(buf, '/')) != NULL) {
			*p = '\0';
			addrp = parse_ip6mask(p + 1);
		} else {
			addrp = parse_ip6mask(NULL);
		}
		memcpy(*maskpp + i, addrp, sizeof(*addrp));

		/* if a null mask is given, the name is ignored, like in "any/0" */
		if (memcmp(*maskpp + i, &zero_addr, sizeof(zero_addr)) == 0)
			strcpy(buf, "::");

		addrp = ip6parse_hostnetwork(buf, &n);
		/* ip6parse_hostnetwork only ever returns one IP
		address (it exits if the resolution fails).
		Therefore, n will always be 1 here.  Leaving the
		code below in anyway in case ip6parse_hostnetwork
		is improved some day to behave like
		ipparse_hostnetwork: */
		if (n > 1) {
			count += n - 1;
			*addrpp = xtables_realloc(*addrpp,
			          sizeof(struct in6_addr) * count);
			*maskpp = xtables_realloc(*maskpp,
			          sizeof(struct in6_addr) * count);
			for (j = 0; j < n; ++j)
				/* for each new addr */
				memcpy(*addrpp + i + j, addrp + j,
				       sizeof(*addrp));
			for (j = 1; j < n; ++j)
				/* for each new mask */
				memcpy(*maskpp + i + j, *maskpp + i,
				       sizeof(*addrp));
			i += n - 1;
		} else {
			memcpy(*addrpp + i, addrp, sizeof(*addrp));
		}
		/* free what ip6parse_hostnetwork had allocated: */
		free(addrp);
	}
	*naddrs = count;
	for (i = 0; i < n; ++i)
		for (j = 0; j < 4; ++j)
			(*addrpp+i)->s6_addr32[j] &= (*maskpp+i)->s6_addr32[j];
}

void xtables_ip6parse_any(const char *name, struct in6_addr **addrpp,
                          struct in6_addr *maskp, unsigned int *naddrs)
{
	static const struct in6_addr zero_addr;
	struct in6_addr *addrp;
	unsigned int i, j, k, n;
	char buf[256], *p;

	strncpy(buf, name, sizeof(buf) - 1);
	buf[sizeof(buf)-1] = '\0';
	if ((p = strrchr(buf, '/')) != NULL) {
		*p = '\0';
		addrp = parse_ip6mask(p + 1);
	} else {
		addrp = parse_ip6mask(NULL);
	}
	memcpy(maskp, addrp, sizeof(*maskp));

	/* if a null mask is given, the name is ignored, like in "any/0" */
	if (memcmp(maskp, &zero_addr, sizeof(zero_addr)) == 0)
		strcpy(buf, "::");

	addrp = *addrpp = ip6parse_hostnetwork(buf, naddrs);
	n = *naddrs;
	for (i = 0, j = 0; i < n; ++i) {
		for (k = 0; k < 4; ++k)
			addrp[j].s6_addr32[k] &= maskp->s6_addr32[k];
		++j;
		for (k = 0; k < j - 1; ++k)
			if (IN6_ARE_ADDR_EQUAL(&addrp[k], &addrp[j - 1])) {
				--*naddrs;
				--j;
				break;
			}
	}
}

void xtables_save_string(const char *value)
{
	static const char no_quote_chars[] = "_-0123456789"
		"abcdefghijklmnopqrstuvwxyz"
		"ABCDEFGHIJKLMNOPQRSTUVWXYZ";
	static const char escape_chars[] = "\"\\'";
	size_t length;
	const char *p;

	length = strcspn(value, no_quote_chars);
	if (length > 0 && value[length] == 0) {
		/* no quoting required */
		fputs(value, stdout);
		putchar(' ');
	} else {
		/* there is at least one dangerous character in the
		   value, which we have to quote.  Write double quotes
		   around the value and escape special characters with
		   a backslash */
		putchar('"');

		for (p = strpbrk(value, escape_chars); p != NULL;
		     p = strpbrk(value, escape_chars)) {
			if (p > value)
				fwrite(value, 1, p - value, stdout);
			putchar('\\');
			putchar(*p);
			value = p + 1;
		}

		/* print the rest and finish the double quoted
		   string */
		fputs(value, stdout);
		printf("\" ");
	}
}

/**
 * Check for option-intrapositional negation.
 * Do not use in new code.
 */
int xtables_check_inverse(const char option[], int *invert,
			  int *my_optind, int argc, char **argv)
{
	if (option == NULL || strcmp(option, "!") != 0)
		return false;

	fprintf(stderr, "Using intrapositioned negation "
	        "(`--option ! this`) is deprecated in favor of "
	        "extrapositioned (`! --option this`).\n");

	if (*invert)
		xt_params->exit_err(PARAMETER_PROBLEM,
			   "Multiple `!' flags not allowed");
	*invert = true;
	if (my_optind != NULL) {
		optarg = argv[*my_optind];
		++*my_optind;
		if (argc && *my_optind > argc)
			xt_params->exit_err(PARAMETER_PROBLEM,
				   "no argument following `!'");
	}

	return true;
}

const struct xtables_pprot xtables_chain_protos[] = {
	{"tcp",       IPPROTO_TCP},
	{"sctp",      IPPROTO_SCTP},
	{"udp",       IPPROTO_UDP},
	{"udplite",   IPPROTO_UDPLITE},
	{"icmp",      IPPROTO_ICMP},
	{"icmpv6",    IPPROTO_ICMPV6},
	{"ipv6-icmp", IPPROTO_ICMPV6},
	{"esp",       IPPROTO_ESP},
	{"ah",        IPPROTO_AH},
	{"ipv6-mh",   IPPROTO_MH},
	{"mh",        IPPROTO_MH},
	{"all",       0},
	{NULL},
};

u_int16_t
xtables_parse_protocol(const char *s)
{
	unsigned int proto;

	if (!xtables_strtoui(s, NULL, &proto, 0, UINT8_MAX)) {
		struct protoent *pent;

		/* first deal with the special case of 'all' to prevent
		 * people from being able to redefine 'all' in nsswitch
		 * and/or provoke expensive [not working] ldap/nis/...
		 * lookups */
		if (!strcmp(s, "all"))
			return 0;

		if ((pent = getprotobyname(s)))
			proto = pent->p_proto;
		else {
			unsigned int i;
			for (i = 0; i < ARRAY_SIZE(xtables_chain_protos); ++i) {
				if (xtables_chain_protos[i].name == NULL)
					continue;

				if (strcmp(s, xtables_chain_protos[i].name) == 0) {
					proto = xtables_chain_protos[i].num;
					break;
				}
			}
			if (i == ARRAY_SIZE(xtables_chain_protos))
				xt_params->exit_err(PARAMETER_PROBLEM,
					   "unknown protocol `%s' specified",
					   s);
		}
	}

	return proto;
}
