package com.retailerp.subscription;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Put on a controller class or method: requests are rejected with 403 unless the tenant's plan (or an add-on)
 * includes the module. Checked by {@link ModuleAccessInterceptor} before the controller method runs.
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresModule {

    Module value();
}
