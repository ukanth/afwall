// IRootShellService.aidl
package dev.ukanth.ufirewall;

// Declare any non-default types here with import statements

interface IRootShellService {
    int getPid();
    int getUid();
    String readCmdline();
}