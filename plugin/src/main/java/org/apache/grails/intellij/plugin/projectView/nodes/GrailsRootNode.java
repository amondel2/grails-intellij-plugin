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
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.apache.grails.intellij.plugin.gradle.GrailsGradleSyncStatus;
import org.apache.grails.intellij.plugin.structure.GrailsApplication;
import org.apache.grails.intellij.plugin.structure.GrailsApplicationManager;

import java.util.ArrayList;
import java.util.List;

public class GrailsRootNode extends ProjectViewNode<Project> {

  public GrailsRootNode(@NotNull Project project, @NotNull ViewSettings viewSettings) {
    super(project, project, viewSettings);
  }

  @Override
  public @NotNull List<AbstractTreeNode<?>> getChildren() {
    Project project = getValue();
    List<AbstractTreeNode<?>> result = new ArrayList<>();
    for (GrailsApplication application : GrailsApplicationManager.getInstance(project).getApplications()) {
      result.add(new GrailsApplicationNode(application, getSettings()));
    }
    // grails-app folders the plugin cannot recognise because their Gradle import failed or never ran:
    // shown where their application node would be, so the pane says why instead of staying empty
    for (GrailsGradleSyncStatus.BlockedGrailsRoot blocked : GrailsGradleSyncStatus.getInstance(project).findBlockedGrailsRoots()) {
      result.add(new GrailsGradleSyncProblemNode(project, blocked, getSettings()));
    }
    return result;
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    Project project = getValue();
    if (GrailsApplicationManager.getInstance(project).findApplication(file) != null) return true;
    for (GrailsGradleSyncStatus.BlockedGrailsRoot blocked : GrailsGradleSyncStatus.getInstance(project).findBlockedGrailsRoots()) {
      if (VfsUtilCore.isAncestor(blocked.root(), file, false)) return true;
    }
    return false;
  }
}
