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

package org.apache.grails.intellij.plugin.domain;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.UsefulTestCase;
import groovy.lang.Closure;
import org.apache.grails.intellij.lib.testFramework.GrailsTestCase;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DelegatesToInfo;
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.GrDelegatesToUtilKt;

import java.util.Collection;
import java.util.List;

/**
 * Where queries ({@code Person.where { active == true }}) refer to the domain properties by their bare names, which
 * WhereQueryClosureMemberContributor resolves, and call the criteria methods ({@code setAlias}, {@code eq}) of the
 * {@code DetachedCriteria} GORM runs them against. The GORM 4+ {@code GormEntity} trait declares no
 * {@code @DelegatesTo} for that, so DetachedCriteriaDelegatesToProvider supplies the delegate. The GORM the other
 * tests run against is older than that, hence the stubs below.
 */
public class GormWhereQueryTest extends GrailsTestCase {
  private PsiFile myDomainFile;

  /** GormTraitContributor only picks GormEntity when {@code org.hibernate.Hibernate} is on the classpath. */
  @Override
  protected boolean needHibernate() {
    return true;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    // #CHECK# org.grails.datastore.mapping.query.api.Criteria
    myFixture.addFileToProject("src/groovy/org/grails/datastore/mapping/query/api/Criteria.groovy", """
      package org.grails.datastore.mapping.query.api

      interface Criteria {
      }
      """);

    // #CHECK# org.grails.datastore.gorm.query.criteria.AbstractDetachedCriteria
    myFixture.addFileToProject("src/groovy/org/grails/datastore/gorm/query/criteria/AbstractDetachedCriteria.groovy", """
      package org.grails.datastore.gorm.query.criteria

      import org.grails.datastore.mapping.query.api.Criteria

      abstract class AbstractDetachedCriteria<T> implements Criteria {
        Criteria setAlias(String alias) { this }
        Criteria eq(String propertyName, Object value) { this }
        Criteria or(@DelegatesTo(AbstractDetachedCriteria) Closure callable) { this }
      }
      """);

    // #CHECK# grails.gorm.DetachedCriteria
    myFixture.addFileToProject("src/groovy/grails/gorm/DetachedCriteria.groovy", """
      package grails.gorm

      import org.grails.datastore.gorm.query.criteria.AbstractDetachedCriteria

      class DetachedCriteria<T> extends AbstractDetachedCriteria<T> {
        DetachedCriteria(Class<T> targetClass) {}
        DetachedCriteria<T> build(@DelegatesTo(DetachedCriteria) Closure callable) { this }
        DetachedCriteria<T> where(@DelegatesTo(DetachedCriteria) Closure callable) { this }
        DetachedCriteria<T> eq(String propertyName, Object value) { this }
        DetachedCriteria<T> or(@DelegatesTo(AbstractDetachedCriteria) Closure callable) { this }
        List<T> list(Map args) { null }
      }
      """);

    // The Groovy the tests run against predates @CompileStatic; the Groovy plugin keys off the annotation name only.
    myFixture.addFileToProject("src/java/groovy/transform/CompileStatic.java", """
      package groovy.transform;

      public @interface CompileStatic {
      }
      """);

    // GormVersion.IS_5 is the lowest version GormTraitContributor injects the trait for.
    myFixture.addFileToProject("src/java/grails/gorm/annotation/Entity.java", """
      package grails.gorm.annotation;

      public @interface Entity {
      }
      """);

    // #CHECK# org.grails.datastore.gorm.GormEntity: as in GORM 6.1 to 8, the closures carry no @DelegatesTo.
    myFixture.addFileToProject("src/groovy/org/grails/datastore/gorm/GormEntity.groovy", """
      package org.grails.datastore.gorm

      import grails.gorm.DetachedCriteria

      trait GormEntity<D> {
        static DetachedCriteria<D> where(Closure callable) { null }
        static DetachedCriteria<D> whereAny(Closure callable) { null }
        static DetachedCriteria<D> whereLazy(Closure callable) { null }
        static D find(Closure callable) { null }
        static List<D> findAll(Closure callable) { null }
        static List<D> findAll(Map args, Closure callable) { null }
      }
      """);

    myDomainFile = addDomain("""

                class Person {
                  String name
                  Integer age
                  Boolean active = true

                  static transients = ['nickname']
                  String nickname

                  static constraints = {
                    name blank: false, maxSize: 100
                  }

                  static List<Person> adults() {
                    where { age >= 18 }.list([:])
                  }
                }
                """);

    myFixture.enableInspections(GrUnresolvedAccessInspection.class);
  }

  /** The reported case: under {@code @CompileStatic} an unresolved property is an error, not just a warning. */
  public void testCompileStaticWhereQueryHasNoErrors() {
    PsiFile file = myFixture.addFileToProject("src/groovy/PersonService.groovy", """
      import grails.gorm.DetachedCriteria
      import groovy.transform.CompileStatic

      @CompileStatic
      class PersonService {
        List<Person> findActiveByMinAge(Integer minAge) {
          DetachedCriteria<Person> query = Person.where {
            active == true && age >= minAge
          }
          query.list(sort: 'name', order: 'asc')
        }

        List<Person> composed() {
          DetachedCriteria<Person> query = Person.whereAny { name == 'a' || id == 1L }
          query.where { version == 0L }.list([:])
        }

        Person first() {
          Person.find { name == 'Ann' }
        }

        List<Person> all() {
          Person.findAll { age < 10 }
        }

        // Proves the class really is type checked: an unknown name is still an error.
        def unknown() {
          Person.where { <error descr="Cannot resolve symbol 'missing'">missing</error> == 1 }
        }
      }
      """);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.checkHighlighting(true, false, true);
  }

  /**
   * A declared {@code DetachedCriteria<Person>} (a parameter or a field) is a class reference type rather than the
   * inferred type a local variable gets from its initializer, and GORM transforms where calls on both.
   */
  public void testCompileStaticWhereOnDeclaredCriteria() {
    PsiFile file = myFixture.addFileToProject("src/groovy/PersonService.groovy", """
      import grails.gorm.DetachedCriteria
      import groovy.transform.CompileStatic

      @CompileStatic
      class PersonService {
        DetachedCriteria<Person> base = Person.where { active == true }

        def fromParameter(DetachedCriteria<Person> query) {
          query.where { age > 1 }
        }

        def fromField() {
          base.where { name == 'Ann' }
        }
      }
      """);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.checkHighlighting(true, false, true);
  }

  public void testNavigateFromDeclaredCriteriaParameter() {
    assertNavigatesToField("""
      import grails.gorm.DetachedCriteria

      class PersonService {
        def search(DetachedCriteria<Person> query) {
          query.where { ag<caret>e > 1 }
        }
      }
      """, "age");
  }

  public void testCompletion() {
    PsiFile file = myFixture.addFileToProject("src/groovy/PersonService.groovy", """
      class PersonService {
        def search() {
          Person.where { <caret> }
        }
      }
      """);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.completeBasic();

    List<String> variants = myFixture.getLookupElementStrings();
    assertNotNull(variants);
    assertContainsElements(variants, "name", "age", "active", "id", "version", "setAlias", "eq", "or");
    assertDoesntContain(variants, "nickname");
  }

  /**
   * GORM runs each of these closures against a {@code DetachedCriteria} of the queried class, delegate first
   * ({@code GormStaticApi#where}), not against the {@code Criteria} interface its methods are declared to return.
   */
  public void testWhereQueryDelegatesToDetachedCriteriaOfQueriedClass() {
    PsiFile file = myFixture.addFileToProject("src/groovy/PersonService.groovy", """
      class PersonService {
        def search() {
          Person.where {}
          Person.whereAny {}
          Person.whereLazy {}
          Person.find {}
          Person.findAll {}
          Person.findAll([max: 1]) {}
          Person.where({})
        }
      }
      """);

    Collection<GrClosableBlock> closures = PsiTreeUtil.findChildrenOfType(file, GrClosableBlock.class);
    assertEquals(7, closures.size());
    for (GrClosableBlock closure : closures) {
      assertDelegatesToPersonCriteria(closure);
    }

    // An unqualified call from within the domain class itself.
    PsiElement inDomain = myDomainFile.findElementAt(myDomainFile.getText().indexOf("age >= 18"));
    assertDelegatesToPersonCriteria(PsiTreeUtil.getParentOfType(inDomain, GrClosableBlock.class));
  }

  /** The reported case: the criteria methods neither resolved nor completed in a where query. */
  public void testCriteriaMethodsResolveInWhereQueries() {
    PsiFile file = myFixture.addFileToProject("src/groovy/PersonService.groovy", """
      import groovy.transform.CompileStatic

      @CompileStatic
      class PersonService {
        def search() {
          Person.where { setAlias('p'); eq('name', 'Ann'); or { eq('age', 1) } }
          Person.whereAny { setAlias('p') }
          Person.whereLazy { setAlias('p') }
          Person.find { setAlias('p') }
          Person.findAll { setAlias('p') }
          Person.findAll([max: 1]) { setAlias('p') }
        }
      }
      """);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.checkHighlighting(true, false, true);
  }

  public void testNavigateToCriteriaMethod() {
    PsiFile file = myFixture.addFileToProject("src/groovy/PersonService.groovy", """
      class PersonService {
        def search() {
          Person.where { setAl<caret>ias('p') }
        }
      }
      """);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    PsiElement target = myFixture.getElementAtCaret();
    UsefulTestCase.assertInstanceOf(target, PsiMethod.class);
    assertEquals("org.grails.datastore.gorm.query.criteria.AbstractDetachedCriteria",
                 ((PsiMethod)target).getContainingClass().getQualifiedName());
  }

  /** Only the GORM where query methods of a domain class get the delegate, not same-named methods of anything else. */
  public void testNoCriteriaDelegateOutsideWhereQueries() {
    PsiFile file = myFixture.addFileToProject("src/groovy/PersonService.groovy", """
      class Finder {
        static Object where(Closure callable) { null }
      }

      class PersonService {
        def search(List<String> names) {
          names.find {}
          names.findAll {}
          Finder.where {}
        }
      }
      """);

    Collection<GrClosableBlock> closures = PsiTreeUtil.findChildrenOfType(file, GrClosableBlock.class);
    assertEquals(3, closures.size());
    for (GrClosableBlock closure : closures) {
      DelegatesToInfo info = GrDelegatesToUtilKt.getDelegatesToInfo(closure);
      assertTrue(closure.getParent().getText(), info == null || !info.getTypeToDelegate().getCanonicalText().startsWith("grails.gorm"));
    }
  }

  public void testNavigateToDomainProperty() {
    assertNavigatesToField("""
      class PersonService {
        def search() {
          Person.where { act<caret>ive == true }
        }
      }
      """, "active");
  }

  public void testNavigateInsideComposedQuery() {
    assertNavigatesToField("""
      class PersonService {
        def search() {
          def query = Person.where { active == true }
          query.where { ag<caret>e > 3 }
        }
      }
      """, "age");
  }

  public void testNavigateInsideDomainClass() {
    myFixture.configureFromExistingVirtualFile(myDomainFile.getVirtualFile());
    myFixture.getEditor().getCaretModel().moveToOffset(myDomainFile.getText().indexOf("age >= 18") + 1);

    PsiElement target = myFixture.getElementAtCaret();
    UsefulTestCase.assertInstanceOf(target, GrField.class);
    assertEquals("age", ((GrField)target).getName());
  }

  /** The reference resolves through the getter, which must still be renamed together with the field. */
  public void testRenamePropertyFromWhereQuery() {
    PsiFile file = myFixture.addFileToProject("src/groovy/PersonService.groovy", """
      class PersonService {
        def search() {
          Person.where { act<caret>ive == true }
        }
      }
      """);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    myFixture.renameElementAtCaret("enabled");

    myFixture.checkResult("""
      class PersonService {
        def search() {
          Person.where { enabled == true }
        }
      }
      """);
    assertTrue(myDomainFile.getText().contains("Boolean enabled = true"));
  }

  /** GORM transforms bare properties, but an explicit getter call still needs a receiver that provides it. */
  public void testGetterCallsAreNotResolvedAsProperties() {
    PsiFile file = myFixture.addFileToProject("src/groovy/PersonService.groovy", """
      import groovy.transform.CompileStatic

      class PersonService {
        @CompileStatic
        def staticallyChecked() {
          Person.where { <error descr="Cannot resolve symbol 'getAge'">getAge</error>() == 18 }
        }

        def dynamicallyChecked() {
          Person.where { <warning descr="Cannot resolve symbol 'getAge'">getAge</warning>() == 18 }
        }
      }
      """);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.checkHighlighting(true, false, true);
  }

  public void testExplicitGetterResolvesToClosureOwner() {
    PsiFile file = myFixture.addFileToProject("src/groovy/PersonService.groovy", """
      import groovy.transform.CompileStatic

      @CompileStatic
      class PersonService {
        Integer getAge() { 18 }

        def search() {
          Person.where { age == getAg<caret>e() }
        }
      }
      """);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.checkHighlighting(true, false, true);

    PsiElement target = myFixture.getElementAtCaret();
    UsefulTestCase.assertInstanceOf(target, PsiMethod.class);
    assertEquals("PersonService", ((PsiMethod)target).getContainingClass().getName());
  }

  /** Only persistent properties take part in a where query; transients and unknown names stay unresolved. */
  public void testTransientAndUnknownPropertiesAreNotResolved() {
    PsiFile file = myFixture.addFileToProject("src/groovy/PersonService.groovy", """
      class PersonService {
        def search() {
          Person.where { <warning descr="Cannot resolve symbol 'nickname'">nickname</warning> == 'x' && <warning descr="Cannot resolve symbol 'missing'">missing</warning> == 1 }
        }
      }
      """);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.checkHighlighting(true, false, true);
  }

  /** The properties belong to the where closure itself, not to closures nested in it or to unrelated calls. */
  public void testPropertiesAreNotContributedOutsideWhereClosures() {
    PsiFile file = myFixture.addFileToProject("src/groovy/PersonService.groovy", """
      class PersonService {
        def search(List<String> names) {
          names.find { <warning descr="Cannot resolve symbol 'age'">age</warning> > 1 }
          Person.where { names.each { <warning descr="Cannot resolve symbol 'active'">active</warning> } }
        }
      }
      """);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.checkHighlighting(true, false, true);
  }

  private static void assertDelegatesToPersonCriteria(GrClosableBlock closure) {
    DelegatesToInfo info = GrDelegatesToUtilKt.getDelegatesToInfo(closure);
    assertNotNull(closure.getParent().getText(), info);
    assertEquals(closure.getParent().getText(), "grails.gorm.DetachedCriteria<Person>", info.getTypeToDelegate().getCanonicalText());
    assertEquals(Closure.DELEGATE_FIRST, info.getStrategy());
  }

  private void assertNavigatesToField(String serviceText, String fieldName) {
    PsiFile file = myFixture.addFileToProject("src/groovy/PersonService.groovy", serviceText);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    PsiElement target = myFixture.getElementAtCaret();
    UsefulTestCase.assertInstanceOf(target, GrField.class);
    GrField field = (GrField)target;
    assertEquals(fieldName, field.getName());
    assertEquals("Person", field.getContainingClass().getName());
  }
}
