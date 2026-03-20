package com.jvn.editor.ui.actioneditor;

enum PuppeteerAssetPlacementRole {
    BACKGROUND("Background", -1000.0),
    CHARACTER("Character", 0.0),
    PROP("Prop", 100.0);

    private final String displayName;
    private final double defaultZ;

    PuppeteerAssetPlacementRole(String displayName, double defaultZ) {
        this.displayName = displayName;
        this.defaultZ = defaultZ;
    }

    String displayName() {
        return displayName;
    }

    double defaultZ() {
        return defaultZ;
    }
}
