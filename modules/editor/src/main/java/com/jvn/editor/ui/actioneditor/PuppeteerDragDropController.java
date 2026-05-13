package com.jvn.editor.ui.actioneditor;

import java.util.function.Function;

/**
 * Wires all drag-source and drag-target callbacks for PuppeteerWindow.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Layer-reorder callbacks from {@link EntitySelector} (entity and group
 *       layer-order delta events)</li>
 *   <li>Asset-drop callback from {@link AnimationPreview} — decodes a
 *       {@link PuppeteerAssetTransfer.Payload} and forwards it to the
 *       supplied {@code assetDropHandler}</li>
 * </ul>
 *
 * <p>Create via {@link #bind}.
 */
public final class PuppeteerDragDropController {

    /**
     * Binds drag-drop callbacks and returns the controller.
     *
     * @param entitySelector   the entity/group layer list widget
     * @param animationPreview the canvas that accepts asset drops
     * @param trackLookup      resolves entity name → track (e.g. {@code project::getTrack})
     * @param groupLookup      resolves group name → group (e.g. {@code project::getGroup})
     * @param onLayerChanged   called after any layer-order mutation to refresh the preview
     * @param assetDropHandler called when a valid asset payload is dropped on the canvas
     */
    public static PuppeteerDragDropController bind(
            EntitySelector entitySelector,
            AnimationPreview animationPreview,
            Function<String, EntityTrack> trackLookup,
            Function<String, EntityGroup> groupLookup,
            Runnable onLayerChanged,
            AssetDropHandler assetDropHandler) {

        PuppeteerDragDropController ctrl = new PuppeteerDragDropController();
        ctrl.bindEntityLayerDelta(entitySelector, trackLookup, onLayerChanged);
        ctrl.bindGroupLayerDelta(entitySelector, groupLookup, onLayerChanged);
        ctrl.bindAssetDrop(animationPreview, assetDropHandler);
        return ctrl;
    }

    private PuppeteerDragDropController() {}

    // -----------------------------------------------------------------------
    // Layer reordering
    // -----------------------------------------------------------------------

    private void bindEntityLayerDelta(
            EntitySelector entitySelector,
            Function<String, EntityTrack> trackLookup,
            Runnable onLayerChanged) {
        entitySelector.setOnEntityLayerDelta((entityName, delta) -> {
            EntityTrack track = trackLookup.apply(entityName);
            if (track == null) return;
            track.setLayerOrder(track.getLayerOrder() + delta);
            onLayerChanged.run();
        });
    }

    private void bindGroupLayerDelta(
            EntitySelector entitySelector,
            Function<String, EntityGroup> groupLookup,
            Runnable onLayerChanged) {
        entitySelector.setOnGroupLayerDelta((groupName, delta) -> {
            EntityGroup group = groupLookup.apply(groupName);
            if (group == null) return;
            group.setLayerOrder(group.getLayerOrder() + delta);
            onLayerChanged.run();
        });
    }

    // -----------------------------------------------------------------------
    // Asset drop from asset browser → canvas
    // -----------------------------------------------------------------------

    private void bindAssetDrop(
            AnimationPreview animationPreview,
            AssetDropHandler handler) {
        animationPreview.setOnAssetDropped(payload -> {
            if (payload == null || !payload.isValid()) return;
            handler.onDrop(payload.relativePath(), payload.suggestedName(), PuppeteerAssetPlacementRole.PROP);
        });
    }

    // -----------------------------------------------------------------------
    // Callback interface
    // -----------------------------------------------------------------------

    /** Receives a decoded asset drop from the canvas. */
    @FunctionalInterface
    public interface AssetDropHandler {
        void onDrop(String relativePath, String suggestedName, PuppeteerAssetPlacementRole role);
    }
}
