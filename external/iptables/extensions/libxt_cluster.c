/*
 * (C) 2009 by Pablo Neira Ayuso <pablo@netfilter.org>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation.
 */
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <getopt.h>
#include <stddef.h>

#include <xtables.h>
#include <linux/netfilter/x_tables.h>
#include <linux/netfilter/xt_cluster.h>

/* hack to keep for check */
static unsigned int total_nodes;
static unsigned int node_mask;

static void
cluster_help(void)
{
	printf(
"cluster match options:\n"
"  --cluster-total-nodes <num>		Set number of total nodes in cluster\n"
"  [!] --cluster-local-node <num>	Set the local node number\n"
"  [!] --cluster-local-nodemask <num>	Set the local node mask\n"
"  --cluster-hash-seed <num>		Set seed value of the Jenkins hash\n");
}

enum {
	CLUSTER_OPT_TOTAL_NODES,
	CLUSTER_OPT_LOCAL_NODE,
	CLUSTER_OPT_NODE_MASK,
	CLUSTER_OPT_HASH_SEED,
};

static const struct option cluster_opts[] = {
	{.name = "cluster-total-nodes",    .has_arg = true, .val = CLUSTER_OPT_TOTAL_NODES},
	{.name = "cluster-local-node",     .has_arg = true, .val = CLUSTER_OPT_LOCAL_NODE},
	{.name = "cluster-local-nodemask", .has_arg = true, .val = CLUSTER_OPT_NODE_MASK},
	{.name = "cluster-hash-seed",      .has_arg = true, .val = CLUSTER_OPT_HASH_SEED},
	XT_GETOPT_TABLEEND,
};

static int 
cluster_parse(int c, char **argv, int invert, unsigned int *flags,
	      const void *entry, struct xt_entry_match **match)
{
	struct xt_cluster_match_info *info = (void *)(*match)->data;
	unsigned int num;

	switch (c) {
	case CLUSTER_OPT_TOTAL_NODES:
		if (*flags & (1 << c)) {
			xtables_error(PARAMETER_PROBLEM,
				      "Can only specify "
				      "`--cluster-total-nodes' once");
		}
		if (!xtables_strtoui(optarg, NULL, &num, 1,
				     XT_CLUSTER_NODES_MAX)) {
			xtables_error(PARAMETER_PROBLEM,
				      "Unable to parse `%s' in "
				      "`--cluster-total-nodes'", optarg);
		}
		total_nodes = num;
		info->total_nodes = total_nodes = num;
		*flags |= 1 << c;
		break;
	case CLUSTER_OPT_LOCAL_NODE:
		if (*flags & (1 << c)) {
			xtables_error(PARAMETER_PROBLEM,
				      "Can only specify "
				      "`--cluster-local-node' once");
		}
		if (*flags & (1 << CLUSTER_OPT_NODE_MASK)) {
			xtables_error(PARAMETER_PROBLEM, "You cannot use "
				      "`--cluster-local-nodemask' and "
				      "`--cluster-local-node'");
		}
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);

		if (!xtables_strtoui(optarg, NULL, &num, 1,
				     XT_CLUSTER_NODES_MAX)) {
			xtables_error(PARAMETER_PROBLEM,
				      "Unable to parse `%s' in "
				      "`--cluster-local-node'", optarg);
		}
		if (invert)
			info->flags |= (1 << XT_CLUSTER_F_INV);

		info->node_mask = node_mask = (1 << (num - 1));
		*flags |= 1 << c;
		break;
	case CLUSTER_OPT_NODE_MASK:
		if (*flags & (1 << c)) {
			xtables_error(PARAMETER_PROBLEM,
				      "Can only specify "
				      "`--cluster-local-node' once");
		}
		if (*flags & (1 << CLUSTER_OPT_LOCAL_NODE)) {
			xtables_error(PARAMETER_PROBLEM, "You cannot use "
				      "`--cluster-local-nodemask' and "
				      "`--cluster-local-node'");
		}
		xtables_check_inverse(optarg, &invert, &optind, 0, argv);

		if (!xtables_strtoui(optarg, NULL, &num, 1,
				     XT_CLUSTER_NODES_MAX)) {
			xtables_error(PARAMETER_PROBLEM,
				      "Unable to parse `%s' in "
				      "`--cluster-local-node'", optarg);
		}
		if (invert)
			info->flags |= (1 << XT_CLUSTER_F_INV);

		info->node_mask = node_mask = num;
		*flags |= 1 << c;
		break;

	case CLUSTER_OPT_HASH_SEED:
		if (*flags & (1 << c)) {
			xtables_error(PARAMETER_PROBLEM,
				      "Can only specify "
				      "`--cluster-hash-seed' once");
		}
		if (!xtables_strtoui(optarg, NULL, &num, 0, UINT32_MAX)) {
			xtables_error(PARAMETER_PROBLEM,
				      "Unable to parse `%s'", optarg);
		}
		info->hash_seed = num;
		*flags |= 1 << c;
		break;
	default:
		return 0;
	}

	return 1;
}

static void
cluster_check(unsigned int flags)
{
	if ((flags & ((1 << CLUSTER_OPT_TOTAL_NODES) |
		      (1 << CLUSTER_OPT_LOCAL_NODE) |
		      (1 << CLUSTER_OPT_HASH_SEED)))
		== ((1 << CLUSTER_OPT_TOTAL_NODES) |
		    (1 << CLUSTER_OPT_LOCAL_NODE) |
		    (1 << CLUSTER_OPT_HASH_SEED))) {
		if (node_mask >= (1ULL << total_nodes)) {
			xtables_error(PARAMETER_PROBLEM,
				      "cluster match: "
				      "`--cluster-local-node' "
				      "must be <= `--cluster-total-nodes'");
		}
		return;
	}
	if ((flags & ((1 << CLUSTER_OPT_TOTAL_NODES) |
		      (1 << CLUSTER_OPT_NODE_MASK) |
		      (1 << CLUSTER_OPT_HASH_SEED)))
		== ((1 << CLUSTER_OPT_TOTAL_NODES) |
		    (1 << CLUSTER_OPT_NODE_MASK) |
		    (1 << CLUSTER_OPT_HASH_SEED))) {
		if (node_mask >= (1ULL << total_nodes)) {
			xtables_error(PARAMETER_PROBLEM,
				      "cluster match: "
				      "`--cluster-local-nodemask' too big "
				      "for `--cluster-total-nodes'");
		}
		return;
	}
	if (!(flags & (1 << CLUSTER_OPT_TOTAL_NODES))) {
		xtables_error(PARAMETER_PROBLEM,
			      "cluster match: `--cluster-total-nodes' "
			      "is missing");
	}
	if (!(flags & (1 << CLUSTER_OPT_HASH_SEED))) {
		xtables_error(PARAMETER_PROBLEM,
			      "cluster match: `--cluster-hash-seed' "
			      "is missing");
	}
	if (!(flags & ((1 << (CLUSTER_OPT_LOCAL_NODE) |
		       (1 << (CLUSTER_OPT_NODE_MASK)))))) {
		xtables_error(PARAMETER_PROBLEM,
			      "cluster match: `--cluster-local-node' or"
			      "`--cluster-local-nodemask' is missing");
	}
}

static void
cluster_print(const void *ip, const struct xt_entry_match *match, int numeric)
{
	const struct xt_cluster_match_info *info = (void *)match->data;

	printf("cluster ");
	if (info->flags & XT_CLUSTER_F_INV)
		printf("!node_mask=0x%08x ", info->node_mask);
	else
		printf("node_mask=0x%08x ", info->node_mask);

	printf("total_nodes=%u hash_seed=0x%08x ", 
		info->total_nodes, info->hash_seed);
}

static void
cluster_save(const void *ip, const struct xt_entry_match *match)
{
	const struct xt_cluster_match_info *info = (void *)match->data;

	if (info->flags & XT_CLUSTER_F_INV)
		printf("! --cluster-local-nodemask 0x%08x ", info->node_mask);
	else
		printf("--cluster-local-nodemask 0x%08x ", info->node_mask);

	printf("--cluster-total-nodes %u --cluster-hash-seed 0x%08x ",
		info->total_nodes, info->hash_seed);
}

static struct xtables_match cluster_mt_reg = {
	.family		= NFPROTO_UNSPEC,
	.name		= "cluster",
	.version	= XTABLES_VERSION,
	.size		= XT_ALIGN(sizeof(struct xt_cluster_match_info)),
	.userspacesize  = XT_ALIGN(sizeof(struct xt_cluster_match_info)),
 	.help		= cluster_help,
	.parse		= cluster_parse,
	.final_check	= cluster_check,
	.print		= cluster_print,
	.save		= cluster_save,
	.extra_opts	= cluster_opts,
};

void libxt_cluster_init(void)
{
	xtables_register_match(&cluster_mt_reg);
}
