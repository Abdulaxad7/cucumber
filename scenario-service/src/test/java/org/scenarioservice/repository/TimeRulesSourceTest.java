package org.scenarioservice.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D14 item 11, D20 #5 (source check): {@code src/main} has no {@code LocalDateTime}, {@code LocalDate},
 * {@code ZoneId.systemDefault()}, {@code new Date()} or clock-less {@code Instant.now()}; "now" always comes from the
 * injected clock. Gradle runs tests from the project directory.
 */
class TimeRulesSourceTest {

    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\bLocalDateTime\\b|\\bLocalDate\\b|ZoneId\\.systemDefault\\(\\)|Instant\\.now\\(\\)|new Date\\(\\)");

    @Test
    @DisplayName("D20 #5: no clock-less time in src/main")
    void mainCodeTakesTimeOnlyFromTheClock() throws IOException {
        Path main = Path.of("src/main/java");
        assertThat(main).isDirectory();
        try (Stream<Path> files = Files.walk(main)) {
            List<String> offenders = files.filter(file -> file.toString().endsWith(".java")).flatMap(file -> {
                try {
                    return Files.readAllLines(file).stream().filter(line -> FORBIDDEN.matcher(line).find())
                            .map(line -> file + ": " + line.trim());
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }).toList();
            assertThat(offenders).isEmpty();
        }
    }
}
