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
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.apache.grails.intellij.plugin.projectView.nodes.GrailsPsiDirectoryNode;
import org.apache.grails.intellij.plugin.structure.GrailsApplication;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.util.Collection;

/**
 * The layout Gradle import produces, which the light fixture cannot: the root module owns the project
 * directory, and every source set is a module of its own whose content root is its phase directory. The
 * module that owns {@code src} therefore registers no test roots, which is why asking only that module
 * found no custom phase on an imported project.
 */
public class Grails3NodeProviderGradleLayoutTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder<?> moduleBuilder) {
    // the heavy fixture ships no mock JDK since 2026.2; see AGENTS.md
    moduleBuilder.addJdk(System.getProperty("java.home"));
  }

  public void testPhasesOwnedBySourceSetModulesAreLifted() {
    myFixture.addFileToProject("src/main/groovy/SomeService.groovy", "class SomeService {}");
    myFixture.addFileToProject("src/smokeTest/groovy/SomeSmokeSpec.groovy", "class SomeSmokeSpec {}");
    myFixture.addFileToProject("src/freeze-test/groovy/FreezeSpec.groovy", "class FreezeSpec {}");
    myFixture.addFileToProject("src/generated/java/GeneratedClient.java", "class GeneratedClient {}");
    sourceSetModule("app.smokeTest", "src/smokeTest", "src/smokeTest/groovy", true);
    // Gradle import registers a Grails testPhases source set as production sources, not tests
    sourceSetModule("app.freezeTest", "src/freeze-test", "src/freeze-test/groovy", false);
    sourceSetModule("app.generated", "src/generated", "src/generated/java", false);

    VirtualFile src = myFixture.findFileInTempDir("src");
    assertNotNull(src);
    assertSame("src must belong to the root module, as after Gradle import",
               getModule(), ProjectFileIndex.getInstance(getProject()).getModuleForFile(src));
    assertEmpty("the root module must register no test roots, as after Gradle import",
                ModuleRootManager.getInstance(getModule()).getSourceRoots(JavaModuleSourceRootTypes.TESTS));

    VirtualFile root = myFixture.getTempDirFixture().getFile("");
    assertNotNull(root);
    GrailsApplication application = new GrailsNodeProviderTestSupport.TestGrailsApplication(root, getProject(), false);
    Collection<AbstractTreeNode<?>> nodes = new Grails3NodeProvider().createNodes(application, ViewSettings.DEFAULT);

    GrailsNodeProviderTestSupport.assertTitleAndLocation(GrailsNodeProviderTestSupport.findNode(nodes, "smokeTest"),
                                                         "Tests:smoke", "src/smokeTest");
    GrailsNodeProviderTestSupport.assertTitleAndLocation(GrailsNodeProviderTestSupport.findNode(nodes, "freeze-test"),
                                                         "Tests:freeze", "src/freeze-test");
    assertNull("a production source set under src is not a phase", GrailsNodeProviderTestSupport.findNode(nodes, "generated"));

    GrailsPsiDirectoryNode srcNode = GrailsNodeProviderTestSupport.findNode(nodes, "src");
    assertNotNull("src node must be present", srcNode);
    assertFalse("a lifted phase must not duplicate under src",
                srcNode.getFilter().shouldShow(srcNode.getValue().findSubdirectory("smokeTest")));
    assertFalse("a lifted phase must not duplicate under src",
                srcNode.getFilter().shouldShow(srcNode.getValue().findSubdirectory("freeze-test")));
    assertTrue("a production source set stays visible under src",
               srcNode.getFilter().shouldShow(srcNode.getValue().findSubdirectory("generated")));
  }

  /** A module of its own for a source set, the way Gradle import creates one: content root at the phase directory. */
  private void sourceSetModule(@NotNull String name, @NotNull String contentRoot, @NotNull String sourceRoot, boolean tests) {
    VirtualFile content = myFixture.findFileInTempDir(contentRoot);
    VirtualFile source = myFixture.findFileInTempDir(sourceRoot);
    assertNotNull(contentRoot, content);
    assertNotNull(sourceRoot, source);
    Module module = PsiTestUtil.addModule(getProject(), JavaModuleType.getModuleType(), name, content);
    PsiTestUtil.addSourceRoot(module, source, tests);
  }
}
