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

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructureBase;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.tree.LeafState;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.util.concurrency.Invoker;
import com.intellij.util.ui.tree.TreeUtil;
import org.apache.grails.intellij.plugin.artefact.api.GrailsArtefactHandler;
import org.apache.grails.intellij.plugin.artefact.api.GrailsDisplayableArtefactHandler;
import org.apache.grails.intellij.plugin.artefact.impl.ControllerArtefactHandler;
import org.apache.grails.intellij.plugin.projectView.GrailsPluginsNode;
import org.apache.grails.intellij.plugin.projectView.api.GrailsViewNodeProvider;
import org.apache.grails.intellij.plugin.projectView.impl.GrailsNodeProviderTestSupport;
import org.apache.grails.intellij.plugin.structure.GrailsApplication;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.tree.TreeNode;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * The Grails pane builds children on the tree's single background thread. For a node in the default leaf
 * state the platform decides whether to draw the expand arrow by building that node's children as soon as
 * its parent is listed, so every artefact group's index search and the plugin search used to run before
 * the application's children appeared, and the pane sat on "Loading...". These tests pin the nodes whose
 * children are expensive to the state that defers that work until the node itself is expanded.
 */
public class GrailsNodeLeafStateTest extends GrailsNodeProviderTestSupport {

  private static final ExtensionPointName<GrailsArtefactHandler> ARTEFACT_HANDLER_EP =
    ExtensionPointName.create("org.intellij.grails.artefactHandler");

  public void testNodesWithExpensiveChildrenAlwaysShowTheExpandArrow() {
    myFixture.addFileToProject("grails-app/controllers/demo/BookController.groovy", "package demo\nclass BookController {}");
    GrailsApplication application = testApplication(true);

    assertEquals(LeafState.NEVER, new GrailsApplicationNode(application, ViewSettings.DEFAULT).getLeafState());
    assertEquals(LeafState.NEVER,
                 new GrailsArtefactHandlerNode(getProject(), ControllerArtefactHandler.INSTANCE, ViewSettings.DEFAULT).getLeafState());
    assertEquals(LeafState.NEVER, new GrailsPluginsNode(getProject(), ViewSettings.DEFAULT).getLeafState());
  }

  /**
   * What the tree model makes of those states. Listing a node's children asks each child whether it is a
   * leaf, and for the default state the model answers by building the child's own children. The counters
   * show that neither the application's providers nor an artefact group's search run until the node in
   * question is expanded, and that expanding the group still finds the controller.
   */
  public void testChildrenAreBuiltOnlyWhenTheNodeIsExpanded() {
    myFixture.addFileToProject("grails-app/controllers/demo/BookController.groovy", "package demo\nclass BookController {}");
    CountingHandler handler = new CountingHandler();
    ARTEFACT_HANDLER_EP.getPoint().registerExtension(handler, getTestRootDisposable());
    CountingProvider provider = new CountingProvider();
    GrailsViewNodeProvider.EP_NAME.getPoint().registerExtension(provider, getTestRootDisposable());

    GrailsApplicationNode applicationNode = new GrailsApplicationNode(testApplication(true), ViewSettings.DEFAULT);
    StructureTreeModel<SingleChildStructure> model =
      new StructureTreeModel<>(new SingleChildStructure(getProject(), applicationNode), null,
                               Invoker.forEventDispatchThread(getTestRootDisposable()), getTestRootDisposable());

    TreeNode root = model.getRoot();
    assertNotNull(root);
    TreeNode applicationTreeNode = assertOneElement(model.getChildren(root));
    assertFalse("the application keeps its arrow without building its children", model.isLeaf(applicationTreeNode));
    assertEquals("listing the root must not run the application's providers", 0, provider.calls);

    List<TreeNode> children = model.getChildren(applicationTreeNode);
    assertEquals("expanding the application runs its providers once", 1, provider.calls);
    TreeNode group = null;
    for (TreeNode child : children) {
      // the async tree model asks this for every child it lists, to decide on the arrow
      boolean leaf = model.isLeaf(child);
      if (TreeUtil.getUserObject(child) instanceof GrailsArtefactHandlerNode node && node.getValue() == handler) {
        group = child;
        assertFalse("the group keeps its arrow without being searched", leaf);
      }
    }
    assertNotNull("the registered handler gets a group node", group);
    assertEquals("listing the application's children must not search any artefact group", 0, handler.directoryLookups);

    assertEquals("expanding the group searches it and finds the controller's package", 1, model.getChildCount(group));
    assertTrue("the search ran for the expansion", handler.directoryLookups > 0);
  }

  /** A structure whose root lists one child, the application node, the way the pane's root lists applications. */
  private static final class SingleChildStructure extends AbstractTreeStructureBase {

    private final AbstractTreeNode<?> myRoot;

    private SingleChildStructure(@NotNull Project project, @NotNull AbstractTreeNode<?> child) {
      super(project);
      myRoot = new AbstractTreeNode<String>(project, "root") {
        @Override
        public @NotNull Collection<? extends AbstractTreeNode<?>> getChildren() {
          // not List.of: the structure base asks the collection whether it contains null, which List.of rejects
          return Collections.singletonList(child);
        }

        @Override
        protected void update(@NotNull PresentationData presentation) {
        }
      };
    }

    @Override
    public @NotNull Object getRootElement() {
      return myRoot;
    }

    @Override
    public @Nullable List<TreeStructureProvider> getProviders() {
      return List.of();
    }

    @Override
    public void commit() {
    }

    @Override
    public boolean hasSomethingToCommit() {
      return false;
    }
  }

  /** An artefact group whose directory lookup, the first step of its search, is counted. */
  private static final class CountingHandler implements GrailsDisplayableArtefactHandler {

    int directoryLookups;

    @Override
    public @NotNull String getArtefactHandlerID() {
      return "Probe";
    }

    @Override
    public @NotNull String getArtefactClassSuffix() {
      return "Controller";
    }

    @Override
    public @Nullable VirtualFile getDirectory(@NotNull GrailsApplication application) {
      directoryLookups++;
      return application.getAppRoot().findChild("controllers");
    }

    @Override
    public @NotNull Icon getIcon() {
      return AllIcons.Nodes.Controller;
    }

    @Override
    public @NotNull String getTitle() {
      return "Probes";
    }
  }

  /** A provider that contributes nothing but counts how often the application asks. */
  private static final class CountingProvider implements GrailsViewNodeProvider {

    int calls;

    @Override
    public @NotNull Collection<AbstractTreeNode<?>> createNodes(@NotNull GrailsApplication application,
                                                                @NotNull ViewSettings settings) {
      calls++;
      return List.of();
    }
  }
}
