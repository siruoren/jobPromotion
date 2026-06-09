package com.siruoren.jobpromotion;

import com.siruoren.jobpromotion.util.XmlUtil;
import org.junit.Test;

import static org.junit.Assert.*;

public class PromotionServiceTest {

    @Test
    public void testCleanConfigXmlRemovesTriggers() {
        String configXml = "<project>"
                + "<description>Test Job</description>"
                + "<triggers>"
                + "  <hudson.triggers.TimerTrigger>"
                + "    <spec>H H * * *</spec>"
                + "  </hudson.triggers.TimerTrigger>"
                + "  <hudson.triggers.SCMTrigger>"
                + "    <spec>H/5 * * * *</spec>"
                + "  </hudson.triggers.SCMTrigger>"
                + "</triggers>"
                + "<builders/>"
                + "</project>";

        String cleaned = XmlUtil.cleanJobConfigXml(configXml);
        assertNotNull(cleaned);
        assertTrue(cleaned.contains("<triggers/>") || cleaned.contains("<triggers />")
                || !cleaned.contains("TimerTrigger") && !cleaned.contains("SCMTrigger"));
    }

    @Test
    public void testCleanConfigXmlRemovesMonitorTags() {
        String configXml = "<project>"
                + "<description>Test Job</description>"
                + "<builders/>"
                + "<hudson.plugins.buildmonitor.BuildMonitor>"
                + "  <name>monitor1</name>"
                + "</hudson.plugins.buildmonitor.BuildMonitor>"
                + "</project>";

        String cleaned = XmlUtil.cleanJobConfigXml(configXml);
        assertNotNull(cleaned);
        assertFalse("BuildMonitor tag should be removed", cleaned.contains("BuildMonitor"));
    }

    @Test
    public void testCleanConfigXmlPreservesNormalContent() {
        String configXml = "<project>"
                + "<description>Test Job</description>"
                + "<builders>"
                + "  <hudson.tasks.Shell>"
                + "    <command>echo hello</command>"
                + "  </hudson.tasks.Shell>"
                + "</builders>"
                + "</project>";

        String cleaned = XmlUtil.cleanJobConfigXml(configXml);
        assertNotNull(cleaned);
        assertTrue(cleaned.contains("echo hello"));
        assertTrue(cleaned.contains("Test Job"));
    }

    @Test
    public void testCleanConfigXmlHandlesInvalidXml() {
        String invalidXml = "this is not xml at all <><><";
        String cleaned = XmlUtil.cleanJobConfigXml(invalidXml);
        assertNotNull(cleaned);
    }

    @Test
    public void testCleanConfigXmlCleansGarbledChars() {
        String configXml = "<project><description>Test\u0000Job</description></project>";
        String cleaned = XmlUtil.cleanJobConfigXml(configXml);
        assertNotNull(cleaned);
        assertFalse("Null character should be removed", cleaned.contains("\0"));
    }

    @Test
    public void testCleanConfigXmlEmptyInput() {
        String cleaned = XmlUtil.cleanJobConfigXml("");
        assertNotNull(cleaned);
    }

    @Test
    public void testCleanFolderConfigXmlPreservesContent() {
        String configXml = "<com.cloudbees.hudson.plugins.folder.Folder>"
                + "<description>My Folder</description>"
                + "<properties/>"
                + "</com.cloudbees.hudson.plugins.folder.Folder>";

        String cleaned = XmlUtil.cleanFolderConfigXml(configXml);
        assertNotNull(cleaned);
        assertTrue(cleaned.contains("My Folder"));
    }

    @Test
    public void testCleanFolderConfigXmlRemovesGarbledChars() {
        String configXml = "<folder><description>Test\u0001Folder</description></folder>";
        String cleaned = XmlUtil.cleanFolderConfigXml(configXml);
        assertNotNull(cleaned);
        assertFalse("Control character should be removed", cleaned.contains("\u0001"));
    }

    @Test
    public void testCleanGarbledCharsNull() {
        String result = XmlUtil.cleanGarbledChars(null);
        assertEquals("", result);
    }

    @Test
    public void testCleanJobConfigXmlRemovesBuildDiscarder() {
        String configXml = "<project>"
                + "<description>Test</description>"
                + "<buildDiscarder>"
                + "  <strategy class=\"hudson.tasks.LogRotator\">"
                + "    <daysToKeep>30</daysToKeep>"
                + "  </strategy>"
                + "</buildDiscarder>"
                + "<builders/>"
                + "</project>";

        String cleaned = XmlUtil.cleanJobConfigXml(configXml);
        assertNotNull(cleaned);
        assertFalse("LogRotator should be removed from buildDiscarder", cleaned.contains("LogRotator"));
    }

    @Test
    public void testFetchRemoteJobsWithoutConfigThrows() {
        PromotionService service = new PromotionService();
        try {
            service.fetchRemoteJobs("", "nonexistent-instance");
            fail("Should throw exception when no source instance configured");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalStateException || e instanceof NullPointerException);
        }
    }
}
