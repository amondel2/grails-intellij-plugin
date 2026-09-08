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

package org.apache.grails.intellij.plugin.service;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.apache.grails.intellij.lib.testFramework.GrailsTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class GrailsBackgroundServiceTest extends GrailsTestCase {

  public void testTasksRunInQueueOrderAndCompleteTheirLifecycle() {
    List<String> events = new ArrayList<>();
    GrailsBackgroundService service = GrailsBackgroundService.getInstance(getProject());
    for (int i = 1; i <= 3; i++) {
      service.run(new RecordingTask(getProject(), events, "t" + i));
    }
    // Backgroundable tasks run synchronously in unit-test mode, so the whole queue has drained here.
    assertEquals(List.of("t1.run", "t1.onSuccess", "t1.onFinished",
                         "t2.run", "t2.onSuccess", "t2.onFinished",
                         "t3.run", "t3.onSuccess", "t3.onFinished"), events);
  }

  public void testQueueKeepsAcceptingTasksAfterItDrained() {
    List<String> events = new ArrayList<>();
    GrailsBackgroundService service = GrailsBackgroundService.getInstance(getProject());
    service.run(new RecordingTask(getProject(), events, "first"));
    service.run(new RecordingTask(getProject(), events, "second"));
    assertEquals(List.of("first.run", "first.onSuccess", "first.onFinished",
                         "second.run", "second.onSuccess", "second.onFinished"), events);
  }

  private static final class RecordingTask extends Task.Backgroundable {
    private final List<String> myEvents;
    private final String myName;

    RecordingTask(Project project, List<String> events, String name) {
      super(project, name, false);
      myEvents = events;
      myName = name;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      myEvents.add(myName + ".run");
    }

    @Override
    public void onSuccess() {
      myEvents.add(myName + ".onSuccess");
    }

    @Override
    public void onFinished() {
      myEvents.add(myName + ".onFinished");
    }
  }
}
