package dev.ukanth.ufirewall.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class UidListParserTest {

    @Test
    public void parsesAndSortsUids() {
        assertEquals(Arrays.asList(-10, 1000, 10123), UidListParser.parse("10123|-10|1000"));
    }

    @Test
    public void emptyAndNullGiveEmptyList() {
        assertTrue(UidListParser.parse(null).isEmpty());
        assertTrue(UidListParser.parse("").isEmpty());
        assertTrue(UidListParser.parse("||").isEmpty());
    }

    @Test
    public void skipsMalformedEntries() {
        // one bad token (e.g. from an old import) must not break the whole list
        assertEquals(Arrays.asList(1000, 10123),
                UidListParser.parse("10123|com.example.app|1000|12abc| |99999999999"));
    }

    @Test
    public void trimsWhitespace() {
        assertEquals(Arrays.asList(1000, 10123), UidListParser.parse(" 10123 | 1000"));
    }

    @Test
    public void mergeKeepsUidsThatAreNotShown() {
        // the emulator case: work profile Chrome (1010145) and an app without INTERNET (10184)
        // are not in the app list; saving must not drop them
        String saved = "-11|1000|10145|1010145|10184|10113";
        List<Integer> shown = Arrays.asList(-11, 1000, 10145, 10113, 10200);
        List<Integer> selected = Arrays.asList(-11, 1000, 10145, 10113);
        assertEquals("-11|1000|10113|10145|10184|1010145", UidListParser.merge(saved, shown, selected));
    }

    @Test
    public void mergeAppliesTheShownApps() {
        // unchecking a shown app removes it, checking one adds it
        assertEquals("1000|10200", UidListParser.merge("1000|10145", Arrays.asList(10145, 10200), Arrays.asList(10200)));
    }

    @Test
    public void mergeWithNothingSaved() {
        assertEquals("10145", UidListParser.merge(null, Arrays.asList(10145), Arrays.asList(10145)));
        assertEquals("", UidListParser.merge("", Arrays.asList(10145), Collections.<Integer>emptyList()));
    }
}
