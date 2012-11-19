#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <libgen.h>

int ip6tables_main(int argc, char **argv);
int ip6tables_save_main(int argc, char **argv);
int ip6tables_restore_main(int argc, char **argv);

int main(int argc, char **argv)
{
	char *progname;

	if (argc < 1) {
		fprintf(stderr, "ERROR: This should not happen.\n");
		exit(EXIT_FAILURE);
	}

	progname = basename(argv[0]);
	if (strcmp(progname, "ip6tables") == 0)
		return ip6tables_main(argc, argv);
	if (strcmp(progname, "ip6tables-save") == 0)
		return ip6tables_save_main(argc, argv);
	if (strcmp(progname, "ip6tables-restore") == 0)
		return ip6tables_restore_main(argc, argv);

	++argv;
	--argc;
	if (argc < 1) {
		fprintf(stderr, "ERROR: No subcommand given.\n");
		exit(EXIT_FAILURE);
	}

	progname = basename(argv[0]);
	if (strcmp(progname, "main") == 0)
		return ip6tables_main(argc, argv);
	if (strcmp(progname, "save") == 0)
		return ip6tables_save_main(argc, argv);
	if (strcmp(progname, "restore") == 0)
		return ip6tables_restore_main(argc, argv);

	fprintf(stderr, "ip6tables multi-purpose version: "
	        "unknown subcommand \"%s\"\n", progname);
	exit(EXIT_FAILURE);
}
