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

package org.apache.grails.intellij.build

import groovy.transform.CompileStatic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import java.nio.file.Path
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Validates that every {@code uses:} entry in the project's GitHub Actions workflow files
 * complies with the ASF approved-actions policy
 * (https://github.com/apache/infrastructure-actions).
 *
 * <p>Policy:</p>
 * <ul>
 *   <li>Actions under the {@code actions/*}, {@code github/*} or {@code apache/*} namespaces are
 *       auto-approved.</li>
 *   <li>Any other action must either match an {@code @*} (any-version) allowlist entry or be pinned
 *       to a full-SHA commit that appears in {@code approved_patterns.yml}.</li>
 * </ul>
 *
 * <p>Violations are reported as ERRORs and fail the build. A trailing {@code # vX.Y.Z} comment
 * that disagrees with the tag ASF recorded for the pinned SHA gives a WARNING.</p>
 *
 * <p>Ported from grails-core ({@code ValidateActionsTask} in its build-logic), which in turn
 * ported it from Apache Groovy. The YAML parsing needs are minimal, so the files are read line by
 * line rather than adding a YAML library to build-logic.</p>
 */
@CompileStatic
abstract class ValidateActionsTask extends DefaultTask {

    static final String DEFAULT_ALLOWLIST_URL =
            'https://raw.githubusercontent.com/apache/infrastructure-actions/main/approved_patterns.yml'

    static final String DEFAULT_ACTIONS_URL =
            'https://raw.githubusercontent.com/apache/infrastructure-actions/main/actions.yml'

    private static final Pattern USES_LINE = Pattern.compile(
            /^\s*(?:-\s*)?uses:\s*["']?([^"'\s#]+)["']?\s*(?:#\s*(.*?))?\s*$/)

    private static final Pattern SHA40 = Pattern.compile('[0-9a-f]{40}')

    private static final Set<String> AUTO_APPROVED_OWNERS = ['actions', 'github', 'apache'] as Set

    /** The workflow files to scan. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getWorkflowFiles()

    /** Base directory the reported paths are relativised against; affects messages only. */
    @Internal
    abstract DirectoryProperty getRootDirectory()

    /** Location of ASF's {@code approved_patterns.yml}, the list of allowed {@code owner/repo@ref} entries. */
    @Input
    abstract Property<String> getAllowlistUrl()

    /** Location of ASF's {@code actions.yml}, which records the release tag of each approved SHA. */
    @Input
    abstract Property<String> getActionsUrl()

    ValidateActionsTask() {
        allowlistUrl.convention(DEFAULT_ALLOWLIST_URL)
        actionsUrl.convention(DEFAULT_ACTIONS_URL)
    }

    @TaskAction
    void validate() {
        Map<String, Set<String>> allowed = parseAllowlist(fetch(allowlistUrl.get()))
        Map<String, Map<String, String>> tagsBySha = parseActions(fetch(actionsUrl.get()))

        List<String> errors = []
        List<String> warnings = []
        Path rootPath = rootDirectory.get().asFile.toPath()

        workflowFiles.files.toSorted().each { File file ->
            String rel = rootPath.relativize(file.toPath()).toString()
            file.readLines('UTF-8').eachWithIndex { String line, int idx ->
                Matcher m = USES_LINE.matcher(line)
                if (!m.matches()) return
                checkEntry(rel, idx + 1, m.group(1), m.group(2), allowed, tagsBySha, errors, warnings)
            }
        }

        warnings.each { logger.warn('[validateActions] WARN {}', it) }
        if (errors) {
            errors.each { logger.error('[validateActions] FAIL {}', it) }
            throw new GradleException("${errors.size()} GitHub Actions policy violation(s); see log.")
        }
        logger.lifecycle('[validateActions] Checked {} workflow file(s) — all compliant.', workflowFiles.files.size())
    }

    private static void checkEntry(String file, int line, String ref, String comment,
                                   Map<String, Set<String>> allowed,
                                   Map<String, Map<String, String>> tagsBySha,
                                   List<String> errors, List<String> warnings) {
        // Local composite actions (./..., ../...) are implicitly trusted
        if (ref.startsWith('./') || ref.startsWith('../')) return
        // Container images are not actions, so the actions allowlist does not govern them. Checked
        // before the @ split, which would otherwise read a digest-pinned image as an action ref.
        if (ref.startsWith('docker://')) return

        int at = ref.indexOf('@')
        if (at < 0) {
            errors << "$file:$line: unrecognised form '$ref' (missing @ref)".toString()
            return
        }
        String identifier = ref.substring(0, at)
        String refPart = ref.substring(at + 1)

        String owner = identifier.contains('/') ? identifier.split('/', 2)[0] : identifier
        if (AUTO_APPROVED_OWNERS.contains(owner)) return

        Set<String> shas = allowed.get(identifier)
        if (shas == null) {
            errors << "$file:$line: '$identifier' is not on the ASF approved list".toString()
            return
        }
        if (shas.contains('*')) return

        if (!SHA40.matcher(refPart).matches()) {
            errors << ("$file:$line: '$ref' must be pinned to a full SHA (allowlist has " +
                    "${shas.size()} approved SHA(s) for '$identifier')").toString()
            return
        }
        if (!shas.contains(refPart)) {
            errors << ("$file:$line: SHA for '$identifier' is not among the " +
                    "${shas.size()} approved SHA(s) in the allowlist").toString()
            return
        }
        // All hard checks passed. Comment handling is WARN-only.
        String expectedTag = tagsBySha.get(identifier)?.get(refPart)
        if (expectedTag == null) return
        if (comment == null || comment.trim().isEmpty()) {
            warnings << "$file:$line: missing trailing '# $expectedTag' comment for SHA-pinned action".toString()
            return
        }
        String commentTag = comment.trim().split(/\s+/)[0]
        if (commentTag != expectedTag) {
            warnings << ("$file:$line: trailing comment '# $commentTag' does not match the " +
                    "ASF-recorded tag '# $expectedTag' for this SHA").toString()
        }
    }

    private static String fetch(String url) {
        try {
            URLConnection conn = URI.create(url).toURL().openConnection()
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            return conn.inputStream.withReader('UTF-8') { Reader r -> r.text }
        } catch (Exception e) {
            throw new GradleException("Failed to fetch $url: ${e.message}", e)
        }
    }

    /** Parses approved_patterns.yml: a YAML list of {@code owner/repo[/subpath]@ref-or-star} strings. */
    private static Map<String, Set<String>> parseAllowlist(String yaml) {
        Map<String, Set<String>> out = [:]
        yaml.eachLine { String raw ->
            String line = raw.trim()
            if (!line.startsWith('- ')) return
            String entry = line.substring(2).trim()
            int at = entry.lastIndexOf('@')
            if (at < 0) return
            String id = entry.substring(0, at)
            String refValue = entry.substring(at + 1)
            Set<String> set = out.get(id)
            if (set == null) {
                set = new LinkedHashSet<String>()
                out.put(id, set)
            }
            set.add(refValue)
        }
        out
    }

    /**
     * Parses actions.yml for the SHA-to-tag mapping. Schema:
     * <pre>
     *     owner/repo[/subpath]:
     *       &lt;sha&gt;:
     *         tag: vX.Y.Z
     *         expires_at: YYYY-MM-DD
     *       '*':
     *         keep: true
     * </pre>
     * Only identifier + SHA + tag are of interest here.
     */
    private static Map<String, Map<String, String>> parseActions(String yaml) {
        Map<String, Map<String, String>> out = [:]
        String currentId = null
        String currentSha = null
        yaml.eachLine { String raw ->
            int hash = raw.indexOf('#')
            String line = hash >= 0 ? raw.substring(0, hash) : raw
            if (line.trim().isEmpty()) return

            if (!line.startsWith(' ') && line.trim().endsWith(':')) {
                currentId = line.trim()
                currentId = currentId.substring(0, currentId.length() - 1)
                out.putIfAbsent(currentId, new LinkedHashMap<String, String>())
                currentSha = null
                return
            }
            if (currentId != null && line.startsWith('  ') && !line.startsWith('    ') && line.trim().endsWith(':')) {
                String key = line.trim()
                key = key.substring(0, key.length() - 1)
                if (key.startsWith("'") && key.endsWith("'")) key = key.substring(1, key.length() - 1)
                currentSha = key
                return
            }
            if (currentId != null && currentSha != null && line.trim().startsWith('tag:')) {
                String tag = line.trim().substring('tag:'.length()).trim()
                if (tag.startsWith("'") && tag.endsWith("'")) tag = tag.substring(1, tag.length() - 1)
                if (tag.startsWith('"') && tag.endsWith('"')) tag = tag.substring(1, tag.length() - 1)
                out.get(currentId).put(currentSha, tag)
            }
        }
        out
    }
}
