package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class PuppeteerVerificationTest {

    @Test
    void runtimeRegistrationFlagsAnimatedGroups() {
        AnimationProject project = new AnimationProject();
        project.getOrCreateTrack("hero");
        EntityGroup group = project.getOrCreateGroup("crowd");
        group.getGroupTrack().addKeyframe(PropertyType.X, new Keyframe(0, 120));

        List<TimelineDiagnostic.Message> messages = PuppeteerVerification.diagnose(
            project,
            Set.of("hero"),
            null,
            PuppeteerVerification.Mode.REGISTER_RUNTIME
        );

        assertTrue(messages.stream().anyMatch(message ->
            message.severity() == TimelineDiagnostic.Severity.ERROR
                && message.entityOrTrack().equals("crowd")
                && message.description().contains("preview-only")));
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
