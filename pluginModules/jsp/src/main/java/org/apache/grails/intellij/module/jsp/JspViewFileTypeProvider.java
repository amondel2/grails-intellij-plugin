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

package org.apache.grails.intellij.module.jsp;

import com.intellij.jsp.highlighter.JspxFileType;
import com.intellij.jsp.highlighter.NewJspFileType;
import com.intellij.openapi.fileTypes.FileType;
import org.apache.grails.intellij.plugin.fileType.GrailsViewFileTypeProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;

/** Counts {@code .jsp} and {@code .jspx} files in a controller's views directory as views. */
public final class JspViewFileTypeProvider implements GrailsViewFileTypeProvider {

  @Override
  public @NotNull @Unmodifiable Collection<FileType> getViewFileTypes() {
    return List.of(NewJspFileType.INSTANCE, JspxFileType.INSTANCE);
  }
}
