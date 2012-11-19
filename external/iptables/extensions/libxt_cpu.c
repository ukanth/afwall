/* Shared library add-on to iptables to add CPU match support. */
#include <stdbool.h>
#include <stdio.h>
#include <netdb.h>
#include <string.h>
#include <stdlib.h>
#include <getopt.h>
#include <xtables.h>
#include <linux/netfilter/xt_cpu.h>

static void cpu_help(void)
{
	printf(
"cpu match options:\n"
"[!] --cpu number   Match CPU number\n");
}

static const struct option cpu_opts[] = {
	{.name = "cpu", .has_arg = true, .val = '1'},
	XT_GETOPT_TABLEEND,
};

static void
parse_cpu(const char *s, struct xt_cpu_info *info)
{
	unsigned int cpu;
	char *end;

	if (!xtables_strtoui(s, &end, &cpu, 0, UINT32_MAX))
		xtables_param_act(XTF_BAD_VALUE, "cpu", "--cpu", s);

	if (*end != '\0')
		xtables_param_act(XTF_BAD_VALUE, "cpu", "--cpu", s);

	info->cpu = cpu;
}

static int
cpu_parse(int c, char **argv, int invert, unsigned int *flags,
          const void *entry, struct xt_entry_match **match)
{
	struct xt_cpu_info *cpuinfo = (struct xt_cpu_info *)(*match)->data;

	switch (c) {
	case '1':
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);
		parse_cpu(optarg, cpuinfo);
		if (invert)
			cpuinfo->invert = 1;
		*flags = 1;
		break;

	default:
		return 0;
	}

	return 1;
}

static void cpu_check(unsigned int flags)
{
	if (!flags)
		xtables_error(PARAMETER_PROBLEM,
			      "You must specify `--cpu'");
}

static void
cpu_print(const void *ip, const struct xt_entry_match *match, int numeric)
{
	const struct xt_cpu_info *info = (void *)match->data;

	printf("cpu %s%u ", info->invert ? "! ":"", info->cpu);
}

static void cpu_save(const void *ip, const struct xt_entry_match *match)
{
	const struct xt_cpu_info *info = (void *)match->data;

	printf("%s--cpu %u ", info->invert ? "! ":"", info->cpu);
}

static struct xtables_match cpu_match = {
	.family		= NFPROTO_UNSPEC,
 	.name		= "cpu",
	.version	= XTABLES_VERSION,
	.size		= XT_ALIGN(sizeof(struct xt_cpu_info)),
	.userspacesize	= XT_ALIGN(sizeof(struct xt_cpu_info)),
	.help		= cpu_help,
	.parse		= cpu_parse,
	.final_check	= cpu_check,
	.print		= cpu_print,
	.save		= cpu_save,
	.extra_opts	= cpu_opts,
};

void libxt_cpu_init(void)
{
	xtables_register_match(&cpu_match);
}
