package com.example.authservice.aop;

import com.example.authservice.configuration.LoggingProperties;
import com.example.authservice.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.util.concurrent.TimeUnit;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class Logging {

    private static final Logger log = LoggerFactory.getLogger(Logging.class);

    private static final ThreadLocal<Boolean> IN_ADVISED_CALL = new ThreadLocal<>();

    private final LoggingProperties properties;

    @Pointcut("within(com.example.authservice.service..*)"
            + " && @within(org.springframework.stereotype.Service)"
            + " && !within(com.example.authservice.service.impl.CustomUserDetailsService)")
    void serviceBean() {
    }

    @Pointcut("execution(public * *(..))")
    void publicMethod() {
    }

    @Around("serviceBean() && publicMethod()")
    public Object logUseCase(ProceedingJoinPoint call) throws Throwable {
        if (!properties.enabled()) {
            return call.proceed();
        }
        boolean outermost = IN_ADVISED_CALL.get() == null;
        if (log.isDebugEnabled()) {
            log.debug("Entering {}", useCase(call));
        }
        if (outermost) {
            IN_ADVISED_CALL.set(Boolean.TRUE);
        }
        long start = System.nanoTime();
        Throwable failure = null;
        try {
            return call.proceed();
        } catch (Throwable ex) {
            failure = ex;
            throw ex;
        } finally {
            long elapsedNanos = System.nanoTime() - start;
            if (outermost) {
                IN_ADVISED_CALL.remove();
            }
            logOutcome(call, outermost, elapsedNanos, failure);
        }
    }

    private void logOutcome(ProceedingJoinPoint call, boolean outermost, long elapsedNanos, Throwable failure) {
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
        if (outermost && elapsedNanos > properties.slowCallThreshold().toNanos()) {
            log.warn("Slow call {}: {} in {} ms (threshold {} ms)", useCase(call), outcome(failure), elapsedMillis,
                    properties.slowCallThreshold().toMillis());
        } else if (outermost && failure instanceof ApiException) {
            log.info("Leaving {}: {} in {} ms", useCase(call), outcome(failure), elapsedMillis);
        } else if (log.isDebugEnabled()) {
            log.debug("Leaving {}: {} in {} ms", useCase(call), outcome(failure), elapsedMillis);
        }
    }

    private static String useCase(ProceedingJoinPoint call) {
        return ClassUtils.getUserClass(call.getTarget()).getSimpleName() + "." + call.getSignature().getName();
    }

    private static String outcome(Throwable failure) {
        if (failure == null) {
            return "returned";
        }
        if (failure instanceof ApiException apiException) {
            return "business error " + apiException.code().name();
        }
        String type = failure.getClass().getSimpleName();
        return "threw " + (type.isEmpty() ? failure.getClass().getName() : type);
    }
}
