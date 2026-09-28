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

package org.apache.grails.intellij.plugin.tests;

import junit.framework.TestCase;
import org.apache.grails.intellij.plugin.GrailsBundle;

public class GrailsBundleMessagesTest extends TestCase {

  public void testProvidersFailedToLoadMessageNamesWhatFailedAndTheConsequence() {
    String message = GrailsBundle.message("error.grails.application.providers.failed.to.load");

    assertNotBlank(message);
    assertTrue("must name what failed: " + message, message.contains("load the Grails application providers"));
    assertTrue("must state the consequence: " + message,
               message.contains("Grails project detection is disabled for this project"));
  }

  public void testProviderFailedForMessageNamesTheProviderAndTheRoot() {
    String message = GrailsBundle.message("warning.grails.application.provider.failed.for",
                                         "com.example.MyProvider", "/projects/demo");

    assertNotBlank(message);
    assertTrue(message.contains("com.example.MyProvider"));
    assertTrue(message.contains("/projects/demo"));
    assertTrue("must say the remaining providers were still tried: " + message,
               message.contains("; the remaining providers were still tried"));
    assertFalse("must not claim Grails support is lost: " + message, message.contains("disabled"));
  }

  public void testBothArgumentsAreConsumedByTheFormatter() {
    // MessageFormat throws on a malformed {n}, so reaching here proves the key's two placeholders
    // are well formed; the assertions prove neither was left unconsumed or misnumbered.
    String message = GrailsBundle.message("warning.grails.application.provider.failed.for",
                                         "FIRST", "SECOND");

    assertFalse("an unconsumed placeholder is left: " + message, message.contains("{"));
    assertTrue("arguments must appear in order: " + message,
               message.indexOf("FIRST") < message.indexOf("SECOND"));
  }

  private static void assertNotBlank(String message) {
    assertNotNull(message);
    assertFalse("message must not be blank", message.trim().isEmpty());
  }
}
