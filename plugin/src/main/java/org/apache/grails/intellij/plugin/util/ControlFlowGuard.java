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
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Recognises an IntelliJ Platform control-flow exception, so a broad {@code catch} protecting an
 * optional code path cannot swallow one. {@code ProcessCanceledException} is a
 * {@code RuntimeException}, so such a catch would see it - and logging, wrapping or returning a
 * fallback from there breaks cooperative cancellation, which the platform also reports as a plugin
 * error.
 *
 * <p>Call {@link #rethrowIfControlFlow} first in a broad catch, <em>after</em> any state the catch
 * block is responsible for releasing; {@code GrailsBackgroundService.startNext()} is the in-repo
 * precedent for that ordering.
 */
public final class ControlFlowGuard {

  private ControlFlowGuard() {
  }

  /** Covers more than {@code ProcessCanceledException} alone: {@code ControlFlowException} is the whole rule. */
  public static boolean isControlFlow(@NotNull Throwable t) {
    return t instanceof ControlFlowException;
  }

  /**
   * Re-throws {@code t} unchanged and never wrapped when it is control flow, else returns. Declares
   * no checked exception, so it is usable from a {@code catch (RuntimeException)} and a
   * {@code catch (Throwable)} alike.
   */
  public static void rethrowIfControlFlow(@NotNull Throwable t) {
    if (isControlFlow(t)) {
      ExceptionUtil.rethrowUnchecked(t);
    }
  }

  /**
   * As {@link #rethrowIfControlFlow}, but also looks one level into the cause: a reflective call
   * cannot throw a control-flow exception directly, so the JVM delivers it wrapped in an
   * {@link java.lang.reflect.InvocationTargetException}.
   */
  public static void rethrowIfWrappedControlFlow(@NotNull Throwable t) {
    if (isControlFlow(t)) {
      ExceptionUtil.rethrowUnchecked(t);
    }
    Throwable cause = t.getCause();
    if (cause != null && isControlFlow(cause)) {
      ExceptionUtil.rethrowUnchecked(cause);
    }
  }
}
