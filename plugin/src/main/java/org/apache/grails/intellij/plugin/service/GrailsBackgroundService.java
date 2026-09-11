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

import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Runs the plugin's backgroundable tasks one at a time, in the order they were queued.
 * <p>
 * Each task is started through {@link ProgressManager#run(Task)} only once the previous one has
 * finished, so two structure-sync tasks never race each other. In unit-test mode the platform runs
 * backgroundable tasks synchronously, and so does this queue.
 */
@Service(Service.Level.PROJECT)
public final class GrailsBackgroundService {
  private final Queue<Task.Backgroundable> myQueue = new ArrayDeque<>();
  private boolean myRunning;

  public GrailsBackgroundService(@SuppressWarnings("unused") Project project) {
  }

  public void run(@NotNull Task.Backgroundable task) {
    synchronized (myQueue) {
      myQueue.add(task);
      if (myRunning) return;
      myRunning = true;
    }
    startNext();
  }

  private void startNext() {
    final Task.Backgroundable next;
    synchronized (myQueue) {
      next = myQueue.poll();
      if (next == null) {
        myRunning = false;
        return;
      }
    }
    try {
      ProgressManager.getInstance().run(new Sequenced(next));
    }
    catch (Throwable t) {
      // run() hands the task off and Sequenced.onFinished() drives the queue from there. A
      // synchronous failure - a disposed project, say - means that callback never arrives, so the
      // queue has to be released here or nothing queued afterwards would ever start.
      synchronized (myQueue) {
        myRunning = false;
      }
      throw t;
    }
  }

  public static @NotNull GrailsBackgroundService getInstance(@NotNull Project project) {
    return project.getService(GrailsBackgroundService.class);
  }

  /** Delegates everything to the queued task and starts the next one when it is done. */
  private final class Sequenced extends Task.Backgroundable {
    private final Task.Backgroundable myDelegate;

    Sequenced(@NotNull Task.Backgroundable delegate) {
      super(delegate.getProject(), delegate.getTitle(), delegate.isCancellable());
      myDelegate = delegate;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      myDelegate.run(indicator);
    }

    @Override
    public void onSuccess() {
      myDelegate.onSuccess();
    }

    @Override
    public void onCancel() {
      myDelegate.onCancel();
    }

    @Override
    public void onThrowable(@NotNull Throwable error) {
      myDelegate.onThrowable(error);
    }

    @Override
    public void onFinished() {
      try {
        myDelegate.onFinished();
      }
      finally {
        startNext();
      }
    }

    @Override
    public boolean shouldStartInBackground() {
      return myDelegate.shouldStartInBackground();
    }

    @Override
    public boolean isConditionalModal() {
      return myDelegate.isConditionalModal();
    }
  }
}
