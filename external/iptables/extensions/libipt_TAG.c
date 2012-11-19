#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <xtables.h>

static void TAG_help(void) {
   printf("TAG target options: none\n");
}

static void TAG_print(const void *ip, const struct xt_entry_target *target,
                      int numeric) {
   printf("TAGGING ");
}

static struct xtables_target tag_tg_reg = {
   .name          = "TAG",
   .version       = XTABLES_VERSION,
   .family        = NFPROTO_IPV4,
   .size          = XT_ALIGN(0),
   .userspacesize = XT_ALIGN(0),
   .help          = TAG_help,
   .print         = TAG_print,
};

void libipt_TAG_init(void) {
   xtables_register_target(&tag_tg_reg);
}

