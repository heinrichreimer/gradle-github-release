/*
 * MIT License
 *
 * Copyright (c) 2019 Jan Heinrich Reimer
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
/*
 * Based on github-release-gradle-plugin by Ton Ly (@BreadMoirai)
 * Licensed under the Apache License v2.0:
 * https://github.com/BreadMoirai/github-release-gradle-plugin
 */

package com.heinrichreimer.gradle.plugin.github.release

import com.heinrichreimer.gradle.plugin.github.release.api.GitHubApiService
import com.heinrichreimer.gradle.plugin.github.release.configuration.GitHubReleaseConfiguration
import com.heinrichreimer.gradle.plugin.github.release.configuration.MutableChangelogSupplierConfiguration
import org.gradle.api.Project
import org.gradle.api.file.ProjectLayout
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.zeroturnaround.exec.ProcessExecutor
import java.io.IOException

@Suppress("UnstableApiUsage")
class ChangelogSupplier(
        configuration: GitHubReleaseConfiguration,
        private val objects: ObjectFactory,
        private val layout: ProjectLayout,
        private val providers: ProviderFactory
) :
        MutableChangelogSupplierConfiguration by ChangelogSupplierExtension(objects, providers),
        GitHubReleaseConfiguration by configuration {

    constructor(
            configuration: GitHubReleaseConfiguration,
            project: Project
    ) : this(
            configuration = configuration,
            objects = project.objects,
            layout = project.layout,
            providers = project.providers
    )

    companion object {
        private val log: Logger = Logging.getLogger(ChangelogSupplier::class.java)
    }

    private val service = GitHubApiService(authorizationProvider)

    /**
     * Looks for the previous release's target commit.
     */
    internal suspend fun getLastReleaseCommit(): String {
        val owner = owner
        val repo = repository


        // Query the GitHub API for releases.
        val response = service
                .getReleasesAsync(owner, repo)
                .await()

        if (response.code() != 200) {
            return ""
        }

        val releases = response.body() ?: return ""

        // find current release if exists
        val index = releases.indexOfFirst { release -> release.tag == tag }
        if (releases.isEmpty()) {
            val cmd = listOf(gitExecutable, "rev-list", "--max-parents=0", "--max-count=1", "HEAD")

            return executeAndGetOutput(cmd).trim()
        } else {
            // get the next release before the current release
            // if current release does not ezist, then gets the most recent release
            val lastRelease = releases[index + 1]
            val lastTag = lastRelease.tag

            val tagResponse = service.getGitReferenceByTagNameAsync(owner, repo, lastTag)
                    .await()

            return tagResponse.body()?.referencedObject?.sha ?: ""
        }
    }

    private fun executeAndGetOutput(commands: Iterable<Any>): String {
        return ProcessExecutor()
                .directory(layout.projectDirectory.asFile)
                .command(commands.map { it.toString() })
                .readOutput(true)
                .exitValueNormal()
                .execute()
                .outputUTF8()
    }

    fun call(): String {
        log.info("Generating release body using commit history.")
        val opts = gitOptions.map(Any::toString).toTypedArray()
        val cmds = listOf(gitExecutable, "rev-list", *opts, "$lastCommit..$currentCommit", "--")
        try {
            return executeAndGetOutput(cmds)
        } catch (e: IOException) {
            if (e.cause != null && e.cause?.message?.contains("CreateProcess error=2") == true) {
                throw  Error("Failed to run git executable to find commit history. " +
                        "Please specify the path to the git executable.\n")
            }
            throw e
        }
    }
}