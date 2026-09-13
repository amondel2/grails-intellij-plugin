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

package org.apache.grails.intellij.plugin.lang.gsp.psi.gsp.impl.gtag;

import com.intellij.jsp.impl.TldDescriptor;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.AnyXmlAttributeDescriptor;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.apache.grails.intellij.plugin.fileType.GspFileType;
import org.apache.grails.intellij.plugin.lang.gsp.resolve.taglib.GspTagLibUtil;
import org.apache.grails.intellij.plugin.util.GrailsUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class GspTagDescriptorService {

  private static final Logger LOG = Logger.getInstance(GspTagDescriptorService.class);

  private static final String TLD_PATH = "/org/jetbrains/plugins/grails/lang/gsp/resolve/taglib/tld/grails.tld";

  /**
   * Attribute names per tag, read straight out of the bundled {@code grails.tld}.
   * <p>
   * The file is a static snapshot shipped with the plugin rather than anything generated from the
   * project, so it is parsed once per class loader. Reading it here instead of through the
   * platform's {@code TldDescriptor} keeps these descriptors available when the Marketplace
   * {@code com.intellij.jsp} plugin, which contributes that metadata, is not installed.
   */
  private static final Map<String, List<String>> TLD_ATTRIBUTES = loadTldAttributes();

  private static final Map<String, TagDescriptor> tagMap = new LinkedHashMap<>();
  static {
    tagMap.put("actionSubmit", new TagDescriptor("input", "type", "name", "value"));
    tagMap.put("actionSubmitImage", new TagDescriptor("input", "type", "name", "value"));
    tagMap.put("applyLayout", null);
    tagMap.put("checkBox", new TagDescriptor("input", "type", "name", "value", "checked"));
    tagMap.put("collect", null);
    tagMap.put("cookie", null);
    tagMap.put("country", null);
    tagMap.put("countrySelect", new TagDescriptor("select", "name"));
    tagMap.put("createLink", null);
    tagMap.put("createLinkTo", null);
    tagMap.put("currencySelect", new TagDescriptor("select", "name"));
    tagMap.put("datePicker", null);
    tagMap.put("def", null);
    tagMap.put("each", null);
    tagMap.put("eachError", null);
    tagMap.put("else", null);
    tagMap.put("elseif", null);
    tagMap.put("encodeAs", null);
    tagMap.put("escapeJavascript", null);
    tagMap.put("external", null);
    tagMap.put("field", new TagDescriptor("input", "type"));
    tagMap.put("fieldError", null);
    tagMap.put("fieldValue", null);
    tagMap.put("findAll", null);
    tagMap.put("form", new TagDescriptor("form", "action", "method"));
    tagMap.put("formatBoolean", null);
    tagMap.put("formatDate", null);
    tagMap.put("formatNumber", null);
    tagMap.put("formRemote", new TagDescriptor("form", "action", "method", "onsubmit"));
    tagMap.put("grep", null);
    tagMap.put("hasErrors", null);
    tagMap.put("header", null);
    tagMap.put("hiddenField", new TagDescriptor("input", "type", "name"));
    tagMap.put("if", null);
    tagMap.put("ifPageProperty", null);
    tagMap.put("img", new TagDescriptor("img", "dir", "uri", "file", "plugin"));
    tagMap.put("include", null);
    tagMap.put("javascript", null);
    tagMap.put("join", null);
    tagMap.put("layoutBody", null);
    tagMap.put("layoutHead", null);
    tagMap.put("layoutTitle", null);
    tagMap.put("link", new TagDescriptor("a", "href"));
    tagMap.put("localeSelect", new TagDescriptor("select", "name"));
    tagMap.put("message", null);
    tagMap.put("meta", null);
    tagMap.put("pageProperty", null);
    tagMap.put("paginate", null);
    tagMap.put("passwordField", new TagDescriptor("input", "type", "name"));
    tagMap.put("radio", new TagDescriptor("input", "type", "name", "value", "checked"));
    tagMap.put("radioGroup", new TagDescriptor("input", "type", "name", "value", "checked"));
    tagMap.put("remoteField", new TagDescriptor("input", "type", "name", "value", "onkeyup"));
    tagMap.put("remoteFunction", null);
    tagMap.put("remoteLink", new TagDescriptor("a", "onclick"));
    tagMap.put("render", null);
    tagMap.put("renderException", null);
    tagMap.put("resource", null);
    tagMap.put("renderErrors", null);
    tagMap.put("renderInput", null);
    tagMap.put("select", new TagDescriptor("select", "name"));
    tagMap.put("set", null);
    tagMap.put("setProvider", null);
    tagMap.put("sortableColumn", null);
    tagMap.put("submitButton", new TagDescriptor("input", "type", "name", "value"));
    tagMap.put("submitToRemote", new TagDescriptor("input", "type", "name", "value"));
    tagMap.put("textArea", new TagDescriptor("textarea"));
    tagMap.put("textField", new TagDescriptor("input", "type", "name", "value"));
    tagMap.put("timeZoneSelect", new TagDescriptor("select", "name"));
    tagMap.put("uploadForm", new TagDescriptor("form", "action", "method", "enctype"));
    tagMap.put("unless", null);
    tagMap.put("validate", null);
    tagMap.put("withTag", null);
    tagMap.put("while", null);
  }

  private final Map<String, Pair<XmlAttributeDescriptor[], Map<String, XmlAttributeDescriptor>>> myTagDescriptors;

  public GspTagDescriptorService(Project project) {
    Map<String, XmlAttributeDescriptor[]> htmlTagAttributes = getHtmlTagAttributes(project);

    Map<String, Pair<XmlAttributeDescriptor[], Map<String, XmlAttributeDescriptor>>> tagDescriptors = new HashMap<>();

    TldDescriptor descriptor = getTldDescriptor(project);
    if (descriptor != null) {
      PsiFile gspFile = PsiFileFactory.getInstance(project).createFileFromText("dummy.gsp", GspFileType.GSP_FILE_TYPE, "");
      XmlDocument document = (XmlDocument)gspFile.getFirstChild();
      assert document != null;
      XmlTag gspRootTag = (XmlTag)document.getFirstChild().getNextSibling();

      for (XmlElementDescriptor elementDescriptor : descriptor.getRootElementsDescriptors(document)) {
        String tagName = elementDescriptor.getName();
        Map<String, XmlAttributeDescriptor> attrMap = htmlAttributesOf(tagName, htmlTagAttributes);

        // these descriptors point back at their <attribute> element in grails.tld, which is what
        // makes a named argument of an SDK tag navigable
        for (XmlAttributeDescriptor attrDescr : elementDescriptor.getAttributesDescriptors(gspRootTag)) {
          attrMap.put(attrDescr.getName(), attrDescr);
        }

        tagDescriptors.put(tagName, Pair.create(attrMap.values().toArray(XmlAttributeDescriptor.EMPTY), attrMap));
      }
    }
    else {
      // No TLD metadata: com.intellij.jsp contributes it and is a Marketplace plugin since 2026.2.
      // The attribute names come out of the bundled file directly instead, so completion still
      // works; only navigation into grails.tld is lost, and the HTML descriptor of an attribute of
      // the same name is the richer one, so the TLD only fills in the names it alone knows.
      for (Map.Entry<String, List<String>> entry : TLD_ATTRIBUTES.entrySet()) {
        String tagName = entry.getKey();
        Map<String, XmlAttributeDescriptor> attrMap = htmlAttributesOf(tagName, htmlTagAttributes);

        for (String attributeName : entry.getValue()) {
          attrMap.putIfAbsent(attributeName, new AnyXmlAttributeDescriptor(attributeName));
        }

        tagDescriptors.put(tagName, Pair.create(attrMap.values().toArray(XmlAttributeDescriptor.EMPTY), attrMap));
      }
    }

    myTagDescriptors = tagDescriptors;
  }

  /** The attributes of the HTML element the tag renders, if it is one of the tags we know. */
  private static @NotNull Map<String, XmlAttributeDescriptor> htmlAttributesOf(@NotNull String tagName,
                                                                              @NotNull Map<String, XmlAttributeDescriptor[]> htmlTagAttributes) {
    Map<String, XmlAttributeDescriptor> attrMap = new LinkedHashMap<>();

    TagDescriptor tagDescriptor = tagMap.get(tagName);
    if (tagDescriptor == null) return attrMap;

    for (XmlAttributeDescriptor attrDescr : htmlTagAttributes.get(tagDescriptor.htmlTag)) {
      attrMap.put(attrDescr.getName(), attrDescr);
    }

    for (String excluded : tagDescriptor.excludedAttributes) {
      attrMap.remove(excluded);
    }

    return attrMap;
  }

  private static Map<String, XmlAttributeDescriptor[]> getHtmlTagAttributes(Project project) {
    Map<String, XmlAttributeDescriptor[]> res = new HashMap<>();

    for (Map.Entry<String, TagDescriptor> entry : tagMap.entrySet()) {
      TagDescriptor tagDescriptor = entry.getValue();
      if (tagDescriptor != null) {
        res.put(tagDescriptor.htmlTag, null);
      }
    }

    StringBuilder sb = new StringBuilder();
    sb.append("<html><body>");

    for (String htmlTag : res.keySet()) {
      sb.append('<').append(htmlTag).append("/>");
    }

    sb.append("</body></html>");

    PsiFile htmlFile = PsiFileFactory.getInstance(project).createFileFromText("dummy.html", HTMLLanguage.INSTANCE, sb);

    XmlTag[] htmlTags = ((XmlTag)htmlFile.getFirstChild().getFirstChild().getNextSibling()).getSubTags()[0].getSubTags();

    for (XmlTag tag : htmlTags) {
      res.put(tag.getName(), tag.getDescriptor().getAttributesDescriptors(tag));
    }

    return res;
  }

  @TestOnly
  public static Set<String> getAllTags() {
    return tagMap.keySet();
  }

  /** The tags the bundled {@code grails.tld} declares. */
  @TestOnly
  public static Set<String> getTldTags() {
    return TLD_ATTRIBUTES.keySet();
  }

  /** The attribute names the bundled {@code grails.tld} declares for {@code tagName}. */
  @TestOnly
  public static @NotNull List<String> getTldAttributes(@NotNull String tagName) {
    return TLD_ATTRIBUTES.getOrDefault(tagName, Collections.emptyList());
  }

  private static @NotNull Map<String, List<String>> loadTldAttributes() {
    try (InputStream stream = GspTagLibUtil.class.getResourceAsStream(TLD_PATH)) {
      if (stream == null) {
        LOG.error("Bundled TLD not found: " + TLD_PATH);
        return Collections.emptyMap();
      }

      Map<String, List<String>> res = new LinkedHashMap<>();
      for (Element tag : JDOMUtil.load(stream).getChildren()) {
        if (!"tag".equals(tag.getName())) continue;

        String tagName = null;
        List<String> attributes = new ArrayList<>();
        for (Element child : tag.getChildren()) {
          if ("name".equals(child.getName())) {
            tagName = child.getTextTrim();
          }
          else if ("attribute".equals(child.getName())) {
            for (Element attribute : child.getChildren()) {
              if ("name".equals(attribute.getName())) attributes.add(attribute.getTextTrim());
            }
          }
        }

        if (tagName != null && !tagName.isEmpty()) res.put(tagName, List.copyOf(attributes));
      }
      return Collections.unmodifiableMap(res);
    }
    catch (IOException | org.jdom.JDOMException e) {
      LOG.error("Cannot read the bundled TLD: " + TLD_PATH, e);
      return Collections.emptyMap();
    }
  }

  private static final class TagDescriptor {
    public final String htmlTag;

    public final String[] excludedAttributes;

    private TagDescriptor(String htmlTag, String... excludedAttributes) {
      this.htmlTag = htmlTag;
      this.excludedAttributes = excludedAttributes;
    }
  }

  public static GspTagDescriptorService getInstance(Project project) {
    return project.getService(GspTagDescriptorService.class);
  }

  public XmlAttributeDescriptor[] getAttributesDescriptors(String tagName) {
    Pair<XmlAttributeDescriptor[], Map<String, XmlAttributeDescriptor>> pair = myTagDescriptors.get(tagName);
    return pair == null ? XmlAttributeDescriptor.EMPTY : pair.first;
  }

  public @Nullable XmlAttributeDescriptor getAttributesDescriptor(String tagName, String attributeName) {
    Pair<XmlAttributeDescriptor[], Map<String, XmlAttributeDescriptor>> pair = myTagDescriptors.get(tagName);
    if (pair == null) return null;

    return pair.second.get(attributeName);
  }

  public static @Nullable TldDescriptor getTldDescriptor(Project project) {
    VirtualFile tldFile = getTldFile();
    if (tldFile == null) return null;

    PsiFile psiFile = PsiManager.getInstance(project).findFile(tldFile);
    if (!(psiFile instanceof XmlFile)) return null;

    return GrailsUtils.getTldDescriptor((XmlFile)psiFile);
  }

  /**
   * The bundled {@code grails.tld}, located through the class loader so that it is found whether
   * the plugin runs from its jar or from a classes directory.
   * <p>
   * Returns null rather than failing when the VFS does not hold the file: this runs inside a cached
   * value computation, where a synchronous refresh is not allowed, and the callers fall back to the
   * built-in tag descriptors. The descriptor is only ever built when the {@code com.intellij.jsp}
   * plugin is installed anyway, since it contributes the TLD metadata.
   */
  private static @Nullable VirtualFile getTldFile() {
    URL url = GspTagLibUtil.class.getResource(TLD_PATH);
    return url == null ? null : VfsUtil.findFileByURL(url);
  }
}
