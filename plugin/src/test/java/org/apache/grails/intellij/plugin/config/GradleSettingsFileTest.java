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

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.io.FileUtil;
import org.apache.grails.intellij.lib.testFramework.GrailsTestCase;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class GradleSettingsFileTest extends GrailsTestCase {
  private Path myRoot;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myRoot = FileUtil.createTempDirectory("gradle-settings", null, true).toPath();
  }

  public void testCreatesSettingsForSingleProjectModule() throws Exception {
    setUp(myRoot, myRoot, "shop", "shop");
    assertEquals("rootProject.name = 'shop'\n", read("settings.gradle"));
  }

  public void testCreatesSettingsWithIncludeForNestedModule() throws Exception {
    Path module = Files.createDirectories(myRoot.resolve("web"));
    setUp(myRoot, module, "shop", "web");
    assertEquals("rootProject.name = 'shop'\ninclude 'web'\n", read("settings.gradle"));
  }

  public void testMapsProjectDirWhenModuleDirDiffersFromName() throws Exception {
    Path module = Files.createDirectories(myRoot.resolve("apps/web"));
    setUp(myRoot, module, "shop", "shop-web");
    assertEquals("rootProject.name = 'shop'\ninclude 'shop-web'\nproject(':shop-web').projectDir = file('apps/web')\n",
                 read("settings.gradle"));
  }

  public void testLeavesExistingRootSettingsAlone() throws Exception {
    write("settings.gradle", "rootProject.name = 'generated-by-grails'\n");
    setUp(myRoot, myRoot, "shop", "shop");
    assertEquals("rootProject.name = 'generated-by-grails'\n", read("settings.gradle"));
  }

  public void testAppendsIncludeToExistingSettingsOnce() throws Exception {
    write("settings.gradle", "rootProject.name = 'shop'");
    Path module = Files.createDirectories(myRoot.resolve("web"));
    setUp(myRoot, module, "shop", "web");
    setUp(myRoot, module, "shop", "web");
    assertEquals("rootProject.name = 'shop'\ninclude 'web'\n", read("settings.gradle"));
  }

  public void testAddsOnlyProjectDirWhenIncludeIsAlreadyThere() throws Exception {
    write("settings.gradle", "rootProject.name = 'shop'\ninclude 'shop-web'\n");
    Path module = Files.createDirectories(myRoot.resolve("apps/web"));
    setUp(myRoot, module, "shop", "shop-web");
    assertEquals("rootProject.name = 'shop'\ninclude 'shop-web'\nproject(':shop-web').projectDir = file('apps/web')\n",
                 read("settings.gradle"));
  }

  public void testAddsOnlyProjectDirWhenKotlinIncludeIsAlreadyThere() throws Exception {
    write("settings.gradle.kts", "rootProject.name = \"shop\"\ninclude(\"shop-web\")\n");
    Path module = Files.createDirectories(myRoot.resolve("apps/web"));
    setUp(myRoot, module, "shop", "shop-web");
    assertEquals("rootProject.name = \"shop\"\ninclude(\"shop-web\")\nproject(\":shop-web\").projectDir = file(\"apps/web\")\n",
                 read("settings.gradle.kts"));
  }

  public void testLeavesCompleteMappingAlone() throws Exception {
    String mapped = "rootProject.name = 'shop'\ninclude 'shop-web'\nproject(':shop-web').projectDir = file('apps/web')\n";
    write("settings.gradle", mapped);
    Path module = Files.createDirectories(myRoot.resolve("apps/web"));
    setUp(myRoot, module, "shop", "shop-web");
    assertEquals(mapped, read("settings.gradle"));
  }

  public void testIgnoresModuleNameInComments() throws Exception {
    write("settings.gradle", "rootProject.name = 'shop'\n// TODO: include 'shop-web'\n/* include 'shop-web' */\n");
    Path module = Files.createDirectories(myRoot.resolve("apps/web"));
    setUp(myRoot, module, "shop", "shop-web");
    assertEquals("rootProject.name = 'shop'\n// TODO: include 'shop-web'\n/* include 'shop-web' */\n" +
                 "include 'shop-web'\nproject(':shop-web').projectDir = file('apps/web')\n",
                 read("settings.gradle"));
  }

  public void testIgnoresModuleNameInUnrelatedStatements() throws Exception {
    write("settings.gradle", "rootProject.name = 'shop'\nincludeBuild 'shop-web'\ndef dir = 'shop-web'\n");
    Path module = Files.createDirectories(myRoot.resolve("apps/web"));
    setUp(myRoot, module, "shop", "shop-web");
    assertEquals("rootProject.name = 'shop'\nincludeBuild 'shop-web'\ndef dir = 'shop-web'\n" +
                 "include 'shop-web'\nproject(':shop-web').projectDir = file('apps/web')\n",
                 read("settings.gradle"));
  }

  public void testLeavesLegacyPathAndRenameMappingAlone() throws Exception {
    String legacy = "rootProject.name = 'shop'\ninclude 'apps:web'\nfindProject(':apps:web')?.name = 'shop-web'\n";
    write("settings.gradle", legacy);
    Path module = Files.createDirectories(myRoot.resolve("apps/web"));
    setUp(myRoot, module, "shop", "shop-web");
    assertEquals(legacy, read("settings.gradle"));
  }

  public void testRecognizesIncludeAmongSeveralArguments() throws Exception {
    write("settings.gradle", "rootProject.name = 'shop'\ninclude ':core',\n        ':shop-web'\n");
    Path module = Files.createDirectories(myRoot.resolve("shop-web"));
    setUp(myRoot, module, "shop", "shop-web");
    assertEquals("rootProject.name = 'shop'\ninclude ':core',\n        ':shop-web'\n", read("settings.gradle"));
  }

  public void testUsesKotlinSyntaxForKotlinSettings() throws Exception {
    write("settings.gradle.kts", "rootProject.name = \"shop\"\n");
    Path module = Files.createDirectories(myRoot.resolve("modules/web"));
    setUp(myRoot, module, "shop", "web");
    assertEquals("rootProject.name = \"shop\"\ninclude(\"web\")\nproject(\":web\").projectDir = file(\"modules/web\")\n",
                 read("settings.gradle.kts"));
    assertFalse(new File(myRoot.toFile(), "settings.gradle").exists());
  }

  private static void setUp(Path root, Path module, String projectName, String moduleName) throws IOException {
    WriteAction.runAndWait(() -> GradleSettingsFile.setUp(root, module, projectName, moduleName));
  }

  private void write(String name, String text) throws IOException {
    Files.writeString(myRoot.resolve(name), text, StandardCharsets.UTF_8);
  }

  private String read(String name) throws IOException {
    return Files.readString(myRoot.resolve(name), StandardCharsets.UTF_8);
  }
}
