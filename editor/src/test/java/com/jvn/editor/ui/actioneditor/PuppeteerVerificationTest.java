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
}
