package dev.ukanth.ufirewall.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AppLanguageTest {

    @Test
    public void systemLanguageIsEmptyTag() {
        assertEquals("", AppLanguage.toTag("sys"));
        assertEquals("", AppLanguage.toTag(""));
        assertEquals("", AppLanguage.toTag(null));
    }

    @Test
    public void codesOfOlderVersionsBecomeLanguageTags() {
        // used to be "new Locale("pt_BR")": a language nobody translated, so English
        assertEquals("pt-BR", AppLanguage.toTag("pt_BR"));
        assertEquals("ast-ES", AppLanguage.toTag("ast_ES"));
        assertEquals("sr-CS", AppLanguage.toTag("sr_CS"));
        assertEquals("zh-CN", AppLanguage.toTag("zh_CN"));
        assertEquals("zh-TW", AppLanguage.toTag("zh_TW"));
        assertEquals("id", AppLanguage.toTag("in"));
        assertEquals("de", AppLanguage.toTag("de"));
        assertEquals("en", AppLanguage.toTag(" en "));
    }

    @Test
    public void tagsStayAsTheyAre() {
        assertEquals("pt-BR", AppLanguage.toTag("pt-BR"));
        assertEquals("id", AppLanguage.toTag("id"));
        // not Indonesian
        assertEquals("it", AppLanguage.toTag("it"));
    }
}
