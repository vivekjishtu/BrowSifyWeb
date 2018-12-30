/*
 * Copyright (C) 2017-2018 Hazuki
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.hazuki.yuzubrowser.legacy.download.core.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Parcelable
import android.webkit.CookieManager
import jp.hazuki.yuzubrowser.legacy.download.core.downloader.client.HttpClient
import jp.hazuki.yuzubrowser.legacy.download.core.utils.*
import kotlinx.android.parcel.Parcelize
import java.io.IOException

@SuppressLint("ParcelCreator")
@Parcelize
class MetaData(val name: String, val mineType: String, val size: Long, val resumable: Boolean) : Parcelable {

    companion object {

        operator fun invoke(context: Context, root: androidx.documentfile.provider.DocumentFile, url: String, request: DownloadRequest): MetaData {
            return try {
                val client = HttpClient.create(url, "HEAD")
                client.connectTimeout = 1000
                client.setCookie(CookieManager.getInstance().getCookie(url))
                client.setReferrer(request.referrer)
                client.setUserAgent(context, request.userAgent)
                client.connect()

                val mimeType = client.mimeType
                val name = client.getFileName(root, url, mimeType, request.defaultExt)
                MetaData(name, mimeType, client.contentLength, client.isResumable)
            } catch (e: IOException) {
                MetaData(guessDownloadFileName(root, url, null, null, request.defaultExt), "application/octet-stream", -1, false)
            }
        }

        operator fun invoke(info: DownloadFileInfo): MetaData {
            return MetaData(info.name, info.mimeType, info.size, info.resumable)
        }
    }
}