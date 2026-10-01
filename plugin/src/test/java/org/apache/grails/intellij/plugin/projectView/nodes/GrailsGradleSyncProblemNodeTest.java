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
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.ContainerUtil;
import org.apache.grails.intellij.lib.testFramework.GrailsTestCase;
import org.apache.grails.intellij.plugin.GrailsBundle;
import org.apache.grails.intellij.plugin.config.GrailsConstants;
import org.apache.grails.intellij.plugin.gradle.GrailsGradleSyncStatus;
import org.apache.grails.intellij.plugin.projectView.impl.GrailsNodeComparator;

public class GrailsGradleSyncProblemNodeTest extends GrailsTestCase {

  public void testRootNodeShowsABlockedGrailsAppAsAWarningThatOffersTheReload() {
    VirtualFile conf = myFixture.addFileToProject("app/grails-app/conf/application.yml", "grails: {}").getVirtualFile();
    VirtualFile appRoot = conf.getParent().getParent().getParent();
    GrailsGradleSyncStatus.getInstance(getProject()).recordFailure(appRoot.getPath(), "Could not resolve org.apache.grails:grails-core");

    GrailsRootNode root = new GrailsRootNode(getProject(), ViewSettings.DEFAULT);
    GrailsGradleSyncProblemNode node = ContainerUtil.findInstance(root.getChildren(), GrailsGradleSyncProblemNode.class);
    assertNotNull("the pane must show the blocked grails-app where its application node would be", node);
    assertEquals(appRoot, node.getValue().root());
    assertTrue(node.contains(conf));
    assertTrue("select-in must find the file through the root node", root.contains(conf));
    assertTrue(node.canNavigate());
    assertFalse(node.canNavigateToSource());

    node.update();
    PresentationData presentation = node.getPresentation();
    assertEquals(AllIcons.General.Warning, presentation.getIcon(false));
    assertEquals("app", presentation.getPresentableText());
    StringBuilder coloredText = new StringBuilder();
    for (PresentableNodeDescriptor.ColoredFragment fragment : presentation.getColoredText()) coloredText.append(fragment.getText());
    assertTrue(coloredText.toString(), coloredText.toString().contains(GrailsBundle.message("gradle.sync.blocked.import.failed")));
    assertTrue(presentation.getTooltip(), presentation.getTooltip().contains("Could not resolve org.apache.grails:grails-core"));
  }

  public void testProblemNodesSortFirst() {
    VirtualFile conf = myFixture.addFileToProject("app/grails-app/conf/application.yml", "grails: {}").getVirtualFile();
    VirtualFile appRoot = conf.getParent().getParent().getParent();
    GrailsGradleSyncStatus.getInstance(getProject()).recordFailure(appRoot.getPath(), "boom");
    GrailsRootNode root = new GrailsRootNode(getProject(), ViewSettings.DEFAULT);
    GrailsGradleSyncProblemNode problem = ContainerUtil.findInstance(root.getChildren(), GrailsGradleSyncProblemNode.class);
    assertNotNull(problem);

    PsiDirectory directory = PsiManager.getInstance(getProject()).findDirectory(appRoot);
    assertNotNull(directory);
    GrailsPsiDirectoryNode plainDirectory = new GrailsPsiDirectoryNode(directory, ViewSettings.DEFAULT);
    GrailsNodeComparator comparator = new GrailsNodeComparator(getProject(), GrailsConstants.GRAILS);
    assertTrue(comparator.compare(problem, plainDirectory) < 0);
    assertTrue(comparator.compare(plainDirectory, problem) > 0);
  }

  public void testNoProblemNodeWithoutABrokenBuild() {
    myFixture.addFileToProject("app/grails-app/conf/application.yml", "grails: {}");
    GrailsRootNode root = new GrailsRootNode(getProject(), ViewSettings.DEFAULT);
    assertNull(ContainerUtil.findInstance(root.getChildren(), GrailsGradleSyncProblemNode.class));
  }
}
