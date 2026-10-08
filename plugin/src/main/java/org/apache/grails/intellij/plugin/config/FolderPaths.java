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

import org.jetbrains.annotations.NotNull;

/**
 * Utility for normalizing folder paths so that duplicates can be detected
 * across common path variations (trailing separators, leading "./" or ".\\",
 * backslash vs forward slash).
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
}
