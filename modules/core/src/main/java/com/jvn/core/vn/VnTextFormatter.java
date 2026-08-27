package com.jvn.core.vn;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ICU-inspired text formatter for VN dialogue with support for:
 * - Variable interpolation: ${varName}
 * - Plurals: {count, plural, one{# item} other{# items}}
 * - Select (gender/case): {gender, select, male{He} female{She} other{They}}
 * - Number formatting: {amount, number}
 *
 * This extends VnVariableInterpolator with richer formatting capabilities.
 */
public final class VnTextFormatter {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9_.-]+)\\}");
    private static final Pattern ICU_BLOCK = Pattern.compile("\\{([A-Za-z0-9_.-]+),\\s*(plural|select|number)(?:,\\s*(.+))?\\}");
    private static final Pattern PLURAL_CASE = Pattern.compile("(zero|one|two|few|many|other|=\\d+)\\{([^}]*)\\}");
    private static final Pattern SELECT_CASE = Pattern.compile("([A-Za-z0-9_-]+)\\{([^}]*)\\}");

    private VnTextFormatter() {}

    /**
     * Format a template with variable substitution and ICU-style formatting.
     */
    public static String format(String template, Map<String, ?> variables) {
        if (template == null) return "";
        if (template.isEmpty()) return template;
        if (variables == null) variables = Map.of();

        // First pass: ICU blocks
        String result = processIcuBlocks(template, variables);

        // Second pass: simple variable interpolation
        result = interpolateVariables(result, variables);

        return result;
    }

    private static String processIcuBlocks(String template, Map<String, ?> variables) {
        Matcher matcher = ICU_BLOCK.matcher(template);
        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            String varName = matcher.group(1);
            String type = matcher.group(2);
            String options = matcher.group(3);
            Object value = variables.get(varName);

            String replacement = switch (type) {
                case "plural" -> formatPlural(value, options);
                case "select" -> formatSelect(value, options);
                case "number" -> formatNumber(value);
                default -> matcher.group(0);
            };

            sb.append(template, lastEnd, matcher.start());
            sb.append(replacement);
            lastEnd = matcher.end();
        }
        sb.append(template, lastEnd, template.length());
        return sb.toString();
    }

    private static String formatPlural(Object value, String options) {
        if (options == null || options.isBlank()) return "";

        int count = toInt(value);
        String category = getPluralCategory(count);

        // First try exact match (=N)
        Matcher caseMatcher = PLURAL_CASE.matcher(options);
        String exactMatch = null;
        String categoryMatch = null;
        String otherMatch = "";

        while (caseMatcher.find()) {
            String caseKey = caseMatcher.group(1);
            String caseValue = caseMatcher.group(2);

            if (caseKey.startsWith("=")) {
                int exactNum = Integer.parseInt(caseKey.substring(1));
                if (exactNum == count) {
                    exactMatch = caseValue;
                }
            } else if (caseKey.equals(category)) {
                categoryMatch = caseValue;
            } else if (caseKey.equals("other")) {
                otherMatch = caseValue;
            }
        }

        String result = exactMatch != null ? exactMatch : (categoryMatch != null ? categoryMatch : otherMatch);
        // Replace # with the actual count
        return result.replace("#", String.valueOf(count));
    }

    private static String formatSelect(Object value, String options) {
        if (options == null || options.isBlank()) return "";

        String key = value != null ? value.toString().toLowerCase() : "other";
        Matcher caseMatcher = SELECT_CASE.matcher(options);
        String match = null;
        String otherMatch = "";

        while (caseMatcher.find()) {
            String caseKey = caseMatcher.group(1).toLowerCase();
            String caseValue = caseMatcher.group(2);

            if (caseKey.equals(key)) {
                match = caseValue;
            } else if (caseKey.equals("other")) {
                otherMatch = caseValue;
            }
        }

        return match != null ? match : otherMatch;
    }

    private static String formatNumber(Object value) {
        if (value == null) return "0";
        if (value instanceof Number num) {
            if (value instanceof Double || value instanceof Float) {
                double d = num.doubleValue();
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    return String.valueOf((long) d);
                }
                return String.format("%.2f", d);
            }
            return String.valueOf(num);
        }
        return value.toString();
    }

    private static String interpolateVariables(String template, Map<String, ?> variables) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = variables.get(key);
            String replacement = value == null ? "" : String.valueOf(value);
            sb.append(template, lastEnd, matcher.start());
            sb.append(replacement);
            lastEnd = matcher.end();
        }
        sb.append(template, lastEnd, template.length());
        return sb.toString();
    }

    /**
     * Get CLDR plural category for English-like languages.
     * This is a simplified implementation covering common cases.
     */
    private static String getPluralCategory(int n) {
        if (n == 0) return "zero";
        if (n == 1) return "one";
        if (n == 2) return "two";
        return "other";
    }

    private static int toInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number num) return num.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
