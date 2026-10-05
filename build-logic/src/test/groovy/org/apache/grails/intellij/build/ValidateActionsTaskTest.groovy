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

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.assertj.core.api.Assertions.assertThat

/**
 * Runs the {@code validateActions} task through Gradle TestKit against local copies of the ASF
 * allowlist files, so the policy is exercised end to end without reaching the network. Every
 * build runs with the configuration cache on, as the real build does.
 */
class ValidateActionsTaskTest {

    private static final String PINNED_SHA = '1111111111111111111111111111111111111111'
    private static final String OLDER_SHA = '2222222222222222222222222222222222222222'
    private static final String UNKNOWN_SHA = '3333333333333333333333333333333333333333'

    // Trimmed copies of approved_patterns.yml and actions.yml in the shape ASF publishes them.
    private static final String ALLOWLIST = """\
        # Licensed to the Apache Software Foundation (ASF) under one
        # This file was generated from actions.yml by gateway/gateway.py.
        - example/pinned-action@$PINNED_SHA
        - example/pinned-action@$OLDER_SHA
        - example/monorepo/sub-action@$PINNED_SHA
        - example/any-version@*
        """.stripIndent()

    private static final String ACTIONS = """\
        # Main configuration file. We manually add trusted actions here.
        example/pinned-action:
          $PINNED_SHA:
            tag: v2.1.0
            expires_at: 2026-12-31
          '$OLDER_SHA':
            tag: 'v1.0.0' # quoted keys and values occur too
        example/monorepo/sub-action:
          $PINNED_SHA:
            tag: "v3.0.0"
        example/any-version:
          '*':
            keep: true
        """.stripIndent()

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder()

    private File projectDir

    @Before
    void setUp() {
        projectDir = tmp.root
        write('settings.gradle', "rootProject.name = 'validate-actions-test'")
        write('allowlist.yml', ALLOWLIST)
        write('actions.yml', ACTIONS)
        write('build.gradle', """\
            plugins {
                id 'org.apache.grails.intellij.build.validate-actions'
            }
            tasks.named('validateActions') {
                allowlistUrl = '${new File(projectDir, 'allowlist.yml').toURI()}'
                actionsUrl = '${new File(projectDir, 'actions.yml').toURI()}'
            }
            """.stripIndent())
    }

    @Test
    void compliantWorkflowsPass() {
        workflow('ci.yml', """\
            jobs:
              build:
                steps:
                  - uses: actions/checkout@v7
                  - uses: github/codeql-action/init@v4
                  - uses: apache/grails-github-actions/post-release@asf
                  - uses: ./.github/actions/local
                  - uses: docker://alpine@sha256:0123456789abcdef
                  - uses: example/any-version@main
                  - uses: example/pinned-action@$PINNED_SHA # v2.1.0
                  - name: quoted
                    uses: "example/monorepo/sub-action@$PINNED_SHA" # v3.0.0
            """)

        BuildResult result = runner().build()

        assertThat(result.task(':validateActions').outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.output)
                .contains('[validateActions] Checked 1 workflow file(s) — all compliant.')
                .doesNotContain('WARN')
    }

    @Test
    void actionMissingFromAllowlistFails() {
        workflow('ci.yml', """\
            steps:
              - uses: actions/checkout@v7
              - uses: someone/unvetted-action@v1
            """)

        BuildResult result = runner().buildAndFail()

        assertThat(result.output)
                .contains("FAIL .github/workflows/ci.yml:3: 'someone/unvetted-action' is not on the ASF approved list")
                .contains('1 GitHub Actions policy violation(s); see log.')
    }

    @Test
    void approvedActionMustBePinnedToFullSha() {
        workflow('ci.yml', """\
            steps:
              - uses: example/pinned-action@v2.1.0
              - uses: example/pinned-action@${PINNED_SHA.substring(0, 7)}
            """)

        BuildResult result = runner().buildAndFail()

        assertThat(result.output)
                .contains("ci.yml:2: 'example/pinned-action@v2.1.0' must be pinned to a full SHA " +
                        "(allowlist has 2 approved SHA(s) for 'example/pinned-action')")
                .contains("ci.yml:3: 'example/pinned-action@1111111' must be pinned to a full SHA")
                .contains('2 GitHub Actions policy violation(s)')
    }

    @Test
    void shaNotOnAllowlistFails() {
        workflow('ci.yml', """\
            steps:
              - uses: example/pinned-action@$UNKNOWN_SHA # v9.9.9
            """)

        BuildResult result = runner().buildAndFail()

        assertThat(result.output).contains(
                "ci.yml:2: SHA for 'example/pinned-action' is not among the 2 approved SHA(s) in the allowlist")
    }

    @Test
    void approvalIsPerSubpath() {
        // Only the sub-action is listed; the repository root action is not approved by it.
        workflow('ci.yml', """\
            steps:
              - uses: example/monorepo@$PINNED_SHA
            """)

        BuildResult result = runner().buildAndFail()

        assertThat(result.output).contains("ci.yml:2: 'example/monorepo' is not on the ASF approved list")
    }

    @Test
    void referenceWithoutVersionFails() {
        workflow('ci.yml', """\
            steps:
              - uses: example/any-version
            """)

        BuildResult result = runner().buildAndFail()

        assertThat(result.output).contains("ci.yml:2: unrecognised form 'example/any-version' (missing @ref)")
    }

    @Test
    void versionCommentMismatchesOnlyWarn() {
        workflow('ci.yml', """\
            steps:
              - uses: example/pinned-action@$PINNED_SHA
              - uses: example/pinned-action@$OLDER_SHA # v1.0.1
              - uses: example/monorepo/sub-action@$PINNED_SHA # v3.0.0 (sub-action)
            """)

        BuildResult result = runner().build()

        assertThat(result.output)
                .contains("WARN .github/workflows/ci.yml:2: missing trailing '# v2.1.0' comment for SHA-pinned action")
                .contains("WARN .github/workflows/ci.yml:3: trailing comment '# v1.0.1' does not match the " +
                        "ASF-recorded tag '# v1.0.0' for this SHA")
                .doesNotContain('ci.yml:4')
                .contains('all compliant')
    }

    @Test
    void reportsEveryViolationAcrossFilesInPathOrder() {
        workflow('b.yaml', """\
            steps:
              - uses: someone/second@v1
            """)
        workflow('a.yml', """\
            steps:
              - uses: someone/first@v1
            """)
        // Not a workflow file: the task only scans *.yml and *.yaml.
        workflow('README.md', '- uses: someone/ignored@v1\n')

        BuildResult result = runner().buildAndFail()

        String output = result.output
        assertThat(output)
                .contains('2 GitHub Actions policy violation(s)')
                .doesNotContain('someone/ignored')
        assertThat(output.indexOf("a.yml:2: 'someone/first'"))
                .isPositive()
                .isLessThan(output.indexOf("b.yaml:2: 'someone/second'"))
    }

    @Test
    void neverUpToDateSoDelistingIsCaught() {
        workflow('ci.yml', """\
            steps:
              - uses: example/pinned-action@$OLDER_SHA # v1.0.0
            """)
        assertThat(runner().build().task(':validateActions').outcome).isEqualTo(TaskOutcome.SUCCESS)

        // ASF drops the SHA; nothing in the repository changes.
        write('allowlist.yml', ALLOWLIST.readLines().findAll { !it.contains(OLDER_SHA) }.join('\n'))

        BuildResult result = runner().buildAndFail()

        assertThat(result.task(':validateActions').outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output).contains("SHA for 'example/pinned-action' is not among the 1 approved SHA(s)")
    }

    @Test
    void unreachableAllowlistFailsTheBuild() {
        workflow('ci.yml', "steps:\n  - uses: actions/checkout@v7\n")
        File missing = new File(projectDir, 'missing.yml')
        write('build.gradle', new File(projectDir, 'build.gradle').text
                .replace(new File(projectDir, 'allowlist.yml').toURI().toString(), missing.toURI().toString()))

        BuildResult result = runner().buildAndFail()

        assertThat(result.output).contains("Failed to fetch ${missing.toURI()}")
    }

    private GradleRunner runner() {
        GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments('validateActions', '--configuration-cache', '--stacktrace')
    }

    private void workflow(String name, String content) {
        write(".github/workflows/$name", content.stripIndent())
    }

    private void write(String path, String content) {
        File file = new File(projectDir, path)
        file.parentFile.mkdirs()
        file.text = content
    }
}
