#!/usr/bin/python
# Author: Rodrigo Zechin Rosauro
"""
This script will convert all _init() functions on iptables extension sources in order
to compile then on Android.
This will for example, convert "void _init(void)" on libipt_LOG.c to "void libipt_LOG_init(void)".
This is necessary because we cannot use the "-D_INIT=$*_init" on LOCAL_CFLAGS due to the way NDK works.
"""

import sys, os, glob, re

def main():
    regex=re.compile(r"[^\w]_init\s*\(")
    os.chdir(os.path.dirname(os.path.abspath(__file__)))
    for prefix in ("libxt", "libipt"):
        for src in glob.glob("%s_*.c"%prefix):
            name = src.replace(".c","")
            f=open(src, "r")
            data=f.read()
            f.close()
            if regex.search(data):
                print "Converting %s..."%src
                data=regex.sub(" %s_init("%name, data)
                f=open(src, "w")
                f.write(data)
                f.close()
    print "DONE!"

if __name__=="__main__":
    main()

