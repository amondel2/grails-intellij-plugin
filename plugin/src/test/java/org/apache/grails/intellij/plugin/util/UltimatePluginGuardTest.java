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

import junit.framework.TestCase;

public class UltimatePluginGuardTest extends TestCase {

  /** A class whose static initialiser fails, which the JVM reports as a {@link LinkageError}. */
  public static final class BrokenInitialiser {
    public static final String VALUE = broken();

    private static String broken() {
      throw new IllegalStateException("static initialiser failed on purpose");
    }
  }

  public static final class Fixture {
    public static final String VALUE = "present";
    public static final String NULL_VALUE = null;
  }

  public void testReadsAPresentField() {
    assertEquals("present", UltimatePluginGuard.staticFieldValue(Fixture.class.getName(), "VALUE", "FALLBACK"));
  }

  public void testPresentFieldIsCachedAndReturnedAgain() {
    assertEquals("present", UltimatePluginGuard.staticFieldValue(Fixture.class.getName(), "VALUE", "FALLBACK"));
    assertEquals("present", UltimatePluginGuard.staticFieldValue(Fixture.class.getName(), "VALUE", "OTHER"));
  }

  public void testMissingClassUsesTheFallback() {
    assertEquals("FALLBACK",
                 UltimatePluginGuard.staticFieldValue("no.such.UltimatePluginGuardFixture", "FIELD", "FALLBACK"));
  }

  public void testMissingFieldUsesTheFallback() {
    assertEquals("FALLBACK", UltimatePluginGuard.staticFieldValue(Fixture.class.getName(), "NO_SUCH_FIELD", "FALLBACK"));
  }

  public void testNullFieldValueUsesTheFallback() {
    assertEquals("FALLBACK", UltimatePluginGuard.staticFieldValue(Fixture.class.getName(), "NULL_VALUE", "FALLBACK"));
  }

  public void testFailedStaticInitialiserUsesTheFallback() {
    assertEquals("FALLBACK", UltimatePluginGuard.staticFieldValue(BrokenInitialiser.class.getName(), "VALUE", "FALLBACK"));
  }

  public void testNullFallbackIsAllowed() {
    assertNull(UltimatePluginGuard.staticFieldValue("no.such.UltimatePluginGuardFixture", "FIELD", null));
  }
}
