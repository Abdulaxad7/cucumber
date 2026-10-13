package com.example.authservice.qa;

import com.example.authservice.model.Project;
import com.example.authservice.model.User;
import com.example.authservice.repository.ProjectRepository;
import com.example.authservice.support.AcmeFixture;
import com.example.authservice.support.AuthServiceIntegrationTest;
import com.example.authservice.support.ProjectFixture;
import com.example.authservice.support.ProjectFixture.Projects;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static com.example.authservice.qa.QaSupport.as;
import static com.example.authservice.qa.QaSupport.json;
import static com.example.authservice.support.ProblemAssertions.assertProblem;
import static com.example.authservice.support.ProjectCalls.project;
import static com.example.authservice.support.ProjectCalls.projects;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * qa adversarial checks for project names (D5, AC-1.3, AC-1.4, AC-1.8, AC-3.2, AC-3.3, A5): Unicode and
 * case-insensitive duplicates, whitespace and control characters, the length counted before trimming, and the full
 * rename error order (400, then 403, then PROJECT_ARCHIVED, then PROJECT_NAME_TAKEN).
 * <p>
 * Names that are blank only by Unicode's definition (no-break space, zero-width space, BOM, NEL), or that contain
 * control characters, belong to the deferred FU-3. For them this class only asserts what holds either way: a 201 or
 * a 400 VALIDATION_FAILED, never a 5xx, and a stored name equal to the input with {@code String.trim()} applied. The
 * observed answers are recorded in the qa report.
 */
@AuthServiceIntegrationTest
@Import(ProjectFixture.class)
class QaProjectNamesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectFixture fixture;

    @Autowired
    private ProjectRepository projectRepository;

    private Projects projects;
    private UUID acme;
    private String acmeProjects;
    private String checkout;

    @BeforeEach
    void seed() {
        projects = fixture.seed();
        acme = projects.acme().orgId();
        acmeProjects = projects(acme);
        checkout = project(acme, projects.checkout());
    }

    @ParameterizedTest(name = "qa AC-1.4: \"{0}\" then \"{1}\" is PROJECT_NAME_TAKEN")
    @CsvSource({"ÜNÏCØDÉ, ünïcødé", "ПРОЕКТ, проект", "ΣΟΦΊΑ, σοφία", "Ärger, ÄRGER", "Café Crème, CAFÉ CRÈME",
            "日本語, 日本語"})
    @Tag("AC-1.4")
    @DisplayName("qa AC-1.4: non-ASCII names are compared case-insensitively")
    void nonAsciiNamesAreComparedCaseInsensitively(String first, String second) throws Exception {
        // Given
        assertThat(create(first).getResponse().getStatus()).isEqualTo(201);

        // When
        MvcResult result = create(second);

        // Then
        assertProblem(result, 409, "PROJECT_NAME_TAKEN", acmeProjects);
        assertThat(acmeNames()).hasSize(3);
    }

    @ParameterizedTest(name = "qa AC-3.2: rename Checkout to \"{0}\" after creating \"{1}\" is PROJECT_NAME_TAKEN")
    @CsvSource({"проект, ПРОЕКТ", "ünïcødé, ÜNÏCØDÉ"})
    @Tag("AC-3.2")
    @DisplayName("qa AC-3.2: rename uses the same case-insensitive comparison for non-ASCII names")
    void renameComparesNonAsciiNamesCaseInsensitively(String renameTo, String existing) throws Exception {
        // Given
        assertThat(create(existing).getResponse().getStatus()).isEqualTo(201);

        // When
        MvcResult result = rename(projects.acme().cai(), renameTo);

        // Then
        assertProblem(result, 409, "PROJECT_NAME_TAKEN", checkout);
        assertThat(checkoutName()).isEqualTo("Checkout");
    }

    @ParameterizedTest(name = "qa D5: \"{0}\" and \"{1}\" are different names under Locale.ROOT lower-casing")
    @CsvSource({"İzmir, izmir", "STRASSE, straße"})
    @Tag("AC-1.4")
    @DisplayName("qa D5: names are compared lower-cased with Locale.ROOT, without locale-specific folding")
    void namesAreComparedWithLocaleRootLowerCasing(String first, String second) throws Exception {
        // Given: D5 compares trim().toLowerCase(Locale.ROOT); "İ" lowers to "i̇", and "ß" doesn't fold to "ss"
        assertThat(create(first).getResponse().getStatus()).isEqualTo(201);

        // When
        MvcResult result = create(second);

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }

    @ParameterizedTest(name = "qa AC-1.4 / AC-1.8: \"{0}\" is trimmed to Checkout, so PROJECT_NAME_TAKEN")
    @ValueSource(strings = {"\\tCheckout", "Checkout\\n", "\\r\\nCHECKOUT\\t ", "\\u0000Checkout", "Checkout\\u001F"})
    @Tag("AC-1.4")
    @Tag("AC-1.8")
    @DisplayName("qa AC-1.4: ASCII whitespace and control characters at the ends are trimmed before comparing")
    void asciiWhitespaceAndControlCharactersAtTheEndsAreTrimmed(String name) throws Exception {
        // When
        MvcResult result = create(name);

        // Then
        assertProblem(result, 409, "PROJECT_NAME_TAKEN", acmeProjects);
    }

    @ParameterizedTest(name = "qa AC-1.3: whitespace-only \"{0}\" is VALIDATION_FAILED")
    @ValueSource(strings = {"\\t", "\\n\\r", " \\t \\n ", "\\u3000", "\\u2003", "\\u2028"})
    @Tag("AC-1.3")
    @DisplayName("qa AC-1.3: names made only of whitespace that Java treats as whitespace are blank")
    void whitespaceOnlyNamesAreBlank(String name) throws Exception {
        // When
        MvcResult result = create(name);

        // Then
        DocumentContext problem = assertProblem(result, 400, "VALIDATION_FAILED", acmeProjects);
        assertThat(problem.read("$.errors[*].field", List.class)).containsExactly("name");
        assertThat(acmeNames()).containsExactlyInAnyOrder("Billing", "Checkout");
    }

    @ParameterizedTest(name = "qa FU-3 characterisation: \"{0}\" is never a 5xx")
    @ValueSource(strings = {"\\u00A0", "\\u200B", "\\uFEFF", "\\u0085", "a\\u0007b", "a\\u007Fb", "a\\u0000b",
            "\\u00A0Checkout", "\\uD800", "e\\u0301"})
    @Tag("AC-1.3")
    @DisplayName("qa FU-3 (deferred): Unicode-blank and control-character names are a 201 or a 400, never a 5xx")
    void unicodeBlankAndControlCharacterNamesAreNeverA5xx(String name) throws Exception {
        // When
        MvcResult result = create(name);

        // Then
        int status = result.getResponse().getStatus();
        assertThat(status).isIn(201, 400);
        if (status == 400) {
            assertProblem(result, 400, "VALIDATION_FAILED", acmeProjects);
            return;
        }
        String expected = JsonPath.<String>read("{\"n\":\"" + name + "\"}", "$.n").trim();
        UUID id = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
        assertThat(projectRepository.findById(id).orElseThrow().getName()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "qa D5 Q3: {0}")
    @CsvSource(delimiter = '|', value = {
            "50 emoji (100 UTF-16 code units)          | EMOJI_50   | 201",
            "a space then 99 characters               | SPACE_99   | 201",
            "100 characters then a tab                | TAB_100    | 400",
            "100 characters between two spaces         | SPACES_100 | 400"})
    @Tag("AC-1.3")
    @DisplayName("qa D5 Q3: the 100 limit is counted on the name as sent, before trimming")
    void theLimitIsCountedOnTheNameAsSent(String description, String shape, int status) throws Exception {
        // Given
        String name = switch (shape) {
            case "EMOJI_50" -> "\\uD83D\\uDE00".repeat(50);
            case "SPACE_99" -> " " + "b".repeat(99);
            case "TAB_100" -> "c".repeat(100) + "\\t";
            default -> " " + "d".repeat(100) + " ";
        };

        // When
        MvcResult result = create(name);

        // Then
        if (status == 201) {
            assertThat(result.getResponse().getStatus()).as(description).isEqualTo(201);
            assertThat(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.name").length())
                    .isLessThanOrEqualTo(100);
        } else {
            assertProblem(result, 400, "VALIDATION_FAILED", acmeProjects);
        }
    }

    @Test
    @Tag("AC-3.3")
    @DisplayName("qa D5 Q3: rename counts the length before trimming too")
    void renameCountsTheLengthBeforeTrimming() throws Exception {
        // When
        MvcResult tooLong = rename(projects.acme().cai(), " " + "e".repeat(100));
        MvcResult exactly = rename(projects.acme().cai(), "f".repeat(100));

        // Then
        assertProblem(tooLong, 400, "VALIDATION_FAILED", checkout);
        assertThat(exactly.getResponse().getStatus()).isEqualTo(200);
        assertThat(checkoutName()).isEqualTo("f".repeat(100));
    }

    @ParameterizedTest(name = "qa D5 rename order: {0}")
    @CsvSource(delimiter = '|', value = {
            "outsider, invalid name                      | eve | CHECKOUT | ACTIVE   | BLANK    | 400 | VALIDATION_FAILED",
            "outsider, 101 characters on archived         | eve | CHECKOUT | ARCHIVED | LONG     | 400 | VALIDATION_FAILED",
            "member, invalid name on archived             | gus | CHECKOUT | ARCHIVED | BLANK    | 400 | VALIDATION_FAILED",
            "admin, space plus 100 characters on archived | cai | CHECKOUT | ARCHIVED | PADDED   | 400 | VALIDATION_FAILED",
            "unknown project, invalid name               | ana | UNKNOWN  | ACTIVE   | BLANK    | 400 | VALIDATION_FAILED",
            "outsider, taken name                        | eve | CHECKOUT | ACTIVE   | TAKEN    | 403 | FORBIDDEN",
            "member, taken name on archived              | gus | CHECKOUT | ARCHIVED | TAKEN    | 403 | FORBIDDEN",
            "reader, taken name on archived              | dan | CHECKOUT | ARCHIVED | TAKEN    | 403 | FORBIDDEN",
            "unknown project, taken name                 | ana | UNKNOWN  | ACTIVE   | TAKEN    | 403 | FORBIDDEN",
            "implicit admin, taken name on archived      | ben | CHECKOUT | ARCHIVED | TAKEN    | 409 | PROJECT_ARCHIVED",
            "explicit admin, own name on archived        | cai | CHECKOUT | ARCHIVED | OWN      | 409 | PROJECT_ARCHIVED",
            "explicit admin, taken name                  | cai | CHECKOUT | ACTIVE   | TAKEN    | 409 | PROJECT_NAME_TAKEN",
            "implicit admin, padded taken name           | ana | CHECKOUT | ACTIVE   | TAKEN_PADDED | 409 | PROJECT_NAME_TAKEN"})
    @Tag("AC-3.1")
    @Tag("AC-3.2")
    @Tag("AC-3.3")
    @Tag("AC-3.6")
    @DisplayName("qa D5: the rename error order is 400, then 403, then PROJECT_ARCHIVED, then PROJECT_NAME_TAKEN")
    void theRenameErrorOrder(String description, String actor, String target, String state, String name, int status,
                             String code) throws Exception {
        // Given
        if (state.equals("ARCHIVED")) {
            assertThat(perform(post(checkout + "/archive"), projects.acme().ana(), null).getResponse().getStatus())
                    .isEqualTo(200);
        }
        UUID projectId = target.equals("UNKNOWN") ? AcmeFixture.UNKNOWN_ID : projects.checkout();
        String path = project(acme, projectId);
        String body = switch (name) {
            case "BLANK" -> "{\"name\":\"   \"}";
            case "LONG" -> "{\"name\":\"" + "g".repeat(101) + "\"}";
            case "PADDED" -> "{\"name\":\" " + "h".repeat(100) + "\"}";
            case "TAKEN" -> "{\"name\":\"Billing\"}";
            case "TAKEN_PADDED" -> "{\"name\":\"  bILLING \\t\"}";
            default -> "{\"name\":\"Checkout\"}";
        };

        // When
        MvcResult result = perform(patch(path), user(actor), body);

        // Then
        assertProblem(result, status, code, path);
        Project stored = projectRepository.findById(projects.checkout()).orElseThrow();
        assertThat(stored.getName()).as(description).isEqualTo("Checkout");
    }

    @Test
    @Tag("AC-3.2")
    @DisplayName("qa AC-3.2: renaming to the own name with padding stores it trimmed")
    void renamingToTheOwnNameWithPaddingStoresItTrimmed() throws Exception {
        // When
        MvcResult result = rename(projects.acme().cai(), "  checkout\\t");

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(checkoutName()).isEqualTo("checkout");
    }

    @Test
    @Tag("AC-3.2")
    @DisplayName("qa AC-3.2: a name freed by a rename can be taken by another project")
    void aFreedNameCanBeTaken() throws Exception {
        // Given
        String billing = project(acme, projects.billing());
        assertThat(perform(patch(billing), projects.acme().ana(), "{\"name\":\"Billing Old\"}").getResponse()
                .getStatus()).isEqualTo(200);

        // When
        MvcResult result = rename(projects.acme().cai(), "billing");

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(checkoutName()).isEqualTo("billing");
    }

    @Test
    @Tag("AC-2.1")
    @DisplayName("qa A5: the list is ordered by name ignoring case")
    void theListIsOrderedByNameIgnoringCase() throws Exception {
        // Given
        for (String name : List.of("beta", "Alpha", "ALPHA 2", "zeta", "Charlie")) {
            assertThat(create(name).getResponse().getStatus()).isEqualTo(201);
        }

        // When
        MvcResult result = perform(get(acmeProjects), projects.acme().ana(), null);

        // Then
        assertThat(JsonPath.<List<String>>read(result.getResponse().getContentAsString(), "$[*].name"))
                .containsExactly("Alpha", "ALPHA 2", "beta", "Billing", "Charlie", "Checkout", "zeta");
    }

    private MvcResult create(String jsonEscapedName) throws Exception {
        return perform(post(acmeProjects), projects.acme().ana(), "{\"name\":\"" + jsonEscapedName + "\"}");
    }

    private MvcResult rename(User actor, String jsonEscapedName) throws Exception {
        return perform(patch(checkout), actor, "{\"name\":\"" + jsonEscapedName + "\"}");
    }

    private MvcResult perform(MockHttpServletRequestBuilder request, User actor,
                              String body) throws Exception {
        MockHttpServletRequestBuilder withActor = as(request, actor);
        return mockMvc.perform(body == null ? withActor : json(withActor, body)).andReturn();
    }

    private User user(String name) {
        return switch (name) {
            case "ana" -> projects.acme().ana();
            case "ben" -> projects.acme().ben();
            case "cai" -> projects.acme().cai();
            case "dan" -> projects.acme().dan();
            case "eve" -> projects.acme().eve();
            case "gus" -> projects.gus();
            default -> throw new IllegalArgumentException(name);
        };
    }

    private String checkoutName() {
        return projectRepository.findById(projects.checkout()).orElseThrow().getName();
    }

    private List<String> acmeNames() {
        return projectRepository.findByOrganizationId(acme).stream().map(Project::getName).toList();
    }
}
