package com.siruoren.jobpromotion;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class PromotionServiceTest {

    private PromotionService service;

    @Before
    public void setUp() {
        service = new PromotionService();
    }

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

        String cleaned = service.cleanConfigXml(configXml);
        assertNotNull(cleaned);
        // triggers tag content should be cleared
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

        String cleaned = service.cleanConfigXml(configXml);
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

        String cleaned = service.cleanConfigXml(configXml);
        assertNotNull(cleaned);
        assertTrue(cleaned.contains("echo hello"));
        assertTrue(cleaned.contains("Test Job"));
    }

    @Test
    public void testCleanConfigXmlHandlesInvalidXml() {
        String invalidXml = "this is not xml at all <><><";
        String cleaned = service.cleanConfigXml(invalidXml);
        // Should return the original (or cleaned garbled chars) without throwing
        assertNotNull(cleaned);
    }

    @Test
    public void testCleanConfigXmlCleansGarbledChars() {
        // Test that common garbled characters are cleaned
        String configXml = "<project><description>Test\u0000Job</description></project>";
        String cleaned = service.cleanConfigXml(configXml);
        assertNotNull(cleaned);
        assertFalse("Null character should be removed", cleaned.contains("\0"));
    }

    @Test
    public void testCleanConfigXmlEmptyInput() {
        String cleaned = service.cleanConfigXml("");
        assertNotNull(cleaned);
    }

    @Test
    public void testFetchRemoteJobsWithoutConfigThrows() {
        // Without Jenkins running, getInstanceByName should fail or return null
        try {
            service.fetchRemoteJobs("", "nonexistent-instance");
            fail("Should throw exception when no source instance configured");
        } catch (Exception e) {
            // Expected - either IllegalStateException or NullPointerException
            assertTrue(e instanceof IllegalStateException || e instanceof NullPointerException);
        }
    }
}
