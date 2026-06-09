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
    public void testFormattedDatesThreadSafety() {
        // Create multiple items and format dates concurrently to verify thread safety
        DeliveryItem item = new DeliveryItem("job1", "job1", false, "", "admin", "source");

        String deliveredAt = item.getFormattedDeliveredAt();
        assertNotNull(deliveredAt);
        assertFalse(deliveredAt.isEmpty());

        // Promoted at should be empty when not promoted
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
    }
}
