#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <libgen.h>

int iptables_main(int argc, char **argv);
int iptables_save_main(int argc, char **argv);
int iptables_restore_main(int argc, char **argv);
int iptables_xml_main(int argc, char **argv);

int main(int argc, char **argv)
{
	char *progname;

	if (argc < 1) {
		fprintf(stderr, "ERROR: This should not happen.\n");
		exit(EXIT_FAILURE);
	}

	progname = basename(argv[0]);
	if (strcmp(progname, "iptables") == 0)
		return iptables_main(argc, argv);
	if (strcmp(progname, "iptables-save") == 0)
		return iptables_save_main(argc, argv);
	if (strcmp(progname, "iptables-restore") == 0)
		return iptables_restore_main(argc, argv);
	if (strcmp(progname, "iptables-xml") == 0)
		return iptables_xml_main(argc, argv);

	++argv;
	--argc;
	if (argc < 1) {
		fprintf(stderr, "ERROR: No subcommand given.\n");
		exit(EXIT_FAILURE);
	}

	progname = basename(argv[0]);
	if (strcmp(progname, "main") == 0)
		return iptables_main(argc, argv);
	if (strcmp(progname, "save") == 0)
		return iptables_save_main(argc, argv);
	if (strcmp(progname, "restore") == 0)
		return iptables_restore_main(argc, argv);
	if (strcmp(progname, "xml") == 0)
		return iptables_xml_main(argc, argv);

	fprintf(stderr, "iptables multi-purpose version: "
	        "unknown subcommand \"%s\"\n", progname);
	exit(EXIT_FAILURE);
}
