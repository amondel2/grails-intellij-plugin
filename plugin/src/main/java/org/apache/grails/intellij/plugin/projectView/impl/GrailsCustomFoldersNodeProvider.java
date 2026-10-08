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

package org.apache.grails.intellij.plugin.projectView.impl;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.apache.grails.intellij.plugin.config.GrailsCustomFoldersProjectService;
import org.apache.grails.intellij.plugin.projectView.NodeWeights;
import org.apache.grails.intellij.plugin.projectView.api.GrailsViewNodeProvider;
import org.apache.grails.intellij.plugin.projectView.nodes.GrailsCustomFolderNode;
import org.apache.grails.intellij.plugin.structure.GrailsApplication;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class GrailsCustomFoldersNodeProvider implements GrailsViewNodeProvider {

  @Override
  public @NotNull Collection<AbstractTreeNode<?>> createNodes(@NotNull GrailsApplication application,
                                                              @NotNull ViewSettings settings) {
    Project project = application.getProject();
    List<String> folders = project.getService(GrailsCustomFoldersProjectService.class).getEffectiveFolders();

    List<String> sorted = new ArrayList<>(folders);
    sorted.sort(Comparator.comparing(GrailsCustomFoldersNodeProvider::folderName));

    Collection<AbstractTreeNode<?>> result = new ArrayList<>();
    for (String configuredPath : sorted) {
      VirtualFile file = resolve(application, configuredPath);
      result.add(new GrailsCustomFolderNode(project, file, settings, NodeWeights.CUSTOM_FOLDER, configuredPath));
    }
    return result;
  }

  private static @Nullable VirtualFile resolve(@NotNull GrailsApplication application, @NotNull String path) {
    String normalized = stripTrailingSeparators(path);
    if (isAbsolute(normalized)) {
      return LocalFileSystem.getInstance().findFileByPath(normalized);
    }
    return application.getRoot().findFileByRelativePath(normalized);
  }

  private static boolean isAbsolute(@NotNull String path) {
    return path.startsWith("/") || path.matches("^[A-Za-z]:.*");
  }

  private static @NotNull String folderName(@NotNull String path) {
    String normalized = stripTrailingSeparators(path);
    int separator = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
    return separator >= 0 ? normalized.substring(separator + 1) : normalized;
  }

  private static @NotNull String stripTrailingSeparators(@NotNull String path) {
    String normalized = path;
    while (normalized.length() > 1 && (normalized.endsWith("/") || normalized.endsWith("\\"))) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }
}
