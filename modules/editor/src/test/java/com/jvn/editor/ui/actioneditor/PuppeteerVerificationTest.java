package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.jvn.core.animation.TimelineData;

class PuppeteerVerificationTest {

    @Test
    void runtimeRegistrationReportsAnimatedGroupsAsBakedIntoRuntimeTracks() {
        AnimationProject project = new AnimationProject();
        EntityTrack hero = project.getOrCreateTrack("hero");
        hero.addKeyframe(PropertyType.X, new Keyframe(0, 100));
        hero.addKeyframe(PropertyType.X, new Keyframe(100, 200));
        EntityGroup group = project.getOrCreateGroup("crowd");
        group.getGroupTrack().addKeyframe(PropertyType.X, new Keyframe(0, 20));
        group.getGroupTrack().addKeyframe(PropertyType.X, new Keyframe(100, 40));
        project.addEntityToGroup("hero", "crowd");

        List<TimelineDiagnostic.Message> messages = PuppeteerVerification.diagnose(
            project,
            Set.of("hero"),
            null,
            PuppeteerVerification.Mode.REGISTER_RUNTIME
        );

        assertFalse(messages.stream().anyMatch(message ->
            message.severity() == TimelineDiagnostic.Severity.ERROR
                && message.entityOrTrack().equals("crowd")
                && message.description().contains("Animated group")));
        assertTrue(messages.stream().anyMatch(message ->
            message.severity() == TimelineDiagnostic.Severity.INFO
                && message.entityOrTrack().equals("crowd")
                && message.description().contains("will be baked")));

        TimelineData data = project.toTimelineData("crowd_intro");
        TimelineData.Track runtimeHero = data.getTrack("hero");
        assertNotNull(runtimeHero);
        assertEquals(120.0, runtimeHero.getValueAt(TimelineData.Property.X, 0), 0.0001);
        assertEquals(240.0, runtimeHero.getValueAt(TimelineData.Property.X, 100), 0.0001);
    }

    @Test
    void runtimeRegistrationWarnsWhenAnimatedGroupHasNoRuntimeChildren() {
        AnimationProject project = new AnimationProject();
        EntityGroup group = project.getOrCreateGroup("empty");
        group.getGroupTrack().addKeyframe(PropertyType.X, new Keyframe(0, 120));

        List<TimelineDiagnostic.Message> messages = PuppeteerVerification.diagnose(
            project,
            Set.of(),
            null,
            PuppeteerVerification.Mode.REGISTER_RUNTIME
        );

        assertTrue(messages.stream().anyMatch(message ->
            message.severity() == TimelineDiagnostic.Severity.WARNING
                && message.entityOrTrack().equals("empty")
                && message.description().contains("has no child entities")));
    }

    @Test
    void diagnoseWarnsWhenRotatingEntityHasNoOrbitAnchor() {
        AnimationProject project = new AnimationProject();
        EntityTrack hero = project.getOrCreateTrack("hero");
        hero.addKeyframe(PropertyType.ROTATION, new Keyframe(0, 0));
        hero.addKeyframe(PropertyType.ROTATION, new Keyframe(500, 90));

        List<TimelineDiagnostic.Message> messages = PuppeteerVerification.diagnose(
            project,
            Set.of("hero"),
            null,
            PuppeteerVerification.Mode.EXPORT_CODE
        );

        assertTrue(messages.stream().anyMatch(message ->
            message.severity() == TimelineDiagnostic.Severity.WARNING
                && message.entityOrTrack().equals("hero")
                && message.description().contains(PuppeteerVerification.ORBIT_PIVOT_RISK_MARKER)));
    }

    @Test
    void diagnoseDoesNotWarnWhenOrbitAnchorAndSnapshotAreValid() {
        AnimationProject project = new AnimationProject();
        EntityTrack hero = project.getOrCreateTrack("hero");
        hero.addKeyframe(PropertyType.ROTATION, new Keyframe(0, 0));
        hero.addKeyframe(PropertyType.ROTATION, new Keyframe(500, 90));
        project.setOrbitAnchor("hero", 120.0, 130.0);
        project.setSceneEntitySnapshots(List.of(new AnimationProject.SceneEntitySnapshot(
            "hero", "entity", "", 100.0, 100.0, 64.0, 64.0, 0.5, 0.5, 0.0, true, 1.0)));

        List<TimelineDiagnostic.Message> messages = PuppeteerVerification.diagnose(
            project,
            Set.of("hero"),
            null,
            PuppeteerVerification.Mode.EXPORT_CODE
        );

        assertFalse(messages.stream().anyMatch(message ->
            message.description().contains(PuppeteerVerification.ORBIT_PIVOT_RISK_MARKER)));
    }

    @Test
    void runtimeRegistrationFlagsMissingAudioFile() {
        AnimationProject project = new AnimationProject();
        project.addAudioCue(new AudioCue(100, "assets/audio/missing.wav", "sound"));

        List<TimelineDiagnostic.Message> messages = PuppeteerVerification.diagnose(
            project,
            null,
            Path.of(".").toFile(),
            PuppeteerVerification.Mode.REGISTER_RUNTIME
        );

        assertTrue(messages.stream().anyMatch(message ->
            message.severity() == TimelineDiagnostic.Severity.ERROR
                && message.description().contains("does not exist on disk")));
    }

    @Test
    void runtimeRegistrationAcceptsExistingRelativeAudioFile() throws Exception {
        Path projectRoot = Files.createTempDirectory("puppeteer-verification");
        Path audio = projectRoot.resolve("assets/audio/test.wav");
        Files.createDirectories(audio.getParent());
        Files.writeString(audio, "stub");

        AnimationProject project = new AnimationProject();
        project.addAudioCue(new AudioCue(100, "assets/audio/test.wav", "sound"));

        List<TimelineDiagnostic.Message> messages = PuppeteerVerification.diagnose(
            project,
            null,
            projectRoot.toFile(),
            PuppeteerVerification.Mode.REGISTER_RUNTIME
        );

        assertTrue(messages.stream().noneMatch(message ->
            message.description().contains("does not exist on disk")));
    }

    @Test
    void runtimeRegistrationWarnsForAbsoluteAudioPaths() throws Exception {
        Path audio = Files.createTempFile("puppeteer-absolute-audio", ".wav");
        AnimationProject project = new AnimationProject();
        project.addAudioCue(new AudioCue(100, audio.toString(), "sound"));

        List<TimelineDiagnostic.Message> messages = PuppeteerVerification.diagnose(
            project,
            null,
            audio.getParent().toFile(),
            PuppeteerVerification.Mode.REGISTER_RUNTIME
        );

        assertTrue(messages.stream().anyMatch(message ->
            message.severity() == TimelineDiagnostic.Severity.WARNING
                && message.description().contains("uses an absolute path")));
        assertTrue(messages.stream().noneMatch(message ->
            message.severity() == TimelineDiagnostic.Severity.ERROR
                && message.description().contains("does not exist on disk")));
    }

    @Test
    void runtimeRegistrationFlagsMissingSceneEntityAsset() throws Exception {
        Path projectRoot = Files.createTempDirectory("puppeteer-scene-assets");
        AnimationProject project = new AnimationProject();
        project.setSceneEntitySnapshots(List.of(new AnimationProject.SceneEntitySnapshot(
            "hero",
            "sprite",
            "assets/characters/missing.png",
            0,
            0,
            100,
            200,
            0.5,
            1.0,
            0,
            true,
            1.0
        )));

        List<TimelineDiagnostic.Message> messages = PuppeteerVerification.diagnose(
            project,
            Set.of("hero"),
            projectRoot.toFile(),
            PuppeteerVerification.Mode.REGISTER_RUNTIME
        );

        assertTrue(messages.stream().anyMatch(message ->
            message.severity() == TimelineDiagnostic.Severity.ERROR
                && message.entityOrTrack().equals("hero")
                && message.description().contains("Scene asset 'assets/characters/missing.png'")));
    }

    @Test
    void runtimeRegistrationWarnsForAbsoluteSceneAssetPaths() throws Exception {
        Path image = Files.createTempFile("puppeteer-absolute-scene", ".png");
        AnimationProject project = new AnimationProject();
        project.setSceneEntitySnapshots(List.of(new AnimationProject.SceneEntitySnapshot(
            "hero",
            "sprite",
            image.toString(),
            0,
            0,
            100,
            200,
            0.5,
            1.0,
            0,
            true,
            1.0
        )));

        List<TimelineDiagnostic.Message> messages = PuppeteerVerification.diagnose(
            project,
            Set.of("hero"),
            image.getParent().toFile(),
            PuppeteerVerification.Mode.REGISTER_RUNTIME
        );

        assertTrue(messages.stream().anyMatch(message ->
            message.severity() == TimelineDiagnostic.Severity.WARNING
                && message.entityOrTrack().equals("hero")
                && message.description().contains("uses an absolute path")));
        assertTrue(messages.stream().noneMatch(message ->
            message.severity() == TimelineDiagnostic.Severity.ERROR
                && message.description().contains("does not exist on disk")));
    }

    @Test
    void runtimeRegistrationWarnsWhenCameraKeysAreMixedIntoEntityTracks() {
        AnimationProject project = new AnimationProject();
        EntityTrack hero = project.getOrCreateTrack("hero");
        hero.addKeyframe(PropertyType.X, new Keyframe(0, 100));
        hero.addKeyframe(PropertyType.CAMERA_X, new Keyframe(0, 12));
        EntityTrack cutaway = project.getOrCreateTrack("cutaway");
        cutaway.addKeyframe(PropertyType.CAMERA_Y, new Keyframe(0, 24));

        List<TimelineDiagnostic.Message> messages = PuppeteerVerification.diagnose(
            project,
            Set.of("hero", "cutaway"),
            null,
            PuppeteerVerification.Mode.REGISTER_RUNTIME
        );

        assertTrue(messages.stream().anyMatch(message ->
            message.severity() == TimelineDiagnostic.Severity.WARNING
                && message.description().contains("spread across multiple tracks")));
        assertTrue(messages.stream().anyMatch(message ->
            message.severity() == TimelineDiagnostic.Severity.WARNING
                && message.description().contains("mixed into entity track 'hero'")));
    }

    @Test
    void runtimeRegistrationIncludesCoreTimelineDataDiagnostics() {
        AnimationProject project = new AnimationProject();
        project.setName("");
        EntityTrack camera = project.getOrCreateTrack(TimelinePanel.RUNTIME_CAMERA_TARGET);
        camera.addKeyframe(PropertyType.X, new Keyframe(0, 20));

        List<TimelineDiagnostic.Message> messages = PuppeteerVerification.diagnose(
            project,
            Set.of(),
            null,
            PuppeteerVerification.Mode.REGISTER_RUNTIME
        );

        assertTrue(messages.stream().anyMatch(message ->
            message.severity() == TimelineDiagnostic.Severity.WARNING
                && message.description().contains("Timeline name is empty")));
        assertTrue(messages.stream().anyMatch(message ->
            message.severity() == TimelineDiagnostic.Severity.WARNING
                && message.description().contains("Entity property X is stored on the camera track")));
    }

    @Test
    void runtimeRegistrationWarnsForSubFrameAnimation() {
        AnimationProject project = new AnimationProject();
        project.setName("sub_frame_mirror");
        EntityTrack body = project.getOrCreateTrack("john_body_default");
        body.addKeyframe(PropertyType.MIRROR_X, new Keyframe(1, 1));

        List<TimelineDiagnostic.Message> messages = PuppeteerVerification.diagnose(
            project,
            Set.of("john_body_default"),
            null,
            PuppeteerVerification.Mode.REGISTER_RUNTIME
        );

        assertTrue(messages.stream().anyMatch(message ->
            message.severity() == TimelineDiagnostic.Severity.WARNING
                && message.entityOrTrack().equals("john_body_default")
                && message.description().contains("within one 60 Hz display frame")));
    }

    @Test
    void timelineNameValidationBlocksUnsafeFileAndVnsNames() {
        assertTrue(PuppeteerVerification.validateTimelineName("../bad name").stream().anyMatch(message ->
            message.severity() == TimelineDiagnostic.Severity.ERROR
                && message.description().contains("unsafe")));
        assertTrue(PuppeteerVerification.validateTimelineName("../bad name").stream().anyMatch(message ->
            message.severity() == TimelineDiagnostic.Severity.ERROR
                && message.description().contains("path separators")));
        assertTrue(PuppeteerVerification.validateTimelineName("con").stream().anyMatch(message ->
            message.severity() == TimelineDiagnostic.Severity.ERROR
                && message.description().contains("reserved filename")));
    }

    @Test
    void timelineNameValidationAcceptsPortableNames() {
        assertTrue(PuppeteerVerification.isValidTimelineName("hero_intro.v1"));
        assertTrue(PuppeteerVerification.isValidTimelineName("battle-cut_02"));
    }
}
