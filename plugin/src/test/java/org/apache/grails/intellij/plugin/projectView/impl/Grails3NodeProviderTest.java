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
import com.intellij.ide.projectView.impl.nodes.PsiFileSystemItemFilter;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.util.PlatformIcons;
import org.apache.grails.intellij.plugin.GroovyMvcIcons;
import org.apache.grails.intellij.plugin.projectView.NodeWeights;
import org.apache.grails.intellij.plugin.projectView.nodes.GrailsPsiDirectoryNode;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class Grails3NodeProviderTest extends GrailsNodeProviderTestSupport {

  public void testSeparatesKebabCaseTestSourceRootsFromSrcNode() {
    myFixture.addFileToProject("src/test/groovy/SomeServiceSpec.groovy", "class SomeServiceSpec {}");
    myFixture.addFileToProject("src/integration-test/groovy/SomeServiceIT.groovy", "class SomeServiceIT {}");
    myFixture.addFileToProject("src/functional-test/groovy/SomeFunctionalFT.groovy", "class SomeFunctionalFT {}");
    myFixture.addFileToProject("src/main/groovy/SomeService.groovy", "class SomeService {}");

    Collection<AbstractTreeNode<?>> nodes =
      new Grails3NodeProvider().createNodes(testApplication(false), ViewSettings.DEFAULT);

    GrailsPsiDirectoryNode srcNode = findNode(nodes, "src");
    assertNotNull("src node must be present", srcNode);
    assertEquals(NodeWeights.SRC_FOLDERS, srcNode.getNodeWeight());
    assertNotNull("src node must have a filter that hides test roots", srcNode.getFilter());

    PsiDirectory srcDir = srcNode.getValue();
    PsiDirectory mainDir = srcDir.findSubdirectory("main");
    PsiDirectory testDir = srcDir.findSubdirectory("test");
    PsiDirectory integrationTestDir = srcDir.findSubdirectory("integration-test");
    PsiDirectory functionalTestDir = srcDir.findSubdirectory("functional-test");
    assertNotNull(mainDir);
    assertNotNull(testDir);
    assertNotNull(integrationTestDir);
    assertNotNull(functionalTestDir);

    assertTrue("plain sources keep showing under src", srcNode.getFilter().shouldShow(mainDir));
    assertFalse("unit test root must not duplicate under src", srcNode.getFilter().shouldShow(testDir));
    assertFalse("integration test root must not duplicate under src", srcNode.getFilter().shouldShow(integrationTestDir));
    assertFalse("functional test root must not duplicate under src", srcNode.getFilter().shouldShow(functionalTestDir));

    assertFalse("src must not claim a file under a lifted test root, or reveal dead-ends",
                srcNode.contains(myFixture.findFileInTempDir("src/test/groovy/SomeServiceSpec.groovy")));
    assertFalse("same for the integration test root",
                srcNode.contains(myFixture.findFileInTempDir("src/integration-test/groovy/SomeServiceIT.groovy")));
    assertTrue("src must still claim its own sources",
               srcNode.contains(myFixture.findFileInTempDir("src/main/groovy/SomeService.groovy")));

    Set<String> nodeNames = nodes.stream()
      .filter(GrailsPsiDirectoryNode.class::isInstance)
      .map(GrailsPsiDirectoryNode.class::cast)
      .map(node -> node.getValue().getName())
      .collect(Collectors.toSet());
    assertTrue("top-level nodes must contain src, got " + nodeNames, nodeNames.contains("src"));
    assertTrue("top-level nodes must contain the unit test root, got " + nodeNames, nodeNames.contains("test"));
    assertTrue("top-level nodes must contain the integration test root, got " + nodeNames,
               nodeNames.contains("integration-test"));
    assertTrue("top-level nodes must contain the functional test root, got " + nodeNames,
               nodeNames.contains("functional-test"));

    GrailsPsiDirectoryNode testNode = findNode(nodes, "test");
    assertNotNull("unit test root node must be present", testNode);
    assertEquals(NodeWeights.TESTS_FOLDER, testNode.getNodeWeight());
    assertSame("unit test root uses the platform test icon", PlatformIcons.TEST_SOURCE_FOLDER, testNode.getNodeIcon());
    assertTitleAndLocation(testNode, "Tests:unit", "src/test");

    GrailsPsiDirectoryNode integrationTestNode = findNode(nodes, "integration-test");
    assertNotNull("integration test root node must be present", integrationTestNode);
    assertEquals(NodeWeights.TESTS_FOLDER, integrationTestNode.getNodeWeight());
    assertSame("specialised test roots use the Grails test icon", GroovyMvcIcons.Grails_test,
               integrationTestNode.getNodeIcon());
    assertTitleAndLocation(integrationTestNode, "Tests:integration", "src/integration-test");

    GrailsPsiDirectoryNode functionalTestNode = findNode(nodes, "functional-test");
    assertNotNull("functional test root node must be present", functionalTestNode);
    assertEquals(NodeWeights.TESTS_FOLDER, functionalTestNode.getNodeWeight());
    assertSame("specialised test roots use the Grails test icon", GroovyMvcIcons.Grails_test,
               functionalTestNode.getNodeIcon());
    assertTitleAndLocation(functionalTestNode, "Tests:functional", "src/functional-test");

    assertNull("the production root must never be lifted to a top-level node", findNode(nodes, "main"));
  }

  /** A custom testPhases entry is lifted, and titled from its directory name. */
  public void testLiftsCustomTestPhaseAndTitlesItFromItsDirectoryName() {
    myFixture.addFileToProject("src/main/groovy/SomeService.groovy", "class SomeService {}");
    myFixture.addFileToProject("src/test/groovy/SomeServiceSpec.groovy", "class SomeServiceSpec {}");
    myFixture.addFileToProject("src/smoke-test/groovy/SomeSmokeIT.groovy", "class SomeSmokeIT {}");
    myFixture.addFileToProject("src/integration-test-cli/groovy/SomeCliIT.groovy", "class SomeCliIT {}");

    Collection<AbstractTreeNode<?>> nodes =
      new Grails3NodeProvider().createNodes(testApplication(false), ViewSettings.DEFAULT);

    GrailsPsiDirectoryNode smokeTest = findNode(nodes, "smoke-test");
    assertNotNull("a custom test phase must be lifted out of src", smokeTest);
    assertEquals("every lifted test root keeps the tests weight", NodeWeights.TESTS_FOLDER, smokeTest.getNodeWeight());
    assertSame("a custom phase is not the unit test root, so it gets the Grails test icon",
               GroovyMvcIcons.Grails_test, smokeTest.getNodeIcon());
    assertTitleAndLocation(smokeTest, "Tests:smoke-test", "src/smoke-test");

    GrailsPsiDirectoryNode cliTest = findNode(nodes, "integration-test-cli");
    assertNotNull("a custom test phase must be lifted out of src", cliTest);
    assertTitleAndLocation(cliTest, "Tests:integration-test-cli", "src/integration-test-cli");

    GrailsPsiDirectoryNode srcNode = findNode(nodes, "src");
    assertNotNull("src node must be present", srcNode);
    assertFalse("a lifted custom phase must not duplicate under src",
                srcNode.getFilter().shouldShow(srcNode.getValue().findSubdirectory("smoke-test")));
  }

  /**
   * Discovery lifts any direct child of src that holds a conventional code source directory, so a
   * folder that holds none of them — whatever it is called — stays inside the src node.
   */
  public void testSrcChildWithoutACodeSourceDirectoryStaysUnderSrc() {
    myFixture.addFileToProject("src/main/groovy/SomeService.groovy", "class SomeService {}");
    myFixture.addFileToProject("src/docs/index.md", "# docs");
    myFixture.addFileToProject("src/assets/logo.txt", "fake-logo");

    Collection<AbstractTreeNode<?>> nodes =
      new Grails3NodeProvider().createNodes(testApplication(false), ViewSettings.DEFAULT);

    GrailsPsiDirectoryNode srcNode = findNode(nodes, "src");
    assertNotNull("src node must be present", srcNode);

    PsiDirectory srcDir = srcNode.getValue();
    PsiDirectory mainDir = srcDir.findSubdirectory("main");
    PsiDirectory docsDir = srcDir.findSubdirectory("docs");
    PsiDirectory assetsDir = srcDir.findSubdirectory("assets");
    assertNotNull(mainDir);
    assertNotNull(docsDir);
    assertNotNull(assetsDir);

    assertTrue("plain sources keep showing under src", srcNode.getFilter().shouldShow(mainDir));
    assertTrue("a src child holding no code source directory stays visible under src",
               srcNode.getFilter().shouldShow(docsDir));
    assertTrue("a src child holding no code source directory stays visible under src",
               srcNode.getFilter().shouldShow(assetsDir));
    assertTrue("src keeps claiming files of a folder it does not lift",
               srcNode.contains(myFixture.findFileInTempDir("src/docs/index.md")));

    assertNull("a src child holding no code source directory must not be lifted to a top-level node",
               findNode(nodes, "docs"));
    assertNull("a src child holding no code source directory must not be lifted to a top-level node",
               findNode(nodes, "assets"));
  }

  public void testTestRootThePlatformCannotResolveGetsNoNode() throws IOException {
    // PsiManager refuses an excluded directory only while excluded files are hidden, which is off
    // in tests; without it every excluded directory still resolves to a PsiDirectory.
    Registry.get("ide.hide.excluded.files").setValue(true, getTestRootDisposable());
    myFixture.addFileToProject("src/main/groovy/SomeService.groovy", "class SomeService {}");
    // created through the VFS rather than addFileToProject so no PsiDirectory is cached for it
    myFixture.getTempDirFixture().findOrCreateDir("src/test");
    VirtualFile testDir = myFixture.findFileInTempDir("src/test");
    assertNotNull("src/test must exist", testDir);
    ModuleRootModificationUtil.updateModel(getModule(), model -> {
      for (ContentEntry entry : model.getContentEntries()) {
        if (VfsUtilCore.isAncestor(entry.getFile(), testDir, false)) entry.addExcludeFolder(testDir.getUrl());
      }
    });

    Collection<AbstractTreeNode<?>> nodes =
      new Grails3NodeProvider().createNodes(testApplication(false), ViewSettings.DEFAULT);

    assertNull("an excluded directory resolves to no PsiDirectory, so it cannot get a node",
               PsiManager.getInstance(getProject()).findDirectory(testDir));
    assertNotNull("src node must be present", findNode(nodes, "src"));
    assertNull("a directory the platform cannot resolve must not be lifted to a top-level node",
               findNode(nodes, "test"));
  }

  /**
   * The src filter asks GrailsViewItems.isUnder whether the item sits below a lifted directory, and
   * the platform permits an item whose virtual file is missing, so that branch has to hold.
   */
  public void testSrcFilterShowsAnItemWithoutAVirtualFile() {
    myFixture.addFileToProject("src/test/groovy/SomeServiceSpec.groovy", "class SomeServiceSpec {}");

    Collection<AbstractTreeNode<?>> nodes =
      new Grails3NodeProvider().createNodes(testApplication(false), ViewSettings.DEFAULT);

    GrailsPsiDirectoryNode srcNode = findNode(nodes, "src");
    assertNotNull("src node must be present", srcNode);
    PsiFileSystemItemFilter filter = srcNode.getFilter();
    assertNotNull("src node must have a filter", filter);

    PsiDirectory detached = (PsiDirectory)Proxy.newProxyInstance(
      PsiDirectory.class.getClassLoader(), new Class<?>[]{PsiDirectory.class}, (proxy, method, args) -> {
        if ("getVirtualFile".equals(method.getName())) return null;
        throw new UnsupportedOperationException(method.getName());
      });

    assertTrue("an item that is under no lifted directory must stay visible under src",
               filter.shouldShow(detached));
  }

  }
