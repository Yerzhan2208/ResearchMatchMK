package com.researchmatch.repository;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the {@link SystemConfigManager} SINGLETON to the Spring container.
 *
 * <p>Deliberately a static {@code @Bean} method returning {@link SystemConfigManager#getInstance()},
 * so the container never constructs a second copy of the configuration.
 */
@Configuration(proxyBeanMethods = false)
public class SystemConfiguration {

    @Bean
    public static SystemConfigManager systemConfigManager() {
        return SystemConfigManager.getInstance();
    }
}
