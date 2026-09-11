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

package org.apache.grails.intellij.plugin.fileType;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * Contributes file types that render a Grails view besides GSP.
 * <p>
 * The types live behind an extension point because the plugins that define them need not be
 * installed: the JSP file types come from the {@code com.intellij.jsp} plugin, which IntelliJ IDEA
 * stopped bundling in 2026.2, so the module contributing them loads only where it is present.
 */
public interface GrailsViewFileTypeProvider {

  ExtensionPointName<GrailsViewFileTypeProvider> EP_NAME =
    ExtensionPointName.create("org.intellij.grails.viewFileTypeProvider");

  @NotNull @Unmodifiable Collection<FileType> getViewFileTypes();

  /** Whether a file of this type belongs in a controller's views directory. */
  static boolean isViewFileType(@NotNull FileType fileType) {
    if (fileType == GspFileType.GSP_FILE_TYPE) return true;
    for (GrailsViewFileTypeProvider provider : EP_NAME.getExtensionList()) {
      if (provider.getViewFileTypes().contains(fileType)) return true;
    }
    return false;
  }
}
