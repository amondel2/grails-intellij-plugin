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

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import org.apache.grails.intellij.lib.testFramework.GrailsTestCase;

import java.util.List;

public class GrailsViewFileTypeProviderTest extends GrailsTestCase {

  public void testGspIsAViewFileTypeWithoutAnyProvider() {
    assertTrue(GrailsViewFileTypeProvider.isViewFileType(GspFileType.GSP_FILE_TYPE));
  }

  public void testUnrelatedFileTypesAreNotViews() {
    assertFalse(GrailsViewFileTypeProvider.isViewFileType(PlainTextFileType.INSTANCE));
  }

  /**
   * The jsp content module carries the JSP view file types and loads only where the
   * {@code com.intellij.jsp} plugin is installed, as it is in the test sandbox. An empty list here
   * means the module did not load - most likely because it is missing from the {@code <content>}
   * block of {@code plugin.xml}, which nothing else in the build checks.
   */
  public void testJspModuleContributesItsViewFileTypes() {
    List<GrailsViewFileTypeProvider> providers = GrailsViewFileTypeProvider.EP_NAME.getExtensionList();
    assertFalse("no provider registered: the jsp content module did not load", providers.isEmpty());

    for (GrailsViewFileTypeProvider provider : providers) {
      for (FileType fileType : provider.getViewFileTypes()) {
        assertTrue(fileType.getName(), GrailsViewFileTypeProvider.isViewFileType(fileType));
      }
    }
  }
}
