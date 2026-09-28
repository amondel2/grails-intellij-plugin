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

package org.apache.grails.intellij.plugin.util;

import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.progress.ProcessCanceledException;
import junit.framework.TestCase;

/**
 * Covers the guard-first rule in {@link UltimatePluginGuard}'s reflective reads. A throwing static
 * initialiser is the only way to put a control-flow exception into that code, and the JVM wraps a
 * non-{@link Error} thrown from {@code <clinit>} in an {@link ExceptionInInitializerError} before
 * {@code Class.forName} can report it - so the doubles below are {@code Error}s, which arrive
 * unchanged, and {@code ProcessCanceledException} could not be used here at all.
 */
public class UltimatePluginGuardTest extends TestCase {

  /**
   * Stands in for the platform's control-flow exceptions, which are all
   * {@link java.util.concurrent.CancellationException}s and therefore not {@code Error}s.
   */
  private static final class ControlFlowError extends Error implements ControlFlowException {
  }

  /** Each of these is initialised at most once per JVM: a failed initialisation is permanent. */
  public static final class CancelledStaticField {
    public static final String VALUE = cancelled();

    public static void touch() {
    }

    private static String cancelled() {
      throw new ControlFlowError();
    }
  }

  public static final class CancelledStaticMethod {
    public static final String VALUE = cancelled();

    public static void touch() {
    }

    private static String cancelled() {
      throw new ControlFlowError();
    }
  }

  public static final class ProcessCanceledStaticField {
    public static final String VALUE = cancelled();

    private static String cancelled() {
      throw new ProcessCanceledException();
    }
  }

  public void testStaticFieldValueRethrowsControlFlowException() {
    try {
      UltimatePluginGuard.staticFieldValue(CancelledStaticField.class.getName(), "VALUE", "FALLBACK");
      fail("expected the control-flow exception to propagate instead of the fallback");
    }
    catch (ControlFlowError expected) {
      // arrived unchanged, and the guard's LOG.warn/fallback pair never ran
    }
  }

  public void testInvokeStaticIfAvailableRethrowsControlFlowException() {
    try {
      UltimatePluginGuard.invokeStaticIfAvailable(CancelledStaticMethod.class.getName(), "touch");
      fail("expected the control-flow exception to propagate instead of being swallowed");
    }
    catch (ControlFlowError expected) {
      // arrived unchanged, and the guard's silent LOG.debug never ran
    }
  }

  public void testGenuineFailureStillUsesTheFallback() {
    assertEquals("FALLBACK",
                 UltimatePluginGuard.staticFieldValue("no.such.UltimatePluginGuardFixture", "FIELD", "FALLBACK"));
    UltimatePluginGuard.invokeStaticIfAvailable("no.such.UltimatePluginGuardFixture", "touch");
  }

  public void testProcessCanceledExceptionFromStaticInitialiserIsUnreachable() {
    // Documents the premise of the two tests above, so neither is "simplified" back to a
    // ProcessCanceledException: here the guard sees the JVM's wrapper, not the cancellation, and
    // the only honest outcome is the fallback.
    assertEquals("FALLBACK",
                 UltimatePluginGuard.staticFieldValue(ProcessCanceledStaticField.class.getName(), "VALUE", "FALLBACK"));
  }
}
