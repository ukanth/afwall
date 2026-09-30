package dev.ukanth.ufirewall.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ApkInfoTest {

    @Test
    public void parsesLineWithoutPath() {
        ApkInfo.Line l = ApkInfo.parse("package:com.android.systemui.auto_generated_rro_vendor__ uid:10086,1010086,1110086");
        assertEquals("com.android.systemui.auto_generated_rro_vendor__", l.packageName);
        assertNull(l.apkPath);
        assertEquals(10086, l.uid);
    }

    @Test
    public void parsesLineWithPath() {
        ApkInfo.Line l = ApkInfo.parse("package:/product/app/YouTubeMusicPrebuilt/YouTubeMusicPrebuilt.apk=com.google.android.apps.youtube.music uid:1010158");
        assertEquals("com.google.android.apps.youtube.music", l.packageName);
        assertEquals("/product/app/YouTubeMusicPrebuilt/YouTubeMusicPrebuilt.apk", l.apkPath);
        assertEquals(1010158, l.uid);
    }

    @Test
    public void pathMayContainEqualsAndAt() {
        ApkInfo.Line l = ApkInfo.parse("package:/data/app/~~a1b2==/com.example-x1y2==/base.apk=com.example uid:10250");
        assertEquals("com.example", l.packageName);
        assertEquals("/data/app/~~a1b2==/com.example-x1y2==/base.apk", l.apkPath);
        l = ApkInfo.parse("package:/apex/com.android.rkpd/priv-app/rkpdapp.google@350820300/rkpdapp.google.apk=com.google.android.rkpdapp uid:10197,1010197");
        assertEquals("com.google.android.rkpdapp", l.packageName);
        assertEquals(10197, l.uid);
    }

    @Test
    public void rejectsOtherLines() {
        assertNull(ApkInfo.parse(null));
        assertNull(ApkInfo.parse(""));
        assertNull(ApkInfo.parse("Error: something"));
        assertNull(ApkInfo.parse("package:com.example"));
        assertNull(ApkInfo.parse("package:com.example uid:abc"));
    }
}
