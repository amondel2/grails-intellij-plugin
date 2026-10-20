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
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.psi.PsiDirectory;
import org.apache.grails.intellij.plugin.config.FolderPaths;
import org.apache.grails.intellij.plugin.projectView.NodeWeights;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * A configured custom folder that resolves on disk. Being a {@link GrailsPsiDirectoryNode} is what
 * makes the node traversable: its value is a {@link PsiDirectory}, so the pane's visitor reaches the
 * files inside it and the data context carries PSI_ELEMENT/VIRTUAL_FILE. A path that resolves to
 * nothing gets the {@link GrailsMissingCustomFolderNode} placeholder instead.
 */
public final class GrailsCustomFolderNode extends GrailsPsiDirectoryNode implements GrailsCustomFolder {

  private final String configuredPath;
  private final int position;

  public GrailsCustomFolderNode(@NotNull Project project,
                                @NotNull PsiDirectory dir,
                                @NotNull ViewSettings settings,
                                @NotNull String configuredPath) {
    this(project, dir, settings, configuredPath, 0);
  }

  public GrailsCustomFolderNode(@NotNull Project project,
                                @NotNull PsiDirectory dir,
                                @NotNull ViewSettings settings,
                                @NotNull String configuredPath,
                                int position) {
    super(dir, settings, AllIcons.Nodes.Folder, NodeWeights.CUSTOM_FOLDER,
          FolderPaths.folderName(configuredPath), null, configuredPath);
    this.configuredPath = configuredPath;
    this.position = position;
  }

  @Override
  public @NotNull String getConfiguredPath() {
    return configuredPath;
  }

  @Override
  public int getPosition() {
    return position;
  }

  /**
   * The platform lists a directory's children only when the directory belongs to the project (or is
   * excluded from it), and otherwise only the directories leading to a content root, which for a
   * folder outside the project is nothing. Such a folder lists its own contents instead.
   */
  @Override
  public Collection<AbstractTreeNode<?>> getChildrenImpl() {
    PsiDirectory directory = getValue();
    if (directory == null) return List.of();
    Project project = directory.getProject();
    if (ProjectFileIndex.getInstance(project).isInProjectOrExcluded(directory.getVirtualFile())) {
      return super.getChildrenImpl();
    }
    return GrailsExternalDirectoryNode.childrenOf(directory, getSettings());
  }

  /** Runs after the project-view decorators, which is the same point the title is written from. */
  @Override
  protected void postprocess(@NotNull PresentationData data) {
    super.postprocess(data);
    data.setTooltip(configuredPath);
  }
}
