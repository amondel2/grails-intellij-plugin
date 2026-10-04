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

package org.apache.grails.intellij.plugin.reference.spring;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.apache.grails.intellij.lib.testFramework.GrailsTestCase;
import org.apache.grails.intellij.plugin.spring.GrailsBeansDsl;
import org.apache.grails.intellij.plugin.spring.GrailsResourceBeanExtractor;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Grails 8 compile-time beans DSL ({@code grails.compiler.beans.GrailsBeans}): resolution of the declarations
 * and their qualifier chains, of the shared {@code field(...)}/{@code method(...)} members inside bean bodies, and
 * extraction of the declared beans. The DSL support is independent of the Spring Support plugin.
 */
public class GrailsBeansDslTest extends GrailsTestCase {

  private PsiClass myGrailsBeansAnnotation;

  private static final String DSL_BODY = """
        field('suffix', String).value('app.greeting-suffix', '!')
        field(Formatter)
        field('names', List).typeArguments(String)
        method('buildGreeting', String) { String name ->
            "Hello, ${name}${suffix}"
        }

        bean(MyService)
        bean(GREETER, Greeter) { MyService myService ->
            new Greeter(buildGreeting('World').toUpperCase() + formatter.format(suffix) + names.first().trim())
        }
        bean('special', Greeter).primary().lazy().scope('prototype', proxyMode: 'x').conditionalOnMissingBean(name: 'other') {
            new Greeter(suffix.trim())
        }
        bean('provider', Provider, DefaultProvider).conditionalOnProperty('app.enabled', havingValue: 'true').aliases('legacyProvider')
        bean(URLHolder).staticMethod().annotate(Deprecated).annotate(SuppressWarnings, value: 'x').conditionalOnGrailsEnv('development')
        group('optional').conditionalOnClass(name: 'com.example.Missing') {
            field('prefix', String).value('app.prefix')
            bean('optionalGreeter', Greeter) {
                new Greeter(prefix.trim())
            }
        }
    """;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myGrailsBeansAnnotation = myFixture.addClass("package grails.compiler.beans; public @interface GrailsBeans {}");
    myFixture.addClass("package grails.plugins; public abstract class Plugin {}");
    myFixture.addClass("package grails.boot.config; public class GrailsAutoConfiguration {}");
    myFixture.addClass("package org.grails.testing; public interface GrailsUnitTest {}");

    addSimpleGroovyFile("class MyService {}");
    addSimpleGroovyFile("class Greeter { Greeter(String greeting) {} }");
    addSimpleGroovyFile("class Formatter { String format(String s) { s } }");
    addSimpleGroovyFile("interface Provider {}");
    addSimpleGroovyFile("class DefaultProvider implements Provider {}");
    addSimpleGroovyFile("class URLHolder {}");
  }

  public void testResolveInApplicationClass() {
    PsiFile file = addSimpleGroovyFile("""
      class Application extends grails.boot.config.GrailsAutoConfiguration {
        static final String GREETER = 'greeter'
        def beans = {
      """ + DSL_BODY + """
        }
      }
      """);

    GrailsTestCase.checkResolve(file);
  }

  public void testResolveInPluginDescriptor() {
    PsiFile file = addSimpleGroovyFile("""
      class GreetingGrailsPlugin extends grails.plugins.Plugin {
        static final String GREETER = 'greeter'
        def beans = {
      """ + DSL_BODY + """
        }
      }
      """);

    GrailsTestCase.checkResolve(file);
  }

  public void testResolveInAnnotatedClass() {
    PsiFile file = addSimpleGroovyFile("""
      @grails.compiler.beans.GrailsBeans
      class GreetingBeans {
        static final String GREETER = 'greeter'
        def beans = {
      """ + DSL_BODY + """
        }
      }
      """);

    GrailsTestCase.checkResolve(file);
  }

  public void testResolveInUnitTest() {
    PsiFile file = addSimpleGroovyFile("""
      class GreeterSpec implements org.grails.testing.GrailsUnitTest {
        def beans = {
          bean(Greeter) { new Greeter('test') }
          bean(MyService).primary()
        }
      }
      """);

    GrailsTestCase.checkResolve(file);
  }

  /**
   * As for the compiler, a plugin descriptor is a concrete class named {@code *GrailsPlugin}, whatever it extends.
   */
  public void testPluginDescriptorRecognisedByName() {
    PsiFile descriptor = addSimpleGroovyFile("""
      class GreetingGrailsPlugin {
        def beans = {
          bean(Greeter) { new Greeter('') }
        }
      }
      """);
    PsiFile abstractDescriptor = addSimpleGroovyFile("""
      abstract class BaseGrailsPlugin extends grails.plugins.Plugin {
        def beans = {
          bean(MyService)
        }
      }
      """);
    PsiFile unsuffixedPlugin = addSimpleGroovyFile("""
      class FormattingPlugin extends grails.plugins.Plugin {
        def beans = {
          bean(Formatter)
        }
      }
      """);

    GrailsTestCase.checkResolve(descriptor);
    assertUnresolved(abstractDescriptor, "bean");
    assertUnresolved(unsuffixedPlugin, "bean");

    assertEquals(Set.of("greeter"), beanNames(abstractDescriptor, descriptor, unsuffixedPlugin));
    assertEquals(Set.of("GreetingGrailsPlugin"), hostNames());
  }

  /**
   * Without {@code @GrailsBeans}, the compiler claims a {@code beans} closure only when it looks like the DSL.
   */
  public void testUnrelatedBeansClosureOnImplicitHost() {
    String unrelatedBeans = """
        def beans = {
          register(MyService)
          services {
            bean(MyService)
          }
        }
      """;
    PsiFile application = addSimpleGroovyFile("class Application extends grails.boot.config.GrailsAutoConfiguration {" + unrelatedBeans + "}");
    PsiFile descriptor = addSimpleGroovyFile("class GreetingGrailsPlugin {" + unrelatedBeans + "}");
    PsiFile annotated = addSimpleGroovyFile("@grails.compiler.beans.GrailsBeans class GreetingBeans {" + unrelatedBeans + "}");
    PsiFile strayStatement = addSimpleGroovyFile("""
      class GreeterSpec implements org.grails.testing.GrailsUnitTest {
        def beans = {
          register(MyService)
          bean(Greeter)
        }
      }
      """);
    PsiFile empty = addSimpleGroovyFile("class EmptyGrailsPlugin { def beans = {} }");

    assertFalse(GrailsBeansDsl.isBeansClosure(findBeansClosure(application)));
    assertFalse(GrailsBeansDsl.isBeansClosure(findBeansClosure(descriptor)));
    assertTrue(GrailsBeansDsl.isBeansClosure(findBeansClosure(annotated)));
    assertTrue(GrailsBeansDsl.isBeansClosure(findBeansClosure(strayStatement)));
    assertTrue(GrailsBeansDsl.isBeansClosure(findBeansClosure(empty)));
  }

  public void testDslInertWithoutGrailsBeans() {
    WriteCommandAction.runWriteCommandAction(getProject(), () -> myGrailsBeansAnnotation.getContainingFile().delete());
    PsiFile file = addSimpleGroovyFile("""
      class Application extends grails.boot.config.GrailsAutoConfiguration {
        def beans = {
          bean(MyService).primary()
        }
      }
      """);

    assertUnresolved(file, "bean", "primary");
    assertEmpty(beanNames(file));
    assertEmpty(GrailsBeansDsl.findBeansHosts(getModule()));
  }

  /**
   * Test classes declare beans for tests only, so they are not hosts of the application's beans.
   */
  public void testTestSourcesAreNotHosts() throws Exception {
    VirtualFile testRoot = myFixture.getTempDirFixture().findOrCreateDir("test");
    PsiTestUtil.addSourceRoot(getModule(), testRoot, true);
    try {
      myFixture.addFileToProject("test/TestBeans.groovy", """
        @grails.compiler.beans.GrailsBeans
        class TestBeans {
          def beans = {
            bean(MyService)
          }
        }
        """);
      myFixture.addFileToProject("test/TestApplication.groovy", """
        class TestApplication extends grails.boot.config.GrailsAutoConfiguration {
          def beans = {
            bean(Greeter) { new Greeter('') }
          }
        }
        """);
      addSimpleGroovyFile("""
        @grails.compiler.beans.GrailsBeans
        class MainBeans {
          def beans = {
            bean(Formatter)
          }
        }
        """);

      assertEquals(Set.of("MainBeans"), hostNames());
    }
    finally {
      PsiTestUtil.removeSourceRoot(getModule(), testRoot);
    }
  }

  public void testUnresolvedQualifier() {
    PsiFile file = addSimpleGroovyFile("""
      class Application extends grails.boot.config.GrailsAutoConfiguration {
        def beans = {
          bean(MyService).primry()
          field(Formatter).primary()
          group('g').lazy() {
            bean(Greeter) { new Greeter(undeclared) }
          }
        }
      }
      """);

    GrailsTestCase.checkResolve(file, "primry", "primary", "lazy", "undeclared");
  }

  public void testDslNotAvailableInOtherClasses() {
    PsiFile file = addSimpleGroovyFile("""
      class NotAHost {
        def beans = {
          bean(MyService).primary()
        }
      }
      """);

    assertUnresolved(file, "bean", "primary");
  }

  public void testDslNotAvailableOnlyInBeansProperty() {
    PsiFile file = addSimpleGroovyFile("""
      class Application extends grails.boot.config.GrailsAutoConfiguration {
        def other = {
          bean(MyService)
        }
      }
      """);

    assertUnresolved(file, "bean");
  }

  public void testDeclarationsNotAvailableInBeanBodies() {
    PsiFile file = addSimpleGroovyFile("""
      class Application extends grails.boot.config.GrailsAutoConfiguration {
        def beans = {
          bean(Greeter) {
            bean(MyService)
            new Greeter('')
          }
        }
      }
      """);

    List<GrReferenceExpression> beanCalls = findReferences(file, "bean");
    assertSize(2, beanCalls);
    assertNotNull(beanCalls.get(0).resolve());
    assertNull(beanCalls.get(1).resolve());
  }

  public void testGroupMembersNotSharedWithTopLevel() {
    PsiFile file = addSimpleGroovyFile("""
      class Application extends grails.boot.config.GrailsAutoConfiguration {
        def beans = {
          group('g') {
            field('prefix', String)
          }
          bean(Greeter) { new Greeter(prefix) }
        }
      }
      """);

    GrailsTestCase.checkResolve(file, "prefix");
  }

  /**
   * The compiler requires every group to be named. ({@code group()} itself still applies to {@code group(String)},
   * which Groovy lets a call without arguments reach with a null name.)
   */
  public void testGroupRequiresName() {
    PsiFile file = addSimpleGroovyFile("""
      class Application extends grails.boot.config.GrailsAutoConfiguration {
        def beans = {
          group {
            bean(MyService)
          }
          group('named').conditionalOnClass(name: 'x') {
            bean(Formatter)
          }
        }
      }
      """);

    // The editor reports the call that does not apply through the assignability inspection
    List<GrReferenceExpression> groupCalls = findReferences(file, "group");
    assertSize(2, groupCalls);
    assertFalse(groupCalls.get(0).advancedResolve().isApplicable());
    assertTrue(groupCalls.get(1).advancedResolve().isApplicable());
  }

  public void testGroupsDoNotNest() {
    PsiFile file = addSimpleGroovyFile("""
      class Application extends grails.boot.config.GrailsAutoConfiguration {
        def beans = {
          group('outer') {
            group('inner') {
              bean(Formatter)
            }
            bean(URLHolder)
          }
        }
      }
      """);

    List<GrReferenceExpression> groupCalls = findReferences(file, "group");
    assertSize(2, groupCalls);
    assertNotNull(groupCalls.get(0).resolve());
    assertNull(groupCalls.get(1).resolve());

    List<GrReferenceExpression> beanCalls = findReferences(file, "bean");
    assertSize(2, beanCalls);
    assertNull(beanCalls.get(0).resolve());
    assertNotNull(beanCalls.get(1).resolve());
  }

  public void testGroupBodyCompletion() {
    configureBySimpleGroovyFile("""
      class Application extends grails.boot.config.GrailsAutoConfiguration {
        def beans = {
          group('g') {
            <caret>
          }
        }
      }
      """);

    myFixture.completeBasic();
    List<String> variants = myFixture.getLookupElementStrings();
    assertContainsElements(variants, "bean", "field", "method");
    assertDoesntContain(variants, "group");
  }

  /**
   * The body is the closure passed last to the outermost call of the chain; the compiler rejects one passed before
   * a qualifier, so it gets none of the shared members.
   */
  public void testBodyIsTrailingClosureOfOutermostCall() {
    PsiFile file = addSimpleGroovyFile("""
      class Application extends grails.boot.config.GrailsAutoConfiguration {
        def beans = {
          field('suffix', String)
          method('repeat', String) { int count -> suffix * count }.annotate(Deprecated)
          bean(Greeter) { new Greeter(suffix) }.primary()
          bean('special', Greeter).primary() { new Greeter(suffix) }
        }
      }
      """);

    GrailsTestCase.checkResolve(file, "suffix", "suffix");
    PsiClass application = PsiTreeUtil.findChildOfType(file, PsiClass.class);
    GrailsBeansDsl.Declaration method = GrailsBeansDsl.getDeclarations(GrailsBeansDsl.getBeansClosure(application)).get(1);
    assertEquals(GrailsBeansDsl.Kind.METHOD, method.getKind());
    assertNull(method.getBody());
  }

  public void testSharedMemberTypes() {
    configureBySimpleGroovyFile("""
      class Application extends grails.boot.config.GrailsAutoConfiguration {
        def beans = {
          field('names', List).typeArguments(String)
          method('greeting', String) { int count -> 'x' * count }
          bean(Greeter) { new Greeter(greeting(2) + names<caret>) }
        }
      }
      """);

    PsiElement resolved = resolveAtCaret();
    assertInstanceOf(resolved, PsiVariable.class);
    assertEquals("java.util.List<java.lang.String>", ((PsiVariable)resolved).getType().getCanonicalText());
    assertEquals("'names'", resolved.getNavigationElement().getText());

    GrReferenceExpression greeting = findReference("greeting(2)");
    PsiElement method = greeting.resolve();
    assertInstanceOf(method, PsiMethod.class);
    PsiType returnType = ((PsiMethod)method).getReturnType();
    assertNotNull(returnType);
    assertEquals("java.lang.String", returnType.getCanonicalText());
    assertEquals(1, ((PsiMethod)method).getParameterList().getParametersCount());
    assertEquals("'greeting'", method.getNavigationElement().getText());
  }

  public void testDerivedSharedMemberName() {
    PsiFile file = addSimpleGroovyFile("""
      class Application extends grails.boot.config.GrailsAutoConfiguration {
        def beans = {
          field(Formatter)
          bean(Greeter) { new Greeter(formatter.format('x')) }
        }
      }
      """);

    GrailsTestCase.checkResolve(file);
  }

  /**
   * The compiler names a member after the class, not after how the reference to it is written.
   */
  public void testDerivedNameFollowsClassNotImportAlias() {
    addSimpleGroovyFile("""
      package com.acme
      class Shouter {}
      """);
    addSimpleGroovyFile("""
      package com.acme
      class Whisperer {}
      """);
    PsiFile file = addSimpleGroovyFile("""
      import com.acme.Shouter as S
      import com.acme.Whisperer as W
      class Application extends grails.boot.config.GrailsAutoConfiguration {
        def beans = {
          field(S)
          bean(MyService) { shouter.toString() + s.toString() }
          bean(W)
        }
      }
      """);

    GrailsTestCase.checkResolve(file, "s");
    assertEquals(Map.of("myService", "MyService", "whisperer", "com.acme.Whisperer"), beanTypes(file));
  }

  /**
   * A type written as a class literal is a type, not a name.
   */
  public void testClassLiteralTypes() {
    PsiFile file = addSimpleGroovyFile("""
      class Application extends grails.boot.config.GrailsAutoConfiguration {
        def beans = {
          field(Formatter.class)
          field('names', List.class).typeArguments(String.class)
          bean(MyService.class)
          bean('special', Greeter.class) { new Greeter(formatter.format(names.first().trim())) }
        }
      }
      """);

    // Groovy never resolves the "class" of a class literal, so only that is reported
    GrailsTestCase.checkResolve(file, "class", "class", "class", "class", "class");
    assertEquals(Map.of("myService", "MyService", "special", "Greeter"), beanTypes(file));
  }

  /**
   * A closure parameter with a default value makes the shared method callable without it, as Groovy's generated
   * overloads do.
   */
  public void testSharedMethodDefaultParameterValues() {
    myFixture.enableInspections(GroovyAssignabilityCheckInspection.class);
    configureBySimpleGroovyFile("""
      class Application extends grails.boot.config.GrailsAutoConfiguration {
        def beans = {
          method('greet', String) { String name, String punctuation = '!' -> name + punctuation }
          bean(Greeter) { new Greeter(greet('World') + greet('World', '?')) }
        }
      }
      """);

    myFixture.checkHighlighting(true, false, true);
  }

  public void testHighlighting() {
    myFixture.enableInspections(GroovyAssignabilityCheckInspection.class);
    configureBySimpleGroovyFile("""
      class Application extends grails.boot.config.GrailsAutoConfiguration {
        static final String GREETER = 'greeter'
        def beans = {
      """ + DSL_BODY + """
        }
      }
      """);

    myFixture.checkHighlighting(true, false, true);
  }

  public void testDeclarationCompletion() {
    configureBySimpleGroovyFile("""
      class Application extends grails.boot.config.GrailsAutoConfiguration {
        def beans = {
          <caret>
        }
      }
      """);

    myFixture.completeBasic();
    assertContainsElements(myFixture.getLookupElementStrings(), "bean", "field", "method", "group");
  }

  public void testQualifierCompletion() {
    configureBySimpleGroovyFile("""
      class Application extends grails.boot.config.GrailsAutoConfiguration {
        def beans = {
          bean(MyService).<caret>
        }
      }
      """);

    myFixture.completeBasic();
    assertContainsElements(myFixture.getLookupElementStrings(),
                           "conditionalOnMissingBean", "conditionalOnMissingBeanName", "conditionalOnBean", "conditionalOnProperty",
                           "conditionalOnExpression", "conditionalOnClass", "conditionalOnGrailsEnv", "aliases", "primary", "lazy",
                           "scope", "staticMethod", "typeArguments", "annotate");
  }

  public void testFieldQualifierCompletion() {
    configureBySimpleGroovyFile("""
      class Application extends grails.boot.config.GrailsAutoConfiguration {
        def beans = {
          field('x', String).<caret>
        }
      }
      """);

    myFixture.completeBasic();
    List<String> variants = myFixture.getLookupElementStrings();
    assertContainsElements(variants, "value", "annotate", "typeArguments");
    assertDoesntContain(variants, "primary", "conditionalOnMissingBean");
  }

  public void testSharedMemberCompletion() {
    configureBySimpleGroovyFile("""
      class Application extends grails.boot.config.GrailsAutoConfiguration {
        def beans = {
          field('greetingSuffix', String)
          method('greetingPrefix', String) { 'Hello' }
          bean(Greeter) { new Greeter(greeting<caret>) }
        }
      }
      """);

    myFixture.completeBasic();
    assertContainsElements(myFixture.getLookupElementStrings(), "greetingSuffix", "greetingPrefix");
  }

  public void testBeanDescriptors() {
    addSimpleGroovyFile("class Names { static final String SPECIAL = 'special' }");
    PsiFile file = addSimpleGroovyFile("""
      class Application extends grails.boot.config.GrailsAutoConfiguration {
        static final String GREETER = 'greeter'
        def beans = {
          field('suffix', String)
          method('helper', String) { 'x' }
          bean(MyService)
          bean(URLHolder)
          bean(GREETER, Greeter) { new Greeter('') }
          bean(Names.SPECIAL + 'Greeter', Greeter).primary() { new Greeter('') }
          bean('provider', Provider, DefaultProvider).aliases('legacyProvider', Names.SPECIAL + 'Provider')
          group('g').conditionalOnClass(name: 'x') {
            bean(Formatter)
          }
        }
      }
      """);

    assertEquals(Map.of("myService", "MyService",
                        "URLHolder", "URLHolder",
                        "greeter", "Greeter",
                        "specialGreeter", "Greeter",
                        "provider", "Provider",
                        "formatter", "Formatter"), beanTypes(file));

    PsiClass application = PsiTreeUtil.findChildOfType(file, PsiClass.class);
    List<GrailsResourceBeanExtractor.BeanDescriptor> descriptors = GrailsBeansDsl.getBeanDescriptors(application);
    GrailsResourceBeanExtractor.BeanDescriptor provider = ContainerUtil.find(descriptors, descriptor -> "provider".equals(descriptor.getName()));
    assertEquals(List.of("legacyProvider", "specialProvider"), provider.getAliases());
    assertEmpty(ContainerUtil.find(descriptors, descriptor -> "greeter".equals(descriptor.getName())).getAliases());

    // Cached, so the Spring model's recursion guard sees the same descriptors on every pass
    assertSame(descriptors, GrailsBeansDsl.getBeanDescriptors(application));
  }

  public void testNoBeanDescriptorsWithoutDsl() {
    PsiFile file = addSimpleGroovyFile("""
      class NotAHost {
        def beans = {
          bean(MyService)
        }
      }
      """);

    PsiClass aClass = PsiTreeUtil.findChildOfType(file, PsiClass.class);
    assertEmpty(GrailsBeansDsl.getBeanDescriptors(aClass));
  }

  /**
   * Asserts the named references do not resolve. Not {@link GrailsTestCase#checkResolve}: on Ultimate the Spring
   * plugin's own Groovy bean DSL support claims a call such as {@code bean(Foo)} as a bean declaration, which that
   * check counts as resolved.
   */
  private static void assertUnresolved(PsiFile file, String... names) {
    Set<String> expected = Set.of(names);
    int count = 0;
    for (GrReferenceExpression ref : PsiTreeUtil.findChildrenOfType(file, GrReferenceExpression.class)) {
      if (expected.contains(ref.getReferenceName())) {
        assertNull(ref.getText(), ref.resolve());
        count++;
      }
    }
    assertTrue(count >= names.length);
  }

  private static Map<String, String> beanTypes(PsiFile file) {
    Map<String, String> result = new HashMap<>();
    for (GrailsResourceBeanExtractor.BeanDescriptor descriptor : GrailsBeansDsl.getBeanDescriptors(PsiTreeUtil.findChildOfType(file, PsiClass.class))) {
      PsiType type = descriptor.getType();
      result.put(descriptor.getName(), type == null ? null : type.getCanonicalText());
    }
    return result;
  }

  private static Set<String> beanNames(PsiFile... files) {
    Set<String> result = new HashSet<>();
    for (PsiFile file : files) {
      result.addAll(beanTypes(file).keySet());
    }
    return result;
  }

  private Set<String> hostNames() {
    return ContainerUtil.map2Set(GrailsBeansDsl.findBeansHosts(getModule()), PsiClass::getName);
  }

  private static GrClosableBlock findBeansClosure(PsiFile file) {
    GrField field = PsiTreeUtil.findChildOfType(file, GrField.class);
    assertNotNull(field);
    GrClosableBlock closure = (GrClosableBlock)field.getInitializerGroovy();
    assertNotNull(closure);
    return closure;
  }

  private static List<GrReferenceExpression> findReferences(PsiFile file, String name) {
    return ContainerUtil.filter(PsiTreeUtil.findChildrenOfType(file, GrReferenceExpression.class),
                                ref -> name.equals(ref.getReferenceName()));
  }

  private PsiElement resolveAtCaret() {
    PsiElement element = myFixture.getFile().findElementAt(myFixture.getCaretOffset() - 1);
    GrReferenceExpression ref = PsiTreeUtil.getParentOfType(element, GrReferenceExpression.class);
    assertNotNull(ref);
    return ref.resolve();
  }

  private GrReferenceExpression findReference(String text) {
    int offset = myFixture.getFile().getText().indexOf(text);
    assertTrue(offset >= 0);
    GrExpression expression = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(offset), GrReferenceExpression.class);
    assertNotNull(expression);
    return (GrReferenceExpression)expression;
  }
}
