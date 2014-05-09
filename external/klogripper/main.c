/*
 * Copyright (C) 2010 Sebastian Pipping <sebastian@pipping.org>
 * Licensed under GPL v2 or later
 */

#define PACKAGE_VERSION  "2010.12.15.1"

#include <sys/klog.h>  /* for klogctl() */
#include <stdlib.h>  /* for NULL */
#include <stdio.h>  /* for fprintf() */
#include <assert.h>  /* for assert() */
#include <unistd.h>  /* sleep() */
#include <string.h>  /* strcmp() */
#include <stdarg.h>  /* for var args ... */
#include <errno.h>   /* errno */


#define SYSLOG_ACTION_READ           2
#define SYSLOG_ACTION_READ_ALL       3
#define SYSLOG_ACTION_SIZE_BUFFER   10

#define MIN(a, b)  (((a) < (b)) ? (a) : (b))


static void die(const char *fmt, ...)
{
	va_list ap;

	va_start(ap, fmt);
	vprintf(fmt, ap);
	va_end(ap);
	exit(1);
}

void usage() {
	fprintf(stdout, "USAGE:\n"
		"  klogripper [--skip-first]\n"
		"  klogripper --help\n"
		"  klogripper --version\n"
	);
}


int main(int argc, char ** argv) {
	int bytes_total;
	int bytes_read;

	char * buffer = NULL;
	int skip = 0;

	if (argc > 2) {
		usage();
		return 1;
	} else if (argc == 2) {
		if (! strcmp(argv[1], "--version")) {
			fprintf(stdout, PACKAGE_VERSION "\n");
			return 0;
		} else if (! strcmp(argv[1], "--help")) {
			usage();
			return 0;
		} else if (! strcmp(argv[1], "--skip-first")) {
 			//fprintf(stdout, "%s%d%s", "PID=", getpid(),"\n");
  			skip = 1;
		}
                else {
			usage();
			return 1;
		}
	}

	/* Read entire kernel log ring buffer */
	bytes_total = klogctl(SYSLOG_ACTION_SIZE_BUFFER, NULL, 0);
	if (bytes_total <= 0)
		die("SYSLOG_ACTION_SIZE_BUFFER returned %d\n", errno);
	assert(bytes_total >= 0);

	buffer = calloc(1, bytes_total + 1);
	if (!buffer)
		return 2;
	if (!skip) {
		bytes_read = klogctl(SYSLOG_ACTION_READ_ALL, buffer, bytes_total);
		if (bytes_read < 0)
			die("SYSLOG_ACTION_READ_ALL returned %d\n", errno);
	        printf("%s", buffer);
	}

	for (;;) {
		bytes_read = klogctl(SYSLOG_ACTION_READ, buffer, bytes_total);
		if (bytes_read < 0)
			die("SYSLOG_ACTION_READ returned %d\n", errno);
		buffer[bytes_read] = '\0';
		if(strstr(buffer, "AFL")) {
		  printf("PID:%d## %s",getpid(), buffer);
		}
	}

	return 0;
}
