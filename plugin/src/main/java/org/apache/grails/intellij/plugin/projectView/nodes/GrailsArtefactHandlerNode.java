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
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.apache.grails.intellij.plugin.artefact.api.GrailsDisplayableArtefactHandler;

import java.util.Collection;
import java.util.Objects;

public class GrailsArtefactHandlerNode extends GrailsArtefactHandlerNodeBase<GrailsDisplayableArtefactHandler> {

  public GrailsArtefactHandlerNode(Project project,
                                   @NotNull GrailsDisplayableArtefactHandler artefactHandler,
                                   @NotNull ViewSettings viewSettings) {
    super(project, viewSettings, artefactHandler);
  }

  @Override
  protected @NotNull GrailsDisplayableArtefactHandler getArtefactHandler() {
    return getValue();
  }

  /**
   * The group's contents come from index searches over the application. With the default leaf state the
   * platform counts a node's children as soon as its parent is listed, to decide whether to draw the expand
   * arrow, so opening the application node ran every group's search before anything was shown. Always
   * drawing the arrow defers each search until the group itself is expanded, which is how the Project view
   * treats directories; a group that turns out empty loses its arrow once opened.
   */
  @Override
  public boolean isAlwaysShowPlus() {
    return true;
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    presentation.setIcon(getValue().getGroupIcon());
    presentation.setPresentableText(getValue().getTitle());
  }

  @Override
  public @NotNull Collection<AbstractTreeNode<?>> getChildren() {
    return GrailsNodes.getArtefactNodes(Objects.requireNonNull(getProject()), getSettings(), getValue(), getArtefacts());
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return file.isDirectory() || TreeNodeUtil.mayContain(getGrailsApplication(), file);
  }
}
