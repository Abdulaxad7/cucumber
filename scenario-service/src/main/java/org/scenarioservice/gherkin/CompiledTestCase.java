package org.scenarioservice.gherkin;

import java.util.List;

/**
 * A test case as Cucumber compiles it (D13), mapped to positions in the parsed feature: {@code scenarioIndex} is the
 * scenario's index in {@link ParsedFeature#scenariosInTextOrder()}, and {@code rowIndex} the example row's index in
 * {@link ParsedFeature#rowsInTextOrder()} ({@code null} for a Scenario). {@code line} is the scenario's or the row's
 * line. Effective tags keep each tag once, in Feature, Rule, Scenario, Examples order. No content in toString.
 */
public record CompiledTestCase(int scenarioIndex, Integer rowIndex, String name, List<String> tags,
                               List<CompiledStep> steps, int line) {

    public CompiledTestCase {
        tags = List.copyOf(tags);
        steps = List.copyOf(steps);
    }

    @Override
    public String toString() {
        return "CompiledTestCase[scenarioIndex=" + scenarioIndex + ", rowIndex=" + rowIndex + ", line=" + line + "]";
    }
}
