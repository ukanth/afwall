package dev.ukanth.ufirewall.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RootFilesTest {

    @Test
    public void bootScriptDirectoriesAreSafe() {
        for (String path : new String[]{"/data/adb/service.d/", "/data/adb/post-fs-data.d/",
                "/sbin/.core/img/.core/service.d/", "/system/etc/init.d/", "/su/su.d/"}) {
            assertTrue(path, RootFiles.isSafePath(path));
        }
    }

    @Test
    public void shellSyntaxAndRelativePathsAreRefused() {
        for (String path : new String[]{null, "", "data/adb", "/data/'; id; '", "/data/$(id)", "/data/`id`",
                "/data/a b", "/data/a\nid", "/data/a;id", "/data/a|id", "/data/a&id"}) {
            assertFalse(String.valueOf(path), RootFiles.isSafePath(path));
        }
    }
}
