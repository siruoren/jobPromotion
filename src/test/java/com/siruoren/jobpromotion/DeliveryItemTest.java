package com.siruoren.jobpromotion;

import org.junit.Test;

import static org.junit.Assert.*;

public class DeliveryItemTest {

    @Test
    public void testConstructorAndGetters() {
        DeliveryItem item = new DeliveryItem("my-job", "folder/my-job", false, "folder", "admin", "source");
        assertEquals("my-job", item.getJobName());
        assertEquals("folder/my-job", item.getJobFullPath());
        assertFalse(item.isFolder());
        assertEquals("folder", item.getFolderPath());
        assertEquals("admin", item.getDeliveredBy());
        assertEquals("source", item.getSourceInstance());
        assertEquals(DeliveryItem.Status.DELIVERED, item.getStatus());
    }

    @Test
    public void testFolderItem() {
        DeliveryItem item = new DeliveryItem("my-folder", "my-folder", true, "", "admin", "source");
        assertTrue(item.isFolder());
    }

    @Test
    public void testMarkCancelled() {
        DeliveryItem item = new DeliveryItem("job1", "job1", false, "", "admin", "source");
        item.markCancelled();
        assertEquals(DeliveryItem.Status.CANCELLED, item.getStatus());
    }

    @Test
    public void testMarkPromoted() {
        DeliveryItem item = new DeliveryItem("job1", "job1", false, "", "admin", "source");
        item.markPromoted("promoter");
        assertEquals(DeliveryItem.Status.PROMOTED, item.getStatus());
        assertEquals("promoter", item.getPromotedBy());
        assertTrue(item.getPromotedAt() > 0);
    }

    @Test
    public void testMarkExpired() {
        DeliveryItem item = new DeliveryItem("job1", "job1", false, "", "admin", "source");
        item.markExpired();
        assertEquals(DeliveryItem.Status.EXPIRED, item.getStatus());
    }

    @Test
    public void testIsExpiredWithin30Days() {
        DeliveryItem item = new DeliveryItem("job1", "job1", false, "", "admin", "source");
        // Just delivered, should not be expired
        assertFalse(item.isExpired());
    }

    @Test
    public void testIsExpiredAfter30Days() {
        DeliveryItem item = new DeliveryItem("job1", "job1", false, "", "admin", "source");
        // Set deliveredAt to 31 days ago
        item.setDeliveredAt(System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000);
        assertTrue(item.isExpired());
    }

    @Test
    public void testIsExpiredExactly30Days() {
        DeliveryItem item = new DeliveryItem("job1", "job1", false, "", "admin", "source");
        // Set deliveredAt to exactly 30 days ago
        item.setDeliveredAt(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000);
        assertFalse(item.isExpired());
    }

    @Test
    public void testIsExpiredNotApplicableForOtherStatuses() {
        DeliveryItem item = new DeliveryItem("job1", "job1", false, "", "admin", "source");
        item.setDeliveredAt(System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000);
        // PROMOTED items should not be expired
        item.markPromoted("admin");
        assertFalse(item.isExpired());

        // CANCELLED items should not be expired
        DeliveryItem item2 = new DeliveryItem("job2", "job2", false, "", "admin", "source");
        item2.setDeliveredAt(System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000);
        item2.markCancelled();
        assertFalse(item2.isExpired());

        // EXPIRED items should not be re-expired
        DeliveryItem item3 = new DeliveryItem("job3", "job3", false, "", "admin", "source");
        item3.setDeliveredAt(System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000);
        item3.markExpired();
        assertFalse(item3.isExpired());
    }

    @Test
    public void testReDeliver() {
        DeliveryItem item = new DeliveryItem("job1", "job1", false, "", "admin", "source");
        item.markPromoted("promoter");
        long originalDeliveredAt = item.getDeliveredAt();

        // Set deliveredAt to past to simulate old delivery
        item.setDeliveredAt(System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000);
        item.setStatus(DeliveryItem.Status.EXPIRED);

        item.reDeliver("newAdmin", "newSource");

        assertEquals(DeliveryItem.Status.DELIVERED, item.getStatus());
        assertEquals("newAdmin", item.getDeliveredBy());
        assertEquals("newSource", item.getSourceInstance());
        assertTrue(item.getDeliveredAt() >= originalDeliveredAt);
        assertNull(item.getPromotedBy());
        assertEquals(0, item.getPromotedAt());
    }

    @Test
    public void testReDeliverResetsPromotionInfo() {
        DeliveryItem item = new DeliveryItem("job1", "job1", false, "", "admin", "source");
        item.markPromoted("promoter");
        assertNotNull(item.getPromotedBy());
        assertTrue(item.getPromotedAt() > 0);

        item.reDeliver("admin2", "source2");
        assertNull(item.getPromotedBy());
        assertEquals(0, item.getPromotedAt());
        assertEquals(DeliveryItem.Status.DELIVERED, item.getStatus());
    }

    @Test
    public void testFormattedDatesThreadSafety() {
        DeliveryItem item = new DeliveryItem("job1", "job1", false, "", "admin", "source");

        String deliveredAt = item.getFormattedDeliveredAt();
        assertNotNull(deliveredAt);
        assertFalse(deliveredAt.isEmpty());

        String promotedAt = item.getFormattedPromotedAt();
        assertEquals("", promotedAt);

        item.markPromoted("admin");
        String promotedAtAfter = item.getFormattedPromotedAt();
        assertNotNull(promotedAtAfter);
        assertFalse(promotedAtAfter.isEmpty());
    }

    @Test
    public void testIdIsGenerated() {
        DeliveryItem item = new DeliveryItem("job1", "job1", false, "", "admin", "source");
        assertNotNull(item.getId());
        assertFalse(item.getId().isEmpty());
    }

    @Test
    public void testToJson() {
        DeliveryItem item = new DeliveryItem("job1", "folder/job1", false, "folder", "admin", "source");
        net.sf.json.JSONObject json = item.toJson();
        assertEquals("job1", json.getString("jobName"));
        assertEquals("folder/job1", json.getString("jobFullPath"));
        assertEquals("DELIVERED", json.getString("status"));
        assertTrue(json.has("formattedDeliveredAt"));
    }

    @Test
    public void testToJsonExpiredStatus() {
        DeliveryItem item = new DeliveryItem("job1", "job1", false, "", "admin", "source");
        item.markExpired();
        net.sf.json.JSONObject json = item.toJson();
        assertEquals("EXPIRED", json.getString("status"));
    }

    @Test
    public void testFromJsonWithExpiredStatus() {
        net.sf.json.JSONObject json = new net.sf.json.JSONObject();
        json.put("id", "test-id");
        json.put("jobName", "job1");
        json.put("jobFullPath", "job1");
        json.put("folder", false);
        json.put("folderPath", "");
        json.put("status", "EXPIRED");
        json.put("deliveredBy", "admin");
        json.put("deliveredAt", System.currentTimeMillis());
        json.put("sourceInstance", "source");

        DeliveryItem item = DeliveryItem.fromJson(json);
        assertEquals(DeliveryItem.Status.EXPIRED, item.getStatus());
        assertEquals("job1", item.getJobName());
    }

    @Test
    public void testFromJsonBackwardCompatibility() {
        // Old data without EXPIRED status should default to DELIVERED
        net.sf.json.JSONObject json = new net.sf.json.JSONObject();
        json.put("id", "test-id");
        json.put("jobName", "job1");
        json.put("jobFullPath", "job1");
        json.put("folder", false);
        json.put("status", "DELIVERED");
        json.put("deliveredBy", "admin");
        json.put("deliveredAt", System.currentTimeMillis());

        DeliveryItem item = DeliveryItem.fromJson(json);
        assertEquals(DeliveryItem.Status.DELIVERED, item.getStatus());
        assertEquals("", item.getSourceInstance()); // missing field defaults to empty
    }
}
