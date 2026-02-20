package com.jvn.editor.ui;

import javafx.scene.control.ColorPicker;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;

/**
 * Pairs a hex-color {@link TextField} with a {@link ColorPicker} swatch.
 * Changes in either control sync to the other.
 * Usage: {@code HBox row = ColorFieldHelper.create(tfChoiceBgColor);}
 */
public final class ColorFieldHelper {

    private ColorFieldHelper() {}

    /**
     * Wrap an existing hex-color TextField with a small ColorPicker.
     * The picker and text field stay in sync bidirectionally.
     *
     * @param textField the existing text field holding a hex color (e.g. "#ff0000")
     * @return an HBox containing [textField, colorPicker]
     */
    public static HBox create(TextField textField) {
        ColorPicker picker = new ColorPicker();
        picker.setPrefWidth(40);
        picker.setMinWidth(40);
        picker.setMaxWidth(40);
        picker.setPrefHeight(26);
        picker.setStyle("-fx-color-label-visible: false;");

        // Init picker from text field
        Color initial = parseColor(textField.getText());
        if (initial != null) picker.setValue(initial);

        // Text → Picker
        textField.textProperty().addListener((o, ov, nv) -> {
            Color c = parseColor(nv);
            if (c != null && !c.equals(picker.getValue())) {
                picker.setValue(c);
            }
        });

        // Picker → Text
        picker.setOnAction(e -> {
            Color c = picker.getValue();
            if (c != null) {
                String hex = toHex(c);
                if (!hex.equalsIgnoreCase(textField.getText().trim())) {
                    textField.setText(hex);
                }
            }
        });

        HBox box = new HBox(4, textField, picker);
        HBox.setHgrow(textField, Priority.ALWAYS);
        return box;
    }

    private static Color parseColor(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return Color.web(text.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x",
            (int) (c.getRed() * 255),
            (int) (c.getGreen() * 255),
            (int) (c.getBlue() * 255));
    }
}
