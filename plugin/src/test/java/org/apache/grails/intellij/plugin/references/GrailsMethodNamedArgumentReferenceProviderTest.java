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
import com.intellij.psi.PsiElement;
import junit.framework.TestCase;
import org.apache.grails.intellij.plugin.references.GrailsMethodNamedArgumentReferenceProvider.Contributor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class GrailsMethodNamedArgumentReferenceProviderTest extends TestCase {

  // ProviderProxy is private and exposes no seam, so the site is reached the way production
  // reaches it: let createRef() call the failing provider constructor through the proxy.
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

  public void testCreateRefRethrowsCancellationUnwrapped() throws Exception {
    assertSame(CANCELLATION, createRefFailure(CancellingProvider.class));
  }

  public void testCreateRefStillWrapsGenuineFailure() throws Exception {
    Throwable thrown = createRefFailure(FailingProvider.class);

    assertEquals(RuntimeException.class, thrown.getClass());
    assertTrue(thrown.getCause() instanceof InvocationTargetException);
    assertEquals(IllegalStateException.class, thrown.getCause().getCause().getClass());
  }

  private static Throwable createRefFailure(Class<? extends Contributor.Provider> providerClass) throws Exception {
    Class<?> proxyClass = Class.forName(PROXY_CLASS_NAME);
    Constructor<?> constructor = proxyClass.getDeclaredConstructor(Class.class);
    constructor.setAccessible(true);

    Method createRef = proxyClass.getDeclaredMethod("createRef", PsiElement.class, GrNamedArgument.class, GroovyResolveResult.class);
    createRef.setAccessible(true);

    try {
      createRef.invoke(constructor.newInstance(providerClass),
                       new Object[] {unused(PsiElement.class), unused(GrNamedArgument.class), unused(GroovyResolveResult.class)});
    }
    catch (InvocationTargetException e) {
      return e.getCause();
    }
    fail("expected createRef() to propagate the provider constructor failure");
    return null;
  }

  /**
   * createRef's parameters are {@code @NotNull}, so the injected check rejects null before the body
   * runs - and ensureInit(), the method under test, is the body's first statement. An inert
   * non-null stand-in therefore satisfies the check and is never dereferenced.
   */
  private static Object unused(Class<?> anInterface) {
    return Proxy.newProxyInstance(anInterface.getClassLoader(), new Class<?>[]{anInterface}, (proxy, method, args) -> null);
  }
}
