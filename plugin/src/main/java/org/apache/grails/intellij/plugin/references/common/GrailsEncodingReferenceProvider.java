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

package org.apache.grails.intellij.plugin.references.common;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.util.Collection;

public class GrailsEncodingReferenceProvider extends PsiReferenceProvider {
  @Override
  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    return new PsiReference[]{new PsiReferenceBase<>(element, false) {
      @Override
      public PsiElement resolve() {
        return null;
      }

      @Override
      public Object @NotNull [] getVariants() {
        Collection<Charset> charsets = Charset.availableCharsets().values();

        LookupElement[] res = new LookupElement[charsets.size()];
        int i = 0;
        for (Charset charset : charsets) {
          res[i++] = LookupElementBuilder.create(charset.name()).withCaseSensitivity(false);
        }

        return res;
      }
    }};
  }
}
