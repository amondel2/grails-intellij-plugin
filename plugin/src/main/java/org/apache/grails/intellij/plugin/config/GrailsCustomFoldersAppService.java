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
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@State(name = "GrailsCustomFolders", storages = @Storage("GrailsCustomFolders.xml"))
public final class GrailsCustomFoldersAppService implements PersistentStateComponent<GrailsCustomFoldersAppService.StateHolder> {
  public static final int MAX_FOLDERS = 3;

  private List<String> folders = new ArrayList<>();

  public static @NotNull GrailsCustomFoldersAppService getStaticInstance() {
    return ApplicationManager.getApplication().getService(GrailsCustomFoldersAppService.class);
  }

  public @NotNull List<String> getFolders() {
    return Collections.unmodifiableList(new ArrayList<>(folders));
  }

  public void setFolders(@NotNull List<String> folders) {
    this.folders = clean(folders);
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
}
