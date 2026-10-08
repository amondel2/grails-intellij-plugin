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
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.tree.LeafState;
import org.apache.grails.intellij.plugin.config.FolderPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * A configured custom folder shown as a top-level node in the Grails project view pane. The folder
 * may not exist on disk, in which case the node is a placeholder: it renders with the folder name
 * and the configured path as tooltip, but has no children and contains nothing.
 */
public final class GrailsCustomFolderNode extends AbstractTreeNode<String> {

  private final VirtualFile myFile;
  private final ViewSettings mySettings;
  private final int myWeight;
  private final String myConfiguredPath;
  private final String myLabel;

  public GrailsCustomFolderNode(@NotNull Project project,
                                @Nullable VirtualFile file,
                                @NotNull ViewSettings settings,
                                int weight,
                                @NotNull String configuredPath) {
    super(project, configuredPath);
    this.myFile = file;
    this.mySettings = settings;
    this.myWeight = weight;
    this.myConfiguredPath = configuredPath;
    String normalized = FolderPaths.normalize(configuredPath);
    int separator = normalized.lastIndexOf('/');
    this.myLabel = separator >= 0 ? normalized.substring(separator + 1) : normalized;
  }

  public @NotNull String getConfiguredPath() {
    return myConfiguredPath;
  }

  public int getNodeWeight() {
    return myWeight;
  }

  /**
   * A folder that does not exist has nothing to expand. Reporting it as a leaf keeps the tree from
   * asking it for children it can never have — the platform computes leaf state by calling
   * {@code getChildren()}, and a node that never settles leaves the pane on "Loading...".
   */
  @Override
  public @NotNull LeafState getLeafState() {
    VirtualFile file = myFile;
    return file != null && file.isDirectory() ? LeafState.DEFAULT : LeafState.ALWAYS;
  }

  @Override
  public @NotNull Collection<? extends AbstractTreeNode<?>> getChildren() {
    VirtualFile file = myFile;
    if (file == null || !file.isDirectory()) return List.of();
    PsiDirectory dir = PsiManager.getInstance(getProject()).findDirectory(file);
    if (dir == null) return List.of();
    return new PsiDirectoryNode(getProject(), dir, mySettings).getChildren();
  }

  public boolean contains(@NotNull VirtualFile file) {
    // VfsUtilCore.isAncestor throws on a null argument even though this parameter is @NotNull.
    if (file == null) return false;
    return myFile != null && myFile.isDirectory() && VfsUtilCore.isAncestor(myFile, file, false);
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    presentation.setIcon(AllIcons.Nodes.Folder);
    presentation.addText(myLabel, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    presentation.setPresentableText(myLabel);
    presentation.setTooltip(myConfiguredPath);
  }
}
