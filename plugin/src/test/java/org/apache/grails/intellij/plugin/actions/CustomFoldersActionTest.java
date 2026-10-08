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
package org.apache.grails.intellij.plugin.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import org.apache.grails.intellij.lib.testFramework.GrailsTestCase;
import org.apache.grails.intellij.plugin.config.GrailsCustomFoldersProjectService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CustomFoldersActionTest extends GrailsTestCase {

  public void testAddPathAppendsToProjectFolders() {
    PsiDirectory directory = createDirectory("custom");
    GrailsCustomFoldersProjectService service = service();
    service.setFolders(List.of("existing"));

    new AddCustomFolderAction().actionPerformed(
      event(data(CommonDataKeys.PROJECT, getProject(), CommonDataKeys.PSI_ELEMENT, directory)));

    VirtualFile virtualFile = directory.getVirtualFile();
    String expected = VfsUtilCore.getRelativePath(virtualFile, getProject().getBaseDir(), '/');
    if (expected == null) expected = virtualFile.getPath();

    assertTrue(expected.endsWith("custom"));
    assertEquals(List.of("existing", expected), service.getFolders());
  }

  public void testDisabledWhenThreeFoldersConfigured() {
    PsiDirectory directory = createDirectory("custom");
    GrailsCustomFoldersProjectService service = service();
    service.setFolders(List.of("a", "b", "c"));

    AddCustomFolderAction action = new AddCustomFolderAction();
    AnActionEvent event = event(data(CommonDataKeys.PROJECT, getProject(), CommonDataKeys.PSI_ELEMENT, directory));

    action.update(event);
    assertFalse(event.getPresentation().isEnabled());
    assertTrue(event.getPresentation().isVisible());

    service.setFolders(List.of());
    action.update(event);
    assertTrue(event.getPresentation().isEnabled());
    assertTrue(event.getPresentation().isVisible());
  }

  // A light fixture has no selected GrailsCustomFolderNode in ProjectView.getCurrentProjectViewPane(),
  // so the pane-driven path of the action cannot be exercised here. This mirrors the action's own
  // removal logic against the project service instead.
  public void testRemoveLogicRemovesConfiguredPathFromService() {
    GrailsCustomFoldersProjectService service = service();
    service.setFolders(new ArrayList<>(List.of("src/main/groovy", "grails-app/domain")));

    List<String> folders = new ArrayList<>(service.getFolders());
    folders.remove("src/main/groovy");
    service.setFolders(folders);

    assertEquals(List.of("grails-app/domain"), service.getFolders());
  }

  public void testRemoveActionIsNoOpWithoutSelectedCustomFolder() {
    GrailsCustomFoldersProjectService service = service();
    service.setFolders(new ArrayList<>(List.of("src/main/groovy", "grails-app/domain")));

    RemoveCustomFolderAction action = new RemoveCustomFolderAction();
    action.actionPerformed(event(data(CommonDataKeys.PROJECT, getProject())));

    assertEquals(List.of("src/main/groovy", "grails-app/domain"), service.getFolders());
  }

  public void testRemoveUpdateInvisibleWithoutSelectedCustomFolder() {
    RemoveCustomFolderAction action = new RemoveCustomFolderAction();
    AnActionEvent event = event(data(CommonDataKeys.PROJECT, getProject()));

    action.update(event);

    assertFalse(event.getPresentation().isEnabled());
    assertFalse(event.getPresentation().isVisible());
  }

  public void testRemoveDoesNotThrowWithoutProject() {
    new RemoveCustomFolderAction().actionPerformed(event(new HashMap<>()));
  }

  public void testConfigureUpdateInvisibleWithoutProject() {
    ConfigureCustomFoldersAction action = new ConfigureCustomFoldersAction();
    AnActionEvent event = event(new HashMap<>());

    action.update(event);

    assertFalse(event.getPresentation().isEnabled());
    assertFalse(event.getPresentation().isVisible());
  }

  // Configure enables for a selected custom folder node or any selected directory (same as Add), so
  // an open project without a project-view selection leaves it disabled. The settings dialog is not opened.
  public void testConfigureUpdateInvisibleWithoutSelectedCustomFolder() {
    ConfigureCustomFoldersAction action = new ConfigureCustomFoldersAction();
    AnActionEvent event = event(data(CommonDataKeys.PROJECT, getProject()));

    action.update(event);

    assertFalse(event.getPresentation().isEnabled());
    assertFalse(event.getPresentation().isVisible());
  }

  public void testConfigureDoesNotThrowWithoutProject() {
    new ConfigureCustomFoldersAction().actionPerformed(event(new HashMap<>()));
  }

  public void testActionsAreRegistered() {
    ActionManager actionManager = ActionManager.getInstance();
    assertTrue(actionManager.getAction("Grails.AddCustomFolder") instanceof AddCustomFolderAction);
    assertTrue(actionManager.getAction("Grails.RemoveCustomFolder") instanceof RemoveCustomFolderAction);
    assertTrue(actionManager.getAction("Grails.ConfigureCustomFolders") instanceof ConfigureCustomFoldersAction);
  }

  private GrailsCustomFoldersProjectService service() {
    return getProject().getService(GrailsCustomFoldersProjectService.class);
  }

  private PsiDirectory createDirectory(String path) {
    myFixture.addFileToProject(path + "/placeholder.txt", "content");
    VirtualFile file = myFixture.findFileInTempDir(path + "/placeholder.txt");
    assertNotNull(file);
    PsiDirectory directory = PsiManager.getInstance(getProject()).findDirectory(Objects.requireNonNull(file.getParent()));
    assertNotNull(directory);
    return directory;
  }

  private static Map<DataKey<?>, Object> data(DataKey<?> key, Object value) {
    Map<DataKey<?>, Object> data = new HashMap<>();
    data.put(key, value);
    return data;
  }

  private static Map<DataKey<?>, Object> data(DataKey<?> key1, Object value1, DataKey<?> key2, Object value2) {
    Map<DataKey<?>, Object> data = new HashMap<>();
    data.put(key1, value1);
    data.put(key2, value2);
    return data;
  }

  private static AnActionEvent event(Map<DataKey<?>, Object> data) {
    DataContext context = new DataContext() {
      @Override
      public @Nullable Object getData(@NotNull String dataId) {
        return null;
      }

      @Override
      @SuppressWarnings("unchecked")
      public <T> T getData(@NotNull DataKey<T> key) {
        return (T)data.get(key);
      }
    };
    return new AnActionEvent(null, context, ActionPlaces.UNKNOWN, new Presentation(), ActionManager.getInstance(), 0);
  }
}
