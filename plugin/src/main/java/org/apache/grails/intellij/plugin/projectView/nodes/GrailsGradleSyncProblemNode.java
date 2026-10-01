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
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import org.apache.grails.intellij.plugin.GrailsBundle;
import org.apache.grails.intellij.plugin.gradle.GrailsGradleSyncStatus;
import org.apache.grails.intellij.plugin.gradle.GrailsGradleSyncStatus.BlockedGrailsRoot;
import org.apache.grails.intellij.plugin.gradle.GrailsGradleSyncStatus.BlockedProject;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

/**
 * Stands in for a Grails application the plugin cannot recognise because the Gradle import of its
 * project failed or never ran. Shown in the Grails project view pane where the application node
 * would be, with a warning icon and the reason; double-clicking it reloads the Gradle project.
 */
public final class GrailsGradleSyncProblemNode extends ProjectViewNode<BlockedGrailsRoot> {

  public GrailsGradleSyncProblemNode(@NotNull Project project, @NotNull BlockedGrailsRoot value, @NotNull ViewSettings settings) {
    super(project, value, settings);
  }

  @Override
  public @NotNull Collection<? extends AbstractTreeNode<?>> getChildren() {
    return Collections.emptyList();
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    BlockedGrailsRoot value = getValue();
    if (value == null) return;
    BlockedProject blocked = value.project();
    presentation.setIcon(AllIcons.General.Warning);
    presentation.setPresentableText(value.root().getName());
    presentation.addText(value.root().getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    presentation.addText("  " + blocked.getShortDescription(), SimpleTextAttributes.ERROR_ATTRIBUTES);
    String tooltip = GrailsBundle.message("gradle.sync.blocked.node.tooltip", blocked.getName());
    if (blocked.errorMessage() != null) {
      tooltip += " " + GrailsBundle.message("gradle.sync.blocked.error", blocked.errorMessage());
    }
    presentation.setTooltip(tooltip);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    BlockedGrailsRoot value = getValue();
    return value != null && VfsUtilCore.isAncestor(value.root(), file, false);
  }

  @Override
  public boolean canNavigate() {
    return true;
  }

  @Override
  public boolean canNavigateToSource() {
    return false;
  }

  @Override
  public void navigate(boolean requestFocus) {
    BlockedGrailsRoot value = getValue();
    Project project = getProject();
    if (value != null && project != null && !project.isDisposed()) {
      GrailsGradleSyncStatus.getInstance(project).reloadGradleProject(value.project().externalProjectPath());
    }
  }

  @Override
  public int getWeight() {
    return 0;
  }
}
