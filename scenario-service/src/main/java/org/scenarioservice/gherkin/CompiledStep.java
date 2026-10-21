package org.scenarioservice.gherkin;

import java.util.List;

/**
 * A step of a test case, as Cucumber's pickle has it (D13): the keyword as written, the text and argument with
 * placeholders replaced. {@link #toString()} shows no content (P16).
 */
public record CompiledStep(String keyword, String text, List<List<String>> dataTable, ParsedDocString docString) {

    public CompiledStep {
        dataTable = dataTable == null ? null : dataTable.stream().map(List::copyOf).toList();
    }

    @Override
    public String toString() {
        return "CompiledStep[]";
    }
}
