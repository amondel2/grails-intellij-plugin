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

package org.apache.grails.intellij.plugin.projectView.nodes;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.apache.grails.intellij.lib.testFramework.GrailsTestCase;
import org.apache.grails.intellij.plugin.projectView.NodeWeights;

import javax.swing.Icon;
import java.util.List;
import java.util.Objects;

public class GrailsPsiDirectoryNodeTest extends GrailsTestCase {

  public void testRendersCustomTitleAndIcon() {
    GrailsPsiDirectoryNode node = nodeWithCustomPresentation("grails-app/i18n/messages.properties", "Translations",
                                                           AllIcons.FileTypes.Properties, NodeWeights.CONFIG_FOLDER);

    PresentationData data = rendered(node);

    assertEquals("Translations", data.getPresentableText());
    assertSame(AllIcons.FileTypes.Properties, data.getIcon(false));
  }

  /**
   * The renderer draws {@code PresentationData}'s coloured fragments, and {@code PsiDirectoryNode} fills
   * them with the directory name. A title written only with {@code setPresentableText} is therefore
   * invisible: the field reads correctly while the tree keeps painting the directory name. This regression
   * shipped once — a lifted test root rendered as {@code test} instead of {@code Tests:unit} while its
   * location string still showed — so the fragment list is what this asserts.
   */
  public void testTitleReplacesTheDirectoryNameFragments() {
    GrailsPsiDirectoryNode node = nodeWithCustomPresentation("grails-app/i18n/messages.properties",
                                                             "Translations", AllIcons.FileTypes.Properties,
                                                             NodeWeights.TRANSLATIONS_FOLDER);

    PresentationData data = rendered(node);

    assertEquals("the fragments the renderer draws must be the title alone",
                 List.of("Translations"), drawnFragments(data));
  }

  /** An untitled node keeps the platform's own fragments, so nothing is lost by not clearing them. */
  public void testWithoutCustomPresentationKeepsPlatformFragments() {
    PsiDirectory directory = findDirectoryCreatedBy("grails-app/views/index.gsp");
    GrailsPsiDirectoryNode node = new GrailsPsiDirectoryNode(directory, ViewSettings.DEFAULT);

    PresentationData data = rendered(node);

    assertEquals("an untitled node shows the platform's own label",
                 "grails-app.views", data.getPresentableText());
    assertTrue("postprocess must not invent a title for an untitled node",
               drawnFragments(data).stream().noneMatch("Views"::equals));
  }

  public void testLocationSurvivesAlongsideTheTitle() {
    PsiDirectory directory = findDirectoryCreatedBy("grails-app/views/index.gsp");
    GrailsPsiDirectoryNode node = new GrailsPsiDirectoryNode(directory, ViewSettings.DEFAULT, null,
                                                             NodeWeights.VIEWS_FOLDER, "Views", null,
                                                             "src/views");

    PresentationData data = rendered(node);

    assertEquals(List.of("Views"), drawnFragments(data));
    assertEquals("src/views", data.getLocationString());
  }

  /**
   * The label is only final after {@code postprocess}, which is the hook that runs on the renderer path.
   * Asserting straight after {@code updateImpl} checks the platform's label, not ours.
   */
  private static @NotNull PresentationData rendered(@NotNull GrailsPsiDirectoryNode node) {
    PresentationData data = new PresentationData();
    node.updateImpl(data);
    node.postprocess(data);
    return data;
  }

  /** The fragments the renderer actually draws, after the platform has finished writing the label. */
  private static @NotNull List<String> drawnFragments(@NotNull PresentationData data) {
    return data.getColoredText().stream().map(fragment -> fragment.getText()).toList();
  }

  public void testWithoutCustomPresentationUsesPlainDirectoryName() {
    PsiDirectory directory = findDirectoryCreatedBy("grails-app/views/index.gsp");
    GrailsPsiDirectoryNode node = new GrailsPsiDirectoryNode(directory, ViewSettings.DEFAULT);

    PresentationData data = new PresentationData();
    node.updateImpl(data);

    assertFalse("no custom title must be applied without customization",
                data.getPresentableText().contains("Translations"));
    assertNotSame("a bespoke icon must not appear without customization", AllIcons.FileTypes.Properties,
                  data.getIcon(false));
  }

  private GrailsPsiDirectoryNode nodeWithCustomPresentation(@NotNull String filePath,
                                                            @NotNull String title,
                                                            @NotNull Icon icon,
                                                            int weight) {
    PsiDirectory directory = findDirectoryCreatedBy(filePath);
    return new GrailsPsiDirectoryNode(directory, ViewSettings.DEFAULT, icon, weight, title);
  }

  private @NotNull PsiDirectory findDirectoryCreatedBy(@NotNull String filePath) {
    myFixture.addFileToProject(filePath, "content");
    VirtualFile file = myFixture.findFileInTempDir(filePath);
    assertNotNull(file);
    PsiDirectory directory = PsiManager.getInstance(getProject()).findDirectory(Objects.requireNonNull(file.getParent()));
    assertNotNull(directory);
    return directory;
  }
}