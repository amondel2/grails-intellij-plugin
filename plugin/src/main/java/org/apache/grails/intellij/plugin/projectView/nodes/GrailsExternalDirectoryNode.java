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

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A directory below a custom folder that lies outside the project. The platform's directory node
 * would list no children for it, so this one lists the directory's own subdirectories and files,
 * leaving out only what the IDE ignores everywhere ({@code .git}, {@code .DS_Store}, ...).
 */
public final class GrailsExternalDirectoryNode extends PsiDirectoryNode {

  public GrailsExternalDirectoryNode(@NotNull Project project, @NotNull PsiDirectory directory, @NotNull ViewSettings settings) {
    super(project, directory, settings);
  }

  @Override
  public Collection<AbstractTreeNode<?>> getChildrenImpl() {
    PsiDirectory directory = getValue();
    return directory == null ? List.of() : childrenOf(directory, getSettings());
  }

  /** The subdirectories, then the files, of a directory the project does not cover. */
  static @NotNull List<AbstractTreeNode<?>> childrenOf(@NotNull PsiDirectory directory, @NotNull ViewSettings settings) {
    Project project = directory.getProject();
    FileTypeRegistry registry = FileTypeRegistry.getInstance();
    List<AbstractTreeNode<?>> children = new ArrayList<>();
    for (PsiDirectory subdirectory : directory.getSubdirectories()) {
      if (!registry.isFileIgnored(subdirectory.getVirtualFile())) {
        children.add(new GrailsExternalDirectoryNode(project, subdirectory, settings));
      }
    }
    for (PsiFile file : directory.getFiles()) {
      if (!registry.isFileIgnored(file.getVirtualFile())) {
        children.add(new PsiFileNode(project, file, settings));
      }
    }
    return children;
  }
}
