// qa (frontend phase 1): AC-6.2, AC-6.3 / D12. scenario-service 1.0.7: `column` counts Unicode code points, an emoji
// outside the BMP is one column and a combining mark is a column of its own. The diagnostic must start exactly before
// the character the parser means, never inside a surrogate pair, and clamp at the line's end.
import {describe, expect, it} from 'vitest';
import {gherkinSummary, offsetFor, toDiagnostics} from '../columns';

/** The 1-based code-point column of `needle`'s first occurrence on `line`. */
function columnOf(line: string, needle: string): number {
    return [...line.slice(0, line.indexOf(needle))].length + 1;
}

function isSurrogateSplit(doc: string, offset: number): boolean {
    const before = doc.charCodeAt(offset - 1);
    return offset > 0 && before >= 0xd800 && before <= 0xdbff;
}

const LINES = [
    '  Sc\u00e9nario: Pay', // precomposed é (1 code point)
    '  Sce\u0301nario: Pay', // e + combining acute (2 code points)
    '  Scenario: 💳 Pay', // astral emoji
    '  Scenario: 👩‍👩‍👧 Pay', // ZWJ family: 5 code points, 8 UTF-16 units
    '  Scenario: 🇺🇿 Pay', // flag: 2 regional indicators
    '  Scenario: 𝒫𝒶𝓎 Pay', // mathematical script letters (astral, not emoji)
    '\tScenario: ​ Pay', // tab and zero-width space
];

describe('qa: code-point columns (AC-6.3, D12)', () => {
    it.each(LINES.map((line) => [line]))('the column of "P" in %j lands on "P"', (line) => {
        const doc = `Feature: X\n${line}\n    Given y\n`;
        const column = columnOf(line, 'Pay');
        const offset = offsetFor(doc, 2, column);
        expect(doc.slice(offset, offset + 3)).toBe('Pay');
        const [diagnostic] = toDiagnostics(doc, [{line: 2, column, message: 'm'}]);
        expect(doc.slice(diagnostic?.from, diagnostic?.to)).toBe('P');
    });

    it('every column on an astral line maps to a character boundary, never inside a pair', () => {
        const line = 'a💳b👩‍👩‍👧c🇺🇿d';
        const doc = `${line}\n`;
        const codePoints = [...line];
        for (let column = 1; column <= codePoints.length + 3; column += 1) {
            const offset = offsetFor(doc, 1, column);
            expect(isSurrogateSplit(doc, offset)).toBe(false);
            if (column <= codePoints.length) expect(doc.codePointAt(offset)).toBe(codePoints[column - 1]?.codePointAt(0));
            else expect(offset).toBe(line.length);
        }
    });

    it('a combining mark is a column of its own', () => {
        const doc = 'xe\u0301y\n';
        expect(doc.slice(offsetFor(doc, 1, 3), offsetFor(doc, 1, 3) + 1)).toBe('\u0301');
        expect(doc.slice(offsetFor(doc, 1, 4), offsetFor(doc, 1, 4) + 1)).toBe('y');
    });

    it.each([
        [0, 0],
        [-5, 0],
        [1, 0],
        [999, 3],
    ])('column %i on "abc" clamps to offset %i', (column, expected) => {
        expect(offsetFor('abc\nnext', 1, column)).toBe(expected);
    });

    it.each([
        [0, 0],
        [-1, 0],
        [2, 3],
        [3, 3],
        [99, 3],
    ])('line %i in "ab\\ncd" (no final newline) maps to offset %i', (line, expected) => {
        // line 0 or below means line 1; a line past the end means the last line
        expect(offsetFor('ab\ncd', line, 1)).toBe(expected);
    });

    it('an empty document gives an empty diagnostic at 0', () => {
        expect(toDiagnostics('', [{line: 1, column: 1, message: 'The text has no Feature'}])).toEqual([
            {from: 0, to: 0, severity: 'error', source: 'Gherkin', message: 'The text has no Feature'},
        ]);
    });

    it('an error at the end of a line is empty and stays on that line', () => {
        const doc = 'Feature: X\n  Scenario: Pay\n';
        const [d] = toDiagnostics(doc, [{line: 2, column: 99, message: 'm'}]);
        expect(d?.from).toBe(d?.to);
        expect(doc.slice(0, d?.from).split('\n').length).toBe(2);
    });

    it('the live region counts errors and names the lowest line whatever the order', () => {
        expect(gherkinSummary([
            {line: 9, column: 1, message: 'a'},
            {line: 2, column: 4, message: 'b'},
            {line: 5, column: 1, message: 'c'},
        ])).toBe('3 Gherkin errors; first on line 2');
    });
});
