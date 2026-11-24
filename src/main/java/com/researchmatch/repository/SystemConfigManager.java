package com.researchmatch.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * SINGLETON — process-wide configuration for ResearchMatch.
 *
 * <p>Design-pattern requirement: exactly one instance must exist. Use {@link #getInstance()};
 * Spring also exposes the same instance as a bean (via the {@code @Bean} in
 * {@code SystemConfiguration}).
 *
 * <p>Configuration may be seeded from {@code system-config.json} on the classpath (optional).
 * JVM system properties named {@code researchmatch.<key>} override values from that file.
 */
public class SystemConfigManager {

    private static final Logger log = LoggerFactory.getLogger(SystemConfigManager.class);

    private static final String CONFIG_RESOURCE = "system-config.json";

    /** Public configuration keys, to avoid typos across members' code. */
    public static final String KEY_MIN_BRANCH_COVERAGE = "coverage.minBranch";
    public static final String KEY_MAX_MATCH_RESULTS = "matching.maxResults";
    public static final String KEY_SEMANTIC_WEIGHT = "matching.semanticWeight";
    public static final String KEY_ALLOW_FACULTY_SEARCH = "privacy.allowFacultySearch";

    /**
     * Seeded defaults. Declared BEFORE {@link #INSTANCE} — static initialisers run in source order, and the
     * singleton constructor needs these values. A JSON value is only used when it is present and non-blank.
     */
    private static final Map<String, String> DEFAULTS = defaultValues();

    /** Eagerly created exactly once — no lazy-race and no second instance from Spring. */
    private static final SystemConfigManager INSTANCE = new SystemConfigManager();

    private final Map<String, String> values;

    /** Private: the only instance is {@link #INSTANCE}, reachable via {@link #getInstance()}. */
    private SystemConfigManager() {
        this(Thread.currentThread().getContextClassLoader());
    }

    private static Map<String, String> defaultValues() {
        Map<String, String> defaults = new HashMap<>();
        defaults.put(KEY_MIN_BRANCH_COVERAGE, "0.85");
        defaults.put(KEY_MAX_MATCH_RESULTS, "20");
        defaults.put(KEY_SEMANTIC_WEIGHT, "0.30");
        defaults.put(KEY_ALLOW_FACULTY_SEARCH, "false");
        return Collections.unmodifiableMap(defaults);
    }

    /** Test/embedded entry point: same defaults, but the JSON is read through the given class loader. */
    SystemConfigManager(ClassLoader classLoader) {
        Map<String, String> loaded = new HashMap<>(DEFAULTS);
        loaded.putAll(loadFromResource(classLoader));

        for (String key : loaded.keySet().toArray(new String[0])) {
            String override = System.getProperty("researchmatch." + key);
            if (override != null) {
                loaded.put(key, override);
            }
        }
        this.values = Collections.unmodifiableMap(loaded);
    }

    public static SystemConfigManager getInstance() {
        return INSTANCE;
    }

    /** Spring {@code @Bean} factory: always the singleton. */
    public static SystemConfigManager instance() {
        return INSTANCE;
    }

    @SuppressWarnings("unchecked")
    static Map<String, String> loadFromResource(ClassLoader loader) {
        ClassLoader resourceLoader = loader == null ? SystemConfigManager.class.getClassLoader() : loader;
        try {
            return readJson(resourceLoader.getResourceAsStream(CONFIG_RESOURCE));
        } catch (Exception e) {
            log.warn("Could not read {}; using defaults", CONFIG_RESOURCE, e);
            return Map.of();
        }
    }

    private static Map<String, String> readJson(InputStream in) throws IOException {
        if (in == null) {
            return Map.of();
        }
        try (in) {
            return stringify(new ObjectMapper().readValue(in, Map.class));
        }
    }

    private static Map<String, String> stringify(Map<String, Object> raw) {
        Map<String, String> result = new HashMap<>();
        raw.forEach((key, value) -> result.put(key, Objects.toString(value, "")));
        return result;
    }

    public String get(String key) {
        return values.get(key);
    }

    public String get(String key, String defaultValue) {
        return values.getOrDefault(key, defaultValue);
    }

    private String lookup(String key) {
        return values.get(key);
    }

    public int getInt(String key, int defaultValue) {
        String raw = lookup(key);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("Config key {} has non-integer value '{}'; using {}", key, raw, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Plain decimal parse. Deliberately NOT {@code Double.parseDouble}: that accepts {@code NaN},
     * {@code Infinity} and hex forms, which must fall back to the default instead.
     */
    public double getDouble(String key, double defaultValue) {
        String raw = lookup(key);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return new BigDecimal(raw.trim()).doubleValue();
        } catch (NumberFormatException e) {
            log.warn("Config key {} has non-numeric value '{}'; using {}", key, raw, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Only the literals {@code true}/{@code false} (any case) are accepted. Anything else — including
     * {@code "yes"} — falls back to the default, which keeps "unknown" distinct from "false".
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        String raw = lookup(key);
        if (raw == null) {
            return defaultValue;
        }
        String normalised = raw.trim();
        if ("true".equalsIgnoreCase(normalised)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalised)) {
            return false;
        }
        log.warn("Config key {} has non-boolean value '{}'; using {}", key, raw, defaultValue);
        return defaultValue;
    }

    public Map<String, String> asMap() {
        return values;
    }
}
