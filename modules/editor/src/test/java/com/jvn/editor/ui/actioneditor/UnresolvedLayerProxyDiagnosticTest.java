package com.jvn.editor.ui.actioneditor;

import com.jvn.core.animation.Easing;
import com.jvn.core.vn.VnCharacter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnresolvedLayerProxyDiagnosticTest {

    private static VnCharacter heroWithArmLayer() {
        return VnCharacter.builder("hero")
            .addExpression("neutral", "neutral.png", List.of("arm_l"))
            .addLayer("arm_l", "arm_l.png")
            .build();
    }

    private static VnCharacter heroWithGroupedArmLayer() {
        return VnCharacter.builder("hero")
            .addExpression("neutral", "neutral.png", List.of("arm_l"))
            .addLayer("arm_l", "arm_l.png")
            .addLayerGroup("arm_group", null, List.of("arm_l"))
            .build();
    }

    private static VnCharacter heroWithGroupedArmLayerOnlyUnderHappy() {
        return VnCharacter.builder("hero")
            .addExpression("happy", "happy.png", List.of("arm_l"))
            .addLayer("arm_l", "arm_l.png")
            .addLayerGroup("arm_group", null, List.of("arm_l"))
            .build();
    }

    private static void addPositionKeyframes(AnimationProject project, String entity, int count) {
        EntityTrack track = project.getOrCreateTrack(entity);
        for (int i = 0; i < count; i++) {
            track.addKeyframe(PropertyType.X, new Keyframe(i * 100.0, i * 10.0, Easing.Type.LINEAR));
        }
    }

    @Test
    void noWarningWhenTrackNameResolvesToDeclaredLayer() {
        AnimationProject project = new AnimationProject();
        addPositionKeyframes(project, "hero_arm_l", 2);

        List<TimelineDiagnostic.Message> messages = PuppeteerVerification.diagnose(
            project, Set.of("hero_arm_l"), null, PuppeteerVerification.Mode.EXPORT_CODE,
            heroWithArmLayer(), "hero");

        assertFalse(messages.stream().anyMatch(m ->
            m.description().contains(PuppeteerVerification.UNRESOLVED_LAYER_PROXY_MARKER)));
    }

    @Test
    void warningWhenTrackNameResolvesToNoCandidate() {
        AnimationProject project = new AnimationProject();
        addPositionKeyframes(project, "arm_l", 3);

        List<TimelineDiagnostic.Message> messages = PuppeteerVerification.diagnose(
            project, Set.of("some_unrelated_entity"), null, PuppeteerVerification.Mode.EXPORT_CODE,
            heroWithArmLayer(), "hero");

        TimelineDiagnostic.Message warning = messages.stream()
            .filter(m -> m.description().contains(PuppeteerVerification.UNRESOLVED_LAYER_PROXY_MARKER))
            .findFirst()
            .orElseThrow(() -> new AssertionError("expected an unresolved-layer warning"));
        assertEquals(TimelineDiagnostic.Severity.WARNING, warning.severity());
        assertEquals("arm_l", warning.entityOrTrack());
        assertTrue(warning.description().contains("3 keyframes"));
    }

    @Test
    void noWarningWhenTrackHasNoKeyframes() {
        AnimationProject project = new AnimationProject();
        project.getOrCreateTrack("arm_l");

        List<TimelineDiagnostic.Message> messages = PuppeteerVerification.diagnose(
            project, Set.of("some_unrelated_entity"), null, PuppeteerVerification.Mode.EXPORT_CODE,
            heroWithArmLayer(), "hero");

        assertFalse(messages.stream().anyMatch(m ->
            m.description().contains(PuppeteerVerification.UNRESOLVED_LAYER_PROXY_MARKER)));
    }

    @Test
    void noWarningWhenCharacterIsNull() {
        AnimationProject project = new AnimationProject();
        addPositionKeyframes(project, "arm_l", 3);

        List<TimelineDiagnostic.Message> messages = PuppeteerVerification.diagnose(
            project, Set.of("some_unrelated_entity"), null, PuppeteerVerification.Mode.EXPORT_CODE,
            null, "hero");

        assertFalse(messages.stream().anyMatch(m ->
            m.description().contains(PuppeteerVerification.UNRESOLVED_LAYER_PROXY_MARKER)));
    }

    @Test
    void noWarningForTrackNameNotPartOfCharacterRig() {
        AnimationProject project = new AnimationProject();
        addPositionKeyframes(project, "unrelated_prop", 2);

        List<TimelineDiagnostic.Message> messages = PuppeteerVerification.diagnose(
            project, Set.of("some_unrelated_entity"), null, PuppeteerVerification.Mode.EXPORT_CODE,
            heroWithArmLayer(), "hero");

        assertFalse(messages.stream().anyMatch(m ->
            m.description().contains(PuppeteerVerification.UNRESOLVED_LAYER_PROXY_MARKER)));
    }

    @Test
    void noWarningWhenGroupedLayerTrackResolvesToGroupTargetName() {
        AnimationProject project = new AnimationProject();
        addPositionKeyframes(project, "arm_l", 2);

        List<TimelineDiagnostic.Message> messages = PuppeteerVerification.diagnose(
            project, Set.of("hero_arm_group"), null, PuppeteerVerification.Mode.EXPORT_CODE,
            heroWithGroupedArmLayer(), "hero");

        assertFalse(messages.stream().anyMatch(m ->
            m.description().contains(PuppeteerVerification.UNRESOLVED_LAYER_PROXY_MARKER)));
    }

    @Test
    void noWarningWhenKnownEntitiesUnavailable() {
        AnimationProject project = new AnimationProject();
        addPositionKeyframes(project, "arm_l", 3);
        for (Set<String> known : java.util.Arrays.asList(null, Set.<String>of())) {
            assertFalse(PuppeteerVerification.diagnose(
                project, known, null, PuppeteerVerification.Mode.EXPORT_CODE,
                heroWithArmLayer(), "hero").stream()
                .anyMatch(m -> m.description().contains(
                    PuppeteerVerification.UNRESOLVED_LAYER_PROXY_MARKER)));
        }
    }

    @Test
    void noWarningWhenBareLayerTrackResolvesViaDeclaredCandidate() {
        AnimationProject project = new AnimationProject();
        addPositionKeyframes(project, "arm_l", 2);
        assertFalse(PuppeteerVerification.diagnose(
            project, Set.of("hero_arm_l"), null, PuppeteerVerification.Mode.EXPORT_CODE,
            heroWithArmLayer(), "hero").stream()
            .anyMatch(m -> m.description().contains(
                PuppeteerVerification.UNRESOLVED_LAYER_PROXY_MARKER)));
    }

    @Test
    void warningWhenGroupedLayerTrackDoesNotResolve() {
        AnimationProject project = new AnimationProject();
        addPositionKeyframes(project, "arm_l", 4);

        List<TimelineDiagnostic.Message> messages = PuppeteerVerification.diagnose(
            project, Set.of("some_unrelated_entity"), null, PuppeteerVerification.Mode.EXPORT_CODE,
            heroWithGroupedArmLayer(), "hero");

        TimelineDiagnostic.Message warning = messages.stream()
            .filter(m -> m.description().contains(PuppeteerVerification.UNRESOLVED_LAYER_PROXY_MARKER))
            .findFirst()
            .orElseThrow(() -> new AssertionError("expected an unresolved-layer warning"));
        assertEquals(TimelineDiagnostic.Severity.WARNING, warning.severity());
        assertEquals("arm_l", warning.entityOrTrack());
        assertTrue(warning.description().contains("4 keyframes"));
    }

    @Test
    void noWarningWhenGroupedLayerResolvesOnlyUnderNonNeutralExpression() {
        AnimationProject project = new AnimationProject();
        addPositionKeyframes(project, "arm_l", 2);

        List<TimelineDiagnostic.Message> messages = PuppeteerVerification.diagnose(
            project, Set.of("hero_happy_arm_group"), null, PuppeteerVerification.Mode.EXPORT_CODE,
            heroWithGroupedArmLayerOnlyUnderHappy(), "hero");

        assertFalse(messages.stream().anyMatch(m ->
            m.description().contains(PuppeteerVerification.UNRESOLVED_LAYER_PROXY_MARKER)));
    }
}
