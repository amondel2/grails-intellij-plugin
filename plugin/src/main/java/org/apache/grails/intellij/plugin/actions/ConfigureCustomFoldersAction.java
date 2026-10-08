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
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.util.ui.tree.TreeUtil;
import org.apache.grails.intellij.plugin.config.GrailsCustomFoldersConfigurable;
import org.apache.grails.intellij.plugin.projectView.nodes.GrailsCustomFolderNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;

public class ConfigureCustomFoldersAction extends DumbAwareAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    boolean visible = false;
    if (project != null) {
      AbstractProjectViewPane pane = ProjectView.getInstance(project).getCurrentProjectViewPane();
      if (pane != null) {
        // Visible when a custom folder node is selected (same as Remove)
        if (isCustomFolderSelected(pane)) {
          visible = true;
        }
        // Also visible when a directory is selected (same as Add)
        else if (getSelectedDirectory(e) != null) {
          visible = true;
        }
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
    ShowSettingsUtil.getInstance().showSettingsDialog(project, GrailsCustomFoldersConfigurable.class);
  }

  private static boolean isCustomFolderSelected(@NotNull AbstractProjectViewPane pane) {
    TreePath path = pane.getSelectedPath();
    return path != null && TreeUtil.getLastUserObject(GrailsCustomFolderNode.class, path) != null;
  }

  private static @Nullable PsiDirectory getSelectedDirectory(@NotNull AnActionEvent e) {
    PsiElement element = e.getData(CommonDataKeys.PSI_ELEMENT);
    if (element instanceof PsiDirectory) {
      return (PsiDirectory)element;
    }
    VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (virtualFile != null && virtualFile.isDirectory()) {
      return PsiManager.getInstance(e.getProject()).findDirectory(virtualFile);
    }
    return null;
  }
}
