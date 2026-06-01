/*
 * Copyright (C) 2026 Vivek Jishtu
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

package jp.hazuki.yuzubrowser.webview

import android.webkit.CookieManager
import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

object WebViewProfileManager {
    const val PRIVATE_PROFILE_NAME = "private"

    fun applyProfile(webView: WebView, isPrivate: Boolean) {
        if (!isPrivate) return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return
        WebViewCompat.setProfile(webView, PRIVATE_PROFILE_NAME)
    }

    fun getCookieManager(webView: WebView): CookieManager {
        return if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            WebViewCompat.getProfile(webView).cookieManager
        } else {
            CookieManager.getInstance()
        }
    }

    fun isPrivate(webView: WebView): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return false
        return WebViewCompat.getProfile(webView).name == PRIVATE_PROFILE_NAME
    }

    fun clearPrivateProfile() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return
        try {
            ProfileStore.getInstance().deleteProfile(PRIVATE_PROFILE_NAME)
        } catch (_: IllegalStateException) {
            // The profile is still loaded or already gone. Try again on the next cleanup path.
        }
    }
}
