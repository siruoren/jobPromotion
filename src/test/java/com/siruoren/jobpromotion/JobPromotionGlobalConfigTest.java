package com.siruoren.jobpromotion;

import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class JobPromotionGlobalConfigTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testGetInstanceByNameWithNull() {
        JobPromotionGlobalConfig config = JobPromotionGlobalConfig.get();
        List<SourceJenkinsInstance> instances = new ArrayList<>();
        config.setInstances(instances);
        assertNull(config.getInstanceByName(null));
    }

    @Test
    public void testGetInstanceByNameWithEmptyName() {
        JobPromotionGlobalConfig config = JobPromotionGlobalConfig.get();
        List<SourceJenkinsInstance> instances = new ArrayList<>();
        config.setInstances(instances);
        assertNull(config.getInstanceByName(""));
    }

    @Test
    public void testGetInstanceByNameReturnsFirstWhenNoMatch() {
        JobPromotionGlobalConfig config = JobPromotionGlobalConfig.get();
        List<SourceJenkinsInstance> instances = new ArrayList<>();
        instances.add(new SourceJenkinsInstance("first", "http://first.com", "admin", "cred1"));
        config.setInstances(instances);

        SourceJenkinsInstance result = config.getInstanceByName("nonexistent");
        assertNotNull(result);
        assertEquals("first", result.getName());
    }

    @Test
    public void testGetInstanceByNameFindsMatch() {
        JobPromotionGlobalConfig config = JobPromotionGlobalConfig.get();
        List<SourceJenkinsInstance> instances = new ArrayList<>();
        instances.add(new SourceJenkinsInstance("first", "http://first.com", "admin", "cred1"));
        instances.add(new SourceJenkinsInstance("second", "http://second.com", "user", "cred2"));
        config.setInstances(instances);

        SourceJenkinsInstance result = config.getInstanceByName("second");
        assertNotNull(result);
        assertEquals("second", result.getName());
        assertEquals("http://second.com", result.getUrl());
        assertEquals("user", result.getUsername());
    }

    @Test
    public void testGetInstanceByNameNullReturnsFirst() {
        JobPromotionGlobalConfig config = JobPromotionGlobalConfig.get();
        List<SourceJenkinsInstance> instances = new ArrayList<>();
        instances.add(new SourceJenkinsInstance("first", "http://first.com", "admin", "cred1"));
        config.setInstances(instances);

        SourceJenkinsInstance result = config.getInstanceByName(null);
        assertNotNull(result);
        assertEquals("first", result.getName());
    }

    @Test
    public void testResolveCredentialsForInstanceNull() {
        JobPromotionGlobalConfig config = JobPromotionGlobalConfig.get();
        assertNull(config.resolveCredentialsForInstance(null));
    }

    @Test
    public void testResolveCredentialsForInstanceEmptyFields() {
        JobPromotionGlobalConfig config = JobPromotionGlobalConfig.get();
        SourceJenkinsInstance instance = new SourceJenkinsInstance("test", "http://test.com", "", "");
        assertNull(config.resolveCredentialsForInstance(instance));
    }

    @Test
    public void testResolveCredentialsForInstanceMissingUsername() {
        JobPromotionGlobalConfig config = JobPromotionGlobalConfig.get();
        SourceJenkinsInstance instance = new SourceJenkinsInstance("test", "http://test.com", "", "some-cred-id");
        assertNull(config.resolveCredentialsForInstance(instance));
    }

    @Test
    public void testResolveCredentialsForInstanceMissingApiTokenId() {
        JobPromotionGlobalConfig config = JobPromotionGlobalConfig.get();
        SourceJenkinsInstance instance = new SourceJenkinsInstance("test", "http://test.com", "admin", "");
        assertNull(config.resolveCredentialsForInstance(instance));
    }

    @Test
    public void testGetAuditLogRetentionDaysDefault() {
        JobPromotionGlobalConfig config = JobPromotionGlobalConfig.get();
        assertEquals(30, config.getAuditLogRetentionDays());
    }

    @Test
    public void testSetAuditLogRetentionDays() {
        JobPromotionGlobalConfig config = JobPromotionGlobalConfig.get();
        config.setAuditLogRetentionDays(60);
        assertEquals(60, config.getAuditLogRetentionDays());
    }

    @Test
    public void testGetSourceJenkinsUrlEmpty() {
        JobPromotionGlobalConfig config = JobPromotionGlobalConfig.get();
        config.setInstances(new ArrayList<>());
        assertEquals("", config.getSourceJenkinsUrl());
    }

    @Test
    public void testGetSourceJenkinsUrl() {
        JobPromotionGlobalConfig config = JobPromotionGlobalConfig.get();
        List<SourceJenkinsInstance> instances = new ArrayList<>();
        instances.add(new SourceJenkinsInstance("first", "http://first.com", "admin", "cred1"));
        config.setInstances(instances);
        assertEquals("http://first.com", config.getSourceJenkinsUrl());
    }

    @Test
    public void testResolveApiTokenCredentialsNull() {
        JobPromotionGlobalConfig config = JobPromotionGlobalConfig.get();
        assertNull(config.resolveApiTokenCredentials(null));
        assertNull(config.resolveApiTokenCredentials(""));
        assertNull(config.resolveApiTokenCredentials("  "));
    }

    @Test
    public void testConfigIsSingleton() {
        JobPromotionGlobalConfig config1 = JobPromotionGlobalConfig.get();
        JobPromotionGlobalConfig config2 = JobPromotionGlobalConfig.get();
        assertSame(config1, config2);
    }
}
