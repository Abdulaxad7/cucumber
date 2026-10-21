package com.example.authservice.controller;

import com.example.authservice.repository.UserRepository;
import com.example.authservice.support.AcmeFixture;
import com.example.authservice.support.AuthServiceIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.example.authservice.support.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * AC-2.11: the losers of simultaneous registrations of one email get the same 409 EMAIL_ALREADY_REGISTERED as a late
 * registration, and exactly one account exists. Concurrency is probabilistic, so the scenario is repeated.
 */
@AuthServiceIntegrationTest
class RegistrationRaceTest {

    private static final int CALLERS = 5;
    private static final String ZED = "{\"email\":\"zed@example.com\",\"password\":\"Secret123!\",\"fullName\":\"Zed\"}";
    private static final String REGISTER = "/api/auth/register";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AcmeFixture fixture;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void emptyDatabase() {
        fixture.reset();
    }

    @RepeatedTest(value = 5, name = "AC-2.11 five simultaneous registrations of one email (run {currentRepetition})")
    @Tag("AC-2.11")
    void losersOfASimultaneousRegistrationGetTheSameConflictAsALateOne() throws Exception {
        // When
        List<MvcResult> results = registerSimultaneously();

        // Then
        assertThat(results).filteredOn(result -> result.getResponse().getStatus() == 201).hasSize(1);
        List<MvcResult> losers = results.stream().filter(result -> result.getResponse().getStatus() != 201).toList();
        assertThat(losers).hasSize(CALLERS - 1);
        for (MvcResult loser : losers) {
            assertProblem(loser, 409, "EMAIL_ALREADY_REGISTERED", REGISTER);
        }
        assertThat(userRepository.findAll()).filteredOn(user -> user.getEmail().equals("zed@example.com")).hasSize(1);
    }

    private List<MvcResult> registerSimultaneously() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(CALLERS);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (int i = 0; i < CALLERS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(ZED))
                            .andReturn();
                }));
            }
            start.countDown();
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
