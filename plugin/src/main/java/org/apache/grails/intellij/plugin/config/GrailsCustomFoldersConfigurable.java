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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.apache.grails.intellij.plugin.projectView.GrailsProjectViewPanes;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The single custom-folders settings page. It edits the global defaults (application service) and
 * this project's own folders (project service) together and previews the folders the Grails view
 * will actually show. The global defaults apply to every Grails project; this project's folders
 * are added to them.
 */
public class GrailsCustomFoldersConfigurable implements Configurable {

  private final @NotNull GrailsCustomFoldersAppService appService;
  private final @NotNull GrailsCustomFoldersProjectService projectService;
  private final @NotNull Project project;

  private @Nullable JPanel component;
  private @Nullable JBTextArea globalArea;
  private @Nullable JBTextArea overrideArea;
  private @Nullable JBTextArea effectiveArea;
  private @Nullable JBLabel sourceLabel;
  private @Nullable JBLabel warningLabel;

  public GrailsCustomFoldersConfigurable(@NotNull Project project) {
    this.project = project;
    this.appService = ApplicationManager.getApplication().getService(GrailsCustomFoldersAppService.class);
    this.projectService = project.getService(GrailsCustomFoldersProjectService.class);
  }

  @Override
  public @NotNull String getDisplayName() {
    return "Custom Folders Configuration";
  }

  @Override
  public @NotNull JComponent createComponent() {
    if (component == null) {
      globalArea = foldersArea(true);
      overrideArea = foldersArea(true);
      effectiveArea = foldersArea(false);

      DocumentAdapter refreshPreview = new DocumentAdapter() {
        @Override
        protected void textChanged(@NotNull DocumentEvent e) {
          updateEffectivePreview();
        }
      };
      globalArea.getDocument().addDocumentListener(refreshPreview);
      overrideArea.getDocument().addDocumentListener(refreshPreview);

      sourceLabel = new JBLabel();
      warningLabel = new JBLabel();
      warningLabel.setIcon(AllIcons.General.Warning);

      JBLabel description = new JBLabel(
        "<html>Relative paths are resolved against the project root; absolute paths are used as-is.<br>"
          + "The Grails view renders at most " + GrailsCustomFoldersProjectService.MAX_FOLDERS
          + " folders in total: this project's folders come first, then the global defaults.</html>");

      component = FormBuilder.createFormBuilder()
        .addComponent(description)
        .addComponent(warningLabel)
        .addLabeledComponent(new JBLabel("Global defaults (up to "
                                           + GrailsCustomFoldersProjectService.MAX_FOLDERS + " folders):"),
                             new JBScrollPane(globalArea), 1, false)
        .addLabeledComponent(new JBLabel("This project's folders (up to "
                                           + GrailsCustomFoldersProjectService.MAX_FOLDERS + " folders):"),
                             new JBScrollPane(overrideArea), 1, false)
        .addComponent(sourceLabel)
        .addLabeledComponent(new JBLabel("Effective folders in the Grails view:"),
                             new JBScrollPane(effectiveArea), 1, false)
        .getPanel();

      reset();
    }
    return component;
  }

  @Override
  public boolean isModified() {
    if (globalArea != null && !parseFolders(globalArea.getText()).equals(appService.getFolders())) return true;
    return overrideArea != null && !parseFolders(overrideArea.getText()).equals(projectService.getFolders());
  }

  @Override
  public void apply() throws ConfigurationException {
    // The global defaults have no change event, so repaint here: editing them changes what every
    // Grails project renders, and the view would otherwise keep the old folders until something
    // else triggered a refresh.
    if (globalArea != null) appService.setFolders(parseFolders(globalArea.getText()));
    if (overrideArea != null) projectService.setFolders(parseFolders(overrideArea.getText()));
    GrailsProjectViewPanes.refresh(project);
    updateEffectivePreview();
  }

  @Override
  public void reset() {
    if (globalArea != null) globalArea.setText(String.join("\n", appService.getFolders()));
    if (overrideArea != null) overrideArea.setText(String.join("\n", projectService.getFolders()));
    updateEffectivePreview();
  }

  @Override
  public void disposeUIResources() {
    component = null;
    globalArea = null;
    overrideArea = null;
    effectiveArea = null;
    sourceLabel = null;
    warningLabel = null;
  }

  private void updateEffectivePreview() {
    if (globalArea == null || overrideArea == null || effectiveArea == null || sourceLabel == null || warningLabel == null) return;
    List<String> effective = previewFolders(globalArea.getText(), overrideArea.getText());
    effectiveArea.setText(String.join("\n", effective));
    sourceLabel.setText(parseFolders(overrideArea.getText()).isEmpty()
                          ? "Using global defaults only"
                          : "Using this project's folders plus global defaults");

    String warning = warningMessage(globalArea.getText(), overrideArea.getText());
    warningLabel.setText(warning);
    warningLabel.setVisible(!warning.isEmpty());
  }

  /**
   * Returns a warning message when the user has entered more unique folders than
   * {@link GrailsCustomFoldersProjectService#MAX_FOLDERS} across both areas combined, or an empty
   * string when the combined count is within the limit. Duplicates (after
   * {@link FolderPaths#normalize(String)}) count once, matching {@link #parseFolders(String)}. The
   * Grails view shows at most {@link GrailsCustomFoldersProjectService#MAX_FOLDERS} folders in
   * total, so the combined count is what matters.
   */
  /**
   * The folders the Grails view would render for the given text, in the same order and with the
   * same cap as {@link GrailsCustomFoldersProjectService#getEffectiveFolders()}. Derived from the
   * text areas rather than from the services, so the preview tracks what the user has typed.
   */
  static @NotNull List<String> previewFolders(@NotNull String globalText, @NotNull String overrideText) {
    return GrailsCustomFoldersProjectService.combine(parseFolders(overrideText), parseFolders(globalText));
  }

  static @NotNull String warningMessage(@NotNull String globalText, @NotNull String overrideText) {
    int max = GrailsCustomFoldersProjectService.MAX_FOLDERS;
    Set<String> seen = new HashSet<>();
    int combined = 0;
    for (String path : uniqueNormalized(overrideText)) {
      if (seen.add(path)) combined++;
    }
    for (String path : uniqueNormalized(globalText)) {
      if (seen.add(path)) combined++;
    }
    if (combined > max) {
      return "You have entered " + combined + " unique folders. Only the first " + max + " will be used.";
    }
    return "";
  }

  private static @NotNull List<String> uniqueNormalized(@NotNull String text) {
    List<String> unique = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (String line : text.split("\n", -1)) {
      String path = line.trim();
      if (path.isEmpty()) continue;
      String normalized = FolderPaths.normalize(path);
      if (normalized.isEmpty()) continue;
      if (seen.add(normalized)) {
        unique.add(normalized);
      }
    }
    return unique;
  }

  private static @NotNull JBTextArea foldersArea(boolean editable) {
    JBTextArea area = new JBTextArea(GrailsCustomFoldersProjectService.MAX_FOLDERS, 40);
    area.setEditable(editable);
    area.setLineWrap(false);
    return area;
  }

  /**
   * Parses folder paths from the given text, dropping blank lines and duplicates
   * (using {@link FolderPaths#normalize(String)} for comparison), and limiting the
   * result to {@link GrailsCustomFoldersProjectService#MAX_FOLDERS} entries.
   */
  static @NotNull List<String> parseFolders(@NotNull String text) {
    List<String> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (String line : text.split("\n", -1)) {
      String path = line.trim();
      if (path.isEmpty()) continue;
      String normalized = FolderPaths.normalize(path);
      if (normalized.isEmpty()) continue;
      if (seen.add(normalized)) {
        result.add(path);
      }
      if (result.size() >= GrailsCustomFoldersProjectService.MAX_FOLDERS) break;
    }
    return result;
  }
}
