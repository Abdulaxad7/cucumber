package com.example.authservice.aop;

import com.example.authservice.configuration.JwtAuthenticationFilter;
import com.example.authservice.configuration.RequestIdMdcFilter;
import com.example.authservice.configuration.UserIdMdcFilter;
import com.example.authservice.controller.AuthController;
import com.example.authservice.controller.OrganizationController;
import com.example.authservice.controller.ProjectAccessController;
import com.example.authservice.exception.ApiExceptionHandler;
import com.example.authservice.security.OrgGuard;
import com.example.authservice.security.ProjectGuard;
import com.example.authservice.service.AuthService;
import com.example.authservice.service.InvitationService;
import com.example.authservice.service.OrganizationService;
import com.example.authservice.service.ProjectAccessService;
import com.example.authservice.service.impl.CustomUserDetailsService;
import com.example.authservice.service.impl.JwtTokenProvider;
import com.example.authservice.service.impl.ProjectRoleService;
import com.example.authservice.support.AuthServiceIntegrationTest;
import org.aopalliance.aop.Advice;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AbstractAspectJAdvice;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where the logging aspect applies (D17 item 1), read from the real proxies: every {@code @Service} use case except
 * {@code CustomUserDetailsService}; never the token provider, guards, controllers, filters or the exception handler;
 * and outside the transaction advice. Proves that AspectJ auto-proxying is active without
 * {@code @EnableAspectJAutoProxy}.
 */
@AuthServiceIntegrationTest
class LoggingAdvicePlacementTest {

    @Autowired
    private ApplicationContext context;

    @ParameterizedTest(name = "the {0} bean is advised by the logging aspect")
    @ValueSource(classes = {AuthService.class, OrganizationService.class, InvitationService.class,
            ProjectAccessService.class, ProjectRoleService.class})
    void everyServiceIsAdvised(Class<?> service) {
        assertThat(loggingAdvisors(context.getBean(service))).hasSize(1);
    }

    @ParameterizedTest(name = "the {0} bean is not advised by the logging aspect")
    @ValueSource(classes = {CustomUserDetailsService.class, JwtTokenProvider.class, OrgGuard.class,
            ProjectGuard.class, AuthController.class, OrganizationController.class, ProjectAccessController.class,
            JwtAuthenticationFilter.class, RequestIdMdcFilter.class, UserIdMdcFilter.class,
            ApiExceptionHandler.class})
    @Tag("AC-9.11")
    void theJwtFilterPathGuardsControllersAndFiltersAreNotAdvised(Class<?> bean) {
        assertThat(loggingAdvisors(context.getBean(bean))).isEmpty();
    }

    @Test
    void theAspectWrapsTheTransactionAdvice() {
        List<Advice> advices = Arrays.stream(((Advised) context.getBean(OrganizationService.class)).getAdvisors())
                .map(Advisor::getAdvice)
                .toList();
        int logging = indexOf(advices, true);
        int transaction = indexOf(advices, false);

        assertThat(logging).as("the logging advice").isNotNegative();
        assertThat(transaction).as("the transaction advice").isNotNegative();
        assertThat(logging).as("the logging advice runs outside the transaction").isLessThan(transaction);
    }

    private static int indexOf(List<Advice> advices, boolean logging) {
        for (int i = 0; i < advices.size(); i++) {
            if (logging ? isLogging(advices.get(i)) : advices.get(i) instanceof TransactionInterceptor) {
                return i;
            }
        }
        return -1;
    }

    private static List<Advisor> loggingAdvisors(Object bean) {
        if (!(bean instanceof Advised advised)) {
            return List.of();
        }
        return Arrays.stream(advised.getAdvisors()).filter(advisor -> isLogging(advisor.getAdvice())).toList();
    }

    private static boolean isLogging(Advice advice) {
        return advice instanceof AbstractAspectJAdvice aspectAdvice
                && aspectAdvice.getAspectJAdviceMethod().getDeclaringClass() == Logging.class;
    }
}
