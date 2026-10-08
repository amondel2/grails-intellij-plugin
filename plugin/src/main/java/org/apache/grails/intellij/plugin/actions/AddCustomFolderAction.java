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

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import org.apache.grails.intellij.plugin.config.GrailsCustomFoldersProjectService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AddCustomFolderAction extends DumbAwareAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    PsiDirectory directory = getSelectedDirectory(e);
    if (directory == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    GrailsCustomFoldersProjectService service = project.getService(GrailsCustomFoldersProjectService.class);
    if (service.getFolders().size() >= GrailsCustomFoldersProjectService.MAX_FOLDERS) {
      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(false);
      e.getPresentation().setDescription(
          "Maximum of " + GrailsCustomFoldersProjectService.MAX_FOLDERS + " custom folders reached. Remove one first.");
    }
    else {
      e.getPresentation().setEnabledAndVisible(true);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    PsiDirectory directory = getSelectedDirectory(e);
    if (project == null || directory == null) return;

    VirtualFile virtualFile = directory.getVirtualFile();
    String path = VfsUtilCore.getRelativePath(virtualFile, project.getBaseDir(), '/');
    if (path == null) {
      path = virtualFile.getPath();
    }

    GrailsCustomFoldersProjectService service = project.getService(GrailsCustomFoldersProjectService.class);
    List<String> folders = new ArrayList<>(service.getFolders());
    if (!folders.contains(path)) {
      folders.add(path);
      service.setFolders(folders);
    }
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
