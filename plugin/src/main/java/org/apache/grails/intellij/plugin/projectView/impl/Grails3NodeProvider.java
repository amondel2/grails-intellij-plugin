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

package org.apache.grails.intellij.plugin.projectView.impl;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileSystemItemFilter;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.apache.grails.intellij.plugin.GroovyMvcIcons;
import org.apache.grails.intellij.plugin.projectView.GrailsPluginsNode;
import org.apache.grails.intellij.plugin.projectView.NodeWeights;
import org.apache.grails.intellij.plugin.projectView.api.GrailsViewNodeProvider;
import org.apache.grails.intellij.plugin.projectView.nodes.GrailsPsiDirectoryNode;
import org.apache.grails.intellij.plugin.structure.GrailsApplication;
import org.apache.grails.intellij.plugin.util.version.Version;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.Icon;

public class Grails3NodeProvider implements GrailsViewNodeProvider {

  private static final List<String> SPECIAL_FILES = List.of("build.gradle", "settings.gradle", "gradle.properties");
  private static final List<String> SPECIAL_DIRS = List.of("src/main/scripts", "src/main/webapp");

  /**
   * The built-in Grails 3+ test source roots directly under {@code src}, keyed by the Grails 2 phase
   * label they were renamed from. Any other lifted root is titled {@code "Tests:" + directoryName}.
   */
  private static final Map<String, String> PHASE_TITLES =
    Map.of("test", "Tests:unit", "integration-test", "Tests:integration", "functional-test", "Tests:functional");

  @Override
  public @NotNull Collection<AbstractTreeNode<?>> createNodes(@NotNull GrailsApplication application,
                                                              @NotNull ViewSettings settings) {
    if (application.getGrailsVersion().compareTo(Version.GRAILS_3_0) < 0) {
      return List.of();
    }

    Collection<AbstractTreeNode<?>> result = new ArrayList<>();

    List<PsiDirectory> specialDirs = new ArrayList<>();
    for (String path : SPECIAL_DIRS) {
      PsiDirectory directory = GrailsViewItems.findPsiDirectory(application, path);
      if (directory != null) specialDirs.add(directory);
    }
    for (PsiDirectory directory : specialDirs) {
      result.add(new GrailsPsiDirectoryNode(directory, settings, NodeWeights.SRC_FOLDERS));
    }

    PsiDirectory src = GrailsViewItems.findPsiDirectory(application, "src");
    if (src != null) {
      List<PsiDirectory> testDirs = findTestSourceDirectories(src);
      // Directories that get their own node, so src can refuse to claim them. PsiDirectoryNode.contains()
      // applies a node's filter to the file itself only, never to its parent, so hiding the directories
      // from src is not enough: src would still claim their contents, and Reveal in Project View would
      // expand src and dead-end. isAncestor(dir, dir, false) is true, so one check covers the directories.
      Set<VirtualFile> lifted = liftedDirectories(specialDirs, testDirs);
      PsiFileSystemItemFilter filter = item -> !GrailsViewItems.isUnder(lifted, item.getVirtualFile())
        && GrailsViewItems.shouldShowItem(item);
      result.add(new GrailsPsiDirectoryNode(src, settings, NodeWeights.SRC_FOLDERS, filter));

      VirtualFile projectRoot = src.getVirtualFile().getParent();
      for (PsiDirectory testDir : testDirs) {
        String name = testDir.getName();
        Icon icon = "test".equals(name) ? PlatformIcons.TEST_SOURCE_FOLDER : GroovyMvcIcons.Grails_test;
        // Every lifted root weighs TESTS_FOLDER, so two custom phases tie at 0 and their relative order
        // is unspecified: GrailsNodeComparator returns the weight difference without reaching the
        // platform comparator. Same class of tie as SRC_FOLDERS under src.
        result.add(new GrailsPsiDirectoryNode(testDir, settings, icon, NodeWeights.TESTS_FOLDER,
                                             PHASE_TITLES.getOrDefault(name, "Tests:" + name),
                                             GrailsViewItems::shouldShowItem,
                                             VfsUtilCore.getRelativePath(testDir.getVirtualFile(), projectRoot, '/')));
      }
    }

    for (String path : SPECIAL_FILES) {
      PsiFile file = GrailsViewItems.findPsiFile(application, path);
      if (file != null) {
        result.add(new PsiFileNode(application.getProject(), file, settings));
      }
    }

    result.add(new GrailsPluginsNode(application.getProject(), settings));
    return result;
  }

  /**
   * Discovers the test source roots to lift out of {@code src}. A custom phase is a direct child of
   * {@code src} that holds a registered test source or test resource root. The index is asked about
   * the registered root itself, never about the directory above it, so a production root registered
   * under {@code src} ({@code src/generated/java} as SOURCE) is not mistaken for a phase, and a
   * phase registered as resources only ({@code src/smoke-test/resources} as TEST_RESOURCE) is
   * still found. The conventional names are the fallback for a phase the module does not register,
   * which is common before Gradle import.
   */
  private static @NotNull List<PsiDirectory> findTestSourceDirectories(@NotNull PsiDirectory src) {
    ProjectFileIndex index = ProjectFileIndex.getInstance(src.getProject());
    VirtualFile srcFile = src.getVirtualFile();
    PsiManager psiManager = PsiManager.getInstance(src.getProject());
    List<PsiDirectory> result = new ArrayList<>();
    Set<VirtualFile> lifted = new HashSet<>();
    Module module = index.getModuleForFile(srcFile);
    if (module != null) {
      for (VirtualFile root : ModuleRootManager.getInstance(module).getSourceRoots()) {
        if (!index.isInTestSourceContent(root)) continue;
        VirtualFile child = directChildOf(srcFile, root);
        if (child != null && lifted.add(child)) {
          PsiDirectory directory = psiManager.findDirectory(child);
          if (directory != null) result.add(directory);
        }
      }
    }
    for (VirtualFile child : srcFile.getChildren()) {
      if (child.isDirectory() && PHASE_TITLES.containsKey(child.getName()) && lifted.add(child)) {
        PsiDirectory directory = psiManager.findDirectory(child);
        if (directory != null) result.add(directory);
      }
    }
    return result;
  }

  /** The direct child of {@code src} that contains {@code file}, or null when there is none. */
  @Nullable
  private static VirtualFile directChildOf(@NotNull VirtualFile src, @NotNull VirtualFile file) {
    VirtualFile current = file;
    while (current != null && !current.equals(src)) {
      VirtualFile parent = current.getParent();
      if (parent != null && parent.equals(src)) return current;
      current = parent;
    }
    return null;
  }

  /** The directories that are rendered as their own node, so {@code src} can refuse to claim them. */
  private static @NotNull Set<VirtualFile> liftedDirectories(@NotNull List<PsiDirectory> specialDirs,
                                                             @NotNull List<PsiDirectory> testDirs) {
    Set<VirtualFile> result = new HashSet<>();
    for (PsiDirectory directory : specialDirs) result.add(directory.getVirtualFile());
    for (PsiDirectory directory : testDirs) result.add(directory.getVirtualFile());
    return result;
  }
}
