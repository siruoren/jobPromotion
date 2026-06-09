package com.siruoren.jobpromotion.util;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility to read plugin version from POM metadata.
 */
public class VersionUtil {

    private static final Logger LOGGER = Logger.getLogger(VersionUtil.class.getName());
    private static String version;

    private VersionUtil() {
    }

    /**
     * Get the plugin version from POM properties.
     */
    public static synchronized String getVersion() {
        if (version == null) {
            try {
                var resources = VersionUtil.class.getClassLoader().getResources("META-INF/maven/com.siruoren/job-promotion/pom.properties");
                while (resources.hasMoreElements()) {
                    var url = resources.nextElement();
                    try (var is = url.openStream()) {
                        var props = new java.util.Properties();
                        props.load(is);
                        String v = props.getProperty("version");
                        if (v != null && !v.isEmpty()) {
                            version = v;
                            return version;
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to read version from POM properties", e);
            }
            version = "unknown";
        }
        return version;
    }
}
