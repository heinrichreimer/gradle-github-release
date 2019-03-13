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

import com.heinrichreimer.gradle.plugin.github.release.configuration.UpdateMode
import com.heinrichreimer.gradle.plugin.github.release.configuration.copyFrom
import kotlinx.coroutines.runBlocking
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

@Suppress("UnstableApiUsage")
class GithubReleasePlugin : Plugin<Project> {

    companion object {
        private const val EXTENSION_NAME = "githubRelease"
        private const val TASK_NAME = "githubRelease"
        private val log: Logger = Logging.getLogger(GithubReleasePlugin::class.java)
    }

    override fun apply(project: Project) {
        val githubReleaseExtension = project.createExtension()
        githubReleaseExtension.setDefaults(project)
        project.registerTask(githubReleaseExtension)
    }

    private fun Project.createExtension(): GithubReleaseExtension {
        log.debug("Creating $EXTENSION_NAME extension for ${GithubReleasePlugin::class.java.simpleName}.")
        return extensions
                .create(
                        EXTENSION_NAME,
                        GithubReleaseExtension::class.java,
                        objects,
                        layout,
                        providers
                )
    }

    private fun GithubReleaseExtension.setDefaults(project: Project) {
        log.debug("Assigning default values for $EXTENSION_NAME extension.")
        owner {
            project.group
                    .toString()
                    .substringAfterLast('.')
        }
        repository {
            project.name
        }
        tag {
            "v${project.version}"
        }
        target = "master"
        nameProvider = tagProvider
        isDraft = false
        isPreRelease = false
        authorization {
            throw IllegalArgumentException("Must specify GitHub authorization token.")
        }
        bodyProvider = changelog
        updateMode = UpdateMode.NONE

        changeLogSupplier.setDefaults(project)
    }

    private fun ChangeLogSupplier.setDefaults(project: Project) {
        log.debug("Assigning default values for changelog supplier.")
        gitExecutable = "git"
        currentCommit = "HEAD"
        lastCommit {
            runBlocking {
                getLastReleaseCommit()
            }
        }
        gitOptions = listOf("--format=oneline", "--abbrev-commit", "--max-count=50")
    }

    private fun Project.registerTask(extension: GithubReleaseExtension) {
        log.debug("Registering $TASK_NAME task for ${GithubReleasePlugin::class.java.simpleName}.")
        tasks
                .register(
                        TASK_NAME,
                        GithubReleaseTask::class.java,
                        objects,
                        layout,
                        providers
                )
                .configure {
                    it.copyFrom(extension)
                }
    }
}
