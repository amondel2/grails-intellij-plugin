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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Writes the {@code settings.gradle} a freshly created Grails module needs, the way the Gradle
 * plugin's module builder does for its own modules, minus the platform-internal API.
 * <p>
 * A missing settings file gets {@code rootProject.name}, plus an {@code include} when the module
 * does not sit at the Gradle root. An existing settings file is left alone except for that
 * {@code include}, which is appended once: {@code grails create-app} may already have written the
 * file, and a second {@code rootProject.name} would only override the first.
 */
final class GradleSettingsFile {

  static final String GROOVY_NAME = "settings.gradle";
  static final String KOTLIN_NAME = "settings.gradle.kts";

  private GradleSettingsFile() {
  }

  /**
   * @param rootProjectPath the Gradle root project directory
   * @param moduleRoot      the module's content root, equal to or below {@code rootProjectPath}
   * @param projectName     the value for {@code rootProject.name} when the file is created
   * @param moduleName      the Gradle project name of the module
   */
  static void setUp(@NotNull Path rootProjectPath, @NotNull Path moduleRoot, @NotNull String projectName, @NotNull String moduleName)
    throws IOException {
    final VirtualFile rootDir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(rootProjectPath);
    if (rootDir == null || !rootDir.isDirectory()) {
      throw new IOException("Gradle root project directory not found: " + rootProjectPath);
    }

    final Path relative = rootProjectPath.toAbsolutePath().normalize().relativize(moduleRoot.toAbsolutePath().normalize());
    final String moduleDir = FileUtil.toSystemIndependentName(relative.toString());
    final boolean moduleIsRoot = moduleDir.isEmpty();

    VirtualFile settings = rootDir.findChild(KOTLIN_NAME);
    if (settings == null) settings = rootDir.findChild(GROOVY_NAME);

    if (settings == null) {
      final StringBuilder text = new StringBuilder();
      text.append(rootProjectNameLine(projectName, false)).append('\n');
      if (!moduleIsRoot) {
        text.append(includeLines(moduleName, moduleDir, false));
      }
      settings = rootDir.createChildData(GradleSettingsFile.class, GROOVY_NAME);
      VfsUtil.saveText(settings, text.toString());
      return;
    }

    if (moduleIsRoot) return; // the existing file already describes this project

    final boolean kotlinDsl = KOTLIN_NAME.equals(settings.getName());
    final String existing = VfsUtilCore.loadText(settings);
    if (existing.contains("'" + moduleName + "'") || existing.contains("\"" + moduleName + "\"")) return; // already included

    final StringBuilder text = new StringBuilder(existing);
    if (!existing.isEmpty() && !existing.endsWith("\n")) text.append('\n');
    text.append(includeLines(moduleName, moduleDir, kotlinDsl));
    VfsUtil.saveText(settings, text.toString());
  }

  private static @NotNull String rootProjectNameLine(@NotNull String projectName, boolean kotlinDsl) {
    return "rootProject.name = " + quote(projectName, kotlinDsl);
  }

  private static @NotNull String includeLines(@NotNull String moduleName, @NotNull String moduleDir, boolean kotlinDsl) {
    final StringBuilder lines = new StringBuilder();
    lines.append(kotlinDsl ? "include(" + quote(moduleName, true) + ")" : "include " + quote(moduleName, false)).append('\n');
    if (!moduleDir.equals(moduleName)) {
      lines.append("project(").append(quote(':' + moduleName, kotlinDsl)).append(").projectDir = file(")
        .append(quote(moduleDir, kotlinDsl)).append(")\n");
    }
    return lines.toString();
  }

  private static @NotNull String quote(@NotNull String value, boolean kotlinDsl) {
    return kotlinDsl ? '"' + value + '"' : "'" + value + "'";
  }
}
