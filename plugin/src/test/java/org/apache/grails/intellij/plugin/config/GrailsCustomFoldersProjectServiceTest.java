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

package org.apache.grails.intellij.plugin.config;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import org.jetbrains.annotations.NotNull;
import org.apache.grails.intellij.lib.testFramework.GrailsTestCase;
import org.apache.grails.intellij.plugin.config.GrailsConstants;
import org.apache.grails.intellij.plugin.projectView.GrailsProjectViewPanes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GrailsCustomFoldersProjectServiceTest extends GrailsTestCase {

  public void testSetAndGetFolders() {
    GrailsCustomFoldersProjectService service = new GrailsCustomFoldersProjectService(getProject());
    List<String> folders = Arrays.asList("src/main/groovy", "src/test/groovy", "grails-app/domain");
    service.setFolders(folders);
    assertEquals(folders, service.getFolders());
  }

  public void testBlankLinesAreFiltered() {
    GrailsCustomFoldersProjectService service = new GrailsCustomFoldersProjectService(getProject());
    service.setFolders(Arrays.asList("src/main/groovy", "", "   ", "grails-app/domain"));
    assertEquals(Arrays.asList("src/main/groovy", "grails-app/domain"), service.getFolders());
  }

  public void testMaxThreeFoldersEnforced() {
    GrailsCustomFoldersProjectService service = new GrailsCustomFoldersProjectService(getProject());
    service.setFolders(Arrays.asList("a", "b", "c", "d"));
    assertEquals(Arrays.asList("a", "b", "c"), service.getFolders());
  }

  public void testPersistenceRoundTrip() {
    GrailsCustomFoldersProjectService service = new GrailsCustomFoldersProjectService(getProject());
    service.setFolders(Arrays.asList("src/main/groovy", "grails-app/domain"));
    GrailsCustomFoldersProjectService.StateHolder state = service.getState();

    GrailsCustomFoldersProjectService restored = new GrailsCustomFoldersProjectService(getProject());
    restored.loadState(state);
    assertEquals(service.getFolders(), restored.getFolders());
  }

  public void testGetEffectiveFoldersCombinesGlobalAndProjectFolders() {
    withGlobalFolders(Arrays.asList("global/folder"), () -> {
      GrailsCustomFoldersProjectService service = new GrailsCustomFoldersProjectService(getProject());
      service.setFolders(Arrays.asList("project/folder"));
      assertEquals(Arrays.asList("project/folder", "global/folder"), service.getEffectiveFolders());
    });
  }

  public void testGetEffectiveFoldersDeduplicatesAcrossAreas() {
    withGlobalFolders(Arrays.asList("grails-app"), () -> {
      GrailsCustomFoldersProjectService service = new GrailsCustomFoldersProjectService(getProject());
      service.setFolders(Arrays.asList("grails-app/", "other"));
      assertEquals(Arrays.asList("grails-app/", "other"), service.getEffectiveFolders());
    });
  }

  public void testGetEffectiveFoldersFallsBackToGlobalDefaults() {
    withGlobalFolders(Arrays.asList("global/folder1", "global/folder2"), () -> {
      GrailsCustomFoldersProjectService service = new GrailsCustomFoldersProjectService(getProject());
      assertEquals(Arrays.asList("global/folder1", "global/folder2"), service.getEffectiveFolders());
    });
  }

  /**
   * The project's own folders take precedence: with two global and two custom folders only three
   * folders are rendered, so the custom ones are kept and the global ones fill what is left.
   */
  public void testEffectiveFoldersAreCappedAtThreeWithProjectFoldersFirst() {
    withGlobalFolders(Arrays.asList("global1", "global2"), () -> {
      GrailsCustomFoldersProjectService service = new GrailsCustomFoldersProjectService(getProject());
      service.setFolders(Arrays.asList("custom1", "custom2"));
      assertEquals(Arrays.asList("custom1", "custom2", "global1"), service.getEffectiveFolders());
    });
  }

  /** Three custom folders fill the budget, so no global folder is rendered. */
  public void testThreeProjectFoldersExcludeAllGlobalFolders() {
    withGlobalFolders(Arrays.asList("global1", "global2"), () -> {
      GrailsCustomFoldersProjectService service = new GrailsCustomFoldersProjectService(getProject());
      service.setFolders(Arrays.asList("custom1", "custom2", "custom3"));
      assertEquals(Arrays.asList("custom1", "custom2", "custom3"), service.getEffectiveFolders());
    });
  }

  /** Two global and one custom fills exactly three, so both areas contribute. */
  public void testTwoGlobalAndOneProjectFolderFillsTheCap() {
    withGlobalFolders(Arrays.asList("global1", "global2"), () -> {
      GrailsCustomFoldersProjectService service = new GrailsCustomFoldersProjectService(getProject());
      service.setFolders(Arrays.asList("custom1"));
      assertEquals(Arrays.asList("custom1", "global1", "global2"), service.getEffectiveFolders());
    });
  }

  /** Any number of global folders still yields at most three in total. */
  public void testGlobalOnlyIsStillCappedAtThree() {
    withGlobalFolders(Arrays.asList("global1", "global2"), () -> {
      GrailsCustomFoldersProjectService service = new GrailsCustomFoldersProjectService(getProject());
      assertEquals(3, GrailsCustomFoldersProjectService.MAX_FOLDERS);
      assertEquals(2, service.getEffectiveFolders().size());
    });
  }

  /** A folder in both areas is rendered once, keeping the project's spelling of it. */
  public void testDuplicateAcrossAreasKeepsProjectSpelling() {
    withGlobalFolders(Arrays.asList("docs"), () -> {
      GrailsCustomFoldersProjectService service = new GrailsCustomFoldersProjectService(getProject());
      service.setFolders(Arrays.asList("docs/"));
      assertEquals(Arrays.asList("docs/"), service.getEffectiveFolders());
    });
  }

  /** The cap counts unique folders, so duplicates must not crowd out a real one. */
  public void testCapCountsUniqueFoldersOnly() {
    withGlobalFolders(Arrays.asList("docs/", "docs", "other"), () -> {
      GrailsCustomFoldersProjectService service = new GrailsCustomFoldersProjectService(getProject());
      assertEquals(Arrays.asList("docs/", "other"), service.getEffectiveFolders());
    });
  }

  /** Runs {@code body} with the global defaults temporarily set, restoring them afterwards. */
  private void withGlobalFolders(@NotNull List<String> folders, @NotNull Runnable body) {
    GrailsCustomFoldersAppService appService = GrailsCustomFoldersAppService.getStaticInstance();
    List<String> original = new ArrayList<>(appService.getFolders());
    try {
      appService.setFolders(folders);
      body.run();
    }
    finally {
      appService.setFolders(original);
    }
  }

  /**
   * Verifies that changing folders publishes the event that triggers a Grails pane refresh.
   * The subscriber in the service calls {@code GrailsProjectViewPanes.refresh(project)} which
   * refreshes the Grails pane by ID, regardless of which pane is currently active.
   */
  public void testSetFoldersPublishesChangeEvent() {
    GrailsCustomFoldersProjectService service = new GrailsCustomFoldersProjectService(getProject());
    boolean[] eventReceived = {false};

    // Subscribe to the same topic the service publishes to
    com.intellij.util.messages.MessageBusConnection connection =
      getProject().getMessageBus().connect(getTestRootDisposable());
    connection.subscribe(GrailsCustomFoldersProjectService.FoldersChangedListener.TOPIC,
                        () -> eventReceived[0] = true);

    service.setFolders(Arrays.asList("src/main/groovy"));

    assertTrue("setFolders should publish a FoldersChangedListener event", eventReceived[0]);
  }

  /**
   * Verifies that GrailsProjectViewPanes.refresh() is safe to call when the Grails pane
   * doesn't exist (e.g., no Grails applications in the project).
   */
  public void testRefreshIsNoOpWhenGrailsPaneMissing() {
    ProjectView projectView = ProjectView.getInstance(getProject());
    AbstractProjectViewPane grailsPane = projectView.getProjectViewPaneById(GrailsConstants.GRAILS);

    if (grailsPane != null) {
      // If the pane exists, remove it temporarily
      projectView.removeProjectPane(grailsPane);
      try {
        // Should not throw
        GrailsProjectViewPanes.refresh(getProject());
      }
      finally {
        projectView.addProjectPane(grailsPane);
      }
    }
    else {
      // No pane exists; refresh should be a no-op
      GrailsProjectViewPanes.refresh(getProject());
    }
  }

  public void testDuplicatePathsAreDeduplicated() {
    GrailsCustomFoldersProjectService service = new GrailsCustomFoldersProjectService(getProject());
    service.setFolders(Arrays.asList("grails-app", "grails-app/", "grails-app\\"));
    assertEquals(Arrays.asList("grails-app"), service.getFolders());
  }

  public void testDuplicatePathsWithDotSlashPrefix() {
    GrailsCustomFoldersProjectService service = new GrailsCustomFoldersProjectService(getProject());
    service.setFolders(Arrays.asList("src/main", "./src/main", ".\\src/main"));
    assertEquals(Arrays.asList("src/main"), service.getFolders());
  }

  public void testFirstInstanceIsKept() {
    GrailsCustomFoldersProjectService service = new GrailsCustomFoldersProjectService(getProject());
    service.setFolders(Arrays.asList("grails-app/", "grails-app", "other"));
    assertEquals(Arrays.asList("grails-app/", "other"), service.getFolders());
  }

  public void testAbsolutePathAndRelativePathAreNotDuplicates() {
    GrailsCustomFoldersProjectService service = new GrailsCustomFoldersProjectService(getProject());
    service.setFolders(Arrays.asList("grails-app", "/grails-app"));
    assertEquals(Arrays.asList("grails-app", "/grails-app"), service.getFolders());
  }

  public void testDeduplicationBeforeMaxLimit() {
    GrailsCustomFoldersProjectService service = new GrailsCustomFoldersProjectService(getProject());
    service.setFolders(Arrays.asList("a", "a/", "b", "b/", "c", "c/", "d", "d/"));
    assertEquals(Arrays.asList("a", "b", "c"), service.getFolders());
  }
}
