/*
 *	libxt_owner - iptables addon for xt_owner
 *
 *	Copyright © CC Computer Consultants GmbH, 2007 - 2008
 *	Jan Engelhardt <jengelh@computergmbh.de>
 */
#include <getopt.h>
#include <grp.h>
#include <netdb.h>
#include <pwd.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <limits.h>

#include <xtables.h>
#include <linux/netfilter/xt_owner.h>

/* match and invert flags */
enum {
	IPT_OWNER_UID   = 0x01,
	IPT_OWNER_GID   = 0x02,
	IPT_OWNER_PID   = 0x04,
	IPT_OWNER_SID   = 0x08,
	IPT_OWNER_COMM  = 0x10,
	IP6T_OWNER_UID  = IPT_OWNER_UID,
	IP6T_OWNER_GID  = IPT_OWNER_GID,
	IP6T_OWNER_PID  = IPT_OWNER_PID,
	IP6T_OWNER_SID  = IPT_OWNER_SID,
	IP6T_OWNER_COMM = IPT_OWNER_COMM,
};

struct ipt_owner_info {
	uid_t uid;
	gid_t gid;
	pid_t pid;
	pid_t sid;
	char comm[16];
	u_int8_t match, invert;	/* flags */
};

struct ip6t_owner_info {
	uid_t uid;
	gid_t gid;
	pid_t pid;
	pid_t sid;
	char comm[16];
	u_int8_t match, invert;	/* flags */
};

/*
 *	Note: "UINT32_MAX - 1" is used in the code because -1 is a reserved
 *	UID/GID value anyway.
 */

enum {
	FLAG_UID_OWNER     = 1 << 0,
	FLAG_GID_OWNER     = 1 << 1,
	FLAG_SOCKET_EXISTS = 1 << 2,
	FLAG_PID_OWNER     = 1 << 3,
	FLAG_SID_OWNER     = 1 << 4,
	FLAG_COMM          = 1 << 5,
};

static void owner_mt_help_v0(void)
{
#ifdef IPT_OWNER_COMM
	printf(
"owner match options:\n"
"[!] --uid-owner userid       Match local UID\n"
"[!] --gid-owner groupid      Match local GID\n"
"[!] --pid-owner processid    Match local PID\n"
"[!] --sid-owner sessionid    Match local SID\n"
"[!] --cmd-owner name         Match local command name\n"
"NOTE: PID, SID and command matching are broken on SMP\n");
#else
	printf(
"owner match options:\n"
"[!] --uid-owner userid       Match local UID\n"
"[!] --gid-owner groupid      Match local GID\n"
"[!] --pid-owner processid    Match local PID\n"
"[!] --sid-owner sessionid    Match local SID\n"
"NOTE: PID and SID matching are broken on SMP\n");
#endif /* IPT_OWNER_COMM */
}

static void owner_mt6_help_v0(void)
{
	printf(
"owner match options:\n"
"[!] --uid-owner userid       Match local UID\n"
"[!] --gid-owner groupid      Match local GID\n"
"[!] --pid-owner processid    Match local PID\n"
"[!] --sid-owner sessionid    Match local SID\n"
"NOTE: PID and SID matching are broken on SMP\n");
}

static void owner_mt_help(void)
{
	printf(
"owner match options:\n"
"[!] --uid-owner userid[-userid]      Match local UID\n"
"[!] --gid-owner groupid[-groupid]    Match local GID\n"
"[!] --socket-exists                  Match if socket exists\n");
}

static const struct option owner_mt_opts_v0[] = {
	{.name = "uid-owner", .has_arg = true, .val = 'u'},
	{.name = "gid-owner", .has_arg = true, .val = 'g'},
	{.name = "pid-owner", .has_arg = true, .val = 'p'},
	{.name = "sid-owner", .has_arg = true, .val = 's'},
#ifdef IPT_OWNER_COMM
	{.name = "cmd-owner", .has_arg = true, .val = 'c'},
#endif
	XT_GETOPT_TABLEEND,
};

static const struct option owner_mt6_opts_v0[] = {
	{.name = "uid-owner", .has_arg = true, .val = 'u'},
	{.name = "gid-owner", .has_arg = true, .val = 'g'},
	{.name = "pid-owner", .has_arg = true, .val = 'p'},
	{.name = "sid-owner", .has_arg = true, .val = 's'},
	XT_GETOPT_TABLEEND,
};

static const struct option owner_mt_opts[] = {
	{.name = "uid-owner",     .has_arg = true,  .val = 'u'},
	{.name = "gid-owner",     .has_arg = true,  .val = 'g'},
	{.name = "socket-exists", .has_arg = false, .val = 'k'},
	XT_GETOPT_TABLEEND,
};

static int
owner_mt_parse_v0(int c, char **argv, int invert, unsigned int *flags,
                  const void *entry, struct xt_entry_match **match)
{
	struct ipt_owner_info *info = (void *)(*match)->data;
	struct passwd *pwd;
	struct group *grp;
	unsigned int id;

	switch (c) {
	case 'u':
		xtables_param_act(XTF_ONLY_ONCE, "owner", "--uid-owner", *flags & FLAG_UID_OWNER);
		if ((pwd = getpwnam(optarg)) != NULL)
			id = pwd->pw_uid;
		else if (!xtables_strtoui(optarg, NULL, &id, 0, UINT32_MAX - 1))
			xtables_param_act(XTF_BAD_VALUE, "owner", "--uid-owner", optarg);
		if (invert)
			info->invert |= IPT_OWNER_UID;
		info->match |= IPT_OWNER_UID;
		info->uid    = id;
		*flags      |= FLAG_UID_OWNER;
		return true;

	case 'g':
		xtables_param_act(XTF_ONLY_ONCE, "owner", "--gid-owner", *flags & FLAG_GID_OWNER);
		if ((grp = getgrnam(optarg)) != NULL)
			id = grp->gr_gid;
		else if (!xtables_strtoui(optarg, NULL, &id, 0, UINT32_MAX - 1))
			xtables_param_act(XTF_BAD_VALUE, "owner", "--gid-owner", optarg);
		if (invert)
			info->invert |= IPT_OWNER_GID;
		info->match |= IPT_OWNER_GID;
		info->gid    = id;
		*flags      |= FLAG_GID_OWNER;
		return true;

	case 'p':
		xtables_param_act(XTF_ONLY_ONCE, "owner", "--pid-owner", *flags & FLAG_PID_OWNER);
		if (!xtables_strtoui(optarg, NULL, &id, 0, INT_MAX))
			xtables_param_act(XTF_BAD_VALUE, "owner", "--pid-owner", optarg);
		if (invert)
			info->invert |= IPT_OWNER_PID;
		info->match |= IPT_OWNER_PID;
		info->pid    = id;
		*flags      |= FLAG_PID_OWNER;
		return true;

	case 's':
		xtables_param_act(XTF_ONLY_ONCE, "owner", "--sid-owner", *flags & FLAG_SID_OWNER);
		if (!xtables_strtoui(optarg, NULL, &id, 0, INT_MAX))
			xtables_param_act(XTF_BAD_VALUE, "owner", "--sid-value", optarg);
		if (invert)
			info->invert |= IPT_OWNER_SID;
		info->match |= IPT_OWNER_SID;
		info->sid    = id;
		*flags      |= FLAG_SID_OWNER;
		return true;

#ifdef IPT_OWNER_COMM
	case 'c':
		xtables_param_act(XTF_ONLY_ONCE, "owner", "--cmd-owner", *flags & FLAG_COMM);
		if (strlen(optarg) > sizeof(info->comm))
			xtables_error(PARAMETER_PROBLEM, "owner match: command "
			           "\"%s\" too long, max. %zu characters",
			           optarg, sizeof(info->comm));

		info->comm[sizeof(info->comm)-1] = '\0';
		strncpy(info->comm, optarg, sizeof(info->comm));

		if (invert)
			info->invert |= IPT_OWNER_COMM;
		info->match |= IPT_OWNER_COMM;
		*flags      |= FLAG_COMM;
		return true;
#endif
	}
	return false;
}

static int
owner_mt6_parse_v0(int c, char **argv, int invert, unsigned int *flags,
                   const void *entry, struct xt_entry_match **match)
{
	struct ip6t_owner_info *info = (void *)(*match)->data;
	struct passwd *pwd;
	struct group *grp;
	unsigned int id;

	switch (c) {
	case 'u':
		xtables_param_act(XTF_ONLY_ONCE, "owner", "--uid-owner",
		          *flags & FLAG_UID_OWNER);
		if ((pwd = getpwnam(optarg)) != NULL)
			id = pwd->pw_uid;
		else if (!xtables_strtoui(optarg, NULL, &id, 0, UINT32_MAX - 1))
			xtables_param_act(XTF_BAD_VALUE, "owner", "--uid-owner", optarg);
		if (invert)
			info->invert |= IP6T_OWNER_UID;
		info->match |= IP6T_OWNER_UID;
		info->uid    = id;
		*flags      |= FLAG_UID_OWNER;
		return true;

	case 'g':
		xtables_param_act(XTF_ONLY_ONCE, "owner", "--gid-owner",
		          *flags & FLAG_GID_OWNER);
		if ((grp = getgrnam(optarg)) != NULL)
			id = grp->gr_gid;
		else if (!xtables_strtoui(optarg, NULL, &id, 0, UINT32_MAX - 1))
			xtables_param_act(XTF_BAD_VALUE, "owner", "--gid-owner", optarg);
		if (invert)
			info->invert |= IP6T_OWNER_GID;
		info->match |= IP6T_OWNER_GID;
		info->gid    = id;
		*flags      |= FLAG_GID_OWNER;
		return true;

	case 'p':
		xtables_param_act(XTF_ONLY_ONCE, "owner", "--pid-owner",
		          *flags & FLAG_PID_OWNER);
		if (!xtables_strtoui(optarg, NULL, &id, 0, INT_MAX))
			xtables_param_act(XTF_BAD_VALUE, "owner", "--pid-owner", optarg);
		if (invert)
			info->invert |= IP6T_OWNER_PID;
		info->match |= IP6T_OWNER_PID;
		info->pid    = id;
		*flags      |= FLAG_PID_OWNER;
		return true;

	case 's':
		xtables_param_act(XTF_ONLY_ONCE, "owner", "--sid-owner",
		          *flags & FLAG_SID_OWNER);
		if (!xtables_strtoui(optarg, NULL, &id, 0, INT_MAX))
			xtables_param_act(XTF_BAD_VALUE, "owner", "--sid-owner", optarg);
		if (invert)
			info->invert |= IP6T_OWNER_SID;
		info->match |= IP6T_OWNER_SID;
		info->sid    = id;
		*flags      |= FLAG_SID_OWNER;
		return true;
	}
	return false;
}

static void owner_parse_range(const char *s, unsigned int *from,
                              unsigned int *to, const char *opt)
{
	char *end;

	/* -1 is reversed, so the max is one less than that. */
	if (!xtables_strtoui(s, &end, from, 0, UINT32_MAX - 1))
		xtables_param_act(XTF_BAD_VALUE, "owner", opt, s);
	*to = *from;
	if (*end == '-' || *end == ':')
		if (!xtables_strtoui(end + 1, &end, to, 0, UINT32_MAX - 1))
			xtables_param_act(XTF_BAD_VALUE, "owner", opt, s);
	if (*end != '\0')
		xtables_param_act(XTF_BAD_VALUE, "owner", opt, s);
}

static int owner_mt_parse(int c, char **argv, int invert, unsigned int *flags,
                          const void *entry, struct xt_entry_match **match)
{
	struct xt_owner_match_info *info = (void *)(*match)->data;
	struct passwd *pwd;
	struct group *grp;
	unsigned int from, to;

	switch (c) {
	case 'u':
		xtables_param_act(XTF_ONLY_ONCE, "owner", "--uid-owner",
		          *flags & FLAG_UID_OWNER);
		if ((pwd = getpwnam(optarg)) != NULL)
			from = to = pwd->pw_uid;
		else
			owner_parse_range(optarg, &from, &to, "--uid-owner");
		if (invert)
			info->invert |= XT_OWNER_UID;
		info->match  |= XT_OWNER_UID;
		info->uid_min = from;
		info->uid_max = to;
		*flags       |= FLAG_UID_OWNER;
		return true;

	case 'g':
		xtables_param_act(XTF_ONLY_ONCE, "owner", "--gid-owner",
		          *flags & FLAG_GID_OWNER);
		if ((grp = getgrnam(optarg)) != NULL)
			from = to = grp->gr_gid;
		else
			owner_parse_range(optarg, &from, &to, "--gid-owner");
		if (invert)
			info->invert |= XT_OWNER_GID;
		info->match  |= XT_OWNER_GID;
		info->gid_min = from;
		info->gid_max = to;
		*flags      |= FLAG_GID_OWNER;
		return true;

	case 'k':
		xtables_param_act(XTF_ONLY_ONCE, "owner", "--socket-exists",
		          *flags & FLAG_SOCKET_EXISTS);
		if (invert)
			info->invert |= XT_OWNER_SOCKET;
		info->match |= XT_OWNER_SOCKET;
		*flags |= FLAG_SOCKET_EXISTS;
		return true;

	}
	return false;
}

static void owner_mt_check(unsigned int flags)
{
	if (flags == 0)
		xtables_error(PARAMETER_PROBLEM, "owner: At least one of "
		           "--uid-owner, --gid-owner or --socket-exists "
		           "is required");
}

static void
owner_mt_print_item_v0(const struct ipt_owner_info *info, const char *label,
                       u_int8_t flag, bool numeric)
{
	if (!(info->match & flag))
		return;
	if (info->invert & flag)
		printf("! ");
	printf("%s ", label);

	switch (info->match & flag) {
	case IPT_OWNER_UID:
		if (!numeric) {
			struct passwd *pwd = getpwuid(info->uid);

			if (pwd != NULL && pwd->pw_name != NULL) {
				printf("%s ", pwd->pw_name);
				break;
			}
		}
		printf("%u ", (unsigned int)info->uid);
		break;

	case IPT_OWNER_GID:
		if (!numeric) {
			struct group *grp = getgrgid(info->gid);

			if (grp != NULL && grp->gr_name != NULL) {
				printf("%s ", grp->gr_name);
				break;
			}
		}
		printf("%u ", (unsigned int)info->gid);
		break;

	case IPT_OWNER_PID:
		printf("%u ", (unsigned int)info->pid);
		break;

	case IPT_OWNER_SID:
		printf("%u ", (unsigned int)info->sid);
		break;

#ifdef IPT_OWNER_COMM
	case IPT_OWNER_COMM:
		printf("%.*s ", (int)sizeof(info->comm), info->comm);
		break;
#endif
	}
}

static void
owner_mt6_print_item_v0(const struct ip6t_owner_info *info, const char *label,
                        u_int8_t flag, bool numeric)
{
	if (!(info->match & flag))
		return;
	if (info->invert & flag)
		printf("! ");
	printf("%s ", label);

	switch (info->match & flag) {
	case IP6T_OWNER_UID:
		if (!numeric) {
			struct passwd *pwd = getpwuid(info->uid);

			if (pwd != NULL && pwd->pw_name != NULL) {
				printf("%s ", pwd->pw_name);
				break;
			}
		}
		printf("%u ", (unsigned int)info->uid);
		break;

	case IP6T_OWNER_GID:
		if (!numeric) {
			struct group *grp = getgrgid(info->gid);

			if (grp != NULL && grp->gr_name != NULL) {
				printf("%s ", grp->gr_name);
				break;
			}
		}
		printf("%u ", (unsigned int)info->gid);
		break;

	case IP6T_OWNER_PID:
		printf("%u ", (unsigned int)info->pid);
		break;

	case IP6T_OWNER_SID:
		printf("%u ", (unsigned int)info->sid);
		break;
	}
}

static void
owner_mt_print_item(const struct xt_owner_match_info *info, const char *label,
                    u_int8_t flag, bool numeric)
{
	if (!(info->match & flag))
		return;
	if (info->invert & flag)
		printf("! ");
	printf("%s ", label);

	switch (info->match & flag) {
	case XT_OWNER_UID:
		if (info->uid_min != info->uid_max) {
			printf("%u-%u ", (unsigned int)info->uid_min,
			       (unsigned int)info->uid_max);
			break;
		} else if (!numeric) {
			const struct passwd *pwd = getpwuid(info->uid_min);

			if (pwd != NULL && pwd->pw_name != NULL) {
				printf("%s ", pwd->pw_name);
				break;
			}
		}
		printf("%u ", (unsigned int)info->uid_min);
		break;

	case XT_OWNER_GID:
		if (info->gid_min != info->gid_max) {
			printf("%u-%u ", (unsigned int)info->gid_min,
			       (unsigned int)info->gid_max);
			break;
		} else if (!numeric) {
			const struct group *grp = getgrgid(info->gid_min);

			if (grp != NULL && grp->gr_name != NULL) {
				printf("%s ", grp->gr_name);
				break;
			}
		}
		printf("%u ", (unsigned int)info->gid_min);
		break;
	}
}

static void
owner_mt_print_v0(const void *ip, const struct xt_entry_match *match,
                  int numeric)
{
	const struct ipt_owner_info *info = (void *)match->data;

	owner_mt_print_item_v0(info, "owner UID match", IPT_OWNER_UID, numeric);
	owner_mt_print_item_v0(info, "owner GID match", IPT_OWNER_GID, numeric);
	owner_mt_print_item_v0(info, "owner PID match", IPT_OWNER_PID, numeric);
	owner_mt_print_item_v0(info, "owner SID match", IPT_OWNER_SID, numeric);
#ifdef IPT_OWNER_COMM
	owner_mt_print_item_v0(info, "owner CMD match", IPT_OWNER_COMM, numeric);
#endif
}

static void
owner_mt6_print_v0(const void *ip, const struct xt_entry_match *match,
                   int numeric)
{
	const struct ip6t_owner_info *info = (void *)match->data;

	owner_mt6_print_item_v0(info, "owner UID match", IPT_OWNER_UID, numeric);
	owner_mt6_print_item_v0(info, "owner GID match", IPT_OWNER_GID, numeric);
	owner_mt6_print_item_v0(info, "owner PID match", IPT_OWNER_PID, numeric);
	owner_mt6_print_item_v0(info, "owner SID match", IPT_OWNER_SID, numeric);
}

static void owner_mt_print(const void *ip, const struct xt_entry_match *match,
                           int numeric)
{
	const struct xt_owner_match_info *info = (void *)match->data;

	owner_mt_print_item(info, "owner socket exists", XT_OWNER_SOCKET, numeric);
	owner_mt_print_item(info, "owner UID match",     XT_OWNER_UID,    numeric);
	owner_mt_print_item(info, "owner GID match",     XT_OWNER_GID,    numeric);
}

static void
owner_mt_save_v0(const void *ip, const struct xt_entry_match *match)
{
	const struct ipt_owner_info *info = (void *)match->data;

	owner_mt_print_item_v0(info, "--uid-owner", IPT_OWNER_UID, true);
	owner_mt_print_item_v0(info, "--gid-owner", IPT_OWNER_GID, true);
	owner_mt_print_item_v0(info, "--pid-owner", IPT_OWNER_PID, true);
	owner_mt_print_item_v0(info, "--sid-owner", IPT_OWNER_SID, true);
#ifdef IPT_OWNER_COMM
	owner_mt_print_item_v0(info, "--cmd-owner", IPT_OWNER_COMM, true);
#endif
}

static void
owner_mt6_save_v0(const void *ip, const struct xt_entry_match *match)
{
	const struct ip6t_owner_info *info = (void *)match->data;

	owner_mt6_print_item_v0(info, "--uid-owner", IPT_OWNER_UID, true);
	owner_mt6_print_item_v0(info, "--gid-owner", IPT_OWNER_GID, true);
	owner_mt6_print_item_v0(info, "--pid-owner", IPT_OWNER_PID, true);
	owner_mt6_print_item_v0(info, "--sid-owner", IPT_OWNER_SID, true);
}

static void owner_mt_save(const void *ip, const struct xt_entry_match *match)
{
	const struct xt_owner_match_info *info = (void *)match->data;

	owner_mt_print_item(info, "--socket-exists",  XT_OWNER_SOCKET, false);
	owner_mt_print_item(info, "--uid-owner",      XT_OWNER_UID,    false);
	owner_mt_print_item(info, "--gid-owner",      XT_OWNER_GID,    false);
}

static struct xtables_match owner_mt_reg[] = {
	{
		.version       = XTABLES_VERSION,
		.name          = "owner",
		.revision      = 0,
		.family        = NFPROTO_IPV4,
		.size          = XT_ALIGN(sizeof(struct ipt_owner_info)),
		.userspacesize = XT_ALIGN(sizeof(struct ipt_owner_info)),
		.help          = owner_mt_help_v0,
		.parse         = owner_mt_parse_v0,
		.final_check   = owner_mt_check,
		.print         = owner_mt_print_v0,
		.save          = owner_mt_save_v0,
		.extra_opts    = owner_mt_opts_v0,
	},
	{
		.version       = XTABLES_VERSION,
		.name          = "owner",
		.revision      = 0,
		.family        = NFPROTO_IPV6,
		.size          = XT_ALIGN(sizeof(struct ip6t_owner_info)),
		.userspacesize = XT_ALIGN(sizeof(struct ip6t_owner_info)),
		.help          = owner_mt6_help_v0,
		.parse         = owner_mt6_parse_v0,
		.final_check   = owner_mt_check,
		.print         = owner_mt6_print_v0,
		.save          = owner_mt6_save_v0,
		.extra_opts    = owner_mt6_opts_v0,
	},
	{
		.version       = XTABLES_VERSION,
		.name          = "owner",
		.revision      = 1,
		.family        = NFPROTO_UNSPEC,
		.size          = XT_ALIGN(sizeof(struct xt_owner_match_info)),
		.userspacesize = XT_ALIGN(sizeof(struct xt_owner_match_info)),
		.help          = owner_mt_help,
		.parse         = owner_mt_parse,
		.final_check   = owner_mt_check,
		.print         = owner_mt_print,
		.save          = owner_mt_save,
		.extra_opts    = owner_mt_opts,
	},
};

void libxt_owner_init(void)
{
	xtables_register_matches(owner_mt_reg, ARRAY_SIZE(owner_mt_reg));
}
