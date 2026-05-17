package com.jvn.editor.ui.actioneditor;

public enum PropertyType {
    X("x", "Position X"),
    Y("y", "Position Y"),
    Z("z", "Depth"),
    PIVOT_X("pivotX", "Pivot X"),
    PIVOT_Y("pivotY", "Pivot Y"),
    ROTATION("rotation", "Rotation"),
    SCALE_X("scaleX", "Scale X"),
    SCALE_Y("scaleY", "Scale Y"),
    MIRROR_X("mirrorX", "Mirror X"),
    ALPHA("alpha", "Opacity"),
    VISIBILITY("visible", "Visible"),
    MATRIX_MXX("matrixMxx", "Matrix MXX", "matrix.mxx"),
    MATRIX_MXY("matrixMxy", "Matrix MXY", "matrix.mxy"),
    MATRIX_MYX("matrixMyx", "Matrix MYX", "matrix.myx"),
    MATRIX_MYY("matrixMyy", "Matrix MYY", "matrix.myy"),
    MATRIX_TX("matrixTx", "Matrix TX", "matrix.tx"),
    MATRIX_TY("matrixTy", "Matrix TY", "matrix.ty"),
    BLUR("blur", "Blur", "effect.blur"),
    BRIGHTNESS("brightness", "Brightness", "effect.brightness"),
    CAMERA_X("cameraX", "Camera X"),
    CAMERA_Y("cameraY", "Camera Y"),
    CAMERA_ZOOM("cameraZoom", "Camera Zoom"),
    CAMERA_DOF_FOCUS("cameraDofFocus", "DOF Focus", "dof.focus"),
    CAMERA_DOF_STRENGTH("cameraDofStrength", "DOF Strength", "dof.strength"),
    CAMERA_DOF_MAX_BLUR("cameraDofMaxBlur", "DOF Max Blur", "dof.maxBlur");

    private final String code;
    private final String displayName;
    private final String timelineCustomKey;

    PropertyType(String code, String displayName) {
        this(code, displayName, null);
    }

    PropertyType(String code, String displayName, String timelineCustomKey) {
        this.code = code;
        this.displayName = displayName;
        this.timelineCustomKey = timelineCustomKey;
    }

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }
    public String getTimelineCustomKey() { return timelineCustomKey; }
    public boolean isTimelineCustomProperty() { return timelineCustomKey != null && !timelineCustomKey.isBlank(); }

    public boolean isEntityProperty() {
        return this == X || this == Y || this == Z || this == PIVOT_X || this == PIVOT_Y || this == ROTATION ||
               this == SCALE_X || this == SCALE_Y || this == MIRROR_X || this == ALPHA || this == VISIBILITY ||
               this == MATRIX_MXX || this == MATRIX_MXY || this == MATRIX_MYX || this == MATRIX_MYY ||
               this == MATRIX_TX || this == MATRIX_TY || this == BLUR || this == BRIGHTNESS;
    }

    public boolean isCameraProperty() {
        return this == CAMERA_X || this == CAMERA_Y || this == CAMERA_ZOOM
            || this == CAMERA_DOF_FOCUS || this == CAMERA_DOF_STRENGTH || this == CAMERA_DOF_MAX_BLUR;
    }

    public double getDefaultValue() {
        return switch (this) {
            case SCALE_X, SCALE_Y, ALPHA, VISIBILITY, CAMERA_ZOOM, MATRIX_MXX, MATRIX_MYY, BRIGHTNESS -> 1.0;
            case PIVOT_X, PIVOT_Y -> 0.5;
            default -> 0.0;
        };
    }

    public static PropertyType fromTimelineCustomKey(String key) {
        if (key == null || key.isBlank()) return null;
        key = key.trim();
        if ("effect.exposure".equals(key)) return BRIGHTNESS;
        for (PropertyType property : values()) {
            if (key.equals(property.timelineCustomKey)) return property;
        }
        return null;
    }
}
