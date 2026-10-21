package org.scenarioservice.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.scenarioservice.aop.RequestLog;
import org.scenarioservice.enums.ProjectRole;
import org.scenarioservice.exception.AccessCheckUnavailableException;
import org.scenarioservice.exception.ForbiddenException;
import org.scenarioservice.exception.ProjectArchivedException;
import org.scenarioservice.exception.UnauthenticatedException;
import org.scenarioservice.support.AccessCheckStub;
import org.scenarioservice.support.ScenarioFixture;
import org.scenarioservice.support.ScenarioFixture.User;
import org.scenarioservice.support.ScenarioServiceIntegrationTest;
import org.scenarioservice.support.TestSettings;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.scenarioservice.support.ScenarioFixture.BILLING;
import static org.scenarioservice.support.ScenarioFixture.CHECKOUT;
import static org.scenarioservice.support.ScenarioFixture.DAN;
import static org.scenarioservice.support.ScenarioFixture.GUS;
import static org.scenarioservice.support.ScenarioFixture.HAL;
import static org.scenarioservice.support.ScenarioFixture.LEGACY;

/**
 * D4, D5 (T5 component tests against the access check stub): every mapping row of C5's answer, rank comparison,
 * archived projects, no caching, the forwarded headers, one WARN line per failure, and no database connection held
 * during the call (P13). The caller is put into the security context and the request id into the MDC, as the filters
 * do for a real request.
 */
@ScenarioServiceIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class ProjectAccessCheckerTest {

    private static final String REQUEST_ID = "req-t5-component";

    private final AccessCheckStub stub = AccessCheckStub.get();

    @Autowired
    private ProjectAccessChecker checker;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void resetTheStub() {
        stub.reset();
        MDC.put("requestId", REQUEST_ID);
    }

    @AfterEach
    void clearTheCaller() {
        SecurityContextHolder.clearContext();
        MDC.remove("requestId");
        stub.reset();
    }

    @Test
    @Tag("AC-7.2")
    @DisplayName("AC-7.2 D4: one C5 call with the caller's Authorization unchanged and the MDC request id")
    void oneCallForwardsTheTokenAndTheRequestId() {
        as(GUS);

        ProjectAccess access = checker.requireRead(CHECKOUT);

        assertThat(access).isEqualTo(new ProjectAccess(CHECKOUT, ProjectRole.MEMBER, false));
        assertThat(stub.received()).containsExactly(new AccessCheckStub.Received("GET",
                "/internal/projects/" + CHECKOUT + "/access", GUS.bearer(), REQUEST_ID));
    }

    @Test
    @Tag("AC-7.5")
    @DisplayName("AC-7.5 D4: no caching; a role change applies on the next call")
    void answersAreNotCached() {
        as(GUS);
        assertThat(checker.requireWrite(CHECKOUT).role()).isEqualTo(ProjectRole.MEMBER);

        stub.role(GUS.id(), CHECKOUT, "READER");
        assertThat(catchThrowable(() -> checker.requireWrite(CHECKOUT))).isInstanceOf(ForbiddenException.class);
        stub.role(GUS.id(), CHECKOUT, null);
        assertThat(catchThrowable(() -> checker.requireRead(CHECKOUT))).isInstanceOf(ForbiddenException.class);

        assertThat(stub.received()).hasSize(3);
    }

    @Test
    @DisplayName("D3: READER and TESTER read; MEMBER and ADMIN also write; no access is 403")
    void rolesAreComparedByRank() {
        as(DAN);
        assertThat(checker.requireRead(CHECKOUT).role()).isEqualTo(ProjectRole.READER);
        assertThat(catchThrowable(() -> checker.requireWrite(CHECKOUT))).isInstanceOf(ForbiddenException.class);
        as(HAL);
        assertThat(checker.requireRead(CHECKOUT).role()).isEqualTo(ProjectRole.TESTER);
        assertThat(catchThrowable(() -> checker.requireWrite(CHECKOUT))).isInstanceOf(ForbiddenException.class);
        as(ScenarioFixture.CAI);
        assertThat(checker.requireWrite(CHECKOUT).role()).isEqualTo(ProjectRole.ADMIN);
        as(GUS);
        assertThat(catchThrowable(() -> checker.requireRead(BILLING))).isInstanceOf(ForbiddenException.class);
        assertThat(catchThrowable(() -> checker.requireRead(UUID.randomUUID()))).isInstanceOf(ForbiddenException.class);
    }

    @Test
    @Tag("AC-7.7")
    @DisplayName("AC-7.7 D3: an archived project is readable; a write is 409 PROJECT_ARCHIVED, after the role check")
    void anArchivedProjectIsReadOnly() {
        as(GUS);
        assertThat(checker.requireRead(LEGACY).archived()).isTrue();
        assertThat(catchThrowable(() -> checker.requireWrite(LEGACY))).isInstanceOf(ProjectArchivedException.class);
        as(DAN);
        assertThat(catchThrowable(() -> checker.requireWrite(LEGACY))).isInstanceOf(ForbiddenException.class);
    }

    @Test
    @Tag("AC-7.4")
    @DisplayName("AC-7.4 D4: C5's 401 is 401 UNAUTHENTICATED")
    void aRejectedTokenIsUnauthenticated() {
        as(GUS);
        stub.rejectToken(GUS.id());

        assertThat(catchThrowable(() -> checker.requireRead(CHECKOUT))).isInstanceOf(UnauthenticatedException.class);
    }

    @ParameterizedTest(name = "AC-7.3 D4: the stub {0} is 503, with one WARN line")
    @ValueSource(strings = {"is not running", "waits 5 seconds", "answers 500", "answers 404", "answers 204",
            "answers 302", "answers role OWNER", "answers a body that isn't JSON", "answers another projectId",
            "answers archived as a string", "answers without archived", "answers without role",
            "answers an empty body", "answers a JSON array"})
    @Tag("AC-7.3")
    @Tag("AC-8.10")
    void anIncompleteAccessCheckFailsClosed(String failure, CapturedOutput output) {
        as(GUS);
        switch (failure) {
            case "is not running" -> stub.stop();
            case "waits 5 seconds" -> stub.delay(Duration.ofSeconds(5));
            case "answers 500" -> stub.answer(500, "{}");
            case "answers 404" -> stub.answer(404, "");
            case "answers 204" -> stub.answer(204, "");
            case "answers 302" -> stub.answer(302, "");
            case "answers role OWNER" -> stub.answer(200, AccessCheckStub.accessBody(CHECKOUT, "OWNER"));
            case "answers a body that isn't JSON" -> stub.answer(200, "<html>ok</html>");
            case "answers another projectId" -> stub.answer(200, AccessCheckStub.accessBody(BILLING, "MEMBER"));
            case "answers archived as a string" -> stub.answer(200, AccessCheckStub.accessBody(CHECKOUT, "MEMBER")
                    .replace("\"archived\":false", "\"archived\":\"false\""));
            case "answers without archived" -> stub.answer(200, AccessCheckStub.accessBody(CHECKOUT, "MEMBER")
                    .replace(",\"archived\":false", ""));
            case "answers without role" -> stub.answer(200, AccessCheckStub.accessBody(CHECKOUT, "MEMBER")
                    .replace("\"role\":\"MEMBER\",", ""));
            case "answers an empty body" -> stub.answer(200, "");
            default -> stub.answer(200, "[]");
        }
        int logStart = output.getAll().length();
        long start = System.nanoTime();

        Throwable result = catchThrowable(() -> checker.requireWrite(CHECKOUT));

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
        assertThat(result).isInstanceOf(AccessCheckUnavailableException.class);
        RequestLog log = RequestLog.since(output, logStart);
        assertThat(log.entries()).filteredOn(line -> line.contains(" WARN ")).singleElement()
                .satisfies(line -> assertThat(line).contains("Access check unavailable"));
        assertThat(log.count("ERROR")).isZero();
        assertThat(log.hasStackTraceLine()).isFalse();
        assertThat(log.lines()).noneMatch(line -> line.contains(GUS.token())
                || line.contains(String.valueOf(TestSettings.ACCESS_CHECK_PORT)) || line.contains("/internal/"));
    }

    @Test
    @DisplayName("P13: the check holds no database connection; it succeeds with the pool exhausted")
    void theCheckHoldsNoDatabaseConnection() throws Exception {
        as(GUS);
        List<Connection> borrowed = new ArrayList<>();
        try {
            for (int i = 0; i < 12; i++) {
                borrowed.add(dataSource.getConnection());
            }
            long start = System.nanoTime();

            assertThatCode(() -> checker.requireWrite(CHECKOUT)).doesNotThrowAnyException();

            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        } finally {
            for (Connection connection : borrowed) {
                connection.close();
            }
        }
        assertThat(borrowed).hasSize(12);
    }

    private static void as(User user) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new CallerPrincipal(user.id(), user.token()), null, List.of()));
    }
}
