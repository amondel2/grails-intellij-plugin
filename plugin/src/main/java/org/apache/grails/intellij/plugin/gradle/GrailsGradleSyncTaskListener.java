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
package org.apache.grails.intellij.plugin.gradle;

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * Feeds the outcome of every Gradle project resolve into {@link GrailsGradleSyncStatus}. The resolve
 * is the phase a broken build script fails in, before any project data reaches the IDE; the data
 * import that follows a successful resolve reports through {@code ProjectDataImportListener}.
 */
public final class GrailsGradleSyncTaskListener implements ExternalSystemTaskNotificationListener {

  @Override
  public void onStart(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
    GrailsGradleSyncStatus status = status(id);
    if (status != null) status.syncStarted(projectPath);
  }

  @Override
  public void onSuccess(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
    GrailsGradleSyncStatus status = status(id);
    if (status != null) status.recordSuccess(projectPath);
  }

  @Override
  public void onFailure(@NotNull String projectPath, @NotNull ExternalSystemTaskId id, @NotNull Exception exception) {
    GrailsGradleSyncStatus status = status(id);
    if (status != null) status.recordFailure(projectPath, exception.getMessage());
  }

  @Override
  public void onCancel(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
    GrailsGradleSyncStatus status = status(id);
    if (status != null) status.syncCancelled(projectPath);
  }

  private static @Nullable GrailsGradleSyncStatus status(@NotNull ExternalSystemTaskId id) {
    if (id.getType() != ExternalSystemTaskType.RESOLVE_PROJECT || !GradleConstants.SYSTEM_ID.equals(id.getProjectSystemId())) {
      return null;
    }
    Project project = id.findProject();
    return project == null || project.isDisposed() ? null : GrailsGradleSyncStatus.getInstance(project);
  }
}
