package org.scenarioservice.gherkin;

/**
 * One parse error (D8): 1-based line and column, and the parser's message. The message quotes the offending text,
 * so it is user content: {@link #toString()} leaves it out (P16), and it is never logged.
 */
public record GherkinParseError(int line, int column, String message) {

    @Override
    public String toString() {
        return "GherkinParseError[line=" + line + ", column=" + column + "]";
    }
}
