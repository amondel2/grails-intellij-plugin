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

package org.apache.grails.intellij.plugin.artefact.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.apache.grails.intellij.plugin.projectView.impl.GrailsNodeProviderTestSupport;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Conventional artefacts are found by walking the handler's directory. These tests pin the rule that walk
 * applies: a Groovy class under the directory, named after its file, with the handler's suffix, inside the
 * requested scope.
 */
public class GrailsArtefactsTest extends GrailsNodeProviderTestSupport {

  public void testConventionalArtefactsAreGroovyClassesNamedAfterTheirFileUnderTheHandlerDirectory() {
    myFixture.addFileToProject("grails-app/controllers/demo/BookController.groovy", "package demo\nclass BookController { def index() {} }");
    // a second class in the file lacks the file's name, suffix or not
    myFixture.addFileToProject("grails-app/controllers/demo/MultiController.groovy",
                               "package demo\nclass MultiController {}\nclass SidekickController {}");
    myFixture.addFileToProject("grails-app/controllers/demo/Helper.groovy", "package demo\nclass Helper {}");
    myFixture.addFileToProject("grails-app/controllers/demo/Renamed.groovy", "package demo\nclass OtherController {}");
    myFixture.addFileToProject("grails-app/controllers/demo/JavaController.java", "package demo;\npublic class JavaController {}");
    myFixture.addFileToProject("grails-app/services/BookService.groovy", "package demo\nclass BookService {}");
    myFixture.addFileToProject("src/main/groovy/demo/StrayController.groovy", "package demo\nclass StrayController {}");

    Collection<PsiClass> artefacts = GrailsArtefacts.getArtefacts(ControllerArtefactHandler.INSTANCE, testApplication(true),
                                                                  GlobalSearchScope.allScope(getProject()));

    assertEquals(Set.of("demo.BookController", "demo.MultiController"), qualifiedNames(artefacts));
  }

  public void testScopeLimitsConventionalArtefacts() {
    PsiFile book = myFixture.addFileToProject("grails-app/controllers/demo/BookController.groovy", "package demo\nclass BookController {}");
    myFixture.addFileToProject("grails-app/controllers/demo/AuthorController.groovy", "package demo\nclass AuthorController {}");

    Collection<PsiClass> artefacts = GrailsArtefacts.getArtefacts(ControllerArtefactHandler.INSTANCE, testApplication(true),
                                                                  GlobalSearchScope.fileScope(book));

    assertEquals(Set.of("demo.BookController"), qualifiedNames(artefacts));
  }

  public void testNoConventionalArtefactsWithoutTheHandlerDirectory() {
    myFixture.addFileToProject("grails-app/services/BookService.groovy", "package demo\nclass BookService {}");

    assertEmpty(GrailsArtefacts.getArtefacts(ControllerArtefactHandler.INSTANCE, testApplication(true),
                                             GlobalSearchScope.allScope(getProject())));
  }

  private static @NotNull Set<String> qualifiedNames(@NotNull Collection<PsiClass> classes) {
    Set<String> result = new HashSet<>();
    for (PsiClass clazz : classes) {
      result.add(clazz.getQualifiedName());
    }
    return result;
  }
}
