/* Shared library add-on to iptables to add bridge port matching support. */
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <getopt.h>
#include <ctype.h>
#include <xtables.h>
#include <linux/netfilter/xt_physdev.h>
#if defined(__GLIBC__) && __GLIBC__ == 2
#include <net/ethernet.h>
#else
#include <linux/if_ether.h>
#endif

static void physdev_help(void)
{
	printf(
"physdev match options:\n"
" [!] --physdev-in inputname[+]		bridge port name ([+] for wildcard)\n"
" [!] --physdev-out outputname[+]	bridge port name ([+] for wildcard)\n"
" [!] --physdev-is-in			arrived on a bridge device\n"
" [!] --physdev-is-out			will leave on a bridge device\n"
" [!] --physdev-is-bridged		it's a bridged packet\n");
}

static const struct option physdev_opts[] = {
	{.name = "physdev-in",         .has_arg = true,  .val = '1'},
	{.name = "physdev-out",        .has_arg = true,  .val = '2'},
	{.name = "physdev-is-in",      .has_arg = false, .val = '3'},
	{.name = "physdev-is-out",     .has_arg = false, .val = '4'},
	{.name = "physdev-is-bridged", .has_arg = false, .val = '5'},
	XT_GETOPT_TABLEEND,
};

static int
physdev_parse(int c, char **argv, int invert, unsigned int *flags,
              const void *entry, struct xt_entry_match **match)
{
	struct xt_physdev_info *info =
		(struct xt_physdev_info*)(*match)->data;

	switch (c) {
	case '1':
		if (*flags & XT_PHYSDEV_OP_IN)
			goto multiple_use;
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		xtables_parse_interface(optarg, info->physindev,
				(unsigned char *)info->in_mask);
		if (invert)
			info->invert |= XT_PHYSDEV_OP_IN;
		info->bitmask |= XT_PHYSDEV_OP_IN;
		*flags |= XT_PHYSDEV_OP_IN;
		break;

	case '2':
		if (*flags & XT_PHYSDEV_OP_OUT)
			goto multiple_use;
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		xtables_parse_interface(optarg, info->physoutdev,
				(unsigned char *)info->out_mask);
		if (invert)
			info->invert |= XT_PHYSDEV_OP_OUT;
		info->bitmask |= XT_PHYSDEV_OP_OUT;
		*flags |= XT_PHYSDEV_OP_OUT;
		break;

	case '3':
		if (*flags & XT_PHYSDEV_OP_ISIN)
			goto multiple_use;
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		info->bitmask |= XT_PHYSDEV_OP_ISIN;
		if (invert)
			info->invert |= XT_PHYSDEV_OP_ISIN;
		*flags |= XT_PHYSDEV_OP_ISIN;
		break;

	case '4':
		if (*flags & XT_PHYSDEV_OP_ISOUT)
			goto multiple_use;
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		info->bitmask |= XT_PHYSDEV_OP_ISOUT;
		if (invert)
			info->invert |= XT_PHYSDEV_OP_ISOUT;
		*flags |= XT_PHYSDEV_OP_ISOUT;
		break;

	case '5':
		if (*flags & XT_PHYSDEV_OP_BRIDGED)
			goto multiple_use;
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		if (invert)
			info->invert |= XT_PHYSDEV_OP_BRIDGED;
		*flags |= XT_PHYSDEV_OP_BRIDGED;
		info->bitmask |= XT_PHYSDEV_OP_BRIDGED;
		break;

	default:
		return 0;
	}

	return 1;
multiple_use:
	xtables_error(PARAMETER_PROBLEM,
	   "multiple use of the same physdev option is not allowed");

}

static void physdev_check(unsigned int flags)
{
	if (flags == 0)
		xtables_error(PARAMETER_PROBLEM, "PHYSDEV: no physdev option specified");
}

static void
physdev_print(const void *ip, const struct xt_entry_match *match, int numeric)
{
	const struct xt_physdev_info *info = (const void *)match->data;

	printf("PHYSDEV match");
	if (info->bitmask & XT_PHYSDEV_OP_ISIN)
		printf("%s --physdev-is-in",
		       info->invert & XT_PHYSDEV_OP_ISIN ? " !":"");
	if (info->bitmask & XT_PHYSDEV_OP_IN)
		printf("%s --physdev-in %s",
		(info->invert & XT_PHYSDEV_OP_IN) ? " !":"", info->physindev);

	if (info->bitmask & XT_PHYSDEV_OP_ISOUT)
		printf("%s --physdev-is-out",
		       info->invert & XT_PHYSDEV_OP_ISOUT ? " !":"");
	if (info->bitmask & XT_PHYSDEV_OP_OUT)
		printf("%s --physdev-out %s",
		(info->invert & XT_PHYSDEV_OP_OUT) ? " !":"", info->physoutdev);
	if (info->bitmask & XT_PHYSDEV_OP_BRIDGED)
		printf("%s --physdev-is-bridged",
		       info->invert & XT_PHYSDEV_OP_BRIDGED ? " !":"");
	printf(" ");
}

static void physdev_save(const void *ip, const struct xt_entry_match *match)
{
	const struct xt_physdev_info *info = (const void *)match->data;

	if (info->bitmask & XT_PHYSDEV_OP_ISIN)
		printf("%s--physdev-is-in ",
		       (info->invert & XT_PHYSDEV_OP_ISIN) ? "! " : "");
	if (info->bitmask & XT_PHYSDEV_OP_IN)
		printf("%s--physdev-in %s ",
		       (info->invert & XT_PHYSDEV_OP_IN) ? "! " : "",
		       info->physindev);

	if (info->bitmask & XT_PHYSDEV_OP_ISOUT)
		printf("%s--physdev-is-out ",
		       (info->invert & XT_PHYSDEV_OP_ISOUT) ? "! " : "");
	if (info->bitmask & XT_PHYSDEV_OP_OUT)
		printf("%s--physdev-out %s ",
		       (info->invert & XT_PHYSDEV_OP_OUT) ? "! " : "",
		       info->physoutdev);
	if (info->bitmask & XT_PHYSDEV_OP_BRIDGED)
		printf("%s--physdev-is-bridged ",
		       (info->invert & XT_PHYSDEV_OP_BRIDGED) ? "! " : "");
}

static struct xtables_match physdev_match = {
	.family		= NFPROTO_UNSPEC,
	.name		= "physdev",
	.version	= XTABLES_VERSION,
	.size		= XT_ALIGN(sizeof(struct xt_physdev_info)),
	.userspacesize	= XT_ALIGN(sizeof(struct xt_physdev_info)),
	.help		= physdev_help,
	.parse		= physdev_parse,
	.final_check	= physdev_check,
	.print		= physdev_print,
	.save		= physdev_save,
	.extra_opts	= physdev_opts,
};

void libxt_physdev_init(void)
{
	xtables_register_match(&physdev_match);
}
