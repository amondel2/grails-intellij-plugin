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

package org.apache.grails.intellij.module.spring;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiTarget;
import com.intellij.spring.model.jam.stereotype.CustomSpringComponent;
import com.intellij.spring.model.jam.stereotype.CustomSpringComponentPsiTarget;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

public class GrailsCustomSpringComponent extends CustomSpringComponent {

  private final String myBeanName;
  private final String[] myAliases;

  public GrailsCustomSpringComponent(@NotNull PsiClass psiClass, @NotNull String beanName) {
    this(psiClass, beanName, ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  /**
   * A bean known by further names. The Spring model takes two components of one class to be the same bean, so the
   * aliases have to be carried by the one component rather than registered as components of their own.
   */
  public GrailsCustomSpringComponent(@NotNull PsiClass psiClass, @NotNull String beanName, String @NotNull [] aliases) {
    super(psiClass);
    myBeanName = beanName;
    myAliases = aliases;
  }

  @Override
  public String getBeanName() {
    return myBeanName;
  }

  @Override
  public String @NotNull [] getAliases() {
    return myAliases;
  }

  @Override
  public PsiTarget getPsiTarget() {
    return new CustomSpringComponentPsiTarget(this);
  }
}
