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

public class GrailsCustomFoldersAppServiceTest extends GrailsTestCase {

  public void testSetAndGetFolders() {
    GrailsCustomFoldersAppService service = new GrailsCustomFoldersAppService();

    assertTrue(service.getFolders().isEmpty());

    service.setFolders(Arrays.asList("one", "two", "three"));
    assertEquals(Arrays.asList("one", "two", "three"), service.getFolders());

    service.setFolders(Collections.<String>emptyList());
    assertTrue(service.getFolders().isEmpty());
  }

  public void testBlankAndEmptyEntriesAreDropped() {
    GrailsCustomFoldersAppService service = new GrailsCustomFoldersAppService();

    service.setFolders(Arrays.asList("one", "", "   ", "\t", "two"));
    assertEquals(Arrays.asList("one", "two"), service.getFolders());

    service.setFolders(Arrays.asList("", "  "));
    assertTrue(service.getFolders().isEmpty());
  }

  public void testMaxThreeFoldersEnforced() {
    GrailsCustomFoldersAppService service = new GrailsCustomFoldersAppService();

    assertEquals(3, GrailsCustomFoldersAppService.MAX_FOLDERS);

    service.setFolders(Arrays.asList("one", "two", "three", "four"));
    assertEquals(Arrays.asList("one", "two", "three"), service.getFolders());

    service.setFolders(Arrays.asList("one", "two", "three"));
    assertEquals(Arrays.asList("one", "two", "three"), service.getFolders());
  }

  public void testPersistenceRoundTrip() {
    GrailsCustomFoldersAppService service = new GrailsCustomFoldersAppService();
    service.setFolders(Arrays.asList("one", "two", "three"));

    GrailsCustomFoldersAppService.StateHolder state = service.getState();

    GrailsCustomFoldersAppService restored = new GrailsCustomFoldersAppService();
    restored.loadState(state);

    assertEquals(service.getFolders(), restored.getFolders());
  }

  public void testPersistenceRoundTripEmptyState() {
    GrailsCustomFoldersAppService service = new GrailsCustomFoldersAppService();

    GrailsCustomFoldersAppService.StateHolder state = service.getState();

    GrailsCustomFoldersAppService restored = new GrailsCustomFoldersAppService();
    restored.loadState(state);

    assertTrue(restored.getFolders().isEmpty());
  }

  public void testGetFoldersReturnsUnmodifiableCopy() {
    GrailsCustomFoldersAppService service = new GrailsCustomFoldersAppService();
    service.setFolders(Arrays.asList("one", "two"));

    List<String> folders = service.getFolders();
    try {
      folders.add("three");
    }
    catch (UnsupportedOperationException ignored) {
      // an unmodifiable list rejects the mutation
    }

    assertEquals(Arrays.asList("one", "two"), service.getFolders());
  }

  public void testDuplicatePathsAreDeduplicated() {
    GrailsCustomFoldersAppService service = new GrailsCustomFoldersAppService();
    service.setFolders(Arrays.asList("grails-app", "grails-app/", "grails-app\\"));
    assertEquals(Arrays.asList("grails-app"), service.getFolders());
  }

  public void testDuplicatePathsWithDotSlashPrefix() {
    GrailsCustomFoldersAppService service = new GrailsCustomFoldersAppService();
    service.setFolders(Arrays.asList("src/main", "./src/main", ".\\src/main"));
    assertEquals(Arrays.asList("src/main"), service.getFolders());
  }

  public void testFirstInstanceIsKept() {
    GrailsCustomFoldersAppService service = new GrailsCustomFoldersAppService();
    service.setFolders(Arrays.asList("grails-app/", "grails-app", "other"));
    assertEquals(Arrays.asList("grails-app/", "other"), service.getFolders());
  }

  public void testAbsolutePathAndRelativePathAreNotDuplicates() {
    GrailsCustomFoldersAppService service = new GrailsCustomFoldersAppService();
    service.setFolders(Arrays.asList("grails-app", "/grails-app"));
    assertEquals(Arrays.asList("grails-app", "/grails-app"), service.getFolders());
  }

  public void testDeduplicationBeforeMaxLimit() {
    GrailsCustomFoldersAppService service = new GrailsCustomFoldersAppService();
    service.setFolders(Arrays.asList("a", "a/", "b", "b/", "c", "c/", "d", "d/"));
    assertEquals(Arrays.asList("a", "b", "c"), service.getFolders());
  }
}
