package com.jvn.core.vn;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VnTextFormatterTest {

    @Test
    void interpolatesSimplePlaceholder() {
        String result = VnTextFormatter.format("Hello ${name}!", Map.of("name", "Ari"));
        assertEquals("Hello Ari!", result);
    }

    @Test
    void missingVariableBecomesBlank() {
        String result = VnTextFormatter.format("Score=${score}, Rank=${rank}", Map.of("score", 42));
        assertEquals("Score=42, Rank=", result);
    }

    @Test
    void multiplePlaceholdersInterpolateInOrder() {
        String result = VnTextFormatter.format(
            "${a}-${b}-${a}",
            Map.of("a", "X", "b", "Y")
        );
        assertEquals("X-Y-X", result);
    }

    @Test
    void pluralOneCase() {
        String result = VnTextFormatter.format(
            "You have {count, plural, one{# item} other{# items}}",
            Map.of("count", 1)
        );
        assertEquals("You have 1 item", result);
    }

    @Test
    void pluralOtherCase() {
        String result = VnTextFormatter.format(
            "You have {count, plural, one{# item} other{# items}}",
            Map.of("count", 5)
        );
        assertEquals("You have 5 items", result);
    }

    @Test
    void pluralZeroCategory() {
        String result = VnTextFormatter.format(
            "You have {count, plural, zero{no items} one{# item} other{# items}}",
            Map.of("count", 0)
        );
        assertEquals("You have no items", result);
    }

    @Test
    void pluralExactMatchTakesPriorityOverCategory() {
        String result = VnTextFormatter.format(
            "{count, plural, =2{a pair} other{# items}}",
            Map.of("count", 2)
        );
        assertEquals("a pair", result);
    }

    @Test
    void selectFormatsByKey() {
        String result = VnTextFormatter.format(
            "{gender, select, male{He} female{She} other{They}} arrived.",
            Map.of("gender", "female")
        );
        assertEquals("She arrived.", result);
    }

    @Test
    void selectFallsBackToOther() {
        String result = VnTextFormatter.format(
            "{gender, select, male{He} female{She} other{They}} arrived.",
            Map.of("gender", "nonbinary")
        );
        assertEquals("They arrived.", result);
    }

    @Test
    void numberFormatsIntegerValuedDouble() {
        String result = VnTextFormatter.format("Total: {amount, number}", Map.of("amount", 10.0));
        assertEquals("Total: 10", result);
    }

    @Test
    void numberFormatsFractionalDouble() {
        // formatNumber uses String.format("%.2f", d), which is locale-sensitive
        // (decimal separator varies by JVM default locale). Compare against the
        // same formatting call rather than hardcoding a separator, so this test
        // is a valid regression oracle regardless of the environment's locale.
        String expected = "Total: " + String.format("%.2f", 3.14159);
        String result = VnTextFormatter.format("Total: {amount, number}", Map.of("amount", 3.14159));
        assertEquals(expected, result);
    }

    @Test
    void icuBlockThenSimpleInterpolationCombined() {
        String result = VnTextFormatter.format(
            "${name} has {count, plural, one{# coin} other{# coins}}.",
            Map.of("name", "Ari", "count", 3)
        );
        assertEquals("Ari has 3 coins.", result);
    }

    @Test
    void variableValueContainingDollarSignIsAppendedLiterally() {
        // This is the key regression case for dropping Matcher.quoteReplacement:
        // appendReplacement treats '$' specially in the replacement string, so if
        // quoteReplacement were dropped incorrectly (or applied to the wrong side),
        // a literal '$' in a variable's value could be misinterpreted as a group
        // reference. The manual append-loop must emit it completely literally.
        String result = VnTextFormatter.format("Price: ${amount}", Map.of("amount", "$5"));
        assertEquals("Price: $5", result);
    }

    @Test
    void variableValueContainingBackslashIsAppendedLiterally() {
        String result = VnTextFormatter.format("Path: ${p}", Map.of("p", "C:\\data\\file"));
        assertEquals("Path: C:\\data\\file", result);
    }

    @Test
    void variableValueContainingDollarAndGroupLikeReferenceIsLiteral() {
        // A value that looks like a backreference ($1) must not be treated as one.
        String result = VnTextFormatter.format("Code: ${code}", Map.of("code", "$1{weird}"));
        assertEquals("Code: $1{weird}", result);
    }

    @Test
    void pluralReplacementContainingDollarSignIsLiteral() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("count", 1);
        String result = VnTextFormatter.format(
            "{count, plural, one{Costs $#} other{Costs $# each}}",
            vars
        );
        assertEquals("Costs $1", result);
    }

    @Test
    void emptyTemplateReturnsEmpty() {
        assertEquals("", VnTextFormatter.format("", Map.of()));
    }

    @Test
    void nullTemplateReturnsEmptyString() {
        assertEquals("", VnTextFormatter.format(null, Map.of()));
    }

    @Test
    void templateWithNoPlaceholdersIsUnchanged() {
        String result = VnTextFormatter.format("Just plain text.", Map.of());
        assertEquals("Just plain text.", result);
    }

    @Test
    void nullVariablesMapTreatedAsEmpty() {
        String result = VnTextFormatter.format("Hello ${name}!", null);
        assertEquals("Hello !", result);
    }
}
