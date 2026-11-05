/*
 * Copyright 2020 Hazuki
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

package jp.hazuki.yuzubrowser.adblock.core

import android.net.Uri
import jp.hazuki.yuzubrowser.adblock.filter.abp.AbpFilterDecoder
import jp.hazuki.yuzubrowser.adblock.filter.unified.element.ElementContainer
import org.json.JSONObject

class CosmeticFiltering(
    private val disables: FilterContainer,
    private val filters: ElementContainer,
) {
    private var cacheUrl: Uri? = null
    private var cache: String? = null

    @Synchronized
    fun loadScript(url: Uri): String? {
        if (cacheUrl == url) return cache

        val request = ContentRequest(url, url, TYPE, false)
        val result = disables[request]

        val isUseGeneric = when (result?.filterType ?: 0) {
            ContentRequest.TYPE_ELEMENT_HIDE -> return null
            ContentRequest.TYPE_ELEMENT_GENERIC_HIDE -> false
            else -> true
        }

        val results = filters[url, isUseGeneric]
        if (results.isEmpty()) return null

        val builder = StringBuilder()
        val plain = results.asSequence().filter { !it.isHide }.map { it.selector }.toSet()
        val selected = results.filter { it.isHide && !plain.contains(it.selector) }
        val selector = selected.filterNot { it.selector.startsWith(AbpFilterDecoder.PROCEDURAL_SELECTOR_PREFIX) }
        val procedural = selected.filter { it.selector.startsWith(AbpFilterDecoder.PROCEDURAL_SELECTOR_PREFIX) }

        builder.append("(function(){const b=[")
        selector.forEach {
            builder.append(JSONObject.quote(it.selector)).append(',')
        }
        builder.append("];const s=document.createElement('style');s.setAttribute('data-bsw-adblock','cosmetic');")
        builder.append("s.textContent=b.map(a=>a+'{display:none!important}').join('\\n');")
        builder.append("(document.head||document.documentElement).appendChild(s);")
        if (procedural.isNotEmpty()) {
            builder.append("const p=[")
            procedural.forEach {
                val rule = it.selector.removePrefix(AbpFilterDecoder.PROCEDURAL_SELECTOR_PREFIX)
                builder.append(JSONObject.quote(rule)).append(',')
            }
            builder.append("];function run(){for(const r of p){try{const i=r.indexOf(':has(');if(i<1||!r.endsWith(')'))continue;const base=r.slice(0,i);const inner=r.slice(i+5,-1);const m=/:has-text\\((['\\\"]?)(.*?)\\1\\)/.exec(inner);if(!m)continue;const needle=m[2].trim();let child=inner.slice(0,m.index)+inner.slice(m.index+m[0].length);child=child.trim().replace(/^>\\s*/,'');if(!child)continue;for(const e of document.querySelectorAll(base)){for(const n of e.querySelectorAll(child)){if((n.textContent||'').includes(needle)){e.style.setProperty('display','none','important');break}}}}catch(_){}}}run();let timer=0;new MutationObserver(()=>{if(timer)return;timer=setTimeout(()=>{timer=0;run()},250)}).observe(document.documentElement,{childList:true,subtree:true,characterData:true});")
        }
        builder.append("})()")

        val script = builder.toString()

        cacheUrl = url
        cache = script

        return script
    }

    companion object {
        private const val TYPE = ContentRequest.TYPE_ELEMENT_GENERIC_HIDE or
            ContentRequest.TYPE_ELEMENT_HIDE
    }
}
