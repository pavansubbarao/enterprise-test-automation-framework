package com.framework.config;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Central configuration loader.
 * Reads config.properties, then overlays System properties and environment variables.
 * Usage:  FrameworkConfig.get("jira.base.url")
 */
@Slf4j
public class FrameworkConfig {

    private static final Properties props = new Properties();
    private static FrameworkConfig instance;

    static {
        loadProperties();
    }

    private FrameworkConfig() {}

    public static FrameworkConfig getInstance() {
        if (instance == null) {
            synchronized (FrameworkConfig.class) {
                if (instance == null) instance = new FrameworkConfig();
            }
        }
        return instance;
    }

    private static void loadProperties() {
        try (InputStream is = FrameworkConfig.class.getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (is != null) {
                props.load(is);
                log.info("Loaded config.properties");
            }
        } catch (IOException e) {
            log.warn("Could not load config.properties – using defaults/env only");
        }

        // Environment-specific override: config-{env}.properties
        String env = resolve("env", "dev");
        String envFile = "config-" + env + ".properties";
        try (InputStream is = FrameworkConfig.class.getClassLoader()
                .getResourceAsStream(envFile)) {
            if (is != null) {
                props.load(is);
                log.info("Loaded environment config: {}", envFile);
            }
        } catch (IOException e) {
            log.debug("No environment-specific config found for: {}", envFile);
        }
    }

    /**
     * Resolve a property value with the priority:
     * 1. System property (-Dkey=value)
     * 2. Environment variable (KEY=value, with dots replaced by underscores, uppercased)
     * 3. config.properties value (may itself reference ${ENV_VAR})
     * 4. Default value
     */
    public static String get(String key) {
        return resolve(key, null);
    }

    public static String get(String key, String defaultValue) {
        return resolve(key, defaultValue);
    }

    public static int getInt(String key, int defaultValue) {
        String val = resolve(key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        String val = resolve(key, String.valueOf(defaultValue));
        return Boolean.parseBoolean(val);
    }

    private static String resolve(String key, String defaultValue) {
        // 1. System property
        String val = System.getProperty(key);
        if (val != null) return expand(val);

        // 2. Environment variable (dots → underscores, uppercase)
        String envKey = key.replace(".", "_").toUpperCase();
        val = System.getenv(envKey);
        if (val != null) return expand(val);

        // 3. Properties file
        val = props.getProperty(key);
        if (val != null) return expand(val);

        return defaultValue;
    }

    /** Expand ${ENV_VAR} placeholders in property values */
    private static String expand(String value) {
        if (value == null || !value.contains("${")) return value;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < value.length()) {
            int start = value.indexOf("${", i);
            if (start < 0) { sb.append(value.substring(i)); break; }
            sb.append(value, i, start);
            int end = value.indexOf("}", start);
            if (end < 0) { sb.append(value.substring(start)); break; }
            String envKey = value.substring(start + 2, end);
            String envVal = System.getenv(envKey);
            sb.append(envVal != null ? envVal : "");
            i = end + 1;
        }
        return sb.toString();
    }
}
