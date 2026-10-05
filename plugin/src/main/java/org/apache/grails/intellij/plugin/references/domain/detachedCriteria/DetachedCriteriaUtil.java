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

package org.apache.grails.intellij.plugin.references.domain.detachedCriteria;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.apache.grails.intellij.plugin.gorm.GormClassNames;
import org.apache.grails.intellij.plugin.references.domain.DomainDescriptor;
import org.apache.grails.intellij.plugin.util.GrailsArtifact;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;

import java.util.Map;
import java.util.Set;

public final class DetachedCriteriaUtil {

  public static final String DETACHED_CRITERIA_CLASS = "grails.gorm.DetachedCriteria";

  // See DetachedCriteriaTransformer: the methods whose closure argument is transformed into a where query.
  private static final Set<String> WHERE_QUERY_METHODS = Set.of("where", "whereAny", "whereLazy", "find", "findAll");

  private DetachedCriteriaUtil() {
  }

  public static boolean isDomainDetachedCriteriaMethod(@NotNull PsiMethod method) {
    if (GrLightMethodBuilder.checkKind(method, DomainDescriptor.DOMAIN_DYNAMIC_METHOD)) {
      String methodName = method.getName();
      return "where".equals(methodName) || "findAll".equals(methodName) || "find".equals(methodName);
    }

    return false;
  }

  public static @Nullable PsiClass getDomainFromSubstitutor(@NotNull GroovyResolveResult resolveResult) {
    PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    Map<PsiTypeParameter,PsiType> substitutionMap = substitutor.getSubstitutionMap();
    if (substitutionMap.size() != 1) return null;

    PsiClass res = PsiTypesUtil.getPsiClass(substitutionMap.values().iterator().next());
    return GrailsArtifact.DOMAIN.isInstance(res) ? res : null;
  }

  public static @Nullable PsiClass getDomainClassByDetachedCriteriaExpression(@Nullable PsiType type) {
    // Any class type, not just an inferred one: a declared DetachedCriteria<Person> (a parameter or a field) is a
    // PsiClassReferenceType.
    if (!(type instanceof PsiClassType classType)) return null;

    PsiClass detachedCriteriaClass = classType.resolve();
    if (detachedCriteriaClass == null || !DETACHED_CRITERIA_CLASS.equals(detachedCriteriaClass.getQualifiedName())) {
      return null;
    }

    PsiType[] parameters = classType.getParameters();
    if (parameters.length != 1) return null;

    PsiClass domainClass = PsiTypesUtil.getPsiClass(parameters[0]);
    if (!GrailsArtifact.DOMAIN.isInstance(domainClass)) return null;

    return domainClass;
  }

  /**
   * The call {@code closure} is passed to when that call is one GORM turns into a where query
   * ({@code Person.where { ... }}, {@code criteria.where { ... }}), whatever it resolves to.
   */
  public static @Nullable GrMethodCall getWhereQueryCall(@NotNull GrClosableBlock closure) {
    PsiElement parent = closure.getParent();
    if (parent instanceof GrArgumentList) parent = parent.getParent();
    if (!(parent instanceof GrMethodCall call)) return null;

    if (!(call.getInvokedExpression() instanceof GrReferenceExpression invoked)) return null;
    return WHERE_QUERY_METHODS.contains(invoked.getReferenceName()) ? call : null;
  }

  /**
   * The domain class a where query is started on: the qualifier of {@code Person.where { ... }}, or the class an
   * unqualified call is made from. Null when that is not a domain class, e.g. when composing an existing criteria.
   */
  public static @Nullable PsiClass getWhereQueryDomainClass(@NotNull GrMethodCall call) {
    if (!(call.getInvokedExpression() instanceof GrReferenceExpression invoked)) return null;

    GrExpression qualifier = invoked.getQualifierExpression();
    PsiClass domainClass;
    if (qualifier == null) {
      domainClass = PsiTreeUtil.getParentOfType(call, PsiClass.class);
    }
    else if (qualifier instanceof GrReferenceExpression qualifierRef && qualifierRef.resolve() instanceof PsiClass qualifierClass) {
      domainClass = qualifierClass;
    }
    else {
      return null;
    }

    return GrailsArtifact.DOMAIN.isInstance(domainClass) ? domainClass : null;
  }

  /**
   * Whether {@code method} is one of the where query methods of the GORM 4+ {@code GormEntity} trait. Below GORM 4 they
   * are light methods instead, see {@link #isDomainDetachedCriteriaMethod(PsiMethod)}.
   */
  public static boolean isGormEntityWhereQueryMethod(@NotNull PsiMethod method) {
    return WHERE_QUERY_METHODS.contains(method.getName())
           && InheritanceUtil.isInheritor(method.getContainingClass(), GormClassNames.ENTITY_TRAIT);
  }

  /** {@code DetachedCriteria<domainClass>}, or null when DetachedCriteria is not on the classpath of {@code context}. */
  public static @Nullable PsiClassType createDetachedCriteriaType(@NotNull PsiClass domainClass, @NotNull PsiElement context) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(context.getProject());
    PsiClass detachedCriteriaClass = facade.findClass(DETACHED_CRITERIA_CLASS, context.getResolveScope());
    if (detachedCriteriaClass == null || detachedCriteriaClass.getTypeParameters().length != 1) return null;

    PsiElementFactory factory = facade.getElementFactory();
    return factory.createType(detachedCriteriaClass, factory.createType(domainClass));
  }
}
