package dev.ukanth.ufirewall;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.widget.Toast;

import com.topjohnwu.superuser.ipc.RootService;

import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.util.G;

public class RootShellConnection implements ServiceConnection {

    private IRootShellService rootIPC;

    public RootShellConnection(){

    }
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.d(G.TAG, "daemon onServiceConnected");
        rootIPC = IRootShellService.Stub.asInterface(service);
        //testDaemon();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.d(G.TAG, "daemon onServiceDisconnected");
        rootIPC = null;
    }

    private void testDaemon() {
        try {
            Log.i(G.TAG, rootIPC.getUid() + "");
            Log.i(G.TAG, rootIPC.getPid() + "");
            Log.i(G.TAG, rootIPC.readCmdline() + "");
        } catch (RemoteException e) {
            Log.e(G.TAG, "Remote error", e);
        }
    }
}