package jp.hazuki.yuzubrowser.legacy.webkit.handler

import jp.hazuki.yuzubrowser.legacy.browser.BrowserController
import jp.hazuki.yuzubrowser.legacy.webkit.TabType
import java.lang.ref.WeakReference

class WebSrcOpenPrivateTabHandler(controller: BrowserController) : WebSrcImageHandler() {
    private val mReference: WeakReference<BrowserController> = WeakReference(controller)

    override fun handleUrl(url: String) {
        mReference.get()?.openInNewTab(url, TabType.PRIVATE)
    }
}
