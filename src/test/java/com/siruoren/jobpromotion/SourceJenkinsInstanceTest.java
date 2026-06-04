package com.siruoren.jobpromotion;

import org.junit.Test;

import static org.junit.Assert.*;

public class SourceJenkinsInstanceTest {

    @Test
    public void testConstructorAndGetters() {
        SourceJenkinsInstance instance = new SourceJenkinsInstance(
                "test-instance", "http://jenkins.example.com", "admin", "cred-123"
        );
        assertEquals("test-instance", instance.getName());
        assertEquals("http://jenkins.example.com", instance.getUrl());
        assertEquals("admin", instance.getUsername());
        assertEquals("cred-123", instance.getApiTokenCredentialsId());
    }

    @Test
    public void testDefaultConstructor() {
        SourceJenkinsInstance instance = new SourceJenkinsInstance();
        assertNull(instance.getName());
        assertNull(instance.getUrl());
        assertNull(instance.getUsername());
        assertNull(instance.getApiTokenCredentialsId());
    }

    @Test
    public void testSetters() {
        SourceJenkinsInstance instance = new SourceJenkinsInstance();
        instance.setName("my-instance");
        instance.setUrl("http://localhost:8080");
        instance.setUsername("user1");
        instance.setApiTokenCredentialsId("api-token-id");

        assertEquals("my-instance", instance.getName());
        assertEquals("http://localhost:8080", instance.getUrl());
        assertEquals("user1", instance.getUsername());
        assertEquals("api-token-id", instance.getApiTokenCredentialsId());
    }

    @Test
    public void testDescriptorDisplayName() {
        SourceJenkinsInstance.DescriptorImpl descriptor = new SourceJenkinsInstance.DescriptorImpl();
        assertEquals("Source Jenkins Instance", descriptor.getDisplayName());
    }
}
