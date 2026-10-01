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

package org.apache.grails.intellij.plugin.gradle;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.vfs.VirtualFile;
import junit.framework.TestCase;
import org.apache.grails.intellij.lib.testFramework.UltimateOnlyTest;
import org.apache.grails.intellij.plugin.config.GrailsFramework;
import org.apache.grails.intellij.plugin.runner.GrailsRunConfigurationType;
import org.apache.grails.intellij.plugin.structure.GrailsApplication;
import org.apache.grails.intellij.plugin.structure.GrailsApplicationProvider;
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

// Grails 6 still uses the org.grails coordinates, and resolves its shell as org.grails:grails-shell
// Kept off the Community run like GradleGrailsImportingTest: same Gradle test-framework base class.
@Category(UltimateOnlyTest.class)
public class GradleGrails6ImportingTest extends GradleImportingTestCase {

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  @Parameterized.Parameters(name = "with Gradle-{0}")
  public static Collection<Object[]> data() {
    return Collections.singletonList(new Object[]{"7.6.4"});
  }

  @Test
  public void importGrailsProject() throws IOException {
    importGrailsProject("6.2.3", "6.2.4", "");

    GradleApacheGrailsImportingTest.assertGrailsModule(getModule("project"), "6.2.3", "org.grails.grails-web", true);
    GrailsApplication application = createApplication();
    TestCase.assertTrue(ReadAction.compute(() -> GrailsRunConfigurationType.isRunnable(application)));
  }

  @Test
  public void importGrailsProjectWithoutPublishedShell() throws IOException {
    // org.grails:grails-shell was never published for 6.2.0: the module must still be a Grails module
    importGrailsProject("6.2.0", "6.2.0", "");

    GradleApacheGrailsImportingTest.assertGrailsModule(getModule("project"), "6.2.0", "org.grails.grails-web", false);
    GrailsApplication application = createApplication();
    TestCase.assertFalse(ReadAction.compute(() -> GrailsRunConfigurationType.isRunnable(application)));
  }

  @Test
  public void importGrailsProjectWithoutGrailsVersionProperty() throws IOException {
    // the version then comes from the resolved grails-core dependency
    importGrailsProject("6.2.3", "6.2.4", "ext.grailsVersion = \"\"");

    GradleApacheGrailsImportingTest.assertGrailsModule(getModule("project"), "6.2.3", "org.grails.grails-web", true);
  }

  private GrailsApplication createApplication() {
    VirtualFile appRoot = GrailsFramework.getInstance().findAppRoot(getModule("project"));
    TestCase.assertNotNull(appRoot);
    GrailsApplication application = ReadAction.compute(
      () -> GrailsApplicationProvider.createGrailsApplication(getModule("project").getProject(), appRoot));
    TestCase.assertNotNull(application);
    return application;
  }

  private void importGrailsProject(String grailsVersion, String grailsGradlePluginVersion, String postfix) throws IOException {
    createProjectSubDirs("grails-app/conf", "grails-app/controllers", "grails-app/domain", "grails-app/i18n",
                         "grails-app/services", "grails-app/taglib", "grails-app/views/layouts", "src/main/groovy");
    createProjectSubFile("gradle.properties", "grailsVersion=" + grailsVersion);

    importProject(createBuildScriptBuilder()
                    .addBuildScriptPostfix("""
                                             repositories {
                                                 maven { url "https://repo.grails.org/grails/core" }
                                             }
                                             dependencies {
                                                classpath "org.grails:grails-gradle-plugin:%s"
                                             }
                                             """.formatted(grailsGradlePluginVersion))
                    .addPostfix("""
                                  version = "0.1"
                                  group = "myapp"
                                  apply plugin:"war"
                                  apply plugin:"org.grails.grails-web"

                                  repositories {
                                      maven { url "https://repo.grails.org/grails/core" }
                                  }
                                  dependencies {
                                      implementation "org.grails:grails-core"
                                  }
                                  """ + postfix).generate());
  }
}
