package jp.hazuki.yuzubrowser.webview

import android.webkit.CookieManager
import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.UUID

object WebViewProfileManager {
    private const val PRIVATE_PROFILE_PREFIX = "private-session-"

    @Volatile
    private var privateProfileName: String? = null

    fun applyProfile(webView: CustomWebView, isPrivate: Boolean) {
        applyProfile(webView.webView, isPrivate)
    }

    fun applyProfile(webView: WebView, isPrivate: Boolean) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            return
        }
        if (isPrivate) {
            WebViewCompat.setProfile(webView, getOrCreatePrivateProfileName())
        }
    }

    fun getCookieManager(webView: CustomWebView): CookieManager {
        return getCookieManager(webView.webView)
    }

    fun getCookieManager(webView: WebView): CookieManager {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            return CookieManager.getInstance()
        }
        return WebViewCompat.getProfile(webView).cookieManager
    }

    fun isPrivate(webView: CustomWebView): Boolean {
        return isPrivate(webView.webView)
    }

    fun isPrivate(webView: WebView): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            return false
        }
        return WebViewCompat.getProfile(webView).name.startsWith(PRIVATE_PROFILE_PREFIX)
    }

    fun clearPrivateProfile() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            privateProfileName = null
            return
        }

        val profileStore = ProfileStore.getInstance()
        val names = LinkedHashSet<String>()
        privateProfileName?.let(names::add)

        runCatching { profileStore.getAllProfileNames() }
            .getOrNull()
            ?.asSequence()
            ?.filter { it.startsWith(PRIVATE_PROFILE_PREFIX) }
            ?.forEach(names::add)

        for (name in names) {
            val profile = runCatching { profileStore.getProfile(name) }.getOrNull()
            if (profile != null) {
                runCatching {
                    profile.cookieManager.removeAllCookies(null)
                    profile.cookieManager.flush()
                }
                runCatching {
                    profile.webStorage.deleteAllData()
                }
            }
            runCatching {
                profileStore.deleteProfile(name)
            }
        }

        privateProfileName = null
    }

    private fun getOrCreatePrivateProfileName(): String {
        privateProfileName?.let { return it }
        return synchronized(this) {
            privateProfileName ?: "$PRIVATE_PROFILE_PREFIX${UUID.randomUUID()}".also {
                privateProfileName = it
            }
        }
    }
}
