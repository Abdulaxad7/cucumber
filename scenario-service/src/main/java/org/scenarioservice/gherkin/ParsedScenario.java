package org.scenarioservice.gherkin;

import org.scenarioservice.enums.ScenarioKind;

import java.util.List;

/**
 * A Background, Scenario or Scenario Outline. A Background has no tags and no examples. {@link #toString()} shows no
 * content (P16).
 */
public record ParsedScenario(ScenarioKind kind, String keyword, String name, String description, int line,
                             List<String> tags, List<ParsedStep> steps, List<ParsedExamples> examples)
        implements ParsedChild {

    public ParsedScenario {
        tags = List.copyOf(tags);
        steps = List.copyOf(steps);
        examples = List.copyOf(examples);
    }

    @Override
    public String toString() {
        return "ParsedScenario[kind=" + kind + ", line=" + line + ", steps=" + steps.size() + "]";
    }
}
