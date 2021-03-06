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

package com.heinrichreimer.gradle.plugin.github.release.configuration

fun GitHubReleaseConfiguration.copyTo(configuration: MutableGitHubReleaseConfiguration) {
    configuration.ownerProvider = ownerProvider
    configuration.repositoryProvider = repositoryProvider
    configuration.authorizationProvider = authorizationProvider
    configuration.tagProvider = tagProvider
    configuration.targetProvider = targetProvider
    configuration.titleProvider = titleProvider
    configuration.bodyProvider = bodyProvider
    configuration.isDraftProvider = isDraftProvider
    configuration.isPreReleaseProvider = isPreReleaseProvider
    configuration.releaseAssets = releaseAssets
    configuration.updateModeProvider = updateModeProvider
}

fun MutableGitHubReleaseConfiguration.copyFrom(configuration: GitHubReleaseConfiguration) =
        configuration.copyTo(this)
