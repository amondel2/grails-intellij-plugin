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
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
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
import java.util.Comparator;
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
   * label they were renamed from. Any other lifted root is titled by {@link #phaseTitle}.
   */
  private static final Map<String, String> PHASE_TITLES =
    Map.of("test", "Tests:unit", "integration-test", "Tests:integration", "functional-test", "Tests:functional");
  /** The built-in phases in the order the pane lists them; custom phases follow, by directory name. */
  private static final List<String> PHASE_ORDER = List.of("test", "integration-test", "functional-test");
  /** How the Grails Gradle plugin lays out a phase declared in {@code testPhases}: {@code gebTest} lives in {@code src/geb-test}. */
  private static final String GRAILS_PHASE_SUFFIX = "-test";
  /** What a phase directory's name may end in: the Grails layout, or a Gradle test suite's ({@code src/gebTest}). */
  private static final List<String> PHASE_SUFFIXES = List.of(GRAILS_PHASE_SUFFIX, "Test");

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
        // Every lifted root weighs TESTS_FOLDER, so GrailsNodeComparator ties them without reaching the
        // platform comparator, and the platform's stable sort keeps the order findTestSourceDirectories
        // chose. Same class of tie as SRC_FOLDERS under src.
        result.add(new GrailsPsiDirectoryNode(testDir, settings, icon, NodeWeights.TESTS_FOLDER, phaseTitle(name),
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
   * The node title for a lifted directory: the Grails 2 phase name for the built-in roots, otherwise
   * {@code Tests:} and the directory name without its test suffix, so {@code geb-test} and {@code gebTest}
   * both read {@code Tests:geb}, while {@code integration-test-cli}, which ends in no suffix, keeps its name.
   */
  private static @NotNull String phaseTitle(@NotNull String directoryName) {
    String title = PHASE_TITLES.get(directoryName);
    if (title != null) return title;
    for (String suffix : PHASE_SUFFIXES) {
      if (directoryName.length() > suffix.length() && directoryName.endsWith(suffix)) {
        return "Tests:" + directoryName.substring(0, directoryName.length() - suffix.length());
      }
    }
    return "Tests:" + directoryName;
  }

  /**
   * Discovers the test phases to lift out of {@code src}: the direct children of {@code src} that
   * <ul>
   *   <li>are a built-in root ({@code test}, {@code integration-test}, {@code functional-test}) or end in
   *       {@code -test}, which is how the Grails Gradle plugin lays out a phase declared in
   *       {@code testPhases}. The name is what works before Gradle import, and after it too: the import
   *       registers a custom source set's directories as production roots unless the build marks the set
   *       as a test suite;</li>
   *   <li>hold a registered test source or test resource root, in whichever module. Gradle import puts
   *       every source set into a module of its own whose content root is the phase directory, so the
   *       module that owns {@code src} registers no roots at all, and asking only that module, as this
   *       used to, found no custom phase on an imported project. The index is asked about the registered
   *       root itself, never about the directory above it, so {@code src/generated/java} registered as
   *       SOURCE is not mistaken for a phase, and a phase registered as resources only
   *       ({@code src/smoke-test/resources} as TEST_RESOURCE) is still found.</li>
   * </ul>
   * The built-in phases come first, in their conventional order, then the others by directory name.
   */
  private static @NotNull List<PsiDirectory> findTestSourceDirectories(@NotNull PsiDirectory src) {
    ProjectFileIndex index = ProjectFileIndex.getInstance(src.getProject());
    VirtualFile srcFile = src.getVirtualFile();
    Set<VirtualFile> lifted = new HashSet<>();
    for (VirtualFile root : ProjectRootManager.getInstance(src.getProject()).getContentSourceRoots()) {
      if (!index.isInTestSourceContent(root)) continue;
      VirtualFile child = directChildOf(srcFile, root);
      if (child != null) lifted.add(child);
    }
    for (VirtualFile child : srcFile.getChildren()) {
      if (child.isDirectory() && isPhaseDirectoryName(child.getName())) lifted.add(child);
    }

    List<VirtualFile> ordered = new ArrayList<>(lifted);
    ordered.sort(Comparator.comparingInt((VirtualFile dir) -> phaseRank(dir.getName())).thenComparing(VirtualFile::getName));
    PsiManager psiManager = PsiManager.getInstance(src.getProject());
    List<PsiDirectory> result = new ArrayList<>();
    for (VirtualFile dir : ordered) {
      PsiDirectory directory = psiManager.findDirectory(dir);
      if (directory != null) result.add(directory);
    }
    return result;
  }

  /** Whether a directory under {@code src} is a phase by name alone: a built-in root, or the Grails {@code <phase>-test} layout. */
  private static boolean isPhaseDirectoryName(@NotNull String name) {
    return PHASE_TITLES.containsKey(name)
           || name.length() > GRAILS_PHASE_SUFFIX.length() && name.endsWith(GRAILS_PHASE_SUFFIX);
  }

  private static int phaseRank(@NotNull String name) {
    int rank = PHASE_ORDER.indexOf(name);
    return rank < 0 ? PHASE_ORDER.size() : rank;
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
