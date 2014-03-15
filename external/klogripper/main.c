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


#define SYSLOG_ACTION_READ_ALL       3
#define SYSLOG_ACTION_SIZE_BUFFER   10

#define MIN(a, b)  (((a) < (b)) ? (a) : (b))


/**
 * Calculates the overlap length of two strings.
 * For instance, the overlap length of (a)"1234" and (b)"34567" is 2
 * as "34" is the longest substring that (a) ends with and (b) starts with.
 */
int overlap_len(const char * former, const char * latter, size_t former_len, size_t latter_len) {
	int candidate = MIN(former_len, latter_len);
	int pos = 0;

	assert(former);
	assert(latter);

	for (; candidate > 0; candidate--) {
		for (; pos < candidate; pos++) {
			if (former[former_len - candidate + pos] != latter[pos]) {
				break;
			}
		}
		if (pos == candidate) {
			return candidate;
		}
	}

	return 0;
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
	int prev_bytes_read = 0;

	char * buffer = NULL;
	char * prev_buffer = NULL;
	char * print_start = NULL;

	unsigned int seconds_to_sleep = 0;
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
			skip = 1;
		} else {
			usage();
			return 1;
		}
	}

	for (;;) {
		/* Read entire kernel log ring buffer */
		bytes_total = klogctl(SYSLOG_ACTION_SIZE_BUFFER, NULL, 0);
		assert(bytes_total >= 0);

		buffer = malloc(bytes_total + 1);
		bytes_read = klogctl(SYSLOG_ACTION_READ_ALL, buffer, bytes_total);
		assert((bytes_read >= 0) && (bytes_read <= bytes_total));
		buffer[bytes_read] = '\0';

		/* Detect overlap with previous run */
		if (prev_buffer) {
			const int overlap = overlap_len(prev_buffer, buffer, prev_bytes_read, bytes_read);
			if (! overlap) {
				fprintf(stderr, "WARNING: "
					"Two consecutive calls to syslog(SYSLOG_ACTION_READ_ALL, ...) "
						"returned content with no overlap. "
					"This may indicate that the kernel is producing messages faster "
					"than we can process, which would mean that we have missed some "
					"messages.\n");
				seconds_to_sleep = 1;
			} else {
				const float percentage = overlap * 100.0f / bytes_total;
				seconds_to_sleep = (percentage > 70) ? 3 : 1;
			}
			print_start = buffer + overlap;
		} else {
			print_start = buffer;
			seconds_to_sleep = 1;
		}

		/* Print whatever is new to us */
		if (skip) {
			skip = 0;
		} else {
			fprintf(stdout, "%s", print_start);
		}

		free(prev_buffer);
		prev_buffer = buffer;
		prev_bytes_read = bytes_read;

		sleep(seconds_to_sleep);
	}

	return 0;
}
