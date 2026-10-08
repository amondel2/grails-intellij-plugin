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

package org.apache.grails.intellij.plugin.projectView;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PlatformTestUtil;
import org.apache.grails.intellij.lib.testFramework.GrailsTestCase;
import org.apache.grails.intellij.plugin.config.GrailsConstants;
import org.apache.grails.intellij.plugin.config.GrailsCustomFoldersProjectService;
import org.apache.grails.intellij.plugin.projectView.api.GrailsViewNodeProvider;
import org.apache.grails.intellij.plugin.projectView.impl.GrailsCustomFoldersNodeProvider;
import org.apache.grails.intellij.plugin.projectView.nodes.GrailsApplicationNode;
import org.apache.grails.intellij.plugin.projectView.nodes.GrailsCustomFolderNode;
import org.apache.grails.intellij.plugin.projectView.nodes.GrailsRootNode;
import org.apache.grails.intellij.plugin.structure.GrailsApplication;
import org.apache.grails.intellij.plugin.structure.GrailsApplicationManager;
import org.apache.grails.intellij.plugin.util.version.Version;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The Grails pane builds its tree by calling {@code getChildren()} down the node chain on a
 * background thread, so any exception a node throws aborts the whole repaint and blanks the view.
 * Applying a custom-folder setting repaints the pane, which is how a folder that does not exist came
 * to be reported as "the plugin fails to repaint". These tests walk the real node chain.
 */
public class GrailsCustomFoldersPaneTest extends GrailsTestCase {

  /**
   * A configured folder that does not exist on disk must still produce a node, and building the tree
   * around it must not throw.
   */
  public void testTreeBuildsWithNonExistentFolder() {
    addGrailsApp();
    List<String> paths = buildTreeAndCollectCustomFolderPaths(List.of("no-such-directory"));

    assertTrue("a non-existent folder should still get its node",
                paths.contains("no-such-directory"));
  }

  /** An absolute path resolves through LocalFileSystem, which returns no VirtualFile at all. */
  public void testTreeBuildsWithNonExistentAbsoluteFolder() {
    addGrailsApp();
    List<String> paths = buildTreeAndCollectCustomFolderPaths(List.of("/no-such-home/aarone"));

    assertTrue("a non-existent absolute folder should still get its node",
                paths.contains("/no-such-home/aarone"));
  }

  /** Neither an unresolvable path nor a missing parent may suppress the node. */
  public void testTreeBuildsWhenFolderParentIsMissing() {
    addGrailsApp();
    List<String> paths = buildTreeAndCollectCustomFolderPaths(List.of("missing-parent/missing-child"));

    assertTrue("a folder with a missing parent should still get its placeholder node",
                paths.contains("missing-parent/missing-child"));
  }

  /**
   * An existing folder and a missing one both survive, and both appear: an unresolvable path must
   * not take its siblings down with it.
   */
  public void testExistingAndNonExistentFoldersBothBuild() {
    addGrailsApp();
    myFixture.addFileToProject("docs/readme.md", "# readme");

    List<String> paths = buildTreeAndCollectCustomFolderPaths(List.of("docs", "no-such-directory"));

    assertTrue("the existing folder should be rendered", paths.contains("docs"));
    assertTrue("the non-existent folder should be rendered alongside it", paths.contains("no-such-directory"));
  }

  /**
   * A missing {@code grails-app} made {@code GrailsApplicationBase.getAppRoot()} throw while the
   * pane built children, and that exception aborted the whole repaint. The providers must tolerate
   * it, so a custom folder node is still produced.
   *
   * <p>Driven through a stub rather than the fixture, because the fixture's application is filtered
   * out of the tree once grails-app is gone, which would hide the very provider call under test.
   */
  public void testProvidersTolerateMissingGrailsAppDirectory() {
    addGrailsApp();
    getProject().getService(GrailsCustomFoldersProjectService.class).setFolders(List.of("no-such-directory"));

    GrailsApplication application = new MissingAppRootApplication(getProject());
    // Every registered provider must survive, because one throwing provider aborts the pane repaint.
    for (GrailsViewNodeProvider provider : GrailsViewNodeProvider.EP_NAME.getExtensionList()) {
      provider.createNodes(application, ViewSettings.DEFAULT);
    }

    List<String> paths = collectCustomFolderPaths(
      new GrailsCustomFoldersNodeProvider().createNodes(application, ViewSettings.DEFAULT));

    assertTrue("a missing grails-app must not prevent custom folder nodes",
                paths.contains("no-such-directory"));
  }

  /**
   * A provider that fails with a plain exception must not cost the other providers their nodes.
   *
   * <p>Registered as an extension so it runs inside {@code GrailsApplicationNode.getChildren()},
   * which is where the isolation lives.
   */
  public void testThrowingProviderDoesNotSuppressSiblingNodes() {
    registerProvider(new ThrowingNodeProvider(new IllegalStateException("provider blew up")));
    assertCustomFolderNodeSurvives("no-such-directory");
  }

  /**
   * The platform cancels a background read by throwing a control-flow exception, and retries the
   * build afterwards. Swallowing that would leave the pane on "Loading..." forever, so it must be
   * rethrown rather than treated as a provider failure.
   */
  public void testControlFlowCancellationIsRethrown() {
    registerProvider(new ThrowingNodeProvider(new ProcessCanceledException()));
    GrailsApplicationNode node = new GrailsApplicationNode(singleApplication(), ViewSettings.DEFAULT);
    try {
      node.getChildren();
      fail("a cancelled background read must be rethrown, not swallowed");
    }
    catch (ProcessCanceledException expected) {
      // Rethrown untouched, so the platform can retry the build. Logging it instead throws a
      // different exception out of the logger, which still strands the pane on "Loading...".
    }
  }

  /** Applies one folder and asserts its node is still built through the application's children. */
  private void assertCustomFolderNodeSurvives(@NotNull String folder) {
    addGrailsApp();
    createApplicationProperties();
    PlatformTestUtil.waitForFuture(GrailsApplicationManager.getInstance(getProject()).queueUpdate(), 5_000);
    getProject().getService(GrailsCustomFoldersProjectService.class).setFolders(List.of(folder));

    GrailsApplicationNode node = new GrailsApplicationNode(singleApplication(), ViewSettings.DEFAULT);

    assertTrue("a failing provider must not remove the custom folder node",
               collectCustomFolderPaths(node.getChildren()).contains(folder));
  }

  private void registerProvider(@NotNull GrailsViewNodeProvider provider) {
    GrailsViewNodeProvider.EP_NAME.getPoint().registerExtension(provider, getTestRootDisposable());
  }

  /** A provider that always throws, to exercise the isolation in the application node. */
  private static final class ThrowingNodeProvider implements GrailsViewNodeProvider {

    private final @NotNull RuntimeException myFailure;

    private ThrowingNodeProvider(@NotNull RuntimeException failure) {
      myFailure = failure;
    }

    @Override
    public @NotNull Collection<AbstractTreeNode<?>> createNodes(@NotNull GrailsApplication application,
                                                                @NotNull ViewSettings settings) {
      throw myFailure;
    }
  }

  /**
   * Stands in for an application whose {@code grails-app} is gone. {@code getAppRoot()} throws the
   * same NPE {@code GrailsApplicationBase} throws in that state, without needing the fixture's
   * application to be invalidated (which would remove it from the tree before the providers run).
   */
  private static final class MissingAppRootApplication extends UserDataHolderBase implements GrailsApplication {

    private final @NotNull Project myProject;
    private final @NotNull VirtualFile myRoot;

    private MissingAppRootApplication(@NotNull Project project) {
      myProject = project;
      myRoot = project.getBaseDir();
    }

    @Override
    public @NotNull VirtualFile getRoot() {
      return myRoot;
    }

    @Override
    public @NotNull VirtualFile getAppRoot() {
      throw new NullPointerException("grails-app is missing, as GrailsApplicationBase.getAppRoot() does");
    }

    @Override
    public @NotNull Project getProject() {
      return myProject;
    }

    @Override
    public @NotNull String getName() {
      return "noAppRoot";
    }

    @Override
    public @NotNull Icon getIcon() {
      return AllIcons.Nodes.Package;
    }

    @Override
    public @NotNull Version getGrailsVersion() {
      return Version.GRAILS_6_0;
    }

    @Override
    public boolean isValid() {
      return true;
    }

    @Override
    public void invalidate() {
    }

    @Override
    public @NotNull GlobalSearchScope getScope(boolean includeDependencies, boolean testsOnly) {
      return GlobalSearchScope.allScope(myProject);
    }
  }

  /**
   * Builds the node chain the pane builds: root, then each application's children, which is where
   * every node provider runs.
   */
  private @NotNull List<String> buildTreeAndCollectCustomFolderPaths(@NotNull List<String> folders) {
    createApplicationProperties();
    PlatformTestUtil.waitForFuture(GrailsApplicationManager.getInstance(getProject()).queueUpdate(), 5_000);
    singleApplication();
    getProject().getService(GrailsCustomFoldersProjectService.class).setFolders(folders);

    Collection<AbstractTreeNode<?>> applicationNodes = buildApplicationNodes();
    assertTrue("the fixture must yield at least one application node, so the tree really was built",
               !applicationNodes.isEmpty());
    return collectCustomFolderPaths(applicationNodes);
  }

  /** The application's child nodes, as the pane builds them: every node provider runs here. */
  private @NotNull Collection<AbstractTreeNode<?>> buildApplicationNodes() {
    GrailsRootNode root = new GrailsRootNode(getProject(), ViewSettings.DEFAULT);
    Collection<AbstractTreeNode<?>> nodes = new ArrayList<>();
    for (AbstractTreeNode<?> applicationNode : root.getChildren()) {
      if (applicationNode instanceof GrailsApplicationNode) {
        nodes.addAll(applicationNode.getChildren());
      }
    }
    return nodes;
  }

  private static @NotNull List<String> collectCustomFolderPaths(@NotNull Collection<AbstractTreeNode<?>> nodes) {
    List<String> paths = new ArrayList<>();
    for (AbstractTreeNode<?> node : nodes) {
      if (node instanceof GrailsCustomFolderNode folder) {
        paths.add(folder.getConfiguredPath());
      }
    }
    return paths;
  }

  private @NotNull GrailsApplication singleApplication() {
    Collection<GrailsApplication> applications = GrailsApplicationManager.getInstance(getProject()).getApplications();
    assertTrue("fixture must register a Grails application", !applications.isEmpty());
    return applications.iterator().next();
  }

  private void addGrailsApp() {
    myFixture.addFileToProject("grails-app/i18n/messages.properties", "greeting=hello");
  }

  

  /**
   * {@code TestGrailsApplicationProvider} only reports an application that has
   * application.properties. It may already exist (created by the fixture), so write through the VFS
   * instead of the fixture, which refuses to overwrite.
   */
  private void createApplicationProperties() {
    WriteAction.runAndWait(() -> {
      try {
        VirtualFile properties = myFixture.getTempDirFixture().findOrCreateDir("application.properties");
        properties.setBinaryContent("app.grails.version=7.0.0\napp.name=TestApp\n".getBytes(StandardCharsets.UTF_8));
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }
}