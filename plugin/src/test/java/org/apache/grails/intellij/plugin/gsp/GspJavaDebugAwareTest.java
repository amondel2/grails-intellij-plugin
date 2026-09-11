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

package org.apache.grails.intellij.plugin.gsp;

import com.intellij.debugger.engine.JavaDebugAware;
import com.intellij.psi.PsiFile;
import org.apache.grails.intellij.lib.testFramework.GrailsTestCase;
import org.apache.grails.intellij.plugin.lang.gsp.debug.GspJavaDebugAware;

public class GspJavaDebugAwareTest extends GrailsTestCase {
  private final JavaDebugAware myDebugAware = new GspJavaDebugAware();

  public void testGspFilesTakeJavaBreakpoints() {
    PsiFile gsp = myFixture.configureByText("index.gsp", "<html><body><%= 1 + 1 %></body></html>");
    assertTrue(myDebugAware.isBreakpointAware(gsp));
    // every root of the GSP view provider, not only the GSP one
    for (PsiFile root : gsp.getViewProvider().getAllFiles()) {
      assertTrue(root.getLanguage().getID(), myDebugAware.isBreakpointAware(root));
    }
  }

  public void testOtherFilesAreLeftToTheirOwnFileTypes() {
    assertFalse(myDebugAware.isBreakpointAware(myFixture.configureByText("A.groovy", "class A {}")));
    assertFalse(myDebugAware.isBreakpointAware(myFixture.configureByText("a.html", "<html/>")));
  }
}
