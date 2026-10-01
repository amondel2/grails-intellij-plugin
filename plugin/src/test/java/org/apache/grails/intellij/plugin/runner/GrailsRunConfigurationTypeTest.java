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

package org.apache.grails.intellij.plugin.runner;

import junit.framework.TestCase;
import org.apache.grails.intellij.plugin.structure.GrailsApplication;
import org.apache.grails.intellij.plugin.util.version.VersionImpl;

import java.lang.reflect.Proxy;

public class GrailsRunConfigurationTypeTest extends TestCase {

  public void testRunnableBeforeGrails6() {
    assertTrue(GrailsRunConfigurationType.isRunnable(application("3.3.1")));
    assertTrue(GrailsRunConfigurationType.isRunnable(application("5.3.0")));
  }

  // Grails 6+ runnability depends on the resolved shell, see GradleGrailsImportingTest and GradleApacheGrailsImportingTest

  private static GrailsApplication application(String version) {
    return (GrailsApplication)Proxy.newProxyInstance(
      GrailsApplication.class.getClassLoader(), new Class<?>[]{GrailsApplication.class},
      (proxy, method, args) -> {
        if (method.getName().equals("getGrailsVersion")) return new VersionImpl(version);
        throw new UnsupportedOperationException(method.getName());
      });
  }
}
