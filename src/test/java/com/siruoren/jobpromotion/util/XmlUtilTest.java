package com.siruoren.jobpromotion.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class XmlUtilTest {

    @Test
    public void testCleanGarbledCharsRemovesControlChars() {
        String input = "hello\u0000world\u0001test\u0008end";
        String result = XmlUtil.cleanGarbledChars(input);
        assertEquals("helloworldtestend", result);
    }

    @Test
    public void testCleanGarbledCharsPreservesNormalChars() {
        String input = "hello world 123";
        assertEquals(input, XmlUtil.cleanGarbledChars(input));
    }

    @Test
    public void testCleanGarbledCharsNull() {
        assertEquals("", XmlUtil.cleanGarbledChars(null));
    }

    @Test
    public void testCleanGarbledCharsEmpty() {
        assertEquals("", XmlUtil.cleanGarbledChars(""));
    }

    @Test
    public void testCleanGarbledCharsPreservesNewlineAndTab() {
        // \n (0x0A) and \r (0x0D) and \t (0x09) should be preserved
        String input = "line1\nline2\ttab\r\n";
        assertEquals(input, XmlUtil.cleanGarbledChars(input));
    }

    @Test
    public void testCleanJobConfigXmlRemovesTriggers() {
        String configXml = "<project>"
                + "<description>Test</description>"
                + "<triggers>"
                + "  <hudson.triggers.TimerTrigger>"
                + "    <spec>H H * * *</spec>"
                + "  </hudson.triggers.TimerTrigger>"
                + "</triggers>"
                + "<builders/>"
                + "</project>";

        String cleaned = XmlUtil.cleanJobConfigXml(configXml);
        assertNotNull(cleaned);
        assertFalse("TimerTrigger should be removed", cleaned.contains("TimerTrigger"));
    }

    @Test
    public void testCleanFolderConfigXmlOnlyCleansChars() {
        String configXml = "<folder><description>Test\u0002</description><triggers><timer/></triggers></folder>";
        String cleaned = XmlUtil.cleanFolderConfigXml(configXml);
        // Folder config should NOT remove triggers, only garbled chars
        assertNotNull(cleaned);
        assertFalse("Control char should be removed", cleaned.contains("\u0002"));
    }
}
