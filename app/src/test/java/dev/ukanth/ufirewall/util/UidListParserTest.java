package dev.ukanth.ufirewall.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

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
}
