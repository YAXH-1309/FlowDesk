package com.flowdesk.core.featureflag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as guarded by a feature flag.
 * When the flag is disabled, the method returns null (or void) without executing.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FeatureFlag {
    String value();
}
