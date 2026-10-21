package org.scenarioservice.service.impl;

import org.scenarioservice.dto.response.DocStringResponse;
import org.scenarioservice.dto.response.ExampleRowResponse;
import org.scenarioservice.dto.response.ExamplesResponse;
import org.scenarioservice.dto.response.FeatureChildResponse;
import org.scenarioservice.dto.response.FeatureResponse;
import org.scenarioservice.dto.response.FeatureSummaryResponse;
import org.scenarioservice.dto.response.RuleResponse;
import org.scenarioservice.dto.response.ScenarioResponse;
import org.scenarioservice.dto.response.StepResponse;
import org.scenarioservice.gherkin.GherkinAdapter;
import org.scenarioservice.gherkin.GherkinParseResult;
import org.scenarioservice.gherkin.ParsedChild;
import org.scenarioservice.gherkin.ParsedExamples;
import org.scenarioservice.gherkin.ParsedFeature;
import org.scenarioservice.gherkin.ParsedRule;
import org.scenarioservice.gherkin.ParsedScenario;
import org.scenarioservice.gherkin.ParsedStep;
import org.scenarioservice.model.ExampleRow;
import org.scenarioservice.model.Examples;
import org.scenarioservice.model.Feature;
import org.scenarioservice.model.Rule;
import org.scenarioservice.model.Scenario;
import org.scenarioservice.model.Step;
import org.scenarioservice.repository.ExampleRowRepository;
import org.scenarioservice.repository.ExamplesRepository;
import org.scenarioservice.repository.RuleRepository;
import org.scenarioservice.repository.ScenarioRepository;
import org.scenarioservice.repository.StepRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds feature responses inside the use case's transaction. Texts, keywords and tags (in text order) come from a
 * parse of the current version, which the stored structure always reflects (D8); the stored rows give the ids,
 * matched by position (rules and scenarios within their container, steps, examples and rows within their parent).
 * A stored structure that doesn't match its text is an internal error.
 */
@Component
public class FeatureViews {

    private final GherkinAdapter gherkin;
    private final RuleRepository rules;
    private final ScenarioRepository scenarios;
    private final StepRepository steps;
    private final ExamplesRepository examples;
    private final ExampleRowRepository exampleRows;

    public FeatureViews(GherkinAdapter gherkin, RuleRepository rules, ScenarioRepository scenarios,
                        StepRepository steps, ExamplesRepository examples, ExampleRowRepository exampleRows) {
        this.gherkin = gherkin;
        this.rules = rules;
        this.scenarios = scenarios;
        this.steps = steps;
        this.examples = examples;
        this.exampleRows = exampleRows;
    }

    public FeatureSummaryResponse summary(Feature feature, String source) {
        ParsedFeature parsed = parse(source);
        return new FeatureSummaryResponse(feature.getId(), feature.getProjectId(), feature.getPath(),
                feature.getName(), feature.getLanguage(), feature.getCurrentVersion(), parsed.tags(),
                gherkin.testCases(source).size(), feature.getCreatedBy(), feature.getCreatedAt(),
                feature.getUpdatedBy(), feature.getUpdatedAt(), feature.getArchivedAt());
    }

    public FeatureResponse feature(Feature feature, String source) {
        return build(feature, source, parse(source), load(feature.getId()));
    }

    /**
     * The response of a write, from the text it parsed and the rows it just stored (D21 #2): no read-back and no second
     * parse, so the project lock is held only for the writes.
     */
    public FeatureResponse written(Feature feature, String source, ParsedFeature parsed,
                                   FeatureStructure.Written written) {
        return build(feature, source, parsed, stored(written.rules(), written.scenarios(), written.steps(),
                written.examples(), written.rows()));
    }

    private FeatureResponse build(Feature feature, String source, ParsedFeature parsed, Stored stored) {
        List<Object> featureLevel = new ArrayList<>(stored.rules());
        featureLevel.addAll(stored.scenariosIn(null));
        featureLevel.sort(Comparator.comparingInt(FeatureViews::position));
        require(featureLevel.size() == parsed.children().size());
        List<FeatureChildResponse> children = new ArrayList<>();
        for (int i = 0; i < featureLevel.size(); i++) {
            ParsedChild child = parsed.children().get(i);
            if (child instanceof ParsedRule rule && featureLevel.get(i) instanceof Rule storedRule) {
                children.add(rule(storedRule, rule, stored));
            } else if (child instanceof ParsedScenario scenario && featureLevel.get(i) instanceof Scenario row) {
                children.add(scenario(row, scenario, stored));
            } else {
                require(false);
            }
        }
        return new FeatureResponse(feature.getId(), feature.getProjectId(), feature.getPath(), feature.getName(),
                feature.getDescription(), feature.getLanguage(), feature.getCurrentVersion(), source, parsed.tags(),
                children, feature.getCreatedBy(), feature.getCreatedAt(), feature.getUpdatedBy(),
                feature.getUpdatedAt(), feature.getArchivedAt());
    }

    private RuleResponse rule(Rule stored, ParsedRule parsed, Stored all) {
        List<Scenario> children = all.scenariosIn(stored.getId());
        require(children.size() == parsed.children().size());
        List<ScenarioResponse> responses = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            responses.add(scenario(children.get(i), parsed.children().get(i), all));
        }
        return new RuleResponse(stored.getId(), RuleResponse.KIND, parsed.keyword(), parsed.name(),
                parsed.description(), parsed.line(), parsed.tags(), responses);
    }

    private ScenarioResponse scenario(Scenario stored, ParsedScenario parsed, Stored all) {
        require(stored.getKind() == parsed.kind() && stored.getLine() == parsed.line());
        List<Step> storedSteps = all.stepsOf(stored.getId());
        require(storedSteps.size() == parsed.steps().size());
        List<StepResponse> stepResponses = new ArrayList<>();
        for (int i = 0; i < storedSteps.size(); i++) {
            ParsedStep step = parsed.steps().get(i);
            stepResponses.add(new StepResponse(storedSteps.get(i).getId(), step.keyword(), step.keywordType().name(),
                    step.text(), step.line(), step.dataTable(), step.docString() == null ? null
                    : new DocStringResponse(step.docString().content(), step.docString().mediaType())));
        }
        List<Examples> storedExamples = all.examplesOf(stored.getId());
        require(storedExamples.size() == parsed.examples().size());
        List<ExamplesResponse> examplesResponses = new ArrayList<>();
        for (int i = 0; i < storedExamples.size(); i++) {
            ParsedExamples block = parsed.examples().get(i);
            List<ExampleRow> storedRows = all.rowsOf(storedExamples.get(i).getId());
            require(storedRows.size() == block.rows().size());
            List<ExampleRowResponse> rowResponses = new ArrayList<>();
            for (int j = 0; j < storedRows.size(); j++) {
                rowResponses.add(new ExampleRowResponse(storedRows.get(j).getId(), block.rows().get(j).line(),
                        block.rows().get(j).cells()));
            }
            examplesResponses.add(new ExamplesResponse(storedExamples.get(i).getId(), block.keyword(), block.name(),
                    block.description(), block.line(), block.tags(), block.header(), rowResponses));
        }
        return new ScenarioResponse(stored.getId(), parsed.kind().name(), parsed.keyword(), parsed.name(),
                parsed.description(), parsed.line(), parsed.tags(), stepResponses, examplesResponses);
    }

    private ParsedFeature parse(String source) {
        if (gherkin.parse(source) instanceof GherkinParseResult.Valid valid) {
            return valid.feature();
        }
        throw new IllegalStateException("A stored version is not valid Gherkin");
    }

    private Stored load(UUID featureId) {
        List<Rule> storedRules = rules.findByFeatureId(featureId);
        List<Scenario> storedScenarios = scenarios.findByFeatureId(featureId);
        List<UUID> scenarioIds = storedScenarios.stream().map(Scenario::getId).toList();
        List<Step> storedSteps = scenarioIds.isEmpty() ? List.of() : steps.findByScenarioIdIn(scenarioIds);
        List<Examples> storedExamples = scenarioIds.isEmpty() ? List.of()
                : examples.findByScenarioIdIn(scenarioIds);
        List<UUID> examplesIds = storedExamples.stream().map(Examples::getId).toList();
        List<ExampleRow> storedRows = examplesIds.isEmpty() ? List.of()
                : exampleRows.findByExamplesIdIn(examplesIds);
        return stored(storedRules, storedScenarios, storedSteps, storedExamples, storedRows);
    }

    private static Stored stored(List<Rule> rules, List<Scenario> scenarios, List<Step> steps, List<Examples> examples,
                                 List<ExampleRow> rows) {
        return new Stored(rules, scenarios, group(steps, Step::getScenarioId, Step::getPosition),
                group(examples, Examples::getScenarioId, Examples::getPosition),
                group(rows, ExampleRow::getExamplesId, ExampleRow::getPosition));
    }

    private static <T> Map<UUID, List<T>> group(List<T> rows, Function<T, UUID> parent, Function<T, Integer> position) {
        return rows.stream().sorted(Comparator.comparing(position))
                .collect(Collectors.groupingBy(parent, Collectors.toList()));
    }

    private static int position(Object rowOrRule) {
        return rowOrRule instanceof Rule rule ? rule.getPosition() : ((Scenario) rowOrRule).getPosition();
    }

    private static void require(boolean consistent) {
        if (!consistent) {
            throw new IllegalStateException("The stored structure doesn't match the current version's text");
        }
    }

    private record Stored(List<Rule> rules, List<Scenario> scenarios, Map<UUID, List<Step>> steps,
                          Map<UUID, List<Examples>> examples, Map<UUID, List<ExampleRow>> rows) {

        List<Scenario> scenariosIn(UUID ruleId) {
            return scenarios.stream().filter(scenario -> Objects.equals(scenario.getRuleId(), ruleId))
                    .sorted(Comparator.comparingInt(Scenario::getPosition)).toList();
        }

        List<Step> stepsOf(UUID scenarioId) {
            return steps.getOrDefault(scenarioId, List.of());
        }

        List<Examples> examplesOf(UUID scenarioId) {
            return examples.getOrDefault(scenarioId, List.of());
        }

        List<ExampleRow> rowsOf(UUID examplesId) {
            return rows.getOrDefault(examplesId, List.of());
        }
    }
}
