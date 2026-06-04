package com.siruoren.jobpromotion;

import org.junit.Test;

import static org.junit.Assert.*;

public class PromotionResultTest {

    @Test
    public void testSuccess() {
        PromotionResult result = PromotionResult.success("job/path", "Created successfully");
        assertEquals("job/path", result.getJobFullPath());
        assertEquals(PromotionResult.Status.SUCCESS, result.getStatus());
        assertEquals("Created successfully", result.getMessage());
        assertTrue(result.isSuccess());
        assertFalse(result.isFailure());
        assertFalse(result.isSkipped());
    }

    @Test
    public void testFailure() {
        PromotionResult result = PromotionResult.failure("job/path", "Something went wrong");
        assertEquals("job/path", result.getJobFullPath());
        assertEquals(PromotionResult.Status.FAILURE, result.getStatus());
        assertEquals("Something went wrong", result.getMessage());
        assertFalse(result.isSuccess());
        assertTrue(result.isFailure());
        assertFalse(result.isSkipped());
    }

    @Test
    public void testSkipped() {
        PromotionResult result = PromotionResult.skipped("job/path", "Already exists");
        assertEquals("job/path", result.getJobFullPath());
        assertEquals(PromotionResult.Status.SKIPPED, result.getStatus());
        assertEquals("Already exists", result.getMessage());
        assertFalse(result.isSuccess());
        assertFalse(result.isFailure());
        assertTrue(result.isSkipped());
    }
}
