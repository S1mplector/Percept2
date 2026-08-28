package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.editor.ui.AssetAutoLabelService.AssetKind;
import com.jvn.editor.ui.AssetAutoLabelService.AssetSuggestion;
import com.jvn.editor.ui.AssetAutoLabelService.LabelStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Opt-in, read-only corpus regression for real-world visual-asset labeling conventions. */
class AssetAutoLabelWasIWriteCorpusTest {
  @Test
  void inventoriesAndValidatesEveryWasIWriteAssetWithoutMutatingTheProject() throws Exception {
    String configured = System.getProperty("wasIWriteRoot", "").trim();
    if (configured.isEmpty()) configured = System.getenv().getOrDefault("WAS_I_WRITE_ROOT", "").trim();
    Assumptions.assumeTrue(!configured.isEmpty(), "Set WAS_I_WRITE_ROOT to run the corpus audit");
    Path root = Path.of(configured).toAbsolutePath().normalize();
    Assumptions.assumeTrue(Files.isRegularFile(root.resolve("jvn.project")),
        "Was I Write project not found: " + root);

    Path registry = root.resolve(AssetAutoLabelService.REGISTRY_PATH);
    boolean registryExisted = Files.exists(registry);
    long registryModified = registryExisted ? Files.getLastModifiedTime(registry).toMillis() : -1;
    AssetAutoLabelService service = new AssetAutoLabelService();
    AssetAutoLabelService.ScanResult result = service.preview(root);

    assertTrue(result.currentAssetCount() > 0);
    assertEquals(0, result.scanIssues(), "Every supported asset should be readable");
    Set<String> scopedLabels = new HashSet<>();
    for (AssetSuggestion asset : result.assets()) {
      if (asset.status() == LabelStatus.MISSING) {
        assertFalse(Files.isRegularFile(asset.file()), asset.relativePath());
      } else {
        assertTrue(Files.isRegularFile(asset.file()), asset.relativePath());
      }
      assertFalse(asset.relativePath().startsWith("/"), asset.relativePath());
      assertFalse(asset.label().isBlank(), "Missing label: " + asset.relativePath());
      if (asset.kind().usesCharacterDeclaration()) {
        assertFalse(asset.owner().isBlank(), "Missing owner: " + asset.relativePath());
      }
      String scope = asset.owner() + "/" + asset.label();
      assertTrue(scopedLabels.add(scope), "Duplicate scoped label: " + scope);
      if (asset.kind().isVnsDeclarable()) {
        assertFalse(service.declarationFor(asset).isBlank(), asset.relativePath());
      }
    }
    assertEquals(registryExisted, Files.exists(registry), "preview must not create registry metadata");
    if (registryExisted) {
      assertEquals(registryModified, Files.getLastModifiedTime(registry).toMillis(),
          "preview must not rewrite registry metadata");
    }

    System.out.println("AUTO-LABEL CORPUS: " + result.currentAssetCount() + " assets; "
        + result.declaredCount() + " declared; " + result.reviewCount() + " suggested; "
        + result.highConfidenceSuggestions() + " high-confidence; " + result.missingCount()
        + " missing declaration target(s)");
    for (AssetKind kind : AssetKind.values()) {
      int count = result.byKind().getOrDefault(kind, 0);
      if (count > 0) System.out.println("  KIND " + kind + "=" + count);
    }
    for (LabelStatus state : LabelStatus.values()) {
      int count = result.byStatus().getOrDefault(state, 0);
      if (count > 0) System.out.println("  STATUS " + state + "=" + count);
    }
    System.out.println("VNS LABEL CONFLICTS:");
    result.assets().stream().filter(asset -> asset.status() == LabelStatus.CONFLICT)
        .forEach(asset -> System.out.println("  " + asset.owner() + "/" + asset.label()
            + " " + asset.relativePath()));
    System.out.println("MISSING DECLARATION TARGETS:");
    result.assets().stream().filter(asset -> asset.status() == LabelStatus.MISSING)
        .limit(80).forEach(asset -> System.out.println("  " + asset.relativePath()));
    System.out.println("LOWEST-CONFIDENCE UNDECLARED:");
    result.assets().stream().filter(asset -> asset.status() == LabelStatus.SUGGESTED)
        .sorted(Comparator.comparingDouble(AssetSuggestion::confidence)
            .thenComparing(AssetSuggestion::relativePath))
        .limit(40).forEach(asset -> System.out.printf(Locale.ROOT,
            "  %.0f%% %-16s owner=%-24s label=%-30s %s%n",
            asset.confidence() * 100, asset.kind(), asset.owner(), asset.label(),
            asset.relativePath()));
  }
}
