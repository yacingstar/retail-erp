package com.retailerp.subscription;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Implementing {@link WebMvcConfigurer} lets us add to Spring MVC's auto-configuration instead of replacing it. */
@Configuration(proxyBeanMethods = false)
public class SubscriptionWebConfig implements WebMvcConfigurer {

    private final SubscriptionService subscriptions;

    public SubscriptionWebConfig(SubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ModuleAccessInterceptor(subscriptions)).addPathPatterns("/api/**");
    }
}
