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

package org.apache.grails.intellij.module.maven;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.openapi.roots.ModuleRootManager;
import org.apache.grails.intellij.lib.testFramework.GrailsTestCase;
import org.apache.grails.intellij.plugin.mvc.MvcCommand;
import org.jetbrains.idea.maven.execution.RunnerBundle;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;
import org.jetbrains.idea.maven.importing.MavenPomPathModuleService;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenInSpecificPath;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class MavenCommandExecutorTest extends GrailsTestCase {

  private MavenRunnerSettings myOriginalRunnerSettings;
  private MavenGeneralSettings myOriginalGeneralSettings;
  private GrailsMavenApplication myApplication;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myOriginalRunnerSettings = MavenRunner.getInstance(getProject()).getSettings();
    MavenRunnerSettings runnerSettings = new MavenRunnerSettings();
    runnerSettings.setJreName(MavenRunnerSettings.USE_PROJECT_JDK);
    MavenRunner.getInstance(getProject()).loadState(runnerSettings);
    var workspaceSettings = MavenWorkspaceSettingsComponent.getInstance(getProject()).getSettings();
    myOriginalGeneralSettings = workspaceSettings.getGeneralSettings();
    workspaceSettings.setGeneralSettings(new MavenGeneralSettings(getProject()));
    var root = myFixture.addFileToProject("pom.xml", "<project/>").getVirtualFile().getParent();
    myApplication = new GrailsMavenApplication(getModule(), root, new MavenId("test", "app", "1.0"));
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      MavenRunner.getInstance(getProject()).loadState(myOriginalRunnerSettings);
      MavenWorkspaceSettingsComponent.getInstance(getProject()).getSettings().setGeneralSettings(myOriginalGeneralSettings);
    }
    finally {
      super.tearDown();
    }
  }

  public void testMappedGoalPreservesJavaLaunchOptionsAndRunnerSettings() throws Exception {
    MavenRunnerSettings settings = MavenRunner.getInstance(getProject()).getSettings();
    settings.setVmOptions("-Doriginal=true");
    settings.setEnvironmentProperties(Map.of("GRAILS_TEST_ENV", "value"));
    settings.setPassParentEnv(false);
    settings.getMavenProperties().put("custom", "value");
    MvcCommand command = new MvcCommand("run-app", "--port=9090", "argument with spaces");
    command.setVmOptions("-Xmx512m");
    command.setEnv("prod");
    command.getProperties().add("-Dgrails.server.host=localhost");

    JavaParameters parameters = new MavenCommandExecutor().createJavaParameters(myApplication, command);

    assertEquals("org.codehaus.classworlds.Launcher", parameters.getMainClass());
    assertSame(ModuleRootManager.getInstance(getModule()).getSdk(), parameters.getJdk());
    assertEquals(myApplication.getRoot().getPath(), parameters.getWorkingDirectory());
    assertThat(parameters.getClassPath().getPathList()).isNotEmpty();
    assertThat(parameters.getProgramParametersList().getList()).contains("grails:run-app", "-Dcustom=value");
    ParametersList vm = parameters.getVMParametersList();
    assertThat(vm.getList()).contains("-Xmx512m", "-Dgrails.env=prod", "-Dgrails.server.host=localhost");
    assertEquals(ParametersList.join(command.getArgs()), vm.getPropertyValue("grails.cli.args"));
    assertEquals("value", parameters.getEnv().get("GRAILS_TEST_ENV"));
    assertFalse(parameters.isPassParentEnvs());
    assertEquals("-Doriginal=true", settings.getVmOptions());
    assertEquals(Map.of("custom", "value"), settings.getMavenProperties());
  }

  public void testUnmappedAndLegacyTestGoalsUseExecWithoutLeakingProperties() throws Exception {
    assertTrue(myApplication.getGrailsVersion().compareToString("2.1.0") < 0);
    MavenCommandExecutor executor = new MavenCommandExecutor();
    for (String goal : List.of("create-domain-class", "test-app")) {
      MvcCommand command = new MvcCommand(goal, "Example with spaces");
      JavaParameters parameters = executor.createJavaParameters(myApplication, command);
      ParametersList program = parameters.getProgramParametersList();
      assertThat(program.getList()).contains("grails:exec");
      assertEquals(goal, program.getPropertyValue("command"));
      assertEquals(ParametersList.join(command.getArgs()), program.getPropertyValue("args"));
      assertFalse(parameters.getVMParametersList().hasProperty("grails.cli.args"));
    }
    ParametersList program = executor.createJavaParameters(myApplication, new MvcCommand("war")).getProgramParametersList();
    assertThat(program.getList()).contains("grails:war").doesNotContain("grails:exec");
    assertFalse(program.hasProperty("command"));
    assertFalse(program.hasProperty("args"));
    assertThat(MavenRunner.getInstance(getProject()).getSettings().getMavenProperties()).isEmpty();
  }

  public void testGeneralSettingsOverrideAndInheritance() throws Exception {
    MavenGeneralSettings projectSettings = MavenProjectsManager.getInstance(getProject()).getGeneralSettings();
    projectSettings.setWorkOffline(true);
    MavenCommandExecutor executor = new MavenCommandExecutor();
    MvcCommand command = new MvcCommand("war");
    assertThat(executor.createJavaParameters(myApplication, command).getProgramParametersList().getList()).contains("--offline");

    MavenGeneralSettings override = projectSettings.clone();
    override.setWorkOffline(false);
    override.setThreads("2");
    ParametersList program = executor.createJavaParameters(myApplication, command, override).getProgramParametersList();
    assertThat(program.getList()).doesNotContain("--offline").containsSubsequence("-T", "2");
    assertTrue(projectSettings.isWorkOffline());
  }

  public void testCustomPomAndExplicitProfilesArePreserved() throws Exception {
    MavenProjectsManager manager = MavenProjectsManager.getInstance(getProject());
    MavenExplicitProfiles originalProfiles = manager.getExplicitProfiles();
    MavenPomPathModuleService pomService = MavenPomPathModuleService.getInstance(getModule());
    String originalPom = pomService.getPomFileUrl();
    try {
      manager.setExplicitProfiles(new MavenExplicitProfiles(List.of("enabled"), List.of("disabled")));
      pomService.setPomFileUrl(myFixture.addFileToProject("custom-pom.xml", "<project/>").getVirtualFile().getUrl());
      ParametersList program = new MavenCommandExecutor().createJavaParameters(myApplication, new MvcCommand("war"))
        .getProgramParametersList();
      assertThat(program.getList()).containsSubsequence("-f", "custom-pom.xml");
      int profilesIndex = program.getList().indexOf("-P");
      assertTrue(profilesIndex >= 0);
      assertThat(program.getList().get(profilesIndex + 1).split(",")).containsExactlyInAnyOrder("enabled", "!disabled");
    }
    finally {
      manager.setExplicitProfiles(originalProfiles);
      pomService.setPomFileUrl(originalPom);
    }
  }

  public void testInvalidMavenHomeStillThrowsExecutionException() throws Exception {
    MavenGeneralSettings settings = new MavenGeneralSettings(getProject());
    settings.setMavenHomeType(new MavenInSpecificPath(myApplication.getRoot().getPath() + "/missing-maven"));
    try {
      new MavenCommandExecutor().createJavaParameters(myApplication, new MvcCommand("war"), settings);
      fail("Expected an invalid Maven home to fail parameter creation");
    }
    catch (ExecutionException expected) {
      assertEquals(RunnerBundle.message("external.maven.home.no.default"), expected.getMessage());
    }
  }
}
