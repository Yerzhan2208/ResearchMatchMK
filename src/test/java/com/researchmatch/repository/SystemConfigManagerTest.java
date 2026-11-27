package com.researchmatch.repository;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemConfigManagerTest {

    @Test
    void getInstanceAlwaysReturnsTheSameInstance() {
        assertThat(SystemConfigManager.getInstance()).isSameAs(SystemConfigManager.getInstance());
        assertThat(SystemConfigManager.instance()).isSameAs(SystemConfigManager.getInstance());
    }

    @Test
    void exposesSeededConfiguration() {
        SystemConfigManager config = SystemConfigManager.getInstance();

        assertThat(config.getDouble(SystemConfigManager.KEY_SEMANTIC_WEIGHT, -1)).isEqualTo(0.70);
        assertThat(config.getDouble(SystemConfigManager.KEY_EVIDENCE_WEIGHT, -1)).isEqualTo(0.30);
        assertThat(config.getInt(SystemConfigManager.KEY_MAX_MATCH_RESULTS, -1)).isEqualTo(20);
        assertThat(config.getDouble(SystemConfigManager.KEY_MIN_BRANCH_COVERAGE, -1)).isEqualTo(0.85);
        assertThat(config.getBoolean(SystemConfigManager.KEY_ALLOW_FACULTY_SEARCH, true)).isFalse();
        assertThat(config.get("missing.key")).isNull();
        assertThat(config.get("missing.key", "fallback")).isEqualTo("fallback");
        assertThat(config.asMap()).containsKey(SystemConfigManager.KEY_MAX_MATCH_RESULTS).isUnmodifiable();
    }

    @Test
    void missingValuesFallBackToDefaults() {
        SystemConfigManager config = SystemConfigManager.getInstance();

        assertThat(config.getInt("does.not.exist", 7)).isEqualTo(7);
        assertThat(config.getDouble("does.not.exist", 1.5)).isEqualTo(1.5);
        assertThat(config.getBoolean("does.not.exist", true)).isTrue();
        assertThat(config.getBoolean("does.not.exist", false)).isFalse();
    }

    @Test
    void explicitFalseIsNotOverriddenByTheDefault() {
        SystemConfigManager config = SystemConfigManager.getInstance();

        assertThat(config.getBoolean(SystemConfigManager.KEY_ALLOW_FACULTY_SEARCH, true)).isFalse();
    }
}
