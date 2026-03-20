package com.jvn.editor.ui.actioneditor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javafx.scene.input.Dragboard;

final class PuppeteerAssetTransfer {
    private static final String PREFIX = "jvn-puppeteer-asset:";

    record Payload(String relativePath, String suggestedName) {
        Payload {
            relativePath = relativePath == null ? "" : relativePath.trim();
            suggestedName = suggestedName == null ? "" : suggestedName.trim();
        }

        boolean isValid() {
            return !relativePath.isBlank();
        }
    }

    private PuppeteerAssetTransfer() {
    }

    static String encode(String relativePath, String suggestedName) {
        Payload payload = new Payload(relativePath, suggestedName);
        if (!payload.isValid()) return "";
        return PREFIX + encodePart(payload.relativePath()) + "|" + encodePart(payload.suggestedName());
    }

    static Payload decode(String raw) {
        if (raw == null || !raw.startsWith(PREFIX)) return null;
        String encoded = raw.substring(PREFIX.length());
        int delimiter = encoded.indexOf('|');
        if (delimiter < 0) return null;
        String relativePath = decodePart(encoded.substring(0, delimiter));
        String suggestedName = decodePart(encoded.substring(delimiter + 1));
        Payload payload = new Payload(relativePath, suggestedName);
        return payload.isValid() ? payload : null;
    }

    static Payload fromDragboard(Dragboard dragboard) {
        return dragboard == null ? null : decode(dragboard.getString());
    }

    private static String encodePart(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            (value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decodePart(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }
}
