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
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import junit.framework.TestCase;
import org.apache.grails.intellij.plugin.config.GrailsFramework;
import org.apache.grails.intellij.plugin.runner.GrailsRunConfigurationType;
import org.apache.grails.intellij.plugin.structure.GrailsApplication;
import org.apache.grails.intellij.plugin.structure.GrailsApplicationProvider;
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.junit.Test;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

// Apache Grails (7+) uses the org.apache.grails coordinates, and Grails 8 requires Gradle 9
public class GradleApacheGrailsImportingTest extends GradleImportingTestCase {
  @Parameterized.Parameter(1) public String grailsVersion;

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  @Parameterized.Parameters(name = "with Gradle-{0}, Grails-{1}")
  public static Collection<Object[]> data() {
    return Collections.singletonList(
      new Object[]{"9.6.0", "8.0.0-M6"}
    );
  }

  @Test
  public void importBasicGrailsProject() throws IOException {
    // repo.grails.org proxies org.gradle:gradle-tooling-api, which the Grails shell depends on
    importGrailsProject("""
                          maven { url = "https://repo.grails.org/grails/restricted" }
                          """);

    assertGrailsModule(getModule("project"), grailsVersion, "org.apache.grails.gradle.grails-web", true);
    GrailsApplication application = createApplication();
    TestCase.assertTrue(ReadAction.compute(() -> GrailsRunConfigurationType.isRunnable(application)));
  }

  @Test
  public void importGrailsProjectWithUnresolvableShell() throws IOException {
    // Maven Central alone cannot resolve the Grails shell, which must not prevent Grails support
    importGrailsProject("");

    assertGrailsModule(getModule("project"), grailsVersion, "org.apache.grails.gradle.grails-web", false);
    // without the shell there is no executor for run-app, so no Grails run configuration is offered
    GrailsApplication application = createApplication();
    TestCase.assertFalse(ReadAction.compute(() -> GrailsRunConfigurationType.isRunnable(application)));
  }

  private GrailsApplication createApplication() {
    VirtualFile appRoot = GrailsFramework.getInstance().findAppRoot(getModule("project"));
    TestCase.assertNotNull(appRoot);
    GrailsApplication application = ReadAction.compute(
      () -> GrailsApplicationProvider.createGrailsApplication(getModule("project").getProject(), appRoot));
    TestCase.assertNotNull(application);
    return application;
  }

  private void importGrailsProject(String extraRepositories) throws IOException {
    createProjectSubDirs("grails-app/conf", "grails-app/controllers", "grails-app/domain", "grails-app/i18n",
                         "grails-app/services", "grails-app/taglib", "grails-app/views/layouts", "src/main/groovy");
    createProjectSubFile("gradle.properties", "grailsVersion=" + grailsVersion);

    importProject(createBuildScriptBuilder().withBuildScriptMavenCentral().withMavenCentral()
                    .addBuildScriptPostfix("""
                                             repositories {
                                                 gradlePluginPortal()
                                             }
                                             dependencies {
                                                classpath platform("org.apache.grails:grails-bom:$grailsVersion")
                                                classpath "org.apache.grails:grails-gradle-plugins"
                                             }
                                             """)
                    .addPostfix("""
                                  version = "0.1"
                                  group = "myapp"
                                  apply plugin:"war"
                                  apply plugin:"org.apache.grails.gradle.grails-web"
                                  apply plugin:"org.apache.grails.gradle.grails-gsp"

                                  repositories {
                                  """ + extraRepositories + """
                                  }
                                  """).generate());
  }

  /**
   * The Grails model built inside the Gradle daemon is what makes the plugin treat the module as a Grails
   * application, so it must be present on the imported module (a mere grails-app folder is not enough).
   */
  static void assertGrailsModule(Module module, String grailsVersion, String grailsPluginId, boolean withShell) {
    String modulePath = ExternalSystemApiUtil.getExternalProjectPath(module);
    TestCase.assertNotNull(modulePath);
    ExternalProjectInfo projectInfo =
      ProjectDataManager.getInstance().getExternalProjectData(module.getProject(), GradleConstants.SYSTEM_ID, modulePath);
    TestCase.assertNotNull(projectInfo);
    TestCase.assertNotNull(projectInfo.getExternalProjectStructure());

    DataNode<ModuleData> moduleNode = GradleProjectResolverUtil.findModule(projectInfo.getExternalProjectStructure(), modulePath);
    TestCase.assertNotNull(moduleNode);

    DataNode<GrailsModuleData> grailsNode = ExternalSystemApiUtil.find(moduleNode, GrailsModuleData.KEY);
    TestCase.assertNotNull("Grails model is missing, see the 'Grails import errors' build message", grailsNode);
    TestCase.assertEquals(grailsVersion, grailsNode.getData().getGrailsVersion());
    TestCase.assertEquals(grailsPluginId, grailsNode.getData().getGrailsPluginId());
    TestCase.assertEquals(withShell, !grailsNode.getData().getShellUrls().isEmpty());
  }
}
