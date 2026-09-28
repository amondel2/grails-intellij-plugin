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

package org.apache.grails.intellij.plugin.structure;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.util.ThrowableRunnable;
import org.apache.grails.intellij.lib.testFramework.GrailsTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class GrailsApplicationProviderTest extends GrailsTestCase {

  private static final String LOGGER_CATEGORY = GrailsApplicationProvider.class.getName();

  private Disposable myProviderDisposable;

  @Override
  protected void tearDown() throws Exception {
    // GrailsTestCase.tearDown() queues an update while a throwing provider would still be registered,
    // so the re-throw under test would fire in tear-down instead of in the test body.
    try {
      if (myProviderDisposable != null) {
        Disposer.dispose(myProviderDisposable);
        myProviderDisposable = null;
      }
    }
    finally {
      super.tearDown();
    }
  }

  public void testCancelledProviderScanPropagatesAndIsNotLogged() throws Throwable {
    ProcessCanceledException cancelled = new ProcessCanceledException();
    registerProvider(new GrailsApplicationProvider() {
      @Override
      public @Nullable GrailsApplication createApplication(@NotNull Project project, @NotNull VirtualFile root) {
        throw cancelled;
      }
    });

    RecordingLoggedErrorProcessor processor = new RecordingLoggedErrorProcessor();
    AtomicBoolean rethrown = new AtomicBoolean();
    LoggedErrorProcessor.executeWith(processor, (ThrowableRunnable<RuntimeException>)() -> {
      try {
        GrailsApplicationProvider.createGrailsApplication(getProject(), notAGrailsRoot());
        rethrown.set(false);
      }
      catch (ProcessCanceledException pce) {
        assertSame("the PCE must be re-thrown unchanged", cancelled, pce);
        rethrown.set(true);
      }
    });

    assertEmpty("a cancelled scan must not be logged: " + processor.myRecords, processor.myRecords);
    assertTrue("expected the cancelled scan to unwind as a ProcessCanceledException", rethrown.get());
  }

  public void testGenuineProviderFailureIsLoggedAndTheRemainingProvidersStillRun() {
    GrailsApplicationProvider failing = new GrailsApplicationProvider() {
      @Override
      public @Nullable GrailsApplication createApplication(@NotNull Project project, @NotNull VirtualFile root) {
        throw new IllegalStateException("intentionally broken provider");
      }
    };
    AtomicBoolean laterProviderConsulted = new AtomicBoolean();
    registerProvider(failing);
    registerProvider(new GrailsApplicationProvider() {
      @Override
      public @Nullable GrailsApplication createApplication(@NotNull Project project, @NotNull VirtualFile root) {
        laterProviderConsulted.set(true);
        return null;
      }
    });

    RecordingLoggedErrorProcessor processor = new RecordingLoggedErrorProcessor();
    AtomicReference<GrailsApplication> application = new AtomicReference<>();
    VirtualFile root = notAGrailsRoot();
    LoggedErrorProcessor.executeWith(processor, (ThrowableRunnable<RuntimeException>)() ->
      application.set(GrailsApplicationProvider.createGrailsApplication(getProject(), root)));

    assertNull("no provider recognised the root, so a broken one must yield null", application.get());
    assertTrue("the providers after the failing one must still be consulted", laterProviderConsulted.get());
    assertEquals("a genuine failure must still be logged exactly once", 1, processor.myRecords.size());
    // The wording of the log line is GrailsBundleMessagesTest's business; what matters here is
    // that it is a WARN on this category rather than an ERROR.
    assertTrue("expected a WARN, got: " + processor.myRecords.get(0),
               processor.myRecords.get(0).startsWith("WARN "));
  }

  private void registerProvider(@NotNull GrailsApplicationProvider provider) {
    if (myProviderDisposable == null) {
      myProviderDisposable = Disposer.newDisposable(getName());
    }
    GrailsApplicationProvider.APPLICATION_PROVIDER.getPoint().registerExtension(provider, myProviderDisposable);
  }

  /** No shipped provider recognises this root, so only the providers a test registers can match it. */
  private @NotNull VirtualFile notAGrailsRoot() {
    try {
      return myFixture.getTempDirFixture().findOrCreateDir("notAGrailsRoot");
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Records what reaches the {@code GrailsApplicationProvider} logger. A {@link ProcessCanceledException}
   * is a {@code RuntimeException}, so the guard under test is the only thing keeping it out of here.
   */
  private static final class RecordingLoggedErrorProcessor extends LoggedErrorProcessor {
    private final List<String> myRecords = new ArrayList<>();

    @Override
    public @NotNull Set<Action> processError(@NotNull String category, @NotNull String message, String[] details, Throwable t) {
      record(category, "ERROR " + message);
      return EnumSet.noneOf(Action.class);
    }

    @Override
    public boolean processWarn(@NotNull String category, @NotNull String message, Throwable t) {
      record(category, "WARN " + message);
      return false;
    }

    private void record(@NotNull String category, @NotNull String record) {
      // Logger.getInstance(Class) registers the category with a leading '#', so match on the suffix.
      if (category.endsWith(LOGGER_CATEGORY)) {
        myRecords.add(record);
      }
    }
  }
}
