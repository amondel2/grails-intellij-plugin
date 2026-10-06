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

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileSystemItemFilter;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

public class GrailsPsiDirectoryNode extends PsiDirectoryNode {

  private final Icon nodeIcon;
  private final int nodeWeight;
  private final String nodeTitle;
  private final String nodeLocation;

  public GrailsPsiDirectoryNode(@NotNull PsiDirectory directory, @NotNull ViewSettings settings) {
    this(directory, settings, null, 3, null, null);
  }

  public GrailsPsiDirectoryNode(@NotNull PsiDirectory directory, @NotNull ViewSettings settings, int nodeWeight) {
    this(directory, settings, null, nodeWeight, null, null);
  }

  public GrailsPsiDirectoryNode(@NotNull PsiDirectory directory,
                                @NotNull ViewSettings settings,
                                int nodeWeight,
                                @Nullable PsiFileSystemItemFilter filter) {
    this(directory, settings, null, nodeWeight, null, filter);
  }

  public GrailsPsiDirectoryNode(@NotNull PsiDirectory directory,
                                @NotNull ViewSettings settings,
                                @Nullable Icon nodeIcon,
                                int nodeWeight,
                                @Nullable String nodeTitle) {
    this(directory, settings, nodeIcon, nodeWeight, nodeTitle, null);
  }

  public GrailsPsiDirectoryNode(@NotNull PsiDirectory directory,
                                @NotNull ViewSettings settings,
                                @Nullable Icon nodeIcon,
                                int nodeWeight,
                                @Nullable String nodeTitle,
                                @Nullable PsiFileSystemItemFilter filter) {
    this(directory, settings, nodeIcon, nodeWeight, nodeTitle, filter, null);
  }

  /**
   * The location is null by default because most nodes show none. {@code OtherGrailsAppSourcesNode} uses the
   * six-argument constructor, which leaves it null, and the subclass then sets its own in {@code updateImpl}.
   */
  public GrailsPsiDirectoryNode(@NotNull PsiDirectory directory,
                                @NotNull ViewSettings settings,
                                @Nullable Icon nodeIcon,
                                int nodeWeight,
                                @Nullable String nodeTitle,
                                @Nullable PsiFileSystemItemFilter filter,
                                @Nullable String nodeLocation) {
    super(directory.getProject(), directory, settings, filter);
    this.nodeIcon = nodeIcon;
    this.nodeWeight = nodeWeight;
    this.nodeTitle = nodeTitle;
    this.nodeLocation = nodeLocation;
  }

  public @Nullable Icon getNodeIcon() {
    return nodeIcon;
  }

  public int getNodeWeight() {
    return nodeWeight;
  }

  @Override
  protected void updateImpl(@NotNull PresentationData data) {
    super.updateImpl(data);
    if (nodeIcon != null) data.setIcon(nodeIcon);
    if (nodeLocation != null) data.setLocationString(nodeLocation);
  }

  /**
   * The title is written as a coloured fragment here, not with {@code setPresentableText} in
   * {@code updateImpl}, because the renderer draws {@code PresentationData}'s fragment list and
   * {@code PsiDirectoryNode} fills that list with the directory name — the qualified path, such as
   * {@code grails-app.i18n}, for a nested one. A field write is therefore invisible: the data reads
   * {@code Tests:unit} while the tree keeps painting {@code test}.
   *
   * <p>{@code postprocess} is the only hook that runs after the platform has finished writing the label, on
   * both the updated presentation and the template, so clearing the fragments and re-adding the title here
   * is what reaches the renderer. Overriding {@code updateImpl} cannot win, because the platform fills the
   * label during that same call.
   */
  @Override
  protected void postprocess(@NotNull PresentationData data) {
    super.postprocess(data);
    if (nodeTitle == null) return;
    data.clearText();
    data.addText(nodeTitle, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    data.setPresentableText(nodeTitle);
  }
}
