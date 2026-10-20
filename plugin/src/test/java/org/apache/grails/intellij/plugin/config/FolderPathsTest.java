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

import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.grails.intellij.lib.testFramework.GrailsTestCase;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The shared folder-path helpers: the duplicate rule the store applies, the label both custom-folder
 * node kinds render, and the one resolution rule the provider, the settings page and the refresh
 * listener share: relative to the application root, or absolute.
 */
public class FolderPathsTest extends GrailsTestCase {

  public void testNormalizeCollapsesTheCommonSpellings() {
    for (String spelling : Arrays.asList("grails-app/", "./grails-app", "grails-app\\", ".\\grails-app")) {
      assertEquals(spelling, "grails-app", FolderPaths.normalize(spelling));
    }
  }

  /**
   * A bare leading separator is not one of the spellings the javadoc lists — only a leading
   * {@code ./} or {@code .\} is stripped — so it survives normalization as a leading slash, which is
   * why {@code /grails-app} and {@code grails-app} are two different folders and not duplicates.
   */
  public void testNormalizeKeepsABareLeadingSeparator() {
    assertEquals("/grails-app", FolderPaths.normalize("\\grails-app"));
    assertEquals("/grails-app", FolderPaths.normalize("/grails-app"));
  }

  public void testDedupeTrimsDropsBlanksAndKeepsTheFirstSpelling() {
    assertEquals(Arrays.asList("a/", "b"),
                 FolderPaths.dedupe(Arrays.asList("  a/  ", "", "   ", "a", "a\\", "b")));
  }

  public void testDedupeKeepsEveryEntryWhenNothingCollapses() {
    assertEquals(Arrays.asList("a", "b", "c"),
                 FolderPaths.dedupe(Arrays.asList("a", "b", "c")));
  }

  public void testDedupeDoesNotTreatAnAbsolutePathAsASpellingOfARelativeOne() {
    assertEquals(Arrays.asList("a", "/a"),
                 FolderPaths.dedupe(Arrays.asList("a", "/a")));
  }

  public void testDedupeOfNothingIsEmpty() {
    assertTrue(FolderPaths.dedupe(Collections.<String>emptyList()).isEmpty());
    assertTrue(FolderPaths.dedupe(Arrays.asList(null, "  ")).isEmpty());
  }

  /** The label is the last segment of the normalized path, whichever spelling names the folder. */
  public void testFolderNameIsTheLastSegmentOfTheNormalizedPath() {
    assertEquals("docs", FolderPaths.folderName("docs"));
    assertEquals("docs", FolderPaths.folderName("docs/"));
    assertEquals("docs", FolderPaths.folderName("./docs/"));
    assertEquals("docs", FolderPaths.folderName("app\\docs\\"));
    assertEquals("c", FolderPaths.folderName("a/b/c"));
    assertEquals(".github", FolderPaths.folderName(".github"));
  }

  public void testFolderNameOfAnEmptyOrRootPathIsEmpty() {
    assertEquals("", FolderPaths.folderName(""));
    assertEquals("", FolderPaths.folderName("/"));
  }

  /**
   * Two folders can share a name at different depths, so the label alone does not identify them —
   * which is what the node's location string is for. What has to hold is that each label follows its
   * own path's last segment.
   */
  public void testFolderNameFollowsEachPathsOwnLastSegment() {
    List<String> names = Arrays.asList("docs", "app/docs", "a/b/c").stream()
      .map(FolderPaths::folderName).toList();
    assertEquals(Arrays.asList("docs", "docs", "c"), names);
  }

  public void testIsAbsoluteRecognisesUnixWindowsUncAndHomePaths() {
    for (String path : Arrays.asList("/home/me/notes", "C:\\notes", "c:/notes", "\\\\server\\share", "~", "~/notes",
                                     "  /home/me/notes  ")) {
      assertTrue(path, FolderPaths.isAbsolute(path));
    }
    for (String path : Arrays.asList("docs", "./docs", "../shared", "grails-app\\i18n", "~notes", "")) {
      assertFalse(path, FolderPaths.isAbsolute(path));
    }
  }

  public void testFindResolvesARelativePathUnderTheApplicationRoot() throws IOException {
    VirtualFile root = myFixture.getTempDirFixture().findOrCreateDir("app");
    VirtualFile docs = myFixture.getTempDirFixture().findOrCreateDir("app/docs");

    assertEquals(docs, FolderPaths.find(root, "docs"));
    assertEquals("the common spellings resolve the same folder", docs, FolderPaths.find(root, "./docs/"));
    assertNull("nothing exists there yet", FolderPaths.find(root, "not-created-yet"));
  }

  /** A sibling of the application in a multi-project build is reached by climbing out of its root. */
  public void testFindClimbsOutOfTheApplicationRootWithDotDot() throws IOException {
    VirtualFile root = myFixture.getTempDirFixture().findOrCreateDir("app");
    VirtualFile shared = myFixture.getTempDirFixture().findOrCreateDir("shared/docs");

    assertEquals(shared, FolderPaths.find(root, "../shared/docs"));
  }

  public void testFindLooksUpAnAbsolutePathOnTheLocalFileSystem() throws IOException {
    VirtualFile root = myFixture.getTempDirFixture().findOrCreateDir("app");
    VirtualFile outside = OutsideProjectDirectory.create(getTestRootDisposable(), "find", "notes.md");

    assertEquals("an absolute path is not read under the application root",
                 outside, FolderPaths.find(root, outside.getPath()));
    assertEquals("a trailing separator does not stop it resolving", outside, FolderPaths.find(root, outside.getPath() + "/"));
    assertEquals("a file is found too; telling it from a folder is the caller's job",
                 outside.findChild("notes.md"), FolderPaths.find(root, outside.getPath() + "/notes.md"));
  }

  /** The settings page checks absolute paths even when it knows no application root. */
  public void testFindWithoutAnApplicationRootResolvesOnlyAbsolutePaths() {
    VirtualFile outside = OutsideProjectDirectory.create(getTestRootDisposable(), "find-no-root");

    assertEquals(outside, FolderPaths.find(null, outside.getPath()));
    assertNull(FolderPaths.find(null, "docs"));
  }

  public void testToAbsolutePathReadsRelativePathsAgainstTheRootAndCanonicalisesThem() {
    assertEquals("/work/app/docs", FolderPaths.toAbsolutePath("/work/app", "docs"));
    assertEquals("/work/app/docs", FolderPaths.toAbsolutePath("/work/app", "./docs/"));
    assertEquals("/work/shared/docs", FolderPaths.toAbsolutePath("/work/app", "../shared/docs"));
    assertEquals("/home/me/notes", FolderPaths.toAbsolutePath("/work/app", "/home/me/notes/"));
  }

  public void testToAbsolutePathExpandsTheHomeDirectory() {
    String home = OSAgnosticPathUtil.expandUserHome("~");
    assertFalse("the test needs a home directory to expand to", home.startsWith("~"));

    assertEquals(FolderPaths.toAbsolutePath("/", home + "/notes"), FolderPaths.toAbsolutePath("/work/app", "~/notes"));
  }

  /** A folder picked under the application root is stored relative to it, so the setting survives a move. */
  public void testToConfiguredPathIsRelativeUnderTheApplicationRoot() throws IOException {
    VirtualFile root = myFixture.getTempDirFixture().findOrCreateDir("app");
    VirtualFile docs = myFixture.getTempDirFixture().findOrCreateDir("app/src/docs");

    assertEquals("src/docs", FolderPaths.toConfiguredPath(root, docs));
    assertNull("the application root is already the top of the Grails view", FolderPaths.toConfiguredPath(root, root));
  }

  public void testToConfiguredPathIsAbsoluteOutsideTheApplicationRoot() throws IOException {
    VirtualFile root = myFixture.getTempDirFixture().findOrCreateDir("app");
    VirtualFile sibling = myFixture.getTempDirFixture().findOrCreateDir("shared");

    assertEquals(sibling.getPath(), FolderPaths.toConfiguredPath(root, sibling));
    assertEquals("with no root known every folder is stored absolute", sibling.getPath(),
                 FolderPaths.toConfiguredPath(null, sibling));
  }
}
