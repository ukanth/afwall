#include <stdio.h>
#include <xtables.h>
#include "xshared.h"

/*
 * Print out any special helps. A user might like to be able to add a --help
 * to the commandline, and see expected results. So we call help for all
 * specified matches and targets.
 */
void print_extension_helps(const struct xtables_target *t,
    const struct xtables_rule_match *m)
{
	for (; t != NULL; t = t->next) {
		if (t->used) {
			printf("\n");
			if (t->help == NULL)
				printf("%s does not take any options\n",
				       t->name);
			else
				t->help();
		}
	}
	for (; m != NULL; m = m->next) {
		printf("\n");
		if (m->match->help == NULL)
			printf("%s does not take any options\n",
			       m->match->name);
		else
			m->match->help();
	}
}
