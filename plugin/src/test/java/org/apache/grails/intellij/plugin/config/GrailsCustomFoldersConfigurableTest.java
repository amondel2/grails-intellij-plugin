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

import org.apache.grails.intellij.lib.testFramework.GrailsTestCase;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GrailsCustomFoldersConfigurableTest extends GrailsTestCase {

  public void testParseFoldersBasic() {
    List<String> result = GrailsCustomFoldersConfigurable.parseFolders("a\nb\nc");
    assertEquals(Arrays.asList("a", "b", "c"), result);
  }

  public void testParseFoldersSkipsBlankLines() {
    List<String> result = GrailsCustomFoldersConfigurable.parseFolders("a\n\n  \nb");
    assertEquals(Arrays.asList("a", "b"), result);
  }

  public void testParseFoldersTrimsWhitespace() {
    List<String> result = GrailsCustomFoldersConfigurable.parseFolders("  a  \n\tb\t");
    assertEquals(Arrays.asList("a", "b"), result);
  }

  public void testParseFoldersDeduplicatesExactDuplicates() {
    List<String> result = GrailsCustomFoldersConfigurable.parseFolders("a\na\nb");
    assertEquals(Arrays.asList("a", "b"), result);
  }

  public void testParseFoldersDeduplicatesTrailingSlash() {
    List<String> result = GrailsCustomFoldersConfigurable.parseFolders("a\na/\nb");
    assertEquals(Arrays.asList("a", "b"), result);
  }

  public void testParseFoldersDeduplicatesDotSlashPrefix() {
    List<String> result = GrailsCustomFoldersConfigurable.parseFolders("a\n./a\nb");
    assertEquals(Arrays.asList("a", "b"), result);
  }

  public void testParseFoldersDeduplicatesBackslash() {
    List<String> result = GrailsCustomFoldersConfigurable.parseFolders("a\na\\\nb");
    assertEquals(Arrays.asList("a", "b"), result);
  }

  public void testParseFoldersKeepsFirstInstance() {
    List<String> result = GrailsCustomFoldersConfigurable.parseFolders("a/\na\nb");
    assertEquals(Arrays.asList("a/", "b"), result);
  }

  public void testParseFoldersDoesNotDeduplicateRelativeVsAbsolute() {
    List<String> result = GrailsCustomFoldersConfigurable.parseFolders("a\n/a");
    assertEquals(Arrays.asList("a", "/a"), result);
  }

  public void testParseFoldersRespectsMaxThree() {
    List<String> result = GrailsCustomFoldersConfigurable.parseFolders("a\nb\nc\nd");
    assertEquals(Arrays.asList("a", "b", "c"), result);
  }

  public void testParseFoldersDeduplicatesBeforeMaxLimit() {
    List<String> result = GrailsCustomFoldersConfigurable.parseFolders("a\na/\nb\nb/\nc\nc/\nd\nd/");
    assertEquals(Arrays.asList("a", "b", "c"), result);
  }

  public void testParseFoldersEmptyInput() {
    List<String> result = GrailsCustomFoldersConfigurable.parseFolders("");
    assertTrue(result.isEmpty());
  }

  public void testParseFoldersOnlyBlankLines() {
    List<String> result = GrailsCustomFoldersConfigurable.parseFolders("\n  \n\t");
    assertTrue(result.isEmpty());
  }

  /**
 * The preview is computed from the typed text, so it shows what will render without applying first.
 * This is what made the settings page appear to ignore edits until Apply.
 */
public void testPreviewTracksTypedGlobalFolders() {
  assertEquals(Arrays.asList("global1", "global2"),
               GrailsCustomFoldersConfigurable.previewFolders("global1\nglobal2", ""));
}

public void testPreviewCombinesGlobalAndProjectFolders() {
  assertEquals(Arrays.asList("custom1", "global1"),
               GrailsCustomFoldersConfigurable.previewFolders("global1", "custom1"));
}

public void testPreviewCapsAtThreeWithProjectFoldersFirst() {
  assertEquals(Arrays.asList("custom1", "custom2", "global1"),
               GrailsCustomFoldersConfigurable.previewFolders("global1\nglobal2", "custom1\ncustom2"));
}

public void testPreviewDropsDuplicatesAcrossAreas() {
  assertEquals(Arrays.asList("docs/", "other"),
               GrailsCustomFoldersConfigurable.previewFolders("docs", "docs/\nother"));
}

public void testPreviewIgnoresBlankLines() {
  assertEquals(Arrays.asList("a", "b"),
               GrailsCustomFoldersConfigurable.previewFolders("\na\n  \nb\n", ""));
}

public void testPreviewOfEmptyInputIsEmpty() {
  assertTrue(GrailsCustomFoldersConfigurable.previewFolders("", "").isEmpty());
}

public void testWarningMessageNoWarningWhenWithinLimit() {
    String warning = GrailsCustomFoldersConfigurable.warningMessage("a\nb", "c");
    assertEquals("", warning);
  }

  public void testWarningMessageNoWarningAtExactLimit() {
    String warning = GrailsCustomFoldersConfigurable.warningMessage("a\nb\nc", "");
    assertEquals("", warning);
  }

  public void testWarningMessageWarnsWhenOverrideExceedsLimit() {
    String warning = GrailsCustomFoldersConfigurable.warningMessage("", "a\nb\nc\nd");
    assertTrue(warning.contains("4"));
    assertTrue(warning.contains("3"));
  }

  public void testWarningMessageWarnsWhenGlobalExceedsLimit() {
    String warning = GrailsCustomFoldersConfigurable.warningMessage("a\nb\nc\nd\ne", "");
    assertTrue(warning.contains("5"));
    assertTrue(warning.contains("3"));
  }

  public void testWarningMessageCountsFoldersAcrossBothAreas() {
    String warning = GrailsCustomFoldersConfigurable.warningMessage("a\nb\nc\nd", "x\ny\nz\nw");
    assertTrue("the two areas contribute four each", warning.contains("8"));
    assertTrue(warning.contains("3"));
  }

  public void testWarningMessageEmptyInput() {
    String warning = GrailsCustomFoldersConfigurable.warningMessage("", "");
    assertEquals("", warning);
  }

  public void testWarningMessageIgnoresBlankLines() {
    String warning = GrailsCustomFoldersConfigurable.warningMessage("a\n\n  \nb", "");
    assertEquals("", warning);
  }

  public void testWarningMessageIgnoresDuplicates() {
    String warning = GrailsCustomFoldersConfigurable.warningMessage("", "tasks/\n/home/aaron/\n/home/aaron/\n/t");
    assertEquals("", warning);
  }

  public void testWarningMessageCountsUniqueFolders() {
    String warning = GrailsCustomFoldersConfigurable.warningMessage("", "a\na/\nb\nc\nd");
    assertTrue(warning.contains("4"));
    assertTrue(warning.contains("3"));
  }

  public void testWarningMessageGlobalCheckedWhenOverridePresent() {
    String warning = GrailsCustomFoldersConfigurable.warningMessage("a\nb\nc\nd", "x");
    assertTrue("the four global folders count towards the total", warning.contains("5"));
    assertTrue(warning.contains("3"));
  }

  /**
 * Two folders here plus two there is four in total, which is over the cap even though neither area
 * exceeds it on its own.
 */
  public void testWarningMessageCombinedTotalOverCap() {
    String warning = GrailsCustomFoldersConfigurable.warningMessage("a\nb", "c\nd");
    assertTrue(warning.contains("4"));
    assertTrue(warning.contains("3"));
  }

  /** Three in total, one from this project and two global, is exactly the cap. */
  public void testWarningMessageNoWarningAtCombinedLimit() {
    assertEquals("", GrailsCustomFoldersConfigurable.warningMessage("a\nb", "c"));
  }
}
