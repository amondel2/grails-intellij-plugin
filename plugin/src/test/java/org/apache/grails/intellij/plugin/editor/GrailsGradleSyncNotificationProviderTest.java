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
package org.apache.grails.intellij.plugin.editor;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.ui.EditorNotificationPanel;
import org.apache.grails.intellij.lib.testFramework.GrailsTestCase;
import org.apache.grails.intellij.plugin.gradle.GrailsGradleSyncStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import java.util.function.Function;
import java.util.function.Supplier;

public class GrailsGradleSyncNotificationProviderTest extends GrailsTestCase {

  private final GrailsGradleSyncNotificationProvider myProvider = new GrailsGradleSyncNotificationProvider();

  // EditorNotificationPanel is a Swing component, which the minimal Mock JDK 11 does not ship
  @Override
  protected @NotNull Supplier<Sdk> getTestJdk() {
    return () -> JavaSdk.getInstance().createJdk("TEST_JDK", IdeaTestUtil.requireRealJdkHome(), false);
  }

  private @NotNull VirtualFile controllerUnder(@NotNull String appDir) {
    return myFixture.addFileToProject(appDir + "/grails-app/controllers/FooController.groovy", "class FooController {}").getVirtualFile();
  }

  private static @NotNull VirtualFile appRootOf(@NotNull VirtualFile controller) {
    return controller.getParent().getParent().getParent();
  }

  public void testBannerOnAFileUnderAGrailsAppWhoseGradleImportFailed() {
    VirtualFile controller = controllerUnder("app");
    GrailsGradleSyncStatus.getInstance(getProject()).recordFailure(appRootOf(controller).getPath(), "Could not compile build file");

    Function<? super FileEditor, ? extends JComponent> data = myProvider.collectNotificationData(getProject(), controller);
    assertNotNull("files of an unrecognised grails-app under a broken build must get the banner", data);

    myFixture.openFileInEditor(controller);
    FileEditor editor = FileEditorManager.getInstance(getProject()).getSelectedEditor(controller);
    assertNotNull(editor);
    JComponent panel = data.apply(editor);
    assertInstanceOf(panel, EditorNotificationPanel.class);
  }

  public void testNoBannerOnceTheImportSucceeds() {
    VirtualFile controller = controllerUnder("app");
    GrailsGradleSyncStatus status = GrailsGradleSyncStatus.getInstance(getProject());
    status.recordFailure(appRootOf(controller).getPath(), "boom");
    assertNotNull(myProvider.collectNotificationData(getProject(), controller));

    status.recordSuccess(appRootOf(controller).getPath());
    assertNull(myProvider.collectNotificationData(getProject(), controller));
  }

  public void testNoBannerOutsideAGrailsAppFolder() {
    VirtualFile file = myFixture.addFileToProject("app/src/main/groovy/Util.groovy", "class Util {}").getVirtualFile();
    GrailsGradleSyncStatus.getInstance(getProject()).recordFailure(file.getParent().getParent().getParent().getParent().getPath(), "boom");
    assertNull(myProvider.collectNotificationData(getProject(), file));
  }

  public void testNoBannerWhenNoLinkedBuildCoversTheFile() {
    VirtualFile controller = controllerUnder("app");
    GrailsGradleSyncStatus.getInstance(getProject()).recordFailure("/somewhere/else", "boom");
    assertNull(myProvider.collectNotificationData(getProject(), controller));
  }
}
