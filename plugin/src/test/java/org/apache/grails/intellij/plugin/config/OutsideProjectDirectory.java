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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * A real directory on the local file system, outside the light fixture's project (whose content lives
 * on the in-memory {@code temp://} file system), for custom folders configured by absolute path. It
 * sits under the system temp directory, which the test framework's VFS root check allows, and is
 * deleted with the given disposable.
 */
public final class OutsideProjectDirectory {

  private OutsideProjectDirectory() {
  }

  /** Creates the directory holding the given files (paths relative to it, parents created) and loads it into the VFS. */
  public static @NotNull VirtualFile create(@NotNull Disposable disposable, @NotNull String name, @NotNull String... files) {
    File dir = new File(FileUtil.getTempDirectory(), "grails-custom-folders-" + name);
    FileUtil.delete(dir);
    FileUtil.createDirectory(dir);
    try {
      for (String file : files) {
        FileUtil.writeToFile(new File(dir, file), "");
      }
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    Disposer.register(disposable, () -> {
      FileUtil.delete(dir);
      LocalFileSystem.getInstance().refreshIoFiles(List.of(dir));
    });

    VirtualFile directory = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
    if (directory == null) throw new IllegalStateException("the VFS cannot see " + dir);
    VfsUtil.markDirtyAndRefresh(false, true, true, directory);
    return directory;
  }
}
