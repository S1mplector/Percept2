package com.jvn.editor.ui.actioneditor;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.jvn.core.animation.TimelineDataDiagnostics;

final class PuppeteerVerification {
    private static final int MAX_TIMELINE_NAME_LENGTH = 96;
    private static final String TIMELINE_NAME_RULES =
        "Use letters, numbers, underscores, dashes, or dots. Do not use spaces, slashes, '..', or reserved device names.";
    private static final Set<String> RESERVED_FILE_NAMES = Set.of(
        "con", "prn", "aux", "nul",
        "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
        "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9"
    );

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
        diagnoseSceneEntityAssets(project, projectRoot, messages);

        if (mode == Mode.REGISTER_RUNTIME) {
            diagnoseRuntimeRegistration(project, messages);
            diagnoseRuntimeData(project, messages);
        }

        return Collections.unmodifiableList(messages);
    }

    static List<TimelineDiagnostic.Message> validateTimelineName(String rawName) {
        List<TimelineDiagnostic.Message> messages = new ArrayList<>();
        String name = rawName == null ? "" : rawName.trim();
        if (name.isBlank()) {
            messages.add(new TimelineDiagnostic.Message(
                TimelineDiagnostic.Severity.ERROR,
                "(timeline)",
                "Timeline name is empty",
                "Set a name such as hero_entrance before registering"
            ));
            return Collections.unmodifiableList(messages);
        }
        if (name.length() > MAX_TIMELINE_NAME_LENGTH) {
            messages.add(new TimelineDiagnostic.Message(
                TimelineDiagnostic.Severity.ERROR,
                name,
                "Timeline name is too long (" + name.length() + " characters)",
                "Keep timeline names under " + MAX_TIMELINE_NAME_LENGTH + " characters"
            ));
        }
        if (".".equals(name) || "..".equals(name) || name.contains("..")) {
            messages.add(new TimelineDiagnostic.Message(
                TimelineDiagnostic.Severity.ERROR,
                name,
                "Timeline name cannot contain '..'",
                TIMELINE_NAME_RULES
            ));
        }
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
            messages.add(new TimelineDiagnostic.Message(
                TimelineDiagnostic.Severity.ERROR,
                name,
                "Timeline name cannot contain path separators",
                TIMELINE_NAME_RULES
            ));
        }
        if (!name.matches("[A-Za-z0-9][A-Za-z0-9_.-]*")) {
            messages.add(new TimelineDiagnostic.Message(
                TimelineDiagnostic.Severity.ERROR,
                name,
                "Timeline name contains characters that are unsafe for JES files or VNS calls",
                TIMELINE_NAME_RULES
            ));
        }
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        int dot = lower.indexOf('.');
        String base = dot >= 0 ? lower.substring(0, dot) : lower;
        if (RESERVED_FILE_NAMES.contains(base)) {
            messages.add(new TimelineDiagnostic.Message(
                TimelineDiagnostic.Severity.ERROR,
                name,
                "Timeline name uses a reserved filename on some operating systems",
                "Rename it to something project-specific, such as " + name + "_timeline"
            ));
        }
        return Collections.unmodifiableList(messages);
    }

    static boolean isValidTimelineName(String rawName) {
        return validateTimelineName(rawName).stream()
            .noneMatch(message -> message.severity() == TimelineDiagnostic.Severity.ERROR);
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

    private static void diagnoseSceneEntityAssets(
        AnimationProject project,
        File projectRoot,
        List<TimelineDiagnostic.Message> messages
    ) {
        for (AnimationProject.SceneEntitySnapshot snapshot : project.getSceneEntitySnapshotsView().values()) {
            if (snapshot == null) continue;
            String imagePath = snapshot.imagePath() == null ? "" : snapshot.imagePath().trim();
            if (imagePath.isBlank()) continue;
            for (String part : imagePath.split("\\|")) {
                String path = part == null ? "" : part.trim();
                if (path.isBlank()) continue;
                Path resolved = resolveProjectPath(projectRoot, path);
                if (resolved == null || !Files.isRegularFile(resolved)) {
                    messages.add(new TimelineDiagnostic.Message(
                        TimelineDiagnostic.Severity.ERROR,
                        snapshot.name().isBlank() ? "(scene)" : snapshot.name(),
                        "Scene asset '" + path + "' does not exist on disk",
                        projectRoot != null && projectRoot.isDirectory()
                            ? "Relink the entity image or import the missing asset into the project"
                            : "Open the project root so Puppeteer can resolve relative asset paths"
                    ));
                }
            }
        }
    }

    private static void diagnoseRuntimeRegistration(
        AnimationProject project,
        List<TimelineDiagnostic.Message> messages
    ) {
        for (EntityGroup group : project.getGroups()) {
            if (group == null) continue;
            EntityTrack groupTrack = group.getGroupTrack();
            if (!isAnimated(groupTrack)) continue;
            List<String> childEntities = project.collectGroupEntityNames(group.getName());
            if (childEntities.isEmpty()) {
                messages.add(new TimelineDiagnostic.Message(
                    TimelineDiagnostic.Severity.WARNING,
                    group.getName(),
                    "Animated group '" + group.getName() + "' has no child entities and will not affect runtime playback",
                    "Add entities to the group or remove the group animation"
                ));
                continue;
            }
            messages.add(new TimelineDiagnostic.Message(
                TimelineDiagnostic.Severity.INFO,
                group.getName(),
                "Animated group '" + group.getName() + "' will be baked into "
                    + childEntities.size() + " child entity track(s) during runtime registration",
                "Use Runtime Data Preview in Puppeteer to inspect the registered result"
            ));
        }

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
                || track.hasKeyframes(PropertyType.Z)
                || track.hasKeyframes(PropertyType.PIVOT_X)
                || track.hasKeyframes(PropertyType.PIVOT_Y)
                || track.hasKeyframes(PropertyType.ROTATION)
                || track.hasKeyframes(PropertyType.SCALE_X)
                || track.hasKeyframes(PropertyType.SCALE_Y)
                || track.hasKeyframes(PropertyType.ALPHA)
                || track.hasKeyframes(PropertyType.VISIBILITY);
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
    }

    private static void diagnoseRuntimeData(
        AnimationProject project,
        List<TimelineDiagnostic.Message> messages
    ) {
        for (TimelineDataDiagnostics.Message message :
            TimelineDataDiagnostics.diagnose(project.toTimelineData(project.getName()))) {
            addIfAbsent(messages, new TimelineDiagnostic.Message(
                mapSeverity(message.severity()),
                message.target(),
                message.description(),
                message.quickFix()
            ));
        }
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

    private static boolean isAnimated(EntityTrack track) {
        if (track == null) return false;
        for (PropertyType property : PropertyType.values()) {
            if (track.hasKeyframes(property)) return true;
        }
        for (String key : track.getAnimatedCustomProperties()) {
            if (track.hasCustomKeyframes(key)) return true;
        }
        return false;
    }

    private static TimelineDiagnostic.Severity mapSeverity(TimelineDataDiagnostics.Severity severity) {
        if (severity == null) return TimelineDiagnostic.Severity.INFO;
        return switch (severity) {
            case ERROR -> TimelineDiagnostic.Severity.ERROR;
            case WARNING -> TimelineDiagnostic.Severity.WARNING;
            case INFO -> TimelineDiagnostic.Severity.INFO;
        };
    }

    private static void addIfAbsent(
        List<TimelineDiagnostic.Message> messages,
        TimelineDiagnostic.Message candidate
    ) {
        if (candidate == null) return;
        for (TimelineDiagnostic.Message existing : messages) {
            if (existing == null) continue;
            if (existing.severity() == candidate.severity()
                && existing.entityOrTrack().equals(candidate.entityOrTrack())
                && existing.description().equals(candidate.description())) {
                return;
            }
        }
        messages.add(candidate);
    }
}
