package com.jvn.editor.ui.actioneditor;

public enum PropertyType {
    X("x", "Position X"),
    Y("y", "Position Y"),
    PIVOT_X("pivotX", "Pivot X"),
    PIVOT_Y("pivotY", "Pivot Y"),
    ROTATION("rotation", "Rotation"),
    SCALE_X("scaleX", "Scale X"),
    SCALE_Y("scaleY", "Scale Y"),
    ALPHA("alpha", "Opacity"),
    CAMERA_X("cameraX", "Camera X"),
    CAMERA_Y("cameraY", "Camera Y"),
    CAMERA_ZOOM("cameraZoom", "Camera Zoom");

    private final String code;
    private final String displayName;

    PropertyType(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }

    public boolean isEntityProperty() {
        return this == X || this == Y || this == PIVOT_X || this == PIVOT_Y || this == ROTATION ||
               this == SCALE_X || this == SCALE_Y || this == ALPHA;
    }

    public boolean isCameraProperty() {
        return this == CAMERA_X || this == CAMERA_Y || this == CAMERA_ZOOM;
    }

    public double getDefaultValue() {
        return switch (this) {
            case SCALE_X, SCALE_Y, ALPHA, CAMERA_ZOOM -> 1.0;
            default -> 0.0;
        };
    }
}
