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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.apache.grails.intellij.lib.testFramework.GrailsTestCase;
import org.apache.grails.intellij.plugin.references.GrailsMethodNamedArgumentReferenceProvider.Contributor;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The {@code org.intellij.grails.namedArgumentReferenceContributor} extension point is how an optional
 * content module (the Spring integration) adds its named-argument references to the shared provider.
 */
public class GrailsMethodNamedArgumentReferenceProviderTest extends GrailsTestCase {

  private static final class CountingContributor implements Contributor {
    private final AtomicInteger myRegistrations = new AtomicInteger();

    @Override
    public void register(@NotNull GrailsMethodNamedArgumentReferenceProvider registrar) {
      myRegistrations.incrementAndGet();
    }
  }

  public void testContributorsFromTheExtensionPointAreRegistered() {
    // build (or reuse) the instance first, which installs the extension-point change listener
    GrailsMethodNamedArgumentReferenceProvider.getInstance();

    CountingContributor contributor = new CountingContributor();
    GrailsMethodNamedArgumentReferenceProvider.EP_NAME.getPoint().registerExtension(contributor, getTestRootDisposable());

    GrailsMethodNamedArgumentReferenceProvider.getInstance();
    assertEquals("a contributor added after the first getInstance() must still be registered", 1, contributor.myRegistrations.get());

    GrailsMethodNamedArgumentReferenceProvider.getInstance();
    assertEquals("the rebuilt instance is cached until the extension point changes again", 1, contributor.myRegistrations.get());
  }

  public void testUnloadingAContributorRebuildsTheProviderWithoutIt() {
    GrailsMethodNamedArgumentReferenceProvider.getInstance();

    Disposable moduleLifetime = Disposer.newDisposable(getName());
    CountingContributor contributor = new CountingContributor();
    try {
      GrailsMethodNamedArgumentReferenceProvider.EP_NAME.getPoint().registerExtension(contributor, moduleLifetime);
      GrailsMethodNamedArgumentReferenceProvider withContributor = GrailsMethodNamedArgumentReferenceProvider.getInstance();
      assertEquals(1, contributor.myRegistrations.get());

      Disposer.dispose(moduleLifetime);
      moduleLifetime = null;

      GrailsMethodNamedArgumentReferenceProvider afterUnload = GrailsMethodNamedArgumentReferenceProvider.getInstance();
      assertNotSame("unloading the contributor's module must rebuild the provider", withContributor, afterUnload);
      assertEquals("the unloaded contributor must not be consulted again", 1, contributor.myRegistrations.get());
    }
    finally {
      if (moduleLifetime != null) Disposer.dispose(moduleLifetime);
    }
  }
}
