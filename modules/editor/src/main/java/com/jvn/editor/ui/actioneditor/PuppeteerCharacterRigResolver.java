package com.jvn.editor.ui.actioneditor;

import com.jvn.core.vn.VnCharacter;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.script.VnScriptParser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * Best-effort, silent-on-failure resolution of a {@link VnCharacter}'s rig
 * declarations ({@code @charlayer}/{@code @chargroup}) from the {@code .vns}
 * script Puppeteer is currently animating. Used only by diagnostics that can
 * safely no-op when rig context is unavailable — never for anything whose
 * correctness depends on the parse succeeding.
 */
final class PuppeteerCharacterRigResolver {
    private PuppeteerCharacterRigResolver() {
    }

    static VnCharacter resolve(File scriptFile, String characterId) {
        if (scriptFile == null || !scriptFile.isFile()) return null;
        String id = characterId == null ? "" : characterId.trim();
        if (id.isBlank()) return null;

        VnScriptParser parser = new VnScriptParser();
        try (InputStream in = Files.newInputStream(scriptFile.toPath())) {
            VnScenario scenario = parser.parse(in, scriptFile.getName(), null);
            return scenario == null ? null : scenario.getCharacter(id);
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }
}
