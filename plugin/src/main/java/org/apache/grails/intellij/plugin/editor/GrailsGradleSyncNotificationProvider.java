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
package org.apache.grails.intellij.plugin.editor;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import org.apache.grails.intellij.plugin.GrailsBundle;
import org.apache.grails.intellij.plugin.gradle.GrailsGradleSyncStatus;
import org.apache.grails.intellij.plugin.gradle.GrailsGradleSyncStatus.BlockedProject;
import org.apache.grails.intellij.plugin.gradle.GrailsGradleSyncStatus.Reason;
import org.apache.grails.intellij.plugin.structure.GrailsApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.function.Function;

/**
 * Banner on files under a {@code grails-app} folder whose project the plugin does not recognise as a
 * Grails application because its Gradle import failed or never ran. Without it those files simply
 * lack every Grails feature, with nothing pointing at the Gradle error in the Build tool window.
 */
public final class GrailsGradleSyncNotificationProvider implements EditorNotificationProvider, DumbAware {

  private static final String GRAILS_APP = "grails-app";

  @Override
  public @Nullable Function<? super FileEditor, ? extends JComponent> collectNotificationData(@NotNull Project project,
                                                                                             @NotNull VirtualFile file) {
    VirtualFile root = findGrailsAppRoot(file);
    if (root == null) return null;
    if (GrailsApplicationManager.getInstance(project).getApplicationByRoot(root) != null) return null;
    GrailsGradleSyncStatus status = GrailsGradleSyncStatus.getInstance(project);
    BlockedProject blocked = status.findBlockingProject(root);
    if (blocked == null) return null;
    return fileEditor -> createPanel(fileEditor, status, blocked);
  }

  /** The parent of the nearest enclosing {@code grails-app} directory, or {@code null} outside one. */
  static @Nullable VirtualFile findGrailsAppRoot(@NotNull VirtualFile file) {
    for (VirtualFile dir = file.getParent(); dir != null; dir = dir.getParent()) {
      if (dir.isDirectory() && GRAILS_APP.equals(dir.getName())) return dir.getParent();
    }
    return null;
  }

  private static @NotNull JComponent createPanel(@NotNull FileEditor fileEditor,
                                                 @NotNull GrailsGradleSyncStatus status,
                                                 @NotNull BlockedProject blocked) {
    EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning);
    String key = blocked.reason() == Reason.IMPORT_FAILED ? "gradle.sync.blocked.editor.import.failed" : "gradle.sync.blocked.editor.not.imported";
    String text = GrailsBundle.message(key, blocked.getName());
    if (blocked.errorMessage() != null) {
      text += " " + GrailsBundle.message("gradle.sync.blocked.error", blocked.errorMessage());
    }
    panel.text(text);
    panel.createActionLabel(GrailsBundle.message("gradle.sync.blocked.action.reload"),
                            () -> status.reloadGradleProject(blocked.externalProjectPath()));
    panel.createActionLabel(GrailsBundle.message("gradle.sync.blocked.action.show.build"), status::showBuildToolWindow);
    return panel;
  }
}
