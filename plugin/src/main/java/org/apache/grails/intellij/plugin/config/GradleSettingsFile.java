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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes the {@code settings.gradle} a freshly created Grails module needs, the way the Gradle
 * plugin's module builder does for its own modules, minus the platform-internal API.
 * <p>
 * A missing settings file gets {@code rootProject.name}, plus an {@code include} when the module
 * does not sit at the Gradle root. An existing settings file keeps its content: {@code grails
 * create-app} may already have written it, and a second {@code rootProject.name} would only
 * override the first. Only the statements the module actually needs are appended - the
 * {@code include}, and the {@code projectDir} assignment when the module directory differs from
 * the Gradle project name - and each only when the file does not already declare it.
 * <p>
 * "Already declares it" is decided on statements rather than on raw text: a quoted occurrence of
 * the module name in a comment, in an {@code includeBuild}, or in an unrelated literal does not
 * count, and an {@code include} without the matching {@code projectDir} is completed instead of
 * being taken for a finished mapping. The form the platform's own builder used to write -
 * {@code include 'apps:web'} together with {@code findProject(':apps:web')?.name = 'shop-web'} -
 * maps name and directory in one go and is left untouched.
 */
final class GradleSettingsFile {

  static final String GROOVY_NAME = "settings.gradle";
  static final String KOTLIN_NAME = "settings.gradle.kts";

  /** The {@code include} keyword as a word of its own: not {@code includeBuild}, not {@code x.include}. */
  private static final Pattern INCLUDE_KEYWORD = Pattern.compile("(?<![\\w.$])include(?![\\w$])");

  /** {@code project(':name').projectDir = file('dir')}, in either DSL. */
  private static final Pattern PROJECT_DIR =
    Pattern.compile("\\bproject\\s*\\(\\s*(['\"])(.*?)\\1\\s*\\)\\s*\\.\\s*projectDir\\s*=");

  /** {@code findProject(':apps:web')?.name = 'shop-web'}: the rename half of the legacy mapping. */
  private static final Pattern PROJECT_RENAME =
    Pattern.compile("\\b(?:findProject|project)\\s*\\(\\s*(['\"])(.*?)\\1\\s*\\)\\s*\\??\\s*\\.\\s*name\\s*=\\s*(['\"])(.*?)\\3");

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
        text.append(includeLine(moduleName, false));
        if (!moduleDir.equals(moduleName)) text.append(projectDirLine(moduleName, moduleDir, false));
      }
      settings = rootDir.createChildData(GradleSettingsFile.class, GROOVY_NAME);
      VfsUtil.saveText(settings, text.toString());
      return;
    }

    if (moduleIsRoot) return; // the existing file already describes this project

    final boolean kotlinDsl = KOTLIN_NAME.equals(settings.getName());
    final String existing = VfsUtilCore.loadText(settings);
    final String code = withoutComments(existing);
    final List<String> included = includedProjectPaths(code);

    if (isLegacyMapping(code, included, moduleName, moduleDir)) return;

    final boolean hasInclude = included.contains(moduleName);
    final boolean needsProjectDir = !moduleDir.equals(moduleName);
    final boolean hasProjectDir = !needsProjectDir || declaresProjectDir(code, moduleName);
    if (hasInclude && hasProjectDir) return;

    final StringBuilder text = new StringBuilder(existing);
    if (!existing.isEmpty() && !existing.endsWith("\n")) text.append('\n');
    if (!hasInclude) text.append(includeLine(moduleName, kotlinDsl));
    if (!hasProjectDir) text.append(projectDirLine(moduleName, moduleDir, kotlinDsl));
    VfsUtil.saveText(settings, text.toString());
  }

  private static @NotNull String rootProjectNameLine(@NotNull String projectName, boolean kotlinDsl) {
    return "rootProject.name = " + quote(projectName, kotlinDsl);
  }

  private static @NotNull String includeLine(@NotNull String moduleName, boolean kotlinDsl) {
    return (kotlinDsl ? "include(" + quote(moduleName, true) + ")" : "include " + quote(moduleName, false)) + "\n";
  }

  private static @NotNull String projectDirLine(@NotNull String moduleName, @NotNull String moduleDir, boolean kotlinDsl) {
    return "project(" + quote(':' + moduleName, kotlinDsl) + ").projectDir = file(" + quote(moduleDir, kotlinDsl) + ")\n";
  }

  private static @NotNull String quote(@NotNull String value, boolean kotlinDsl) {
    return kotlinDsl ? '"' + value + '"' : "'" + value + "'";
  }

  /**
   * The legacy mapping the platform's {@code AbstractGradleModuleBuilder} wrote: the module is
   * included under its directory as a Gradle path and then renamed, which already fixes both the
   * name and the directory. Rewriting it with an {@code include} of the name plus a
   * {@code projectDir} would leave two projects pointing at the same directory.
   */
  private static boolean isLegacyMapping(@NotNull String code,
                                         @NotNull List<String> included,
                                         @NotNull String moduleName,
                                         @NotNull String moduleDir) {
    final String pathForDir = moduleDir.replace('/', ':');
    if (!included.contains(pathForDir)) return false;

    final Matcher matcher = PROJECT_RENAME.matcher(code);
    while (matcher.find()) {
      if (moduleName.equals(matcher.group(4)) && pathForDir.equals(projectPath(matcher.group(2)))) return true;
    }
    return false;
  }

  /**
   * Whether the file assigns a {@code projectDir} to the module's project, whatever directory it
   * names: the assignment already there wins over the one we would append.
   */
  private static boolean declaresProjectDir(@NotNull String code, @NotNull String moduleName) {
    final Matcher matcher = PROJECT_DIR.matcher(code);
    while (matcher.find()) {
      if (moduleName.equals(projectPath(matcher.group(2)))) return true;
    }
    return false;
  }

  /** The Gradle project paths of every {@code include} statement in {@code code}. */
  private static @NotNull List<String> includedProjectPaths(@NotNull String code) {
    final List<String> paths = new ArrayList<>();
    final Matcher matcher = INCLUDE_KEYWORD.matcher(code);
    while (matcher.find()) {
      if (startsStatement(code, matcher.start())) collectArguments(code, matcher.end(), paths);
    }
    return paths;
  }

  /** A keyword only opens a statement when nothing but whitespace precedes it on its line. */
  private static boolean startsStatement(@NotNull String code, int at) {
    for (int i = at - 1; i >= 0; i--) {
      final char c = code.charAt(i);
      if (c == ' ' || c == '\t') continue;
      return c == '\n' || c == '\r' || c == ';' || c == '{' || c == '}';
    }
    return true;
  }

  /**
   * Reads the string arguments of the statement starting at {@code from}, which ends at its
   * closing parenthesis when the call is parenthesized and at the first line end that is not a
   * comma continuation otherwise.
   */
  private static void collectArguments(@NotNull String code, int from, @NotNull List<String> paths) {
    boolean parenthesized = false;
    int depth = 0;
    char lastMeaningful = 0;
    int i = from;
    while (i < code.length()) {
      final char c = code.charAt(i);
      if (c == '\'' || c == '"') {
        final int end = endOfLiteral(code, i);
        paths.add(projectPath(code.substring(i + 1, Math.max(i + 1, end - 1))));
        lastMeaningful = c;
        i = end;
        continue;
      }
      if (c == '(') {
        if (depth == 0) parenthesized = true;
        depth++;
      }
      else if (c == ')') {
        depth--;
        if (parenthesized && depth <= 0) return;
      }
      else if (c == ';') {
        return;
      }
      else if (c == '\n' && depth == 0 && lastMeaningful != ',') {
        return;
      }
      if (!Character.isWhitespace(c)) lastMeaningful = c;
      i++;
    }
  }

  /** {@code ':apps:web'} and {@code 'apps:web'} name the same Gradle project. */
  private static @NotNull String projectPath(@NotNull String literal) {
    String path = literal.trim();
    while (path.startsWith(":")) path = path.substring(1);
    return path;
  }

  /**
   * Blanks out line and block comments, leaving string literals and line structure intact, so that
   * a module name mentioned in a comment cannot be mistaken for a statement.
   */
  private static @NotNull String withoutComments(@NotNull String text) {
    final StringBuilder out = new StringBuilder(text.length());
    int i = 0;
    while (i < text.length()) {
      final char c = text.charAt(i);
      if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
        while (i < text.length() && text.charAt(i) != '\n') i++;
        continue;
      }
      if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
        for (i += 2; i < text.length(); i++) {
          if (text.charAt(i) == '\n') out.append('\n'); // statements after the comment stay statements
          if (text.charAt(i) == '*' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
            i++;
            break;
          }
        }
        i++;
        continue;
      }
      if (c == '\'' || c == '"') {
        final int end = endOfLiteral(text, i);
        out.append(text, i, end);
        i = end;
        continue;
      }
      out.append(c);
      i++;
    }
    return out.toString();
  }

  /** The index just past the closing quote of the literal opening at {@code start}. */
  private static int endOfLiteral(@NotNull String text, int start) {
    final char quote = text.charAt(start);
    for (int i = start + 1; i < text.length(); i++) {
      final char c = text.charAt(i);
      if (c == '\\') {
        i++;
        continue;
      }
      if (c == quote) return i + 1;
      if (c == '\n') return i; // an unterminated literal ends with its line
    }
    return text.length();
  }
}
