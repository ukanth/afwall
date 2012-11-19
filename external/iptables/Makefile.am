# -*- Makefile -*-

ACLOCAL_AMFLAGS  = -I m4
AUTOMAKE_OPTIONS = foreign subdir-objects

AM_CFLAGS        = ${regular_CFLAGS} -I${top_builddir}/include -I${top_srcdir}/include ${kinclude_CFLAGS}
SUBDIRS          = extensions
if ENABLE_DEVEL
SUBDIRS         += include
endif
if ENABLE_LIBIPQ
SUBDIRS         += libipq
endif
if HAVE_LIBNFNETLINK
SUBDIRS         += utils
endif
lib_LTLIBRARIES =

# libiptc
lib_LTLIBRARIES           += libiptc/libip4tc.la libiptc/libip6tc.la libiptc/libiptc.la
libiptc_libiptc_la_SOURCES  =
libiptc_libiptc_la_LIBADD   = libiptc/libip4tc.la libiptc/libip6tc.la
libiptc_libiptc_la_LDFLAGS  = -version-info 0:0:0 ${libiptc_LDFLAGS2}
libiptc_libip4tc_la_SOURCES = libiptc/libip4tc.c
libiptc_libip4tc_la_LDFLAGS = -version-info 0:0:0
libiptc_libip6tc_la_SOURCES = libiptc/libip6tc.c
libiptc_libip6tc_la_LDFLAGS = -version-info 0:0:0 ${libiptc_LDFLAGS2}

lib_LTLIBRARIES      += libxtables.la
libxtables_la_SOURCES = xtables.c
libxtables_la_LDFLAGS = -version-info ${libxtables_vcurrent}:0:${libxtables_vage}
if ENABLE_SHARED
libxtables_la_CFLAGS  = ${AM_CFLAGS}
libxtables_la_LIBADD  = -ldl
else
libxtables_la_CFLAGS  = ${AM_CFLAGS} -DNO_SHARED_LIBS=1
libxtables_la_LIBADD  =
endif

iptables_multi_SOURCES    = iptables-multi.c iptables-save.c \
                            iptables-restore.c iptables-xml.c \
                            iptables-standalone.c iptables.c xshared.c
iptables_multi_CFLAGS     = ${AM_CFLAGS} -DIPTABLES_MULTI
if ENABLE_STATIC
iptables_multi_CFLAGS    += -DALL_INCLUSIVE
endif
iptables_multi_LDFLAGS    = -rdynamic
iptables_multi_LDADD      = libiptc/libip4tc.la extensions/libext4.a libxtables.la -lm

ip6tables_multi_SOURCES   = ip6tables-multi.c ip6tables-save.c \
                            ip6tables-restore.c ip6tables-standalone.c \
                            ip6tables.c xshared.c
ip6tables_multi_CFLAGS    = ${AM_CFLAGS} -DIPTABLES_MULTI
if ENABLE_STATIC
ip6tables_multi_CFLAGS   += -DALL_INCLUSIVE
endif
ip6tables_multi_LDFLAGS   = -rdynamic
ip6tables_multi_LDADD     = libiptc/libip6tc.la extensions/libext6.a libxtables.la -lm

sbin_PROGRAMS    =
man_MANS         = iptables.8 iptables-restore.8 iptables-save.8 \
                   iptables-xml.8 ip6tables.8 ip6tables-restore.8 \
                   ip6tables-save.8
CLEANFILES       = iptables.8 ip6tables.8

if ENABLE_IPV4
sbin_PROGRAMS += iptables-multi
v4_bin_links   = iptables-xml
v4_sbin_links  = iptables iptables-restore iptables-save
endif
if ENABLE_IPV6
sbin_PROGRAMS += ip6tables-multi
v6_sbin_links  = ip6tables ip6tables-restore ip6tables-save
endif

iptables.8: ${srcdir}/iptables.8.in extensions/matches4.man extensions/targets4.man
	${AM_VERBOSE_GEN} sed -e 's/@PACKAGE_AND_VERSION@/${PACKAGE} ${PACKAGE_VERSION}/g' -e '/@MATCH@/ r extensions/matches4.man' -e '/@TARGET@/ r extensions/targets4.man' $< >$@;

ip6tables.8: ${srcdir}/ip6tables.8.in extensions/matches6.man extensions/targets6.man
	${AM_VERBOSE_GEN} sed -e 's/@PACKAGE_AND_VERSION@/${PACKAGE} ${PACKAGE_VERSION}/g' -e '/@MATCH@/ r extensions/matches6.man' -e '/@TARGET@/ r extensions/targets6.man' $< >$@;

pkgconfig_DATA = libiptc.pc xtables.pc

.PHONY: tarball
tarball:
	rm -Rf /tmp/${PACKAGE_TARNAME}-${PACKAGE_VERSION};
	pushd ${top_srcdir} && git archive --prefix=${PACKAGE_TARNAME}-${PACKAGE_VERSION}/ HEAD | tar -C /tmp -x && popd;
	pushd /tmp/${PACKAGE_TARNAME}-${PACKAGE_VERSION} && ./autogen.sh && popd;
	tar -C /tmp -cjf ${PACKAGE_TARNAME}-${PACKAGE_VERSION}.tar.bz2 --owner=root --group=root ${PACKAGE_TARNAME}-${PACKAGE_VERSION}/;
	rm -Rf /tmp/${PACKAGE_TARNAME}-${PACKAGE_VERSION};

config.status: extensions/GNUmakefile.in \
	include/xtables.h.in include/iptables/internal.h.in

# Using if..fi avoids an ugly "error (ignored)" message :)
install-exec-hook:
	-if test -z "${DESTDIR}"; then /sbin/ldconfig; fi;
	${INSTALL} -dm0755 "${DESTDIR}${bindir}";
	for i in ${v4_bin_links}; do ${LN_S} -f "${sbindir}/iptables-multi" "${DESTDIR}${bindir}/$$i"; done;
	for i in ${v4_sbin_links}; do ${LN_S} -f iptables-multi "${DESTDIR}${sbindir}/$$i"; done;
	for i in ${v6_sbin_links}; do ${LN_S} -f ip6tables-multi "${DESTDIR}${sbindir}/$$i"; done;
