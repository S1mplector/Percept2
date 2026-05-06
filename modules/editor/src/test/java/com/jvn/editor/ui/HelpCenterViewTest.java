package com.jvn.editor.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HelpCenterViewTest {

  @Test
  void classifiesExampleGuideFamiliesIntoAuthoringSections() {
    assertEquals("vns", HelpCenterView.guideSectionKeyForWorkspacePath("docs/guides/vns-by-example/03-choices.md"));
    assertEquals("jes", HelpCenterView.guideSectionKeyForWorkspacePath("docs/guides/jes-by-example/05-camera.md"));
  }

  @Test
  void assignsStableTopicBucketsForGuideTree() {
    assertEquals("Language Reference",
        HelpCenterView.guideBucketForPath("vns", "docs/scripting/vns/language/vns-commands.md").title());
    assertEquals("Timeline DSL",
        HelpCenterView.guideBucketForPath("jes", "docs/scripting/jes/timeline/jes-timeline.md").title());
    assertEquals("Right Sidebar Tools",
        HelpCenterView.guideBucketForPath("editor", "docs/editor/sidebars/right/sidebar-help-center.md").title());
  }
}
