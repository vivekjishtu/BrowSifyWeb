/*
 * Copyright (C) 2017-2019 Hazuki
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

package jp.hazuki.yuzubrowser.search.presentation.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import jp.hazuki.yuzubrowser.search.R
import jp.hazuki.yuzubrowser.search.databinding.SearchActivitySuggestHistoryBinding
import jp.hazuki.yuzubrowser.search.databinding.SearchActivtySuggestSuggestBinding
import jp.hazuki.yuzubrowser.search.model.SearchSuggestModel

class SearchSuggestAdapter : RecyclerView.Adapter<SearchSuggestAdapter.SuggestHolder>() {
    val list: MutableList<SearchSuggestModel> = mutableListOf()

    var listener: OnSearchSelectedListener? = null

    override fun getItemCount() = list.size

    override fun getItemViewType(position: Int): Int {
        return when (list[position]) {
            is SearchSuggestModel.SuggestModel -> TYPE_SUGGEST
            is SearchSuggestModel.HistoryModel -> TYPE_HISTORY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SUGGEST -> SuggestHolder.Suggest(SearchActivtySuggestSuggestBinding.inflate(inflater, parent, false))
            TYPE_HISTORY -> SuggestHolder.History(SearchActivitySuggestHistoryBinding.inflate(inflater, parent, false))
            else -> throw IllegalArgumentException("Unknown viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: SuggestHolder, position: Int) {
        when (val item = list[position]) {
            is SearchSuggestModel.SuggestModel -> {
                val h = holder as SuggestHolder.Suggest
                h.binding.textView.text = item.suggest
                h.binding.textView.setOnClickListener {
                    listener?.onSelectedQuery(item.suggest)
                }
                h.binding.imageButton.setOnClickListener {
                    listener?.onInputQuery(item.suggest)
                }
                if (item.suggestHistory) {
                    h.binding.textView.setOnLongClickListener {
                        if (item.suggestHistory) listener?.onDeleteQuery(item.suggest)
                        true
                    }
                } else {
                    h.binding.textView.setOnLongClickListener(null)
                }
            }
            is SearchSuggestModel.HistoryModel -> {
                val h = holder as SuggestHolder.History
                h.binding.titleTextView.text = item.title
                h.binding.urlTextView.text = item.url
                h.binding.background.setOnClickListener {
                    listener?.onSelectedQuery(item.url)
                }
                h.binding.inputImageButton.setOnClickListener {
                    listener?.onInputQuery(item.url)
                }
            }
        }
    }

    sealed class SuggestHolder(v: View) : RecyclerView.ViewHolder(v) {
        class Suggest(val binding: SearchActivtySuggestSuggestBinding) : SuggestHolder(binding.root)
        class History(val binding: SearchActivitySuggestHistoryBinding) : SuggestHolder(binding.root)
    }

    companion object {
        private const val TYPE_SUGGEST = 0
        private const val TYPE_HISTORY = 1
    }

    interface OnSearchSelectedListener {
        fun onSelectedQuery(query: String)

        fun onInputQuery(query: String)

        fun onDeleteQuery(query: String)
    }
}
