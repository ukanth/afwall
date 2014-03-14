#include "platform.h"
#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <dirent.h>
#include <errno.h>

enum { COMMON_BUFSIZE = (BUFSIZ >= 256*sizeof(void*) ? BUFSIZ+1 : 256*sizeof(void*)) };
extern char bb_common_bufsiz1[COMMON_BUFSIZE];

typedef struct llist_t {
  struct llist_t *link;
  char *data;
} llist_t;
void llist_add_to(llist_t **old_head, void *data) FAST_FUNC;
void llist_add_to_end(llist_t **list_head, void *data) FAST_FUNC;
void *llist_pop(llist_t **elm) FAST_FUNC;
void llist_unlink(llist_t **head, llist_t *elm) FAST_FUNC;
void llist_free(llist_t *elm, void (*freeit)(void *data)) FAST_FUNC;
llist_t *llist_rev(llist_t *list) FAST_FUNC;
llist_t *llist_find_str(llist_t *first, const char *str) FAST_FUNC;

extern const char *opt_complementary;
extern uint32_t option_mask32;
extern uint32_t getopt32(char **argv, const char *applet_opts, ...) FAST_FUNC;

int xatoi_positive(const char *numstr) FAST_FUNC;

extern char *xmalloc_fgetline(FILE *file) FAST_FUNC RETURNS_MALLOC;

#define LONE_DASH(s)     ((s)[0] == '-' && !(s)[1])
#define NOT_LONE_DASH(s) ((s)[0] != '-' || (s)[1])

#undef FALSE
#define FALSE   ((int) 0)
#undef TRUE
#define TRUE    ((int) 1)
#undef SKIP
#define SKIP    ((int) 2)

enum {
  ACTION_RECURSE        = (1 << 0),
  ACTION_FOLLOWLINKS    = (1 << 1),
  ACTION_FOLLOWLINKS_L0 = (1 << 2),
  ACTION_DEPTHFIRST     = (1 << 3),
  /*ACTION_REVERSE      = (1 << 4), - unused */
  ACTION_QUIET          = (1 << 5),
  ACTION_DANGLING_OK    = (1 << 6),
};
typedef uint8_t recurse_flags_t;
extern int recursive_action(const char *fileName, unsigned flags,
    int FAST_FUNC (*fileAction)(const char *fileName, struct stat* statbuf, void* userData, int depth),
    int FAST_FUNC (*dirAction)(const char *fileName, struct stat* statbuf, void* userData, int depth),
    void* userData, unsigned depth) FAST_FUNC;

#define DOT_OR_DOTDOT(s) ((s)[0] == '.' && (!(s)[1] || ((s)[1] == '.' && !(s)[2])))

char *concat_path_file(const char *path, const char *filename) FAST_FUNC;
char *concat_subpath_file(const char *path, const char *filename) FAST_FUNC;

char *last_char_is(const char *s, int c) FAST_FUNC;

char *xasprintf(const char *format, ...) __attribute__ ((format(printf, 1, 2))) FAST_FUNC RETURNS_MALLOC;
