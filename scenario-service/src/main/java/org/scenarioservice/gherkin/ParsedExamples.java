package org.scenarioservice.gherkin;

import java.util.List;

/**
 * One Examples block: its header (the table's first row) and data rows; both empty when the block has no table
 * (D19 #7). {@link #toString()} shows no content (P16).
 */
public record ParsedExamples(String keyword, String name, String description, int line, List<String> tags,
                             List<String> header, List<ParsedRow> rows) {

    public ParsedExamples {
        tags = List.copyOf(tags);
        header = List.copyOf(header);
        rows = List.copyOf(rows);
    }

    @Override
    public String toString() {
        return "ParsedExamples[line=" + line + ", rows=" + rows.size() + "]";
    }
}
