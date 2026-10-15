package com.retailerp.tenancy;

import javax.sql.DataSource;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TenancyConfig {

    /** Registers our resolver with Hibernate (same as hibernate.tenant_identifier_resolver in properties). */
    @Bean
    HibernatePropertiesCustomizer tenantIdentifierResolverCustomizer() {
        return properties -> properties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, new TenantIdentifierResolver());
    }

    /**
     * Wraps the auto-configured DataSource bean (HikariCP) right after Spring creates it.
     * A BeanPostProcessor sees every bean as it's created and may replace it; static so it's registered early.
     * Flyway is configured with its own user, so it builds its own DataSource and is NOT wrapped.
     */
    @Bean
    static BeanPostProcessor tenantAwareDataSourcePostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource dataSource && !(bean instanceof TenantAwareDataSource) && "dataSource".equals(beanName)) {
                    return new TenantAwareDataSource(dataSource);
                }
                return bean;
            }
        };
    }
}
