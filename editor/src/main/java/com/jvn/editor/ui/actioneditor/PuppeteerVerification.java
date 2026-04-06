package com.jvn.editor.ui.actioneditor;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

final class PuppeteerVerification {
    enum Mode {
        EXPORT_CODE,
        REGISTER_RUNTIME
    }

    private PuppeteerVerification() {
    }

    static List<TimelineDiagnostic.Message> diagnose(
        AnimationProject project,
        Set<String> knownEntities,
        File projectRoot,
        Mode mode
    ) {
        if (project == null) {
            return Collections.emptyList();
        }

        List<TimelineDiagnostic.Message> messages = new ArrayList<>(
            TimelineDiagnostic.diagnose(project, knownEntities)
        );

        diagnoseContent(project, messages);
        diagnoseAudioFiles(project, projectRoot, messages);

        if (mode == Mode.REGISTER_RUNTIME) {
            diagnoseRuntimeRegistration(project, messages);
        }

        return Collections.unmodifiableList(messages);
    }

    private static void diagnoseContent(AnimationProject project, List<TimelineDiagnostic.Message> messages) {
        boolean hasTracks = project.getTracks().iterator().hasNext();
        boolean hasGroups = project.getGroups().iterator().hasNext();
        boolean hasAudio = !project.getAudioCues().isEmpty();
        boolean hasEvents = !project.getEditorEventCues().isEmpty();
        if (!hasTracks && !hasGroups && !hasAudio && !hasEvents) {
            messages.add(new TimelineDiagnostic.Message(
                TimelineDiagnostic.Severity.WARNING,
                "(timeline)",
                "Timeline is empty and will export/register as a no-op",
                "Add keyframes, audio cues, or event cues before exporting"
            ));
        }
    }

    private static void diagnoseAudioFiles(
        AnimationProject project,
        File projectRoot,
        List<TimelineDiagnostic.Message> messages
    ) {
        for (AudioCue cue : project.getAudioCues()) {
            if (cue == null) continue;
            String path = cue.getAudioFile() == null ? "" : cue.getAudioFile().trim();
            if (path.isBlank()) continue;
            Path resolved = resolveProjectPath(projectRoot, path);
            if (resolved == null || !Files.isRegularFile(resolved)) {
                messages.add(new TimelineDiagnostic.Message(
                    TimelineDiagnostic.Severity.ERROR,
                    "(audio)",
                    "Audio cue file '" + path + "' does not exist on disk",
                    projectRoot != null && projectRoot.isDirectory()
                        ? "Import the file into the project or fix the relative path"
                        : "Open the project root or use an absolute file path"
                ));
            }
        }
    }

    private static void diagnoseRuntimeRegistration(
        AnimationProject project,
        List<TimelineDiagnostic.Message> messages
    ) {
        int cameraCarrierCount = 0;
        String mixedCameraTrack = null;

        for (EntityTrack track : project.getTracks()) {
            if (track == null) continue;
            boolean hasCameraKeys = track.hasKeyframes(PropertyType.CAMERA_X)
                || track.hasKeyframes(PropertyType.CAMERA_Y)
                || track.hasKeyframes(PropertyType.CAMERA_ZOOM);
            if (!hasCameraKeys) continue;
            cameraCarrierCount++;

            boolean hasEntityKeys = track.hasKeyframes(PropertyType.X)
                || track.hasKeyframes(PropertyType.Y)
                || track.hasKeyframes(PropertyType.PIVOT_X)
                || track.hasKeyframes(PropertyType.PIVOT_Y)
                || track.hasKeyframes(PropertyType.ROTATION)
                || track.hasKeyframes(PropertyType.SCALE_X)
                || track.hasKeyframes(PropertyType.SCALE_Y)
                || track.hasKeyframes(PropertyType.ALPHA);
            if (hasEntityKeys && mixedCameraTrack == null) {
                mixedCameraTrack = track.getEntityName();
            }
        }

        if (cameraCarrierCount > 1) {
            messages.add(new TimelineDiagnostic.Message(
                TimelineDiagnostic.Severity.WARNING,
                "__camera__",
                "Camera keyframes are spread across multiple tracks; runtime registration will apply all of them in track order",
                "Keep camera animation on the dedicated runtime camera lane"
            ));
        }
        if (mixedCameraTrack != null
            && !TimelinePanel.RUNTIME_CAMERA_TARGET.equalsIgnoreCase(mixedCameraTrack)) {
            messages.add(new TimelineDiagnostic.Message(
                TimelineDiagnostic.Severity.WARNING,
                mixedCameraTrack,
                "Camera keyframes are mixed into entity track '" + mixedCameraTrack + "'",
                "Move camera animation to the dedicated runtime camera lane"
            ));
        }

        for (EntityGroup group : project.getGroups()) {
            if (group == null) continue;
            EntityTrack groupTrack = group.getGroupTrack();
            if (groupTrack == null) continue;
            if (!hasAnimatedGroupTrack(groupTrack)) continue;

            messages.add(new TimelineDiagnostic.Message(
                TimelineDiagnostic.Severity.ERROR,
                group.getName(),
                "Animated group '" + group.getName() + "' is preview-only for runtime registration",
                "Bake group motion into child entity tracks before registering"
            ));
        }
    }

    private static boolean hasAnimatedGroupTrack(EntityTrack track) {
        return track.hasKeyframes(PropertyType.X)
            || track.hasKeyframes(PropertyType.Y)
            || track.hasKeyframes(PropertyType.ROTATION)
            || track.hasKeyframes(PropertyType.SCALE_X)
            || track.hasKeyframes(PropertyType.SCALE_Y)
            || track.hasKeyframes(PropertyType.ALPHA);
    }

    private static Path resolveProjectPath(File projectRoot, String rawPath) {
        String path = rawPath == null ? "" : rawPath.trim();
        if (path.isBlank()) return null;

        Path direct = Path.of(path);
        if (direct.isAbsolute()) {
            return direct.normalize();
        }
        if (projectRoot != null && projectRoot.isDirectory()) {
            return projectRoot.toPath().resolve(path.replace('\\', '/')).normalize();
        }
        return direct.normalize();
    }
}
