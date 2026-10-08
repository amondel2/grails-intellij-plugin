/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.grails.intellij.plugin.projectView.impl;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.tree.LeafState;
import org.jetbrains.annotations.NotNull;
import org.apache.grails.intellij.plugin.config.GrailsCustomFoldersAppService;
import org.apache.grails.intellij.plugin.config.GrailsCustomFoldersProjectService;
import org.apache.grails.intellij.plugin.projectView.NodeWeights;
import org.apache.grails.intellij.plugin.projectView.nodes.GrailsCustomFolderNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GrailsCustomFoldersNodeProviderTest extends GrailsNodeProviderTestSupport {

  public void testNodesCreatedForEachConfiguredFolder() {
    myFixture.addFileToProject("docs/readme.md", "# readme");
    myFixture.addFileToProject(".github/workflows/ci.yml", "jobs: {}");
    myFixture.addFileToProject("build/output.txt", "stray");

    GrailsCustomFoldersProjectService service = getProject().getService(GrailsCustomFoldersProjectService.class);
    service.setFolders(List.of("docs", ".github", "build"));

    List<GrailsCustomFolderNode> nodes = customFolderNodes(
      new GrailsCustomFoldersNodeProvider().createNodes(testApplication(false), ViewSettings.DEFAULT));

    assertEquals("one node per configured folder", 3, nodes.size());
    for (GrailsCustomFolderNode node : nodes) {
      assertEquals("every custom folder node carries the custom-folder weight",
                   NodeWeights.CUSTOM_FOLDER, node.getNodeWeight());
    }
    assertEquals("each node is labelled with its folder name",
                 List.of(".github", "build", "docs"),
                 nodes.stream().map(GrailsCustomFoldersNodeProviderTest::label).toList());
  }

  public void testNodesCreatedForGlobalAndProjectFolders() {
    myFixture.addFileToProject("docs/readme.md", "# readme");
    myFixture.addFileToProject("src/placeholder.txt", "");

    withGlobalFolders(List.of("docs"), () -> {
      GrailsCustomFoldersProjectService service = getProject().getService(GrailsCustomFoldersProjectService.class);
      service.setFolders(List.of("src"));

      List<GrailsCustomFolderNode> nodes = customFolderNodes(
        new GrailsCustomFoldersNodeProvider().createNodes(testApplication(false), ViewSettings.DEFAULT));

      assertEquals("global defaults and project folders each produce a node",
                   List.of("docs", "src"),
                   nodes.stream().map(GrailsCustomFoldersNodeProviderTest::label).toList());
    });
  }

  /**
   * The rendered nodes are capped at three in total, with the project's own folders taking
   * precedence over the global defaults.
   */
  public void testNodesCappedAtThreeWithProjectFoldersFirst() {
    myFixture.addFileToProject("global1/placeholder.txt", "");
    myFixture.addFileToProject("global2/placeholder.txt", "");
    myFixture.addFileToProject("custom1/placeholder.txt", "");
    myFixture.addFileToProject("custom2/placeholder.txt", "");

    withGlobalFolders(List.of("global1", "global2"), () -> {
      GrailsCustomFoldersProjectService service = getProject().getService(GrailsCustomFoldersProjectService.class);
      service.setFolders(List.of("custom1", "custom2"));

      List<GrailsCustomFolderNode> nodes = customFolderNodes(
        new GrailsCustomFoldersNodeProvider().createNodes(testApplication(false), ViewSettings.DEFAULT));

      assertEquals("two custom and one global folder are rendered",
                   List.of("custom1", "custom2", "global1"),
                   nodes.stream().map(GrailsCustomFoldersNodeProviderTest::label).toList());
    });
  }

  /** Three project folders fill the budget, so no global folder gets a node. */
  public void testThreeProjectFoldersExcludeGlobalFolders() {
    myFixture.addFileToProject("global1/placeholder.txt", "");
    myFixture.addFileToProject("custom1/placeholder.txt", "");
    myFixture.addFileToProject("custom2/placeholder.txt", "");
    myFixture.addFileToProject("custom3/placeholder.txt", "");

    withGlobalFolders(List.of("global1"), () -> {
      GrailsCustomFoldersProjectService service = getProject().getService(GrailsCustomFoldersProjectService.class);
      service.setFolders(List.of("custom1", "custom2", "custom3"));

      List<GrailsCustomFolderNode> nodes = customFolderNodes(
        new GrailsCustomFoldersNodeProvider().createNodes(testApplication(false), ViewSettings.DEFAULT));

      assertEquals("only the three custom folders are rendered",
                   List.of("custom1", "custom2", "custom3"),
                   nodes.stream().map(GrailsCustomFoldersNodeProviderTest::label).toList());
    });
  }

  private void withGlobalFolders(@NotNull List<String> folders, @NotNull Runnable body) {
    GrailsCustomFoldersAppService appService = GrailsCustomFoldersAppService.getStaticInstance();
    List<String> original = new ArrayList<>(appService.getFolders());
    try {
      appService.setFolders(folders);
      body.run();
    }
    finally {
      appService.setFolders(original);
    }
  }

  public void testTrailingSlashKeepsFolderName() {
    myFixture.addFileToProject("docs/readme.md", "# readme");

    GrailsCustomFoldersProjectService service = getProject().getService(GrailsCustomFoldersProjectService.class);
    service.setFolders(List.of("docs/"));

    List<GrailsCustomFolderNode> nodes = customFolderNodes(
      new GrailsCustomFoldersNodeProvider().createNodes(testApplication(false), ViewSettings.DEFAULT));

    assertEquals("a trailing slash still produces its node", 1, nodes.size());
    assertEquals("a trailing slash does not blank the folder name", "docs", label(nodes.get(0)));
    assertFalse("a trailing slash still resolves the existing folder", nodes.get(0).getChildren().isEmpty());
  }

  public void testLeadingDotSlashStillResolves() {
    myFixture.addFileToProject("docs/readme.md", "# readme");

    GrailsCustomFoldersProjectService service = getProject().getService(GrailsCustomFoldersProjectService.class);
    service.setFolders(List.of("./docs"));

    List<GrailsCustomFolderNode> nodes = customFolderNodes(
      new GrailsCustomFoldersNodeProvider().createNodes(testApplication(false), ViewSettings.DEFAULT));

    assertEquals("a leading ./ still produces its node", 1, nodes.size());
    assertEquals("a leading ./ does not blank the folder name", "docs", label(nodes.get(0)));
    assertFalse("a leading ./ still resolves the existing folder", nodes.get(0).getChildren().isEmpty());
  }

  public void testTrailingBackslashKeepsFolderName() {
    myFixture.addFileToProject("docs/readme.md", "# readme");

    GrailsCustomFoldersProjectService service = getProject().getService(GrailsCustomFoldersProjectService.class);
    service.setFolders(List.of("docs\\"));

    List<GrailsCustomFolderNode> nodes = customFolderNodes(
      new GrailsCustomFoldersNodeProvider().createNodes(testApplication(false), ViewSettings.DEFAULT));

    assertEquals("a trailing backslash still produces its node", 1, nodes.size());
    assertEquals("a trailing backslash does not blank the folder name", "docs", label(nodes.get(0)));
    assertFalse("a trailing backslash still resolves the existing folder", nodes.get(0).getChildren().isEmpty());
  }

  public void testNodesSortedAlphabeticallyByLabel() {
    myFixture.addFileToProject("zebra/z.txt", "");
    myFixture.addFileToProject("alpha/a.txt", "");
    myFixture.addFileToProject("middle/m.txt", "");

    GrailsCustomFoldersProjectService service = getProject().getService(GrailsCustomFoldersProjectService.class);
    service.setFolders(List.of("zebra", "alpha", "middle"));

    List<GrailsCustomFolderNode> nodes = customFolderNodes(
      new GrailsCustomFoldersNodeProvider().createNodes(testApplication(false), ViewSettings.DEFAULT));

    assertEquals("nodes are ordered alphabetically by label regardless of configuration order",
                 List.of("alpha", "middle", "zebra"),
                 nodes.stream().map(GrailsCustomFoldersNodeProviderTest::label).toList());
  }

  public void testNonExistentFolderIsPlaceholder() throws Exception {
    VirtualFile otherDir = myFixture.getTempDirFixture().findOrCreateDir("other");
    assertNotNull(otherDir);

    GrailsCustomFoldersProjectService service = getProject().getService(GrailsCustomFoldersProjectService.class);
    service.setFolders(List.of("nonexistent"));

    List<GrailsCustomFolderNode> nodes = customFolderNodes(
      new GrailsCustomFoldersNodeProvider().createNodes(testApplication(false), ViewSettings.DEFAULT));

    assertEquals("a missing folder still gets its placeholder node", 1, nodes.size());
    GrailsCustomFolderNode node = nodes.get(0);
    assertEquals("the placeholder is labelled with the configured folder name", "nonexistent", label(node));
    assertTrue("a placeholder has no children", node.getChildren().isEmpty());
    assertFalse("a placeholder contains nothing", node.contains(otherDir));
    assertEquals("a placeholder must report itself as a leaf, or the tree keeps asking for children",
                 LeafState.ALWAYS, node.getLeafState());
  }

  public void testContainsFileInsideFolder() {
    myFixture.addFileToProject("docs/readme.md", "# readme");

    GrailsCustomFoldersProjectService service = getProject().getService(GrailsCustomFoldersProjectService.class);
    service.setFolders(List.of("docs"));

    GrailsCustomFolderNode node = customFolderNodes(
      new GrailsCustomFoldersNodeProvider().createNodes(testApplication(false), ViewSettings.DEFAULT)).get(0);

    VirtualFile file = myFixture.findFileInTempDir("docs/readme.md");
    assertNotNull(file);
    assertTrue("the folder directory itself is contained", node.contains(file.getParent()));
    assertTrue("a file inside the folder is contained, so Reveal in Project View can find the node",
               node.contains(file));
  }

  /**
   * The tree computes leaf state by calling getChildren(), so a placeholder that never settles leaves
   * the pane on "Loading...". An existing folder must stay expandable.
   */
  public void testExistingFolderIsNotALeaf() {
    myFixture.addFileToProject("docs/readme.md", "# readme");

    GrailsCustomFoldersProjectService service = getProject().getService(GrailsCustomFoldersProjectService.class);
    service.setFolders(List.of("docs"));

    GrailsCustomFolderNode node = customFolderNodes(
      new GrailsCustomFoldersNodeProvider().createNodes(testApplication(false), ViewSettings.DEFAULT)).get(0);

    assertEquals("an existing folder must stay expandable",
                 LeafState.DEFAULT, node.getLeafState());
    assertFalse("an existing folder has children to browse", node.getChildren().isEmpty());
  }

  public void testNoNodesWhenNoFoldersConfigured() {
    withGlobalFolders(List.of(), () -> {
      getProject().getService(GrailsCustomFoldersProjectService.class).setFolders(List.of());

      Collection<AbstractTreeNode<?>> nodes =
        new GrailsCustomFoldersNodeProvider().createNodes(testApplication(false), ViewSettings.DEFAULT);

      assertTrue("no custom folder nodes without configured folders", customFolderNodes(nodes).isEmpty());
    });
  }

  private static @NotNull List<GrailsCustomFolderNode> customFolderNodes(
    @NotNull Collection<AbstractTreeNode<?>> nodes) {
    return nodes.stream()
      .filter(GrailsCustomFolderNode.class::isInstance)
      .map(GrailsCustomFolderNode.class::cast)
      .toList();
  }

  private static @NotNull String label(@NotNull GrailsCustomFolderNode node) {
    node.update();
    return node.getPresentation().getPresentableText();
  }

}
