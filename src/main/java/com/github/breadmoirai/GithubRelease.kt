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

package com.github.breadmoirai

import com.github.breadmoirai.configuration.GithubReleaseConfiguration
import com.github.breadmoirai.exception.IllegalNetworkResponseCodeException
import com.github.breadmoirai.exception.RepositoryNotFoundException
import com.j256.simplemagic.ContentInfoUtil
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import okhttp3.*
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider

class GithubRelease(configuration: GithubReleaseConfiguration) : Runnable, GithubReleaseConfiguration by configuration {

    companion object {
        private val log: Logger = Logging.getLogger(GithubRelease::class.java)
        val JSON: MediaType = MediaType.parse("application/json; charset=utf-8")!!

        internal fun createRequestWithHeaders(authorization: Provider<CharSequence>): Request.Builder {
            return Request.Builder()
                    .addHeader("Authorization", "token " + authorization.get().toString())
                    .addHeader("User-Agent", "breadmoirai github-release-gradle-plugin")
                    .addHeader("Accept", "application/vnd.github.v3+json")
                    .addHeader("Content-Type", JSON.toString())
        }
    }

    private val client: OkHttpClient = OkHttpClient()
    private val slurper: JsonSlurper = JsonSlurper()

    override fun run() {
        val previousReleaseResponse = checkForPreviousRelease()
        val code = previousReleaseResponse.code()
        when (code) {
            200 -> {
                log.info("Found existing release.")
                when {
                    overwrite -> {
                        log.info("Deleting existing release.")
                        deletePreviousRelease(previousReleaseResponse)
                        val createReleaseResponse = createRelease()
                        uploadAssets(createReleaseResponse)
                    }
                    allowUploadToExistingProvider.getOrElse(false) -> {
                        log.info("Assets will be added to existing release.")
                        uploadAssets(previousReleaseResponse)
                    }
                    else -> throw IllegalStateException("Failed to upload release, release already exists.\n" +
                            "Set property 'overwrite = true' to replace existing releases on conflict.")
                }
            }
            404 -> {
                val createReleaseResponse = createRelease()
                uploadAssets(createReleaseResponse)
            }
            else -> throw IllegalNetworkResponseCodeException(previousReleaseResponse)
        }
    }


    private fun checkForPreviousRelease(): Response {
        val releaseUrl = "https://api.github.com/repos/$owner/$repo/releases/tags/$tagName"
        log.debug("Checking for previuos release.")
        val request: Request = createRequestWithHeaders(authorizationProvider)
                .url(releaseUrl)
                .get()
                .build()
        return client
                .newCall(request)
                .execute()
    }

    private fun deletePreviousRelease(previous: Response): Response {
        val responseJson = slurper.parseText(previous.body()?.string()) as Map<String, Any>
        val prevReleaseUrl: String = responseJson["url"] as String

        log.info("Deleting previous release.")
        val request: Request = createRequestWithHeaders(authorizationProvider)
                .url(prevReleaseUrl)
                .delete()
                .build()
        val response: Response = client.newCall(request).execute()
        val status = response.code()
        return when (status) {
            204 -> response
            404 -> throw RepositoryNotFoundException(owner.toString(), repo.toString())
            else -> throw IllegalNetworkResponseCodeException(response)
        }
    }

    private fun createRelease(): Response {
        log.info("Creating GitHub release.")
        val json: String = JsonOutput.toJson(mapOf(
                "tag_name" to tagName,
                "target_commitish" to targetCommitish,
                "name" to releaseName,
                "body" to body,
                "draft" to draft,
                "prerelease" to prerelease
        ))
        val requestBody: RequestBody = RequestBody.create(JSON, json)
        val request: Request = createRequestWithHeaders(authorizationProvider)
                .url("https://api.github.com/repos/$owner/$repo/releases")
                .post(requestBody)
                .build()

        val response: Response = client.newCall(request).execute()
        val code = response.code()
        return when (code) {
            201 -> {
                log.info("Created release. Status: ${response.header("Status")}")
                response
            }
            404 -> throw RepositoryNotFoundException(owner.toString(), repo.toString())
            else -> throw IllegalNetworkResponseCodeException(response)
        }
    }

    /**
     * The responses returned is automatically closed for convenience. This behavior may change in the future if required.
     * @param response this response should reference the release that the assets will be uploaded to
     * @return a list of responses from uploaded each asset
     */
    private fun uploadAssets(response: Response): List<Response> {
        val assets = releaseAssets.files
        if (assets.isEmpty()) {
            log.debug("Skip uploading release assets, no assets found.")
            return emptyList()
        }

        log.info("Uploading release assets.")
        val responseJson = slurper.parseText(response.body()?.string()) as Map<String, Any>

        val contentInfoUtil = ContentInfoUtil()
        val assetResponses = assets.asSequence().map { asset ->
            log.debug("Uploading asset '${asset.name}'")
            val type = contentInfoUtil
                    .findMatch(asset)
                    ?.let { info ->
                        MediaType.parse(info.mimeType)
                    }
            if (type == null) {
                log.warn("Could not guess media type for file '${asset.name}'")
            }
            val uploadUrl: String = responseJson["upload_url"] as String
            val assetBody: RequestBody = RequestBody.create(type, asset)

            val assetPost: Request = createRequestWithHeaders(authorizationProvider)
                    .url(uploadUrl.replace("{?name,label}", "?name=${asset.name}"))
                    .post(assetBody)
                    .build()

            client.newCall(assetPost).execute()
        }
        assetResponses.forEach { it.close() }
        return assetResponses.toList()
    }

}
