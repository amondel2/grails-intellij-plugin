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

package org.apache.grails.intellij.plugin.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import org.apache.grails.intellij.plugin.projectView.GrailsProjectViewPanes;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@State(name = "GrailsCustomFolders", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class GrailsCustomFoldersProjectService implements PersistentStateComponent<GrailsCustomFoldersProjectService.StateHolder> {
  public static final int MAX_FOLDERS = 3;

  private final Project project;

  private List<String> folders = new ArrayList<>();

  public GrailsCustomFoldersProjectService(@NotNull Project project) {
    this.project = project;
  }

  public @NotNull List<String> getFolders() {
    return Collections.unmodifiableList(new ArrayList<>(folders));
  }

  public void setFolders(@NotNull List<String> folders) {
    this.folders = clean(folders);
    // Repaint on the UI thread: this runs from the settings dialog and from the tree actions, and the
    // pane must be updated on the EDT. Going through the pane directly is what makes the view react
    // immediately; the event below is only a notification for anything else listening.
    project.getMessageBus().syncPublisher(FoldersChangedListener.TOPIC).foldersChanged();
    ApplicationManager.getApplication().invokeLater(() -> GrailsProjectViewPanes.refresh(project),
                                                    ModalityState.defaultModalityState(), project.getDisposed());
  }

  /**
   * Returns the folders the Grails view shows for this project: this project's own folders
   * first, then the global defaults, with the total limited to {@link #MAX_FOLDERS}. A folder
   * configured in both areas appears once — duplicates are detected after
   * {@link FolderPaths#normalize(String)}.
   */
  public @NotNull List<String> getEffectiveFolders() {
    return Collections.unmodifiableList(combine(folders, GrailsCustomFoldersAppService.getStaticInstance().getFolders()));
  }

  /**
   * Combines this project's folders with the global defaults the way the Grails view shows them:
   * the project's own folders first (they are the more specific configuration), then the global
   * defaults, with the total limited to {@link #MAX_FOLDERS}. Duplicates (after
   * {@link FolderPaths#normalize(String)}) are dropped.
   */
  static @NotNull List<String> combine(@NotNull List<String> projectFolders, @NotNull List<String> globalFolders) {
    List<String> effective = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (String folder : projectFolders) {
      if (effective.size() >= MAX_FOLDERS) break;
      if (seen.add(FolderPaths.normalize(folder))) {
        effective.add(folder);
      }
    }
    for (String folder : globalFolders) {
      if (effective.size() >= MAX_FOLDERS) break;
      if (seen.add(FolderPaths.normalize(folder))) {
        effective.add(folder);
      }
    }
    return effective;
  }

  static @NotNull List<String> clean(@NotNull List<String> raw) {
    List<String> res = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (String folder : raw) {
      if (folder == null) continue;
      String trimmed = folder.trim();
      if (trimmed.isEmpty()) continue;
      String normalized = FolderPaths.normalize(trimmed);
      if (normalized.isEmpty()) continue;
      if (seen.add(normalized)) {
        res.add(trimmed);
      }
      if (res.size() >= MAX_FOLDERS) break;
    }
    return res;
  }

  @Override
  public StateHolder getState() {
    StateHolder res = new StateHolder();
    res.folders = new ArrayList<>(folders);
    return res;
  }

  @Override
  public void loadState(@NotNull StateHolder state) {
    folders = clean(state.folders);
  }

  public static final class StateHolder {
    public List<String> folders = new ArrayList<>();
  }

  public interface FoldersChangedListener {

    Topic<FoldersChangedListener> TOPIC = Topic.create("Grails Custom Folders Changed", FoldersChangedListener.class);

    void foldersChanged();
  }
}
