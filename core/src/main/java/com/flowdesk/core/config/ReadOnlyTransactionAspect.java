package com.flowdesk.core.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * AOP aspect that sets the {@link ReadWriteContext} to read-only before any method
 * annotated with {@code @Transactional(readOnly = true)}, ensuring those transactions
 * are routed to a read replica by {@link ReadReplicaRoutingDataSource}.
 *
 * <p>Ordered before the transaction interceptor (order 1) so the context is set
 * before the connection is acquired.</p>
 */
@Aspect
@Component
@Order(1)
public class ReadOnlyTransactionAspect {

    @Around("@annotation(org.springframework.transaction.annotation.Transactional) && " +
            "@annotation(transactional)")
    public Object routeReadOnlyTransaction(
            ProceedingJoinPoint joinPoint,
            org.springframework.transaction.annotation.Transactional transactional) throws Throwable {

        if (transactional.readOnly()) {
            ReadWriteContext.setReadOnly(true);
        }
        try {
            return joinPoint.proceed();
        } finally {
            ReadWriteContext.clear();
        }
    }
}
