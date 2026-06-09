package com.siruoren.jobpromotion;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class AuditLogEntryTest {

    @Test
    public void testConstructorAndGetters() {
        List<String> paths = Arrays.asList("job1", "job2", "job3");
        AuditLogEntry entry = new AuditLogEntry(
                1700000000000L, "admin", "PROMOTE", "source-jenkins",
                paths, true, 2, 1, 0, "deliverer", "promoter"
        );
        assertEquals(1700000000000L, entry.getTimestamp());
        assertEquals("admin", entry.getUsername());
        assertEquals("PROMOTE", entry.getAction());
        assertEquals("source-jenkins", entry.getSourceInstance());
        assertEquals(paths, entry.getJobPaths());
        assertTrue(entry.isForceUpdate());
        assertEquals(2, entry.getSuccessCount());
        assertEquals(1, entry.getFailureCount());
        assertEquals(0, entry.getSkippedCount());
        assertEquals("deliverer", entry.getDeliveredBy());
        assertEquals("promoter", entry.getPromotedBy());
    }

    @Test
    public void testDefaultConstructor() {
        AuditLogEntry entry = new AuditLogEntry();
        assertEquals(0, entry.getTimestamp());
        assertNull(entry.getUsername());
        assertNull(entry.getAction());
    }

    @Test
    public void testSetters() {
        AuditLogEntry entry = new AuditLogEntry();
        entry.setTimestamp(1700000000000L);
        entry.setUsername("user1");
        entry.setAction("PROMOTE");
        entry.setSourceInstance("source");
        entry.setForceUpdate(false);
        entry.setSuccessCount(1);
        entry.setFailureCount(0);
        entry.setSkippedCount(0);
        entry.setDeliveredBy("deliverer");
        entry.setPromotedBy("promoter");

        assertEquals(1700000000000L, entry.getTimestamp());
        assertEquals("user1", entry.getUsername());
        assertEquals("PROMOTE", entry.getAction());
        assertEquals("source", entry.getSourceInstance());
        assertFalse(entry.isForceUpdate());
        assertEquals(1, entry.getSuccessCount());
        assertEquals("deliverer", entry.getDeliveredBy());
        assertEquals("promoter", entry.getPromotedBy());
    }

    @Test
    public void testFormattedTimestamp() {
        AuditLogEntry entry = new AuditLogEntry();
        entry.setTimestamp(1700000000000L);
        String formatted = entry.getFormattedTimestamp();
        assertNotNull(formatted);
        assertFalse(formatted.isEmpty());
    }

    @Test
    public void testJobPathsSummaryFewJobs() {
        List<String> paths = Arrays.asList("job1", "job2");
        AuditLogEntry entry = new AuditLogEntry(0, "admin", "PROMOTE", "", paths, false, 0, 0, 0, "", "");
        assertEquals("job1, job2", entry.getJobPathsSummary());
    }

    @Test
    public void testJobPathsSummaryManyJobs() {
        List<String> paths = Arrays.asList("job1", "job2", "job3", "job4", "job5");
        AuditLogEntry entry = new AuditLogEntry(0, "admin", "PROMOTE", "", paths, false, 0, 0, 0, "", "");
        assertEquals("job1, job2 ... (5 jobs)", entry.getJobPathsSummary());
    }

    @Test
    public void testJobPathsSummaryEmpty() {
        AuditLogEntry entry = new AuditLogEntry(0, "admin", "PROMOTE", "", null, false, 0, 0, 0, "", "");
        assertEquals("", entry.getJobPathsSummary());
    }

    @Test
    public void testToJson() {
        List<String> paths = Arrays.asList("job1", "job2");
        AuditLogEntry entry = new AuditLogEntry(1700000000000L, "admin", "PROMOTE", "source", paths, true, 2, 0, 0, "deliverer", "promoter");
        net.sf.json.JSONObject json = entry.toJson();
        assertEquals(1700000000000L, json.getLong("timestamp"));
        assertEquals("admin", json.getString("username"));
        assertEquals("PROMOTE", json.getString("action"));
        assertEquals("source", json.getString("sourceInstance"));
        assertTrue(json.getBoolean("forceUpdate"));
        assertEquals(2, json.getInt("successCount"));
        assertEquals(0, json.getInt("failureCount"));
        assertEquals(2, json.getJSONArray("jobPaths").size());
        assertEquals("deliverer", json.getString("deliveredBy"));
        assertEquals("promoter", json.getString("promotedBy"));
    }

    @Test
    public void testToJsonArray() {
        AuditLogEntry entry1 = new AuditLogEntry(1700000000000L, "admin", "PROMOTE", "", Collections.emptyList(), false, 0, 0, 0, "", "");
        AuditLogEntry entry2 = new AuditLogEntry(1700000001000L, "user", "PROMOTE", "", Collections.emptyList(), false, 0, 0, 0, "", "");
        net.sf.json.JSONArray arr = AuditLogEntry.toJsonArray(Arrays.asList(entry1, entry2));
        assertEquals(2, arr.size());
    }

    @Test
    public void testFromJsonWithDeliveredByPromotedBy() {
        net.sf.json.JSONObject json = new net.sf.json.JSONObject();
        json.put("timestamp", 1700000000000L);
        json.put("username", "admin");
        json.put("action", "PROMOTE");
        json.put("sourceInstance", "source");
        json.put("forceUpdate", true);
        json.put("successCount", 2);
        json.put("failureCount", 0);
        json.put("skippedCount", 1);
        json.put("deliveredBy", "deliverer");
        json.put("promotedBy", "promoter");
        json.put("jobPaths", new net.sf.json.JSONArray());

        AuditLogEntry entry = AuditLogEntry.fromJson(json);
        assertEquals("deliverer", entry.getDeliveredBy());
        assertEquals("promoter", entry.getPromotedBy());
    }

    @Test
    public void testFromJsonBackwardCompatibility() {
        // Old data without deliveredBy/promotedBy should default to empty
        net.sf.json.JSONObject json = new net.sf.json.JSONObject();
        json.put("timestamp", 1700000000000L);
        json.put("username", "admin");
        json.put("action", "PROMOTE");
        json.put("sourceInstance", "source");
        json.put("forceUpdate", true);
        json.put("successCount", 2);
        json.put("failureCount", 0);
        json.put("skippedCount", 1);
        json.put("jobPaths", new net.sf.json.JSONArray());

        AuditLogEntry entry = AuditLogEntry.fromJson(json);
        assertEquals("", entry.getDeliveredBy());
        assertEquals("", entry.getPromotedBy());
    }
}
