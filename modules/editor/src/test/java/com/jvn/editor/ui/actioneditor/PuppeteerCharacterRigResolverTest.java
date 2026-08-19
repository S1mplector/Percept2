package com.jvn.editor.ui.actioneditor;

import com.jvn.core.vn.VnCharacter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PuppeteerCharacterRigResolverTest {

    private static final String SCRIPT =
        "@charlayer hero arm_l assets/hero/arm_l.png\n" +
        "@charlayer hero arm_r assets/hero/arm_r.png\n" +
        "@charpreset hero neutral $arm_l | $arm_r\n";

    @Test
    void resolvesCharacterFromScriptFile(@TempDir Path tempDir) throws IOException {
        File script = tempDir.resolve("hero.vns").toFile();
        Files.writeString(script.toPath(), SCRIPT, StandardCharsets.UTF_8);

        VnCharacter character = PuppeteerCharacterRigResolver.resolve(script, "hero");

        assertEquals("hero", character.getId());
        assertTrue(character.getLayerIds().containsAll(java.util.Set.of("arm_l", "arm_r")));
    }

    @Test
    void returnsNullWhenScriptFileIsNull() {
        assertNull(PuppeteerCharacterRigResolver.resolve(null, "hero"));
    }

    @Test
    void returnsNullWhenScriptFileDoesNotExist(@TempDir Path tempDir) {
        File missing = tempDir.resolve("missing.vns").toFile();
        assertNull(PuppeteerCharacterRigResolver.resolve(missing, "hero"));
    }

    @Test
    void returnsNullWhenCharacterIdIsBlank(@TempDir Path tempDir) throws IOException {
        File script = tempDir.resolve("hero.vns").toFile();
        Files.writeString(script.toPath(), SCRIPT, StandardCharsets.UTF_8);

        assertNull(PuppeteerCharacterRigResolver.resolve(script, ""));
        assertNull(PuppeteerCharacterRigResolver.resolve(script, null));
    }

    @Test
    void returnsNullWhenCharacterNotDeclaredInScript(@TempDir Path tempDir) throws IOException {
        File script = tempDir.resolve("hero.vns").toFile();
        Files.writeString(script.toPath(), SCRIPT, StandardCharsets.UTF_8);

        assertNull(PuppeteerCharacterRigResolver.resolve(script, "someone_else"));
    }

    @Test
    void returnsNullWhenScriptFailsToParse(@TempDir Path tempDir) throws IOException {
        File script = tempDir.resolve("broken.vns").toFile();
        Files.writeString(script.toPath(), "@this is not valid vns syntax {{{", StandardCharsets.UTF_8);

        assertNull(PuppeteerCharacterRigResolver.resolve(script, "hero"));
    }
}
