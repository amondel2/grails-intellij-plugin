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
package org.apache.grails.intellij.plugin.actions;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.tree.TreeUtil;
import org.apache.grails.intellij.plugin.config.FolderPaths;
import org.apache.grails.intellij.plugin.config.GrailsCustomFoldersProjectService;
import org.apache.grails.intellij.plugin.projectView.nodes.GrailsCustomFolderNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;

public class RemoveCustomFolderAction extends DumbAwareAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    boolean visible = false;
    if (project != null) {
      AbstractProjectViewPane pane = ProjectView.getInstance(project).getCurrentProjectViewPane();
      if (pane != null) {
        GrailsCustomFolderNode node = selectedCustomFolder(pane);
        visible = node != null && isProjectFolder(project, node.getConfiguredPath());
      }
    }
    e.getPresentation().setEnabledAndVisible(visible);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    AbstractProjectViewPane pane = ProjectView.getInstance(project).getCurrentProjectViewPane();
    if (pane == null) return;
    GrailsCustomFolderNode node = selectedCustomFolder(pane);
    if (node == null) return;

    GrailsCustomFoldersProjectService service = project.getService(GrailsCustomFoldersProjectService.class);
    List<String> folders = new ArrayList<>(service.getFolders());
    // Folders may have changed between update and actionPerformed (e.g. another settings dialog
    // closed); if the selected folder is no longer in the project's list, there is nothing to do.
    if (!folders.remove(node.getConfiguredPath())) return;
    service.setFolders(folders);
  }

  private static @Nullable GrailsCustomFolderNode selectedCustomFolder(@NotNull AbstractProjectViewPane pane) {
    TreePath path = pane.getSelectedPath();
    return path == null ? null : TreeUtil.getLastUserObject(GrailsCustomFolderNode.class, path);
  }

  static boolean isProjectFolder(@NotNull Project project, @NotNull String configuredPath) {
    String normalized = FolderPaths.normalize(configuredPath);
    GrailsCustomFoldersProjectService service = project.getService(GrailsCustomFoldersProjectService.class);
    for (String folder : service.getFolders()) {
      if (FolderPaths.normalize(folder).equals(normalized)) return true;
    }
    return false;
  }
}
