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
package org.apache.grails.intellij.plugin.runner.impl;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleConsoleProperties;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole;
import org.jetbrains.plugins.gradle.execution.test.runner.events.GradleTestsExecutionConsoleOutputProcessor;
import org.apache.grails.intellij.plugin.runner.GrailsCommandLineExecutor;
import org.apache.grails.intellij.plugin.runner.GrailsRunConfiguration;
import org.apache.grails.intellij.plugin.runner.GrailsRunnerSetup;
import org.apache.grails.intellij.plugin.mvc.MvcCommand;

public class Grails3TestCommandLineState extends GrailsTestAppCommandLineState {

  private volatile MvcCommand myProxyCommand;

  public Grails3TestCommandLineState(@NotNull ExecutionEnvironment environment,
                                     @NotNull GrailsRunConfiguration configuration,
                                     @NotNull GrailsCommandLineExecutor executor) throws ExecutionException {
    super(environment, configuration, executor);
  }

  @Override
  public @NotNull MvcCommand getCommand() {
    MvcCommand cached = myProxyCommand;
    if (cached != null) return cached;
    synchronized (this) {
      if (myProxyCommand == null) {
        myProxyCommand = createProxyCommand();
      }
      return myProxyCommand;
    }
  }

  /**
   * Wraps the real command in Grails' {@code intellij-command-proxy}, which runs it through Gradle
   * with our init script attached so test events come back in a form the Gradle test console reads.
   */
  private @NotNull MvcCommand createProxyCommand() {
    MvcCommand originalCommand = super.getCommand();
    MvcCommand command = new MvcCommand("intellij-command-proxy");
    command.getArgs().add(originalCommand.getCommand());
    command.getArgs().addAll(originalCommand.getArgs());
    command.setEnv(originalCommand.getEnv());
    command.setVmOptions(originalCommand.getVmOptions());
    command.setEnvVariables(originalCommand.getEnvVariables());
    command.setPassParentEnvs(originalCommand.isPassParentEnvs());
    command.getEnvVariables().put(GrailsRunnerSetup.PATH_TO_GRADLE_INIT_SCRIPT_KEY,
                                 GrailsRunnerSetup.getPathToGradleInitScript());
    return command;
  }

  @Override
  protected @NotNull ConsoleView createConsole(@NotNull Executor executor) {
    String splitterPropertyName = SMTestRunnerConnectionUtil.getSplitterPropertyName(GrailsRunnerSetup.TEST_FRAMEWORK_NAME);
    GradleConsoleProperties consoleProperties =
      new GradleConsoleProperties(getConfiguration(), GrailsRunnerSetup.TEST_FRAMEWORK_NAME, executor);
    GradleTestsExecutionConsole console = new GradleTestsExecutionConsole(consoleProperties, splitterPropertyName);
    // Same wiring as the Gradle plugin's own test console manager: initConsoleView installs the
    // results form, its events processor and the start/finish handling around the attached process.
    SMTestRunnerConnectionUtil.initConsoleView(console, GrailsRunnerSetup.TEST_FRAMEWORK_NAME);
    console.addAttachToProcessListener(handler -> handler.addProcessListener(new ProcessListener() {
      @Override
      @SuppressWarnings("rawtypes") // ProcessListener declares a raw Key
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        GradleTestsExecutionConsoleOutputProcessor.onOutput(console, event.getText(), outputType);
      }
    }));
    return console;
  }
}
