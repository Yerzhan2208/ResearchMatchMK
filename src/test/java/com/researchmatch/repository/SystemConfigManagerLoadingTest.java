package com.researchmatch.repository;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Branch coverage for {@link SystemConfigManager} loading paths.
 *
 * <p>Each test builds a throwaway {@link SystemConfigManager} with an isolated, first-parent loader so the
 * {@code system-config.json} shipped on the real classpath cannot leak into the assertions, and the real
 * singleton is never mutated.
 */
class SystemConfigManagerLoadingTest {

    /** Class loader that serves {@code system-config.json} from memory (or nothing at all). */
    private static final class JsonClassLoader extends ClassLoader {
        private final byte[] content;

        JsonClassLoader(byte[] content) {
            this(content, SystemConfigManager.class.getClassLoader());
        }

        JsonClassLoader(byte[] content, ClassLoader parent) {
            super(parent);
            this.content = content;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (content != null && "system-config.json".equals(name)) {
                return new ByteArrayInputStream(content);
            }
            return null;
        }
    }

    private static String json(String... keyValuePairs) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < keyValuePairs.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(keyValuePairs[i]);
        }
        return sb.append("}").toString();
    }

    private static JsonClassLoader loaderFor(String json) {
        return new JsonClassLoader(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void readsJsonFromClasspathAndStringifiesValues() {
        SystemConfigManager config = new SystemConfigManager(loaderFor(
                json("\"matching.maxResults\": 5", "\"nullable.key\": null")));

        assertThat(config.getInt(SystemConfigManager.KEY_MAX_MATCH_RESULTS, -1)).isEqualTo(5);
        // JSON null is stringified to the empty string.
        assertThat(config.get("nullable.key")).isEmpty();
        assertThat(config.getBoolean(SystemConfigManager.KEY_ALLOW_FACULTY_SEARCH, false)).isFalse();
    }

    @Test
    void fallsBackToDefaultsWhenResourceIsMissing() {
        SystemConfigManager config = new SystemConfigManager(new JsonClassLoader(null));

        assertThat(config.asMap()).containsEntry(SystemConfigManager.KEY_MAX_MATCH_RESULTS, "20");
    }

    @Test
    void fallsBackToDefaultsWhenJsonIsMalformed() {
        SystemConfigManager config = new SystemConfigManager(loaderFor("{not json"));

        assertThat(config.getInt(SystemConfigManager.KEY_MAX_MATCH_RESULTS, -1)).isEqualTo(20);
    }

    @Test
    void nullClassLoaderUsesOwnLoader() {
        SystemConfigManager config = new SystemConfigManager((ClassLoader) null);

        assertThat(config.getDouble(SystemConfigManager.KEY_SEMANTIC_WEIGHT, -1)).isEqualTo(0.30);
    }

    @Test
    void unparsableValuesFallBackToDefaults() {
        SystemConfigManager config = new SystemConfigManager(loaderFor(json(
                "\"matching.maxResults\": \"many\"",
                "\"matching.semanticWeight\": \"heavy\"",
                "\"privacy.allowFacultySearch\": \"yes\"")));

        assertThat(config.getInt(SystemConfigManager.KEY_MAX_MATCH_RESULTS, 3)).isEqualTo(3);
        assertThat(config.getDouble(SystemConfigManager.KEY_SEMANTIC_WEIGHT, 0.5)).isEqualTo(0.5);
        // "yes" is not a boolean literal, so the caller's default is used (here: false).
        assertThat(config.getBoolean(SystemConfigManager.KEY_ALLOW_FACULTY_SEARCH, false)).isFalse();
        assertThat(config.getBoolean(SystemConfigManager.KEY_ALLOW_FACULTY_SEARCH, true)).isTrue();
    }

    @Test
    void specialFloatFormsAreRejected() {
        SystemConfigManager config = new SystemConfigManager(loaderFor(json(
                "\"matching.semanticWeight\": \"NaN\"",
                "\"coverage.minBranch\": \" 0.9 \"")));

        assertThat(config.getDouble(SystemConfigManager.KEY_SEMANTIC_WEIGHT, 0.25)).isEqualTo(0.25);
        assertThat(config.getDouble(SystemConfigManager.KEY_MIN_BRANCH_COVERAGE, -1)).isEqualTo(0.9);
    }

    @Test
    void booleanLiteralsAreCaseInsensitive() {
        SystemConfigManager config = new SystemConfigManager(loaderFor(json(
                "\"privacy.allowFacultySearch\": \" TRUE \"")));

        assertThat(config.getBoolean(SystemConfigManager.KEY_ALLOW_FACULTY_SEARCH, false)).isTrue();
    }

    @Test
    void systemPropertiesOverrideConfiguration() {
        String key = "researchmatch." + SystemConfigManager.KEY_MAX_MATCH_RESULTS;
        String previous = System.getProperty(key);
        System.setProperty(key, "99");
        try {
            SystemConfigManager config = new SystemConfigManager(new JsonClassLoader(null));
            assertThat(config.getInt(SystemConfigManager.KEY_MAX_MATCH_RESULTS, -1)).isEqualTo(99);
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }

    @Test
    void resourceLoaderReadsTheRealFileFromTargetClasses() throws Exception {
        Path classes = Path.of(SystemConfigManager.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        try (URLClassLoader loader = new URLClassLoader(new URL[] {classes.toUri().toURL()}, null)) {
            assertThat(SystemConfigManager.loadFromResource(loader))
                    .containsEntry(SystemConfigManager.KEY_SEMANTIC_WEIGHT, "0.3");
        }
        assertThat(Files.exists(classes.resolve("system-config.json"))).isTrue();
    }
}
