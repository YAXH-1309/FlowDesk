package com.flowdesk.core.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method as requiring an {@code Idempotency-Key} header (UUID format).
 * Enforced by {@link IdempotencyInterceptor}.
 * Returns HTTP 400 if the header is missing or not a valid UUID.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IdempotencyRequired {
}
