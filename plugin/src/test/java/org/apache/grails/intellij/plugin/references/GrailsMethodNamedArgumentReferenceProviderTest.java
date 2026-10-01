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

package org.apache.grails.intellij.plugin.references;

import com.intellij.openapi.progress.ProcessCanceledException;
import junit.framework.TestCase;
import org.apache.grails.intellij.plugin.references.GrailsMethodNamedArgumentReferenceProvider.Contributor;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class GrailsMethodNamedArgumentReferenceProviderTest extends TestCase {

  /**
   * ProviderProxy is private and exposes no seam, so the site under test - {@code ensureInit} - is
   * reached by reflection. Both {@code createRef} overloads call it as their first statement and
   * catch nothing, so calling it directly covers the same ground without standing in for the three
   * {@code @NotNull} PSI parameters, and without this test failing on a {@code createRef} signature
   * change it was never asserting anything about.
   */
  private static final String PROXY_CLASS_NAME =
    "org.apache.grails.intellij.plugin.references.GrailsMethodNamedArgumentReferenceProvider$ProviderProxy";

  private static final ProcessCanceledException CANCELLATION = new ProcessCanceledException();

  public static final class CancellingProvider extends Contributor.Provider {
    public CancellingProvider() {
      throw CANCELLATION;
    }
  }

  public static final class FailingProvider extends Contributor.Provider {
    public FailingProvider() {
      throw new IllegalStateException("provider is broken");
    }
  }

  public void testEnsureInitRethrowsCancellationUnwrapped() throws Exception {
    assertSame("a cancellation must arrive unchanged and never wrapped",
               CANCELLATION, ensureInitFailure(CancellingProvider.class));
  }

  public void testEnsureInitStillWrapsGenuineFailure() throws Exception {
    Throwable thrown = ensureInitFailure(FailingProvider.class);

    assertEquals("createRef has no throws clause, so a genuine failure must still be wrapped",
                 RuntimeException.class, thrown.getClass());
    assertTrue(thrown.getCause() instanceof InvocationTargetException);
    assertEquals(IllegalStateException.class, thrown.getCause().getCause().getClass());
  }

  private static Throwable ensureInitFailure(Class<? extends Contributor.Provider> providerClass) throws Exception {
    Class<?> proxyClass = Class.forName(PROXY_CLASS_NAME);
    Constructor<?> constructor = proxyClass.getDeclaredConstructor(Class.class);
    constructor.setAccessible(true);

    Method ensureInit = proxyClass.getDeclaredMethod("ensureInit");
    ensureInit.setAccessible(true);

    try {
      ensureInit.invoke(constructor.newInstance(providerClass));
    }
    catch (InvocationTargetException e) {
      return e.getCause();
    }
    fail("expected ensureInit() to propagate the provider constructor failure");
    return null;
  }
}
