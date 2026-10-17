package dev.ukanth.ufirewall.plugin;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Collections;

public class TaskerActionTest {

    private static TaskerAction action(String action, String pkg, String net) {
        return new TaskerAction(action, pkg, net, null, null, "id-1", 1700000000000L, "net.dinglisch.android.taskerm");
    }

    private static KeyPair keyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        return gen.generateKeyPair();
    }

    private static byte[] sign(KeyPair pair, byte[] payload) throws Exception {
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(pair.getPrivate());
        s.update(payload);
        return s.sign();
    }

    @Test
    public void signedActionVerifies() throws Exception {
        KeyPair pair = keyPair();
        TaskerAction a = action(TaskerAction.ALLOW, "org.example.app", "wifi");
        byte[] sig = sign(pair, a.payload());
        assertTrue(ActionSigner.verify(Collections.singletonList(pair.getPublic().getEncoded()), a.payload(), sig));
    }

    @Test
    public void changedFieldFails() throws Exception {
        KeyPair pair = keyPair();
        byte[] sig = sign(pair, action(TaskerAction.BLOCK, "org.example.app", "wifi").payload());
        TaskerAction changed = action(TaskerAction.ALLOW, "org.example.app", "wifi");
        assertFalse(ActionSigner.verify(Collections.singletonList(pair.getPublic().getEncoded()), changed.payload(), sig));
        TaskerAction otherApp = action(TaskerAction.BLOCK, "org.example.other", "wifi");
        assertFalse(ActionSigner.verify(Collections.singletonList(pair.getPublic().getEncoded()), otherApp.payload(), sig));
    }

    @Test
    public void otherKeyFails() throws Exception {
        KeyPair mine = keyPair();
        KeyPair other = keyPair();
        TaskerAction a = action(TaskerAction.DISABLE, null, null);
        byte[] sig = sign(other, a.payload());
        assertFalse(ActionSigner.verify(Collections.singletonList(mine.getPublic().getEncoded()), a.payload(), sig));
        // any trusted key will do (older keys stay trusted after a new one is made)
        assertTrue(ActionSigner.verify(Arrays.asList(mine.getPublic().getEncoded(), other.getPublic().getEncoded()),
                a.payload(), sig));
    }

    @Test
    public void garbageFails() throws Exception {
        KeyPair pair = keyPair();
        TaskerAction a = action(TaskerAction.ENABLE, null, null);
        assertFalse(ActionSigner.verify(Collections.singletonList(pair.getPublic().getEncoded()), a.payload(), new byte[]{1, 2, 3}));
        assertFalse(ActionSigner.verify(Collections.singletonList(new byte[]{4, 5}), a.payload(), sign(pair, a.payload())));
        assertFalse(ActionSigner.verify(Collections.<byte[]>emptyList(), a.payload(), sign(pair, a.payload())));
    }

    @Test
    public void fieldsCantBeShifted() {
        // same characters, split differently between fields: must not give the same payload
        TaskerAction a = new TaskerAction(TaskerAction.PROFILE, null, null, "ab", "c", "id", 1, null);
        TaskerAction b = new TaskerAction(TaskerAction.PROFILE, null, null, "a", "bc", "id", 1, null);
        assertFalse(Arrays.equals(a.payload(), b.payload()));
        TaskerAction c = new TaskerAction(TaskerAction.PROFILE, null, null, "a\nprofileName=1:b", null, "id", 1, null);
        TaskerAction d = new TaskerAction(TaskerAction.PROFILE, null, null, "a", "b", "id", 1, null);
        assertFalse(Arrays.equals(c.payload(), d.payload()));
        // null and empty differ too
        TaskerAction e = new TaskerAction(TaskerAction.ENABLE, "", null, null, null, "id", 1, null);
        TaskerAction f = new TaskerAction(TaskerAction.ENABLE, null, null, null, null, "id", 1, null);
        assertFalse(Arrays.equals(e.payload(), f.payload()));
        assertArrayEquals(f.payload(), new TaskerAction(TaskerAction.ENABLE, null, null, null, null, "id", 1, null).payload());
    }

    @Test
    public void check() {
        assertNull(action(TaskerAction.ENABLE, null, null).check());
        assertNull(action(TaskerAction.ALLOW, "org.example.app", "all").check());
        assertNull(action(TaskerAction.ALLOW, "%app", "mobile").check());
        assertNotNull(action("rm -rf", null, null).check());
        assertNotNull(action(TaskerAction.ALLOW, null, "wifi").check());
        assertNotNull(action(TaskerAction.ALLOW, "not a package", "wifi").check());
        assertNotNull(action(TaskerAction.BLOCK, "org.example.app", "bluetooth").check());
        assertNotNull(new TaskerAction(TaskerAction.PROFILE, null, null, null, "x", "id", 1, null).check());
        assertNotNull(new TaskerAction(TaskerAction.ENABLE, null, null, null, null, null, 1, null).check());
    }

    @Test
    public void packageToRun() {
        TaskerAction fixed = action(TaskerAction.ALLOW, "org.example.app", "wifi");
        // a fixed package can't be replaced through the bundle's copy
        assertEquals("org.example.app", fixed.packageToRun("org.evil.app"));
        TaskerAction variable = action(TaskerAction.ALLOW, "%app", "wifi");
        assertEquals("org.example.other", variable.packageToRun("org.example.other"));
        assertNull(variable.packageToRun("%app")); // variable not set
        assertNull(variable.packageToRun("org.example;reboot"));
        assertNull(variable.packageToRun(null));
    }

    @Test
    public void loosens() {
        assertTrue(action(TaskerAction.DISABLE, null, null).loosens());
        assertTrue(action(TaskerAction.ALLOW, "org.example.app", "wifi").loosens());
        assertFalse(action(TaskerAction.BLOCK, "org.example.app", "wifi").loosens());
        assertFalse(action(TaskerAction.ENABLE, null, null).loosens());
    }
}
