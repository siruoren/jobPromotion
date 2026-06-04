package com.siruoren.jobpromotion;

import org.junit.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.*;

public class JenkinsRemoteClientTest {

    @Test
    public void testConstructorTrimsTrailingSlashes() {
        JenkinsRemoteClient client = new JenkinsRemoteClient("http://jenkins.example.com///", "admin", "token123");
        // Verify via reflection that baseUrl is trimmed
        try {
            java.lang.reflect.Field baseUrlField = JenkinsRemoteClient.class.getDeclaredField("baseUrl");
            baseUrlField.setAccessible(true);
            String baseUrl = (String) baseUrlField.get(client);
            assertEquals("http://jenkins.example.com", baseUrl);
        } catch (Exception e) {
            fail("Failed to access baseUrl field: " + e.getMessage());
        }
    }

    @Test
    public void testConstructorPreservesUrlWithoutTrailingSlash() {
        JenkinsRemoteClient client = new JenkinsRemoteClient("http://jenkins.example.com", "admin", "token123");
        try {
            java.lang.reflect.Field baseUrlField = JenkinsRemoteClient.class.getDeclaredField("baseUrl");
            baseUrlField.setAccessible(true);
            String baseUrl = (String) baseUrlField.get(client);
            assertEquals("http://jenkins.example.com", baseUrl);
        } catch (Exception e) {
            fail("Failed to access baseUrl field: " + e.getMessage());
        }
    }

    @Test
    public void testConstructorTrimsWhitespace() {
        JenkinsRemoteClient client = new JenkinsRemoteClient("  http://jenkins.example.com/  ", "admin", "token123");
        try {
            java.lang.reflect.Field baseUrlField = JenkinsRemoteClient.class.getDeclaredField("baseUrl");
            baseUrlField.setAccessible(true);
            String baseUrl = (String) baseUrlField.get(client);
            assertEquals("http://jenkins.example.com", baseUrl);
        } catch (Exception e) {
            fail("Failed to access baseUrl field: " + e.getMessage());
        }
    }

    @Test
    public void testBasicAuthHeader() throws Exception {
        JenkinsRemoteClient client = new JenkinsRemoteClient("http://jenkins.example.com", "admin", "secret-token");
        Method getBasicAuth = JenkinsRemoteClient.class.getDeclaredMethod("getBasicAuth");
        getBasicAuth.setAccessible(true);
        String authHeader = (String) getBasicAuth.invoke(client);

        assertTrue(authHeader.startsWith("Basic "));
        String encoded = authHeader.substring("Basic ".length());
        String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        assertEquals("admin:secret-token", decoded);
    }

    @Test
    public void testEncodeFolderPath() throws Exception {
        JenkinsRemoteClient client = new JenkinsRemoteClient("http://jenkins.example.com", "admin", "token");
        Method encodeMethod = JenkinsRemoteClient.class.getDeclaredMethod("encodeFolderPath", String.class);
        encodeMethod.setAccessible(true);

        // Simple path
        String result = (String) encodeMethod.invoke(client, "folder1");
        assertEquals("folder1", result);

        // Nested path
        result = (String) encodeMethod.invoke(client, "folder1/subfolder");
        assertEquals("folder1/job/subfolder", result);

        // Deep nested path
        result = (String) encodeMethod.invoke(client, "a/b/c");
        assertEquals("a/job/b/job/c", result);

        // Empty path
        result = (String) encodeMethod.invoke(client, "");
        assertEquals("", result);

        // Null path
        result = (String) encodeMethod.invoke(client, (String) null);
        assertEquals("", result);

        // Path with special characters
        result = (String) encodeMethod.invoke(client, "my folder/job name");
        assertEquals("my+folder/job/job+name", result);
    }
}
