#define IF_FEATURE_GREP_CONTEXT(...) __VA_ARGS__
#define IF_FEATURE_GREP_EGREP_ALIAS(...)
#define IF_EXTRA_COMPAT(...) __VA_ARGS__

#define ENABLE_FEATURE_GREP_FGREP_ALIAS 0
#define ENABLE_FEATURE_GREP_EGREP_ALIAS 0

#define ENABLE_FEATURE_CLEAN_UP 1

#define OPTSTR_GREP \
	"lnqvscFiHhe:f:Lorm:wx" \
	IF_FEATURE_GREP_CONTEXT("A:B:C:") \
	IF_FEATURE_GREP_EGREP_ALIAS("E") \
	IF_EXTRA_COMPAT("z") \
	"aI"

#define grep_full_usage "\n\n" \
       "Search for PATTERN in FILEs (or stdin)\n" \
     "\n	-H	Add 'filename:' prefix" \
     "\n	-h	Do not add 'filename:' prefix" \
     "\n	-n	Add 'line_no:' prefix" \
     "\n	-l	Show only names of files that match" \
     "\n	-L	Show only names of files that don't match" \
     "\n	-c	Show only count of matching lines" \
     "\n	-o	Show only the matching part of line" \
     "\n	-q	Quiet. Return 0 if PATTERN is found, 1 otherwise" \
     "\n	-v	Select non-matching lines" \
     "\n	-s	Suppress open and read errors" \
     "\n	-r	Recurse" \
     "\n	-i	Ignore case" \
     "\n	-w	Match whole words only" \
     "\n	-x	Match whole lines only" \
     "\n	-F	PATTERN is a literal (not regexp)" \
	IF_FEATURE_GREP_EGREP_ALIAS( \
     "\n	-E	PATTERN is an extended regexp" \
	) \
	IF_EXTRA_COMPAT( \
     "\n	-z	Input is NUL terminated" \
	) \
     "\n	-m N	Match up to N times per file" \
	IF_FEATURE_GREP_CONTEXT( \
     "\n	-A N	Print N lines of trailing context" \
     "\n	-B N	Print N lines of leading context" \
     "\n	-C N	Same as '-A N -B N'" \
	) \
     "\n	-e PTRN	Pattern to match" \
     "\n	-f FILE	Read pattern from file"
