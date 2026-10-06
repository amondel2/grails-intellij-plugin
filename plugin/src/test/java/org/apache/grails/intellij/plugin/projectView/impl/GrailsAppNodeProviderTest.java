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

package org.apache.grails.intellij.plugin.projectView.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileSystemItemFilter;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.apache.grails.intellij.plugin.artefact.api.ArtefactHandlers;
import org.apache.grails.intellij.plugin.artefact.api.GrailsDisplayableArtefactHandler;
import org.apache.grails.intellij.plugin.projectView.NodeWeights;
import org.apache.grails.intellij.plugin.projectView.nodes.GrailsArtefactHandlerNode;
import org.apache.grails.intellij.plugin.projectView.nodes.GrailsPsiDirectoryNode;
import org.apache.grails.intellij.plugin.projectView.nodes.OtherGrailsAppSourcesNode;
import org.apache.grails.intellij.plugin.structure.GrailsApplication;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GrailsAppNodeProviderTest extends GrailsNodeProviderTestSupport {

  /** The Grails 3+ test source roots, which render as nodes of their own after src. */
  private static final Set<String> GRAILS_TEST_ROOTS = Set.of("test", "integration-test", "functional-test");

  public void testRendersTranslationsAndAssetSubfolderNodes() {
    addAssetAndTranslationFixture();

    Collection<AbstractTreeNode<?>> nodes = new GrailsAppNodeProvider().createNodes(testApplication(true), ViewSettings.DEFAULT);

    GrailsPsiDirectoryNode translations = findNode(nodes, "i18n");
    assertNotNull("Translations node must be present", translations);
    assertEquals(NodeWeights.TRANSLATIONS_FOLDER, translations.getNodeWeight());
    assertSame(AllIcons.FileTypes.Properties, translations.getNodeIcon());

    GrailsPsiDirectoryNode stylesheets = findNode(nodes, "stylesheets");
    assertNotNull("Stylesheets node must be present", stylesheets);
    assertEquals(NodeWeights.STYLESHEETS_FOLDER, stylesheets.getNodeWeight());
    assertSame(AllIcons.FileTypes.Css, stylesheets.getNodeIcon());

    GrailsPsiDirectoryNode images = findNode(nodes, "images");
    assertNotNull("Images node must be present", images);
    assertEquals(NodeWeights.IMAGES_FOLDER, images.getNodeWeight());
    assertSame(AllIcons.FileTypes.Image, images.getNodeIcon());

    GrailsPsiDirectoryNode javascripts = findNode(nodes, "javascripts");
    assertNotNull("JavaScripts node must be present", javascripts);
    assertEquals(NodeWeights.JAVASCRIPTS_FOLDER, javascripts.getNodeWeight());
    assertSame(AllIcons.FileTypes.JavaScript, javascripts.getNodeIcon());

    GrailsPsiDirectoryNode utils = findNode(nodes, "utils");
    assertNotNull("Utils node must be present", utils);
    assertEquals(NodeWeights.UTILS_FOLDER, utils.getNodeWeight());
    assertSame(AllIcons.Nodes.Class, utils.getNodeIcon());

    GrailsPsiDirectoryNode migrations = findNode(nodes, "migrations");
    assertNotNull("Migrations node must be present", migrations);
    assertEquals(NodeWeights.MIGRATIONS_FOLDER, migrations.getNodeWeight());
    assertSame(AllIcons.Nodes.DataSchema, migrations.getNodeIcon());
  }

  public void testRendersTitlesForNewNodes() {
    addAssetAndTranslationFixture();

    Collection<AbstractTreeNode<?>> nodes = new GrailsAppNodeProvider().createNodes(testApplication(true), ViewSettings.DEFAULT);

    assertTitleAndIcon(findNode(nodes, "i18n"), "Translations", AllIcons.FileTypes.Properties);
    assertTitleAndIcon(findNode(nodes, "utils"), "Utils", AllIcons.Nodes.Class);
    assertTitleAndIcon(findNode(nodes, "migrations"), "Migrations", AllIcons.Nodes.DataSchema);
    assertTitleAndIcon(findNode(nodes, "images"), "Images", AllIcons.FileTypes.Image);
    assertTitleAndIcon(findNode(nodes, "javascripts"), "JavaScripts", AllIcons.FileTypes.JavaScript);
    assertTitleAndIcon(findNode(nodes, "stylesheets"), "Stylesheets", AllIcons.FileTypes.Css);
  }

  public void testMissingAssetSubfolderProducesNoNode() {
    myFixture.addFileToProject("grails-app/i18n/messages.properties", "a=b");
    myFixture.addFileToProject("grails-app/assets/stylesheets/app.css", "h1 {}");

    Collection<AbstractTreeNode<?>> nodes = new GrailsAppNodeProvider().createNodes(testApplication(true), ViewSettings.DEFAULT);

    assertNotNull("Stylesheets node must be present", findNode(nodes, "stylesheets"));
    assertNull("no node for grails-app/assets/images, which is absent", findNode(nodes, "images"));
    assertNull("no node for grails-app/assets/javascripts, which is absent", findNode(nodes, "javascripts"));
    assertNull("no fake fonts node", findNode(nodes, "fonts"));
    assertNull("no node for grails-app/migrations, which is absent", findNode(nodes, "migrations"));
    assertNull("no node for grails-app/utils, which is absent", findNode(nodes, "utils"));
    assertNull("no node for grails-app/views, which is absent", findNode(nodes, "views"));
    assertNull("no node for grails-app/init, which is absent", findNode(nodes, "init"));
  }

  public void testOtherGrailsAppSourcesNodeExcludesTranslationsAndAssetSubfolders() {
    addAssetAndTranslationFixture();

    OtherGrailsAppSourcesNode node = otherSourcesNode(testApplication(true));

    Collection<AbstractTreeNode<?>> children = node.getChildrenImpl();
    Set<String> childNames = childNames(children);
    assertFalse("i18n must not duplicate under Other sources", childNames.contains("i18n"));
    assertFalse("utils must not duplicate under Other sources", childNames.contains("utils"));
    assertFalse("migrations must not duplicate under Other sources", childNames.contains("migrations"));

    PsiDirectoryNode assetsChild = assetsNodeUnderOtherSources(children);
    PsiFileSystemItemFilter filter = assetsChild.getFilter();
    assertNotNull("assets node must carry a filter hiding the dedicated subfolders", filter);
    PsiDirectory assetsDir = assetsChild.getValue();
    assertFalse("a file in assets/stylesheets must not be shown here, it has its own node",
                filter.shouldShow(assetsDir.findSubdirectory("stylesheets")));
    assertFalse("a file in assets/images must not be shown here, it has its own node",
                filter.shouldShow(assetsDir.findSubdirectory("images")));
    assertFalse("a file in assets/javascripts must not be shown here, it has its own node",
                filter.shouldShow(assetsDir.findSubdirectory("javascripts")));
    assertTrue("a file directly under assets must stay visible, no dedicated node claims it",
               filter.shouldShow(assetsDir.findFile("extra.txt")));
  }

  public void testNestedVendorAssetFoldersStayVisible() {
    addAssetAndTranslationFixture();
    myFixture.addFileToProject("grails-app/assets/vendor/jquery-ui/jquery-ui.js", "console.log(2);");
    myFixture.addFileToProject("grails-app/assets/vendor/jquery-ui/images/ui-icons.png", "fake-png");

    OtherGrailsAppSourcesNode node = otherSourcesNode(testApplication(true));

    PsiDirectoryNode assetsChild = assetsNodeUnderOtherSources(node.getChildrenImpl());
    PsiFileSystemItemFilter filter = assetsChild.getFilter();
    assertNotNull("assets node must carry a filter hiding the dedicated subfolders", filter);
    PsiDirectory assetsDir = assetsChild.getValue();

    PsiDirectory vendor = assetsDir.findSubdirectory("vendor");
    assertNotNull("grails-app/assets/vendor", vendor);
    PsiDirectory jqueryUi = vendor.findSubdirectory("jquery-ui");
    assertNotNull("grails-app/assets/vendor/jquery-ui", jqueryUi);
    PsiDirectory jqueryUiImages = jqueryUi.findSubdirectory("images");
    assertNotNull("grails-app/assets/vendor/jquery-ui/images", jqueryUiImages);

    assertTrue("a nested folder named like a dedicated asset subfolder stays visible",
               filter.shouldShow(jqueryUiImages));
    assertTrue("contains() must claim a file under a visible nested asset folder",
               node.contains(myFixture.findFileInTempDir("grails-app/assets/vendor/jquery-ui/images/ui-icons.png")));

    assertFalse("the direct images subfolder stays hidden", filter.shouldShow(assetsDir.findSubdirectory("images")));
    assertFalse("the direct javascripts subfolder stays hidden", filter.shouldShow(assetsDir.findSubdirectory("javascripts")));
    assertFalse("the direct stylesheets subfolder stays hidden", filter.shouldShow(assetsDir.findSubdirectory("stylesheets")));
    assertFalse("contains() must still refuse a file under a hidden asset subfolder",
                node.contains(myFixture.findFileInTempDir("grails-app/assets/stylesheets/app.css")));
  }

  public void testContainsExcludesHiddenAssetSubfoldersAndSpecialFolders() {
    addAssetAndTranslationFixture();
    myFixture.addFileToProject("grails-app/views/index.gsp", "<html/>");
    myFixture.addFileToProject("grails-app/assets/fonts/webfont.woff", "stray-font");

    GrailsApplication application = testApplication(true);
    OtherGrailsAppSourcesNode node = otherSourcesNode(application);

    assertFalse("css under a hidden asset subfolder must not be claimed (reveal dead-end)",
                node.contains(myFixture.findFileInTempDir("grails-app/assets/stylesheets/app.css")));
    assertFalse("messages under the extracted Translations folder must not be claimed",
                node.contains(myFixture.findFileInTempDir("grails-app/i18n/messages.properties")));
    assertFalse("codecs under the extracted Utils folder must not be claimed",
                node.contains(myFixture.findFileInTempDir("grails-app/utils/ShoutyCodec.groovy")));
    assertFalse("changelogs under the extracted Migrations folder must not be claimed",
                node.contains(myFixture.findFileInTempDir("grails-app/migrations/changelog.groovy")));
    assertFalse("views are rendered as a dedicated node, not here",
                node.contains(myFixture.findFileInTempDir("grails-app/views/index.gsp")));
    assertTrue("a file directly under assets must stay claimed, no dedicated node claims it",
               node.contains(myFixture.findFileInTempDir("grails-app/assets/extra.txt")));
    assertTrue("non-hidden asset subfolders stay visible",
               node.contains(myFixture.findFileInTempDir("grails-app/assets/fonts/webfont.woff")));
  }

  /**
   * Pins Q-A: hidden from Other sources means exactly a registered special folder or a registered
   * asset subfolder. An artefact-handler directory is rendered under Other sources with a filter,
   * so contains() keeps claiming files below it and the filter decides per file.
   */
  public void testArtefactHandlerDirectoriesStayUnderOtherSources() {
    myFixture.addFileToProject("grails-app/assets/fonts/webfont.woff", "stray-font");
    myFixture.addFileToProject("grails-app/controllers/FooController.groovy",
                               "class FooController { def index() {} }");
    myFixture.addFileToProject("grails-app/controllers/ControllerHelper.groovy",
                               "class ControllerHelper { String shout() { 'hi' } }");
    myFixture.addFileToProject("grails-app/services/BookService.groovy", "class BookService { }");

    OtherGrailsAppSourcesNode node = otherSourcesNode(testApplication(true));

    Collection<AbstractTreeNode<?>> children = node.getChildrenImpl();
    PsiDirectoryNode controllers = findDirectoryNode(children, "controllers");
    assertNotNull("the controllers directory must be rendered under Other sources, it is not a special folder",
                  controllers);
    PsiFileSystemItemFilter filter = controllers.getFilter();
    assertNotNull("an artefact-handler directory carries a filter that hides its artefacts", filter);

    PsiFile artefact = controllers.getValue().findFile("FooController.groovy");
    PsiFile plain = controllers.getValue().findFile("ControllerHelper.groovy");
    assertNotNull(artefact);
    assertNotNull(plain);

    assertFalse("a displayable artefact is rendered by its own handler node, not under Other sources",
                filter.shouldShow(artefact));
    assertTrue("a non-artefact file of an artefact-handler directory must stay visible",
               filter.shouldShow(plain));

    assertFalse("contains() must refuse a displayable artefact, the directory filter refuses it",
                node.contains(artefact.getVirtualFile()));
    assertTrue("contains() must keep claiming a non-artefact file of an artefact-handler directory",
               node.contains(plain.getVirtualFile()));

    PsiDirectoryNode assets = assetsNodeUnderOtherSources(children);
    assertTrue("an assets subfolder that is not a registered asset subfolder must stay visible",
               assets.getFilter().shouldShow(assets.getValue().findSubdirectory("fonts")));
    assertTrue("contains() must keep claiming a file of an unregistered assets subfolder",
               node.contains(myFixture.findFileInTempDir("grails-app/assets/fonts/webfont.woff")));
  }

  public void testHandlerWeightOrderForComparator() {
    myFixture.addFileToProject("grails-app/domain/Book.groovy", "class Book { }");
    myFixture.addFileToProject("grails-app/services/BookService.groovy", "class BookService { }");
    myFixture.addFileToProject("grails-app/controllers/BookController.groovy", "class BookController { }");
    myFixture.addFileToProject("grails-app/taglib/BookLib.groovy", "class BookLib { }");

    GrailsApplication application = testApplication(true);
    List<GrailsArtefactHandlerNode> handlers = new GrailsAppNodeProvider().createNodes(application, ViewSettings.DEFAULT)
      .stream()
      .filter(GrailsArtefactHandlerNode.class::isInstance)
      .map(GrailsArtefactHandlerNode.class::cast)
      .sorted(new GrailsNodeComparator(getProject(), "GrailsView"))
      .toList();

    assertEquals("the rendered handler nodes must sort by the weights their handlers report",
                 List.of("Domain Classes", "Services", "Controllers", "Tag Libraries"),
                 handlers.stream().map(handler -> handler.getValue().getTitle()).toList());

    // Spelled as literals rather than as NodeWeights references: compared against the constants, this
    // would compare each handler's weight to the value it returns by construction and could not fail.
    // Interceptors is pinned here because isVisible() needs a Grails 3+ application, so this fixture's
    // provider never renders the node that would otherwise cover it.
    Map<String, Integer> reported = new LinkedHashMap<>();
    for (GrailsDisplayableArtefactHandler handler : ArtefactHandlers.displayableArtefactHandlers()) {
      reported.put(handler.getTitle(), handler.getWeight());
    }
    assertEquals("every displayable artefact handler must report the weight that orders its node",
                 Map.of("Domain Classes", 20,
                        "Services", 25,
                        "Controllers", 30,
                        "Interceptors", 31,
                        "Tag Libraries", 90),
                 reported);
  }

  public void testDirectoryWeightOrderForComparator() {
    addAssetAndTranslationFixture();
    myFixture.addFileToProject("grails-app/init/application.groovy", "class Application { }");
    myFixture.addFileToProject("grails-app/views/index.gsp", "<html/>");
    myFixture.addFileToProject("grails-app/conf/application.yml", "grails: {}");
    myFixture.addFileToProject("src/main/groovy/Application.groovy", "class Application {}");
    myFixture.addFileToProject("src/test/groovy/ApplicationSpec.groovy", "class ApplicationSpec {}");
    myFixture.addFileToProject("src/integration-test/groovy/BookServiceIT.groovy", "class BookServiceIT {}");
    myFixture.addFileToProject("src/functional-test/groovy/BookServiceFT.groovy", "class BookServiceFT {}");

    GrailsApplication application = testApplication(true);
    List<AbstractTreeNode<?>> nodes = new ArrayList<>(new GrailsAppNodeProvider().createNodes(application, ViewSettings.DEFAULT));
    // src and the Grails test roots come from the Grails 3+ provider, so the pane's order only
    // exists once both providers' output is sorted together.
    nodes.addAll(new Grails3NodeProvider().createNodes(application, ViewSettings.DEFAULT));

    List<GrailsPsiDirectoryNode> rendered = sortedDirectoryNodes(nodes);
    List<GrailsPsiDirectoryNode> dedicated = rendered.stream()
      .filter(node -> !GRAILS_TEST_ROOTS.contains(node.getValue().getName()))
      .toList();

    assertEquals("the dedicated Grails nodes must render in weight order",
                 List.of("Images", "JavaScripts", "Stylesheets", "Views", "Migrations", "Translations", "Utils",
                         "Initialization", "Configuration", "grails-app", "src"),
                 dedicated.stream().map(GrailsAppNodeProviderTest::presentableName).toList());
    assertEquals("the Grails test roots must render after src, all three of them",
                 GRAILS_TEST_ROOTS,
                 rendered.subList(dedicated.size(), rendered.size()).stream()
                   .map(node -> node.getValue().getName())
                   .collect(Collectors.toCollection(LinkedHashSet::new)));

    GrailsPsiDirectoryNode otherSources = findNode(nodes, "grails-app");
    assertNotNull("the Other sources node must be present", otherSources);
    assertTrue("the grails-app directory renders as the Other sources node",
               otherSources instanceof OtherGrailsAppSourcesNode);
    assertEquals("the grails-app directory is labelled by its location string", "Other sources",
                 rendered(otherSources).getLocationString());
  }

  private List<GrailsPsiDirectoryNode> sortedDirectoryNodes(@NotNull Collection<AbstractTreeNode<?>> nodes) {
    return nodes.stream()
      .filter(GrailsPsiDirectoryNode.class::isInstance)
      .map(GrailsPsiDirectoryNode.class::cast)
      .sorted(new GrailsNodeComparator(getProject(), "GrailsView"))
      .toList();
  }

  private static @NotNull String presentableName(@NotNull GrailsPsiDirectoryNode node) {
    return rendered(node).getPresentableText();
  }

  private static Set<String> childNames(@NotNull Collection<AbstractTreeNode<?>> children) {
    return children.stream()
      .map(child -> child.getValue() instanceof PsiDirectory directory
                    ? directory.getName() : String.valueOf(child.getValue()))
      .collect(Collectors.toSet());
  }

  private static @Nullable PsiDirectoryNode findDirectoryNode(@NotNull Collection<AbstractTreeNode<?>> children,
                                                             @NotNull String name) {
    for (AbstractTreeNode<?> child : children) {
      if (child instanceof PsiDirectoryNode directory && name.equals(directory.getValue().getName())) {
        return directory;
      }
    }
    return null;
  }

  }
