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

package org.apache.grails.intellij.plugin.spring;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Grails 8 compile-time beans DSL: a {@code beans} closure property whose top-level statements are
 * {@code bean(...)}, {@code field(...)}, {@code method(...)} and {@code group(...)} declarations, compiled by
 * {@code grails.compiler.beans.GrailsBeans} into real {@code @Bean} factory methods.
 * <p>
 * The property is compiled on a class annotated with {@code @GrailsBeans}, and implicitly, when it looks like the
 * DSL, on a plugin descriptor (a concrete class named {@code *GrailsPlugin}), on the application class
 * ({@code grails.boot.config.GrailsAutoConfiguration}) and on a unit test ({@code org.grails.testing.GrailsUnitTest}).
 * These are the rules of the compiler's {@code GlobalGrailsClassInjectorTransformation} and
 * {@code GrailsBeansASTTransformation}. Nothing is recognised unless the {@code GrailsBeans} annotation is on the
 * class path, so pre-8 projects are unaffected.
 *
 * @see GrailsBeansDslMemberContributor
 */
public final class GrailsBeansDsl {

  public static final String GRAILS_BEANS_ANNOTATION = "grails.compiler.beans.GrailsBeans";
  public static final String BEANS_PROPERTY = "beans";

  private static final String PLUGIN_DESCRIPTOR_SUFFIX = "GrailsPlugin";
  private static final String GRAILS_AUTO_CONFIGURATION = "grails.boot.config.GrailsAutoConfiguration";
  private static final String GRAILS_UNIT_TEST = "org.grails.testing.GrailsUnitTest";

  private static final String TYPE_ARGUMENTS_CALL = "typeArguments";
  private static final String ALIASES_CALL = "aliases";
  private static final String CLASS_LITERAL = "class";
  private static final int MAX_CONSTANT_DEPTH = 16;

  public enum Kind {
    BEAN("bean"), FIELD("field"), METHOD("method"), GROUP("group");

    private final String myCallName;

    Kind(String callName) {
      myCallName = callName;
    }

    public String getCallName() {
      return myCallName;
    }

    static @Nullable Kind byCallName(@Nullable String callName) {
      for (Kind kind : values()) {
        if (kind.myCallName.equals(callName)) return kind;
      }
      return null;
    }
  }

  private GrailsBeansDsl() {
  }

  /**
   * Whether the beans DSL is available to code in the given context, i.e. the project builds against Grails 8+.
   */
  public static boolean isAvailable(@NotNull PsiElement context) {
    return JavaPsiFacade.getInstance(context.getProject()).findClass(GRAILS_BEANS_ANNOTATION, context.getResolveScope()) != null;
  }

  /**
   * Whether the compiler takes a {@code beans} property of the class as the DSL without {@code @GrailsBeans}, as
   * long as the property looks like the DSL: a plugin descriptor, the application class or a unit test.
   */
  private static boolean isImplicitHost(@NotNull PsiClass aClass) {
    return isPluginDescriptor(aClass)
           || InheritanceUtil.isInheritor(aClass, GRAILS_AUTO_CONFIGURATION)
           || InheritanceUtil.isInheritor(aClass, GRAILS_UNIT_TEST);
  }

  /**
   * A plugin descriptor as the compiler recognises one: by its name, whatever it extends, and only when concrete.
   */
  private static boolean isPluginDescriptor(@NotNull PsiClass aClass) {
    String name = aClass.getName();
    return name != null && name.endsWith(PLUGIN_DESCRIPTOR_SUFFIX)
           && !aClass.isInterface() && !aClass.hasModifierProperty(PsiModifier.ABSTRACT);
  }

  /**
   * The {@code beans} closure declared on the given class, if it is compiled as the beans DSL.
   */
  public static @Nullable GrClosableBlock getBeansClosure(@Nullable PsiClass aClass) {
    if (!(aClass instanceof GrTypeDefinition)) return null;
    PsiField field = ((GrTypeDefinition)aClass).findCodeFieldByName(BEANS_PROPERTY, false);
    if (!(field instanceof GrField) || !(((GrField)field).getInitializerGroovy() instanceof GrClosableBlock closure)) return null;
    return isBeansClosure(closure) ? closure : null;
  }

  /**
   * Whether the closure is the initializer of a {@code beans} property compiled as the beans DSL.
   */
  public static boolean isBeansClosure(@NotNull GrClosableBlock closure) {
    if (!(closure.getParent() instanceof GrField field) || !BEANS_PROPERTY.equals(field.getName())) return false;
    return CachedValuesManager.getCachedValue(closure, () -> Result.create(
      computeIsBeansClosure(field, closure),
      PsiModificationTracker.MODIFICATION_COUNT, ProjectRootManager.getInstance(closure.getProject())));
  }

  private static boolean computeIsBeansClosure(@NotNull GrField field, @NotNull GrClosableBlock closure) {
    PsiClass host = field.getContainingClass();
    if (host == null || !isAvailable(closure)) return false;
    // The annotation claims the property whatever it holds; the implicit hosts leave an unrelated beans closure alone
    if (host.hasAnnotation(GRAILS_BEANS_ANNOTATION)) return true;
    return isImplicitHost(host) && looksLikeBeansDsl(closure);
  }

  /**
   * The compiler's test for claiming the {@code beans} closure of an implicit host: a top-level statement that looks
   * like a declaration, or no statements at all. A bare name is taken as a declaration still being typed rather than
   * as code of something else.
   */
  private static boolean looksLikeBeansDsl(@NotNull GrClosableBlock closure) {
    boolean undecided = true;
    for (GrStatement statement : closure.getStatements()) {
      if (isDeclarationShaped(statement)) return true;
      if (!(statement instanceof GrReferenceExpression ref) || ref.isQualified()) undecided = false;
    }
    return undecided;
  }

  /**
   * Whether the statement looks like a declaration to the compiler: a {@code bean}/{@code field}/{@code method}/
   * {@code group} call somewhere along its qualifier chain, including a chain whose last qualifier is still being typed.
   */
  private static boolean isDeclarationShaped(@NotNull GrStatement statement) {
    PsiElement expression = statement;
    while (true) {
      if (expression instanceof GrMethodCall call) {
        if (!(call.getInvokedExpression() instanceof GrReferenceExpression ref)) return false;
        if (Kind.byCallName(ref.getReferenceName()) != null) return true;
        expression = ref.getQualifierExpression();
      }
      else if (expression instanceof GrReferenceExpression ref) {
        expression = ref.getQualifierExpression();
      }
      else {
        return false;
      }
    }
  }

  /**
   * Whether DSL declarations are written directly inside the closure: the {@code beans} closure itself, or the
   * body of one of its top-level {@code group(...)} declarations.
   */
  public static boolean isDeclarationContainer(@NotNull GrClosableBlock closure) {
    if (isBeansClosure(closure)) return true;
    Declaration declaration = getDeclarationOfBody(closure);
    return declaration != null && declaration.getKind() == Kind.GROUP;
  }

  /**
   * The declaration the closure is the body of, when it is the body of a {@code bean(...)}, {@code method(...)}
   * or {@code group(...)} declared directly in a {@code beans} closure or in one of its groups. As for the compiler,
   * the body is the closure passed last to the outermost call of the qualifier chain, never one passed inside it.
   */
  public static @Nullable Declaration getDeclarationOfBody(@NotNull GrClosableBlock closure) {
    PsiElement parent = closure.getParent();
    if (parent instanceof GrArgumentList) parent = parent.getParent();
    if (!(parent instanceof GrMethodCall statement) || !(statement.getParent() instanceof GrClosableBlock container)) return null;

    for (Declaration declaration : getDeclarations(container)) {
      if (declaration.getStatement() == statement) {
        if (declaration.getKind() == Kind.FIELD || declaration.getBody() != closure) return null;
        // Groups do not nest
        boolean declared = declaration.getKind() == Kind.GROUP ? isBeansClosure(container) : isDeclarationContainer(container);
        return declared ? declaration : null;
      }
    }
    return null;
  }

  /**
   * The DSL declarations written directly inside the closure. The closure is not checked to be a declaration
   * container: see {@link #isDeclarationContainer(GrClosableBlock)}.
   */
  public static @NotNull List<Declaration> getDeclarations(@NotNull GrClosableBlock container) {
    // The declarations memoize what their arguments resolve to, which other files can change
    return CachedValuesManager.getCachedValue(container, () -> Result.create(computeDeclarations(container),
                                                                             PsiModificationTracker.MODIFICATION_COUNT));
  }

  private static @NotNull List<Declaration> computeDeclarations(@NotNull GrClosableBlock container) {
    List<Declaration> result = new ArrayList<>();
    for (PsiElement statement : container.getStatements()) {
      if (!(statement instanceof GrMethodCall call)) continue;
      GrMethodCall root = getChainRoot(call);
      if (root == null) continue;
      Kind kind = Kind.byCallName(PsiUtil.getUnqualifiedMethodName(root));
      if (kind != null) {
        result.add(new Declaration(kind, root, call));
      }
    }
    return Collections.unmodifiableList(result);
  }

  /**
   * The beans the class declares through the DSL, including those inside its groups, with their aliases.
   */
  public static @NotNull List<GrailsResourceBeanExtractor.BeanDescriptor> getBeanDescriptors(@NotNull PsiClass aClass) {
    GrClosableBlock beansClosure = getBeansClosure(aClass);
    if (beansClosure == null) return Collections.emptyList();

    // Cached, as the resources.groovy and doWithSpring descriptors are, so the Spring model's recursion guard sees
    // the same descriptors on every pass. Folding the names resolves references, which is guarded here.
    return CachedValuesManager.getCachedValue(beansClosure, () -> {
      List<GrailsResourceBeanExtractor.BeanDescriptor> result = RecursionManager.doPreventingRecursion(
        beansClosure, true, () -> {
          List<GrailsResourceBeanExtractor.BeanDescriptor> descriptors = new ArrayList<>();
          collectBeanDescriptors(beansClosure, descriptors);
          return Collections.unmodifiableList(descriptors);
        });
      return Result.create(result == null ? Collections.emptyList() : result, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  private static void collectBeanDescriptors(@NotNull GrClosableBlock container, @NotNull List<GrailsResourceBeanExtractor.BeanDescriptor> result) {
    for (Declaration declaration : getDeclarations(container)) {
      if (declaration.getKind() == Kind.GROUP) {
        GrClosableBlock body = declaration.getBody();
        if (body != null) collectBeanDescriptors(body, result);
        continue;
      }
      if (declaration.getKind() != Kind.BEAN) continue;

      String name = declaration.getName();
      GrReferenceExpression typeReference = declaration.getTypeReference();
      if (name == null || typeReference == null) continue;

      GrailsResourceBeanExtractor.BeanDescriptor descriptor = new GrailsResourceBeanExtractor.BeanDescriptor(name);
      descriptor.getReferences().add(typeReference);
      for (String alias : declaration.getAliases()) {
        if (!alias.equals(name) && !descriptor.getAliases().contains(alias)) descriptor.getAliases().add(alias);
      }
      result.add(descriptor);
    }
  }

  /**
   * The classes in the production sources of the module (and the modules it depends on) whose {@code beans}
   * property may be compiled as the DSL. Test classes are left out: the beans they declare exist in tests only.
   */
  public static @NotNull Collection<PsiClass> findBeansHosts(@NotNull Module module) {
    Project project = module.getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    GlobalSearchScope librariesScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false);
    PsiClass annotation = facade.findClass(GRAILS_BEANS_ANNOTATION, librariesScope);
    if (annotation == null) return Collections.emptyList();

    GlobalSearchScope sourceScope = librariesScope.intersectWith(GlobalSearchScope.projectScope(project));
    Set<PsiClass> result = new LinkedHashSet<>(AnnotatedElementsSearch.searchPsiClasses(annotation, sourceScope).findAll());
    PsiClass autoConfiguration = facade.findClass(GRAILS_AUTO_CONFIGURATION, librariesScope);
    if (autoConfiguration != null) {
      result.addAll(ClassInheritorsSearch.search(autoConfiguration, sourceScope, true).findAll());
    }
    for (PsiClass candidate : AllClassesSearch.search(sourceScope, project, name -> name.endsWith(PLUGIN_DESCRIPTOR_SUFFIX)).findAll()) {
      if (isPluginDescriptor(candidate)) result.add(candidate);
    }
    return result;
  }

  /**
   * The unqualified call at the root of a qualifier chain such as {@code bean(Foo).primary().lazy()}.
   */
  private static @Nullable GrMethodCall getChainRoot(@NotNull GrMethodCall call) {
    GrMethodCall result = call;
    while (true) {
      if (!(result.getInvokedExpression() instanceof GrReferenceExpression ref)) return null;
      GrExpression qualifier = ref.getQualifierExpression();
      if (qualifier == null) return result;
      if (!(qualifier instanceof GrMethodCall qualifierCall)) return null;
      result = qualifierCall;
    }
  }

  /**
   * The reference naming the class in a type argument written {@code Foo} or {@code Foo.class}.
   */
  private static @Nullable GrReferenceExpression getClassReference(@Nullable GrExpression expression) {
    if (!(expression instanceof GrReferenceExpression ref)) return null;
    if (CLASS_LITERAL.equals(ref.getReferenceName()) && ref.getQualifierExpression() instanceof GrReferenceExpression qualifier) {
      return qualifier;
    }
    return ref;
  }

  private static boolean isTypeArgument(@NotNull GrExpression expression) {
    GrReferenceExpression ref = getClassReference(expression);
    if (ref == null) return false;
    PsiElement resolved = ref.resolve();
    if (resolved != null) return resolved instanceof PsiClass;
    String name = ref.getReferenceName();
    return name != null && !name.isEmpty() && Character.isUpperCase(name.charAt(0)) && !name.equals(name.toUpperCase());
  }

  /**
   * Folds a compile-time String constant the way the transform does for a declared name: a literal, a reference to
   * a {@code static final} field initialised to one, or a concatenation of those.
   */
  static @Nullable String evaluateStringConstant(@Nullable GrExpression expression, int depth) {
    if (expression == null || depth > MAX_CONSTANT_DEPTH) return null;

    if (expression instanceof GrParenthesizedExpression parenthesized) {
      return evaluateStringConstant(parenthesized.getOperand(), depth + 1);
    }
    if (expression instanceof GrLiteral literal) {
      return literal.getValue() instanceof String value ? value : null;
    }
    if (expression instanceof GrBinaryExpression binary && binary.getOperationTokenType() == GroovyTokenTypes.mPLUS) {
      String left = evaluateStringConstant(binary.getLeftOperand(), depth + 1);
      String right = left == null ? null : evaluateStringConstant(binary.getRightOperand(), depth + 1);
      return right == null ? null : left + right;
    }
    if (!(expression instanceof GrReferenceExpression ref)) return null;

    PsiElement resolved = ref.resolve();
    // A Groovy property is reached through its getter from outside its class
    if (resolved instanceof GrAccessorMethod accessor) resolved = accessor.getProperty();
    if (!(resolved instanceof PsiField field)
        || !field.hasModifierProperty(PsiModifier.STATIC) || !field.hasModifierProperty(PsiModifier.FINAL)) {
      return null;
    }
    if (field instanceof GrField grField) {
      return evaluateStringConstant(grField.getInitializerGroovy(), depth + 1);
    }
    return field.computeConstantValue() instanceof String value ? value : null;
  }

  /**
   * One top-level {@code bean}/{@code field}/{@code method}/{@code group} statement, with its chained qualifiers.
   * What its arguments resolve to is computed once, and lives as long as the cached declarations do.
   */
  public static final class Declaration {
    private final Kind myKind;
    private final GrMethodCall myRoot;
    private final GrMethodCall myStatement;
    private final NotNullLazyValue<Head> myHead = NotNullLazyValue.volatileLazy(this::computeHead);
    private final NullableLazyValue<String> myName = NullableLazyValue.volatileLazyNullable(this::computeName);
    private final NullableLazyValue<PsiType> myType = NullableLazyValue.volatileLazyNullable(this::computeType);

    /**
     * The {@code [name, ] Type} arguments a declaration starts with.
     */
    private record Head(@Nullable GrExpression name, @Nullable GrExpression type) {
    }

    private Declaration(@NotNull Kind kind, @NotNull GrMethodCall root, @NotNull GrMethodCall statement) {
      myKind = kind;
      myRoot = root;
      myStatement = statement;
    }

    public @NotNull Kind getKind() {
      return myKind;
    }

    /**
     * The whole statement, i.e. the last call of the qualifier chain.
     */
    public @NotNull GrMethodCall getStatement() {
      return myStatement;
    }

    public @Nullable GrExpression getNameExpression() {
      return myHead.getValue().name();
    }

    public @Nullable GrExpression getTypeExpression() {
      return myHead.getValue().type();
    }

    /**
     * The reference to the declared type's class, whether the type is written {@code Foo} or {@code Foo.class}.
     */
    public @Nullable GrReferenceExpression getTypeReference() {
      return getClassReference(getTypeExpression());
    }

    private @NotNull Head computeHead() {
      GrExpression[] arguments = myRoot.getExpressionArguments();
      GrExpression first = arguments.length > 0 ? arguments[0] : null;
      if (myKind == Kind.GROUP) return new Head(first, null);
      if (first == null) return new Head(null, null);
      if (isTypeArgument(first)) return new Head(null, first);
      return new Head(first, arguments.length > 1 ? arguments[1] : null);
    }

    /**
     * The element to navigate to for the declared member: its name when given, else its type.
     */
    public @NotNull PsiElement getNavigationElement() {
      GrExpression name = getNameExpression();
      if (name != null) return name;
      GrExpression type = getTypeExpression();
      return type != null ? type : myRoot;
    }

    /**
     * The declared name: a String constant when given, else the decapitalized simple name of the declared type.
     */
    public @Nullable String getName() {
      return myName.getValue();
    }

    private @Nullable String computeName() {
      GrExpression nameExpression = getNameExpression();
      if (nameExpression != null) {
        return evaluateStringConstant(nameExpression, 0);
      }
      GrReferenceExpression typeReference = getTypeReference();
      if (typeReference == null) return null;
      // The compiler names it after the class, which an import alias does not rename
      String typeName = typeReference.resolve() instanceof PsiClass typeClass ? typeClass.getName() : typeReference.getReferenceName();
      return typeName == null ? null : StringUtil.decapitalize(typeName);
    }

    /**
     * The further names a bean is registered under: the String constants passed to {@code .aliases(...)}.
     */
    public @NotNull List<String> getAliases() {
      GrMethodCall aliases = myKind == Kind.BEAN ? findQualifier(ALIASES_CALL) : null;
      if (aliases == null) return Collections.emptyList();

      List<String> result = new ArrayList<>();
      for (GrExpression argument : aliases.getExpressionArguments()) {
        String alias = evaluateStringConstant(argument, 0);
        if (alias != null && !alias.isBlank()) result.add(alias);
      }
      return result;
    }

    /**
     * The declared type, carrying any {@code .typeArguments(...)} chained onto the declaration.
     */
    public @Nullable PsiType getType() {
      return myType.getValue();
    }

    private @Nullable PsiType computeType() {
      GrReferenceExpression typeReference = getTypeReference();
      if (typeReference == null || !(typeReference.resolve() instanceof PsiClass typeClass)) {
        return null;
      }

      JavaPsiFacade facade = JavaPsiFacade.getInstance(typeClass.getProject());
      List<PsiType> typeArguments = getTypeArguments();
      if (typeArguments.size() == typeClass.getTypeParameters().length && !typeArguments.isEmpty()) {
        return facade.getElementFactory().createType(typeClass, typeArguments.toArray(PsiType.EMPTY_ARRAY));
      }
      return facade.getElementFactory().createType(typeClass);
    }

    private @NotNull List<PsiType> getTypeArguments() {
      GrMethodCall call = findQualifier(TYPE_ARGUMENTS_CALL);
      if (call == null) return Collections.emptyList();

      List<PsiType> result = new ArrayList<>();
      for (GrExpression argument : call.getExpressionArguments()) {
        GrReferenceExpression argumentReference = getClassReference(argument);
        if (argumentReference == null || !(argumentReference.resolve() instanceof PsiClass argumentClass)) {
          return Collections.emptyList();
        }
        PsiClassType argumentType = JavaPsiFacade.getElementFactory(argumentClass.getProject()).createType(argumentClass);
        result.add(argumentType);
      }
      return result;
    }

    /**
     * The qualifier call of the given name chained onto the declaration.
     */
    private @Nullable GrMethodCall findQualifier(@NotNull String name) {
      for (GrMethodCall call = myStatement; call != myRoot; ) {
        if (!(call.getInvokedExpression() instanceof GrReferenceExpression ref)) break;
        if (name.equals(ref.getReferenceName())) return call;
        if (!(ref.getQualifierExpression() instanceof GrMethodCall qualifier)) break;
        call = qualifier;
      }
      return null;
    }

    /**
     * The closure the declaration's body is written in: as for the compiler, the closure passed last to the
     * outermost call of the qualifier chain. A closure passed to a call inside the chain is not the body.
     */
    public @Nullable GrClosableBlock getBody() {
      GrClosableBlock[] closures = myStatement.getClosureArguments();
      if (closures.length > 0) return closures[closures.length - 1];
      GrExpression[] arguments = myStatement.getExpressionArguments();
      return arguments.length > 0 && arguments[arguments.length - 1] instanceof GrClosableBlock closure ? closure : null;
    }

    /**
     * The class the {@code beans} property is declared on.
     */
    public @Nullable PsiClass getHostClass() {
      return PsiTreeUtil.getParentOfType(myRoot, PsiClass.class);
    }
  }
}
