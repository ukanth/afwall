/*
 * Shared library add-on to iptables to add early socket matching support.
 *
 * Copyright (C) 2007 BalaBit IT Ltd.
 */
#include <xtables.h>

static struct xtables_match socket_mt_reg = {
	.name	       = "socket",
	.version       = XTABLES_VERSION,
	.family	       = NFPROTO_IPV4,
	.size	       = XT_ALIGN(0),
	.userspacesize = XT_ALIGN(0),
};

void libxt_socket_init(void)
{
	xtables_register_match(&socket_mt_reg);
}
