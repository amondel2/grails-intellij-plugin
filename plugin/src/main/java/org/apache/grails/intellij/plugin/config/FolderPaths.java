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
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility for custom folder paths: normalizing them so that duplicates can be detected across common
 * path variations (trailing separators, leading "./" or ".\\", backslash vs forward slash), and
 * resolving them. A relative path is resolved against the Grails application root; an absolute path,
 * or one starting with {@code ~}, names a directory anywhere on the local file system.
 */
public final class FolderPaths {

  private FolderPaths() {
  }

  /**
   * Returns a normalized form of the given path suitable for duplicate comparison.
   * <p>
   * The normalization:
   * <ul>
   *   <li>trims surrounding whitespace</li>
   *   <li>removes leading "./" or ".\\" segments</li>
   *   <li>removes trailing "/" and "\\" separators</li>
   *   <li>converts remaining backslashes to forward slashes</li>
   * </ul>
   * This means "grails-app", "./grails-app", "grails-app/", "grails-app\\",
   * and ".\\grails-app" all normalize to the same key.
   */
  public static @NotNull String normalize(@NotNull String path) {
    String result = path.trim();

    while (result.startsWith("./") || result.startsWith(".\\")) {
      result = result.substring(2);
    }

    while (result.length() > 1 && (result.endsWith("/") || result.endsWith("\\"))) {
      result = result.substring(0, result.length() - 1);
    }

    return result.replace('\\', '/');
  }

  /**
   * Trims, drops blanks, and drops entries that {@link #normalize(String)} to a key already seen,
   * keeping the first spelling of each. Nothing is capped here: the cap belongs to the code that
   * renders the folders, not to the store, so lowering it can never delete a stored folder.
   */
  public static @NotNull List<String> dedupe(@NotNull Collection<String> raw) {
    List<String> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (String folder : raw) {
      if (folder == null) continue;
      String trimmed = folder.trim();
      if (trimmed.isEmpty()) continue;
      String normalized = normalize(trimmed);
      if (normalized.isEmpty()) continue;
      if (seen.add(normalized)) {
        result.add(trimmed);
      }
    }
    return result;
  }

  /** The last segment of the normalized path, which is what both custom-folder node kinds label themselves with. */
  public static @NotNull String folderName(@NotNull String path) {
    String normalized = normalize(path);
    int separator = normalized.lastIndexOf('/');
    return separator >= 0 ? normalized.substring(separator + 1) : normalized;
  }

  /**
   * Whether the configured path names a location on its own rather than one under the application
   * root: a Unix or Windows absolute path, a UNC path, or a path starting with {@code ~}.
   */
  public static boolean isAbsolute(@NotNull String path) {
    String normalized = normalize(path);
    return normalized.equals("~") || normalized.startsWith("~/") || OSAgnosticPathUtil.isAbsolute(normalized);
  }

  /**
   * The normalized path with a leading {@code ~} replaced by the user's home directory. Only an
   * absolute result is looked up on the local file system; anything else stays relative.
   */
  private static @NotNull String expand(@NotNull String path) {
    return normalize(OSAgnosticPathUtil.expandUserHome(normalize(path)));
  }

  /**
   * The directory, or file, the configured path names: an absolute path is looked up on the local
   * file system and a relative one under the application root, where {@code ..} may climb out of it.
   * Null when nothing exists there yet, or for a relative path when there is no root to read it against.
   */
  public static @Nullable VirtualFile find(@Nullable VirtualFile applicationRoot, @NotNull String configuredPath) {
    String path = expand(configuredPath);
    if (path.isEmpty()) return null;
    if (isAbsolute(configuredPath)) {
      return LocalFileSystem.getInstance().findFileByPath(path);
    }
    return applicationRoot == null ? null : applicationRoot.findFileByRelativePath(path);
  }

  /**
   * The canonical, system-independent path the configured path stands for under the given application
   * root, whether or not anything exists there. VFS events carry paths in this form, so a configured
   * folder appearing on disk is recognised by comparing against it.
   */
  public static @NotNull String toAbsolutePath(@NotNull String applicationRootPath, @NotNull String configuredPath) {
    String path = expand(configuredPath);
    String absolute = isAbsolute(configuredPath) ? path : applicationRootPath + "/" + path;
    return FileUtil.toCanonicalPath(absolute, '/', true);
  }

  /**
   * The path to store for a directory the user picked: relative to the application root when the
   * directory sits under it, so the configuration survives the project moving, and absolute otherwise.
   * Null for the application root itself, which is already the top of the Grails view.
   */
  public static @Nullable String toConfiguredPath(@Nullable VirtualFile applicationRoot, @NotNull VirtualFile directory) {
    if (applicationRoot != null && VfsUtilCore.isAncestor(applicationRoot, directory, false)) {
      String relative = VfsUtilCore.getRelativePath(directory, applicationRoot, '/');
      return relative == null || relative.isEmpty() ? null : relative;
    }
    return directory.getPath();
  }
}
