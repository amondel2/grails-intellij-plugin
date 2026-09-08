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

package org.apache.grails.intellij.plugin.lang.gsp.debug;

import com.intellij.debugger.engine.JavaDebugAware;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.apache.grails.intellij.plugin.fileType.GspFileType;

/**
 * Lets the Java debugger put line breakpoints in GSP files. Replaces the deprecated
 * {@code LanguageFileType.isJVMDebuggingSupported} override on {@link GspFileType}; the positions
 * themselves are mapped by {@link GspPositionManager}.
 */
public final class GspJavaDebugAware extends JavaDebugAware {
  @Override
  public boolean isBreakpointAware(@NotNull PsiFile psiFile) {
    // The view provider's type covers every root of a GSP file, not just the GSP one.
    return psiFile.getViewProvider().getFileType() == GspFileType.GSP_FILE_TYPE;
  }
}
