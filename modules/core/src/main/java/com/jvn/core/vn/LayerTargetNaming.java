package com.jvn.core.vn;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Pure, static naming-convention helpers for resolving Puppeteer/VNS layer
 * and group ids to the runtime entity names {@code VnCharacterSceneAccessor}
 * proxies are registered under (e.g. {@code hero_arm_l},
 * {@code hero_happy_arm_l}). Shared between the fx-module runtime resolver
 * ({@code VnRenderer}) and editor-module diagnostics
 * ({@code PuppeteerVerification}), which have no other common dependency.
 */
public final class LayerTargetNaming {
    private LayerTargetNaming() {
    }

    public static String selectorSafeName(String raw) {
        String value = raw == null ? "" : raw.trim();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isLetterOrDigit(ch) || ch == '_') {
                out.append(ch);
            } else {
                out.append('_');
            }
        }
        String cleaned = out.toString().replaceAll("_+", "_");
        while (cleaned.startsWith("_")) cleaned = cleaned.substring(1);
        while (cleaned.endsWith("_")) cleaned = cleaned.substring(0, cleaned.length() - 1);
        return cleaned;
    }

    public static List<String> layerTargetNames(String characterId, String expression, String layerId) {
        String safeCharacter = selectorSafeName(characterId);
        String safeExpression = selectorSafeName(expression == null || expression.isBlank() ? "neutral" : expression);
        String safeLayer = selectorSafeName(layerId);
        if (safeCharacter.isBlank() || safeExpression.isBlank() || safeLayer.isBlank()) return List.of();
        LinkedHashSet<String> names = new LinkedHashSet<>();
        names.add(safeCharacter + "_" + safeExpression + "_" + safeLayer);
        names.add(safeCharacter + "_" + safeLayer);
        return List.copyOf(names);
    }

    public static List<String> groupTargetNames(String characterId, String expression, String groupId) {
        String safeCharacter = selectorSafeName(characterId);
        String safeExpression = selectorSafeName(expression == null || expression.isBlank() ? "neutral" : expression);
        String safeGroup = selectorSafeName(groupId);
        if (safeCharacter.isBlank() || safeExpression.isBlank() || safeGroup.isBlank()) return List.of();
        LinkedHashSet<String> names = new LinkedHashSet<>();
        names.add(safeCharacter + "_" + safeGroup);
        names.add(safeCharacter + "_" + safeExpression + "_" + safeGroup);
        return List.copyOf(names);
    }

    public static List<String> declaredLayerTargetNames(
        VnCharacter character,
        String characterId,
        String expression,
        String layerId
    ) {
        String safeCharacter = selectorSafeName(characterId);
        String safeLayer = selectorSafeName(layerId);
        if (safeCharacter.isBlank() || safeLayer.isBlank()) return List.of();
        LinkedHashSet<String> names = new LinkedHashSet<>();
        names.add(safeCharacter + "_" + safeLayer);
        String currentExpression = selectorSafeName(expression == null || expression.isBlank() ? "neutral" : expression);
        if (!currentExpression.isBlank()) {
            names.add(safeCharacter + "_" + currentExpression + "_" + safeLayer);
        }
        if (character != null) {
            for (String declaredExpression : character.getExpressionLayerIdsByName().keySet()) {
                String safeExpression = selectorSafeName(declaredExpression);
                if (!safeExpression.isBlank()) {
                    names.add(safeCharacter + "_" + safeExpression + "_" + safeLayer);
                }
            }
        }
        return List.copyOf(names);
    }
}
