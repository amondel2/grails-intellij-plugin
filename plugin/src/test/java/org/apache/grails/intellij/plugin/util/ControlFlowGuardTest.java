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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

public class ControlFlowGuardTest extends TestCase {

  /** A control-flow exception that is not a ProcessCanceledException. */
  private static final class NotACancelled extends RuntimeException implements ControlFlowException {
  }

  public void testIsControlFlow() {
    assertTrue(ControlFlowGuard.isControlFlow(new ProcessCanceledException()));
    assertTrue(ControlFlowGuard.isControlFlow(new NotACancelled()));
    assertFalse(ControlFlowGuard.isControlFlow(new RuntimeException()));
    assertFalse(ControlFlowGuard.isControlFlow(new IllegalStateException()));
    assertFalse(ControlFlowGuard.isControlFlow(new IOException()));
  }

  public void testRethrowProcessCanceledException() {
    ProcessCanceledException pce = new ProcessCanceledException();
    try {
      ControlFlowGuard.rethrowIfControlFlow(pce);
      fail("expected the PCE to be re-thrown");
    }
    catch (Throwable t) {
      // unchanged and never wrapped
      assertSame(pce, t);
    }
  }

  public void testRethrowNonPceControlFlowException() {
    NotACancelled controlFlow = new NotACancelled();
    try {
      ControlFlowGuard.rethrowIfControlFlow(controlFlow);
      fail("expected the control-flow exception to be re-thrown");
    }
    catch (Throwable t) {
      assertSame(controlFlow, t);
    }
  }

  public void testDoesNotRethrowGenuineFailures() {
    ControlFlowGuard.rethrowIfControlFlow(new RuntimeException());
    ControlFlowGuard.rethrowIfControlFlow(new IllegalStateException());
    ControlFlowGuard.rethrowIfControlFlow(new IOException());
  }

  public void testRethrowWrappedControlFlow() {
    ProcessCanceledException pce = new ProcessCanceledException();
    try {
      ControlFlowGuard.rethrowIfWrappedControlFlow(new InvocationTargetException(pce));
      fail("expected the unwrapped PCE to be re-thrown");
    }
    catch (Throwable t) {
      assertSame(pce, t);
    }
  }

  public void testRethrowWrappedControlFlowWithDirectControlFlow() {
    NotACancelled controlFlow = new NotACancelled();
    try {
      ControlFlowGuard.rethrowIfWrappedControlFlow(controlFlow);
      fail("expected the control-flow exception to be re-thrown");
    }
    catch (Throwable t) {
      assertSame(controlFlow, t);
    }
  }

  public void testDoesNotRethrowWrappedGenuineFailure() {
    ControlFlowGuard.rethrowIfWrappedControlFlow(new InvocationTargetException(new IllegalStateException()));
  }
}
