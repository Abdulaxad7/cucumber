package org.scenarioservice.gherkin;

import org.scenarioservice.enums.StepKeyword;

import java.util.List;

/**
 * A step: the keyword as written without trailing whitespace, its normalised type, the text (placeholders kept), and
 * at most one argument: a data table (rows of cells) or a doc string; the other is {@code null}. {@link #toString()}
 * shows no content (P16).
 */
public record ParsedStep(String keyword, StepKeyword keywordType, String text, int line,
                         List<List<String>> dataTable, ParsedDocString docString) {

    public ParsedStep {
        dataTable = dataTable == null ? null : dataTable.stream().map(List::copyOf).toList();
    }

    @Override
    public String toString() {
        return "ParsedStep[" + keywordType + ", line=" + line + "]";
    }
}
