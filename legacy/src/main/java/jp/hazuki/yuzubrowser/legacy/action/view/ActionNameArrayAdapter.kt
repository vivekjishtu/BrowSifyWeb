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

package jp.hazuki.yuzubrowser.legacy.action.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import jp.hazuki.yuzubrowser.legacy.R
import jp.hazuki.yuzubrowser.legacy.action.ActionIconMap
import jp.hazuki.yuzubrowser.legacy.action.ActionNameArray
import jp.hazuki.yuzubrowser.legacy.action.SingleAction
import jp.hazuki.yuzubrowser.ui.widget.recycler.OnRecyclerListener

class ActionNameArrayAdapter(
    context: Context,
    val nameArray: ActionNameArray,
    private val listener: OnRecyclerListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val checked = BooleanArray(nameArray.actionList.size)
    private val inflater = LayoutInflater.from(context)
    private val icons = ActionIconMap(context.resources)
    private var mListener: OnSettingButtonListener? = null

    private val listItems = mutableListOf<ListItem>()

    init {
        val categories = listOf(
            0 to R.string.category_navigation,
            2000 to R.string.category_controls,
            5000 to R.string.category_page_tools,
            10000 to R.string.category_tabs,
            35000 to R.string.category_tools_search,
            38000 to R.string.category_display,
            50000 to R.string.category_system,
            100000 to R.string.category_other
        )

        val categorizedItems = mutableListOf<Pair<Int, Int>>()

        for (i in 0 until nameArray.actionList.size) {
            val value = nameArray.actionValues[i]
            var categoryResId = R.string.category_navigation
            for (cat in categories.reversed()) {
                if (value >= cat.first) {
                    categoryResId = cat.second
                    break
                }
            }
            categorizedItems.add(categoryResId to i)
        }

        val grouped = categorizedItems.groupBy { it.first }

        for (cat in categories) {
            grouped[cat.second]?.let { items ->
                listItems.add(ListItem.Header(context.getString(cat.second)))
                for (item in items) {
                    listItems.add(ListItem.ActionItem(item.second))
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return listItems.size
    }

    override fun getItemViewType(position: Int): Int {
        return when (listItems[position]) {
            is ListItem.Header -> VIEW_TYPE_HEADER
            is ListItem.ActionItem -> VIEW_TYPE_ITEM
        }
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    private fun getName(originalPosition: Int): String {
        return nameArray.actionList[originalPosition]!!
    }

    fun getItemValue(originalPosition: Int): Int {
        return nameArray.actionValues[originalPosition]
    }

    private fun getIcon(originalPosition: Int): Drawable? {
        return icons[nameArray.actionValues[originalPosition]]
    }

    fun isChecked(originalPosition: Int): Boolean {
        return checked[originalPosition]
    }

    fun clearChoices() {
        for (i in checked.indices) {
            checked[i] = false
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(inflater.inflate(R.layout.select_action_header, parent, false))
            else -> ViewHolder(inflater.inflate(R.layout.select_action_item, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = listItems[position]) {
            is ListItem.Header -> {
                (holder as HeaderViewHolder).headerText.text = item.title
            }
            is ListItem.ActionItem -> {
                val originalPosition = item.position
                val h = holder as ViewHolder
                h.icon.setImageDrawable(getIcon(originalPosition))
                h.text.text = getName(originalPosition)

                val checked = isChecked(originalPosition)

                h.checkBox.isChecked = checked

                if (SingleAction.checkSubPreference(getItemValue(originalPosition))) {
                    h.setting.visibility = View.VISIBLE
                    h.setting.isEnabled = checked
                    h.setting.imageAlpha = if (checked) 0xff else 0x88
                    h.setting.setOnClickListener { mListener?.invoke(originalPosition) }
                } else {
                    h.setting.visibility = View.GONE
                }

                h.itemView.setOnClickListener { listener.onRecyclerItemClicked(it, originalPosition) }
                h.itemView.setOnLongClickListener { listener.onRecyclerItemLongClicked(it, originalPosition) }
            }
        }
    }

    fun toggleCheck(originalPosition: Int): Boolean {
        val newState = !checked[originalPosition]
        checked[originalPosition] = newState

        // Find adapter position
        val adapterPos = getAdapterPosition(originalPosition)
        if (adapterPos != -1) {
            notifyItemChanged(adapterPos)
        }
        return newState
    }

    fun getAdapterPosition(originalPosition: Int): Int {
        return listItems.indexOfFirst { it is ListItem.ActionItem && it.position == originalPosition }
    }

    fun setChecked(originalPosition: Int, value: Boolean) {
        checked[originalPosition] = value
    }

    fun setListener(mListener: OnSettingButtonListener) {
        this.mListener = mListener
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iconImageView)
        val text: TextView = view.findViewById(R.id.nameTextView)
        val setting: ImageButton = view.findViewById(R.id.settingsButton)
        val checkBox: CheckBox = view.findViewById(R.id.checkBox)
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val headerText: TextView = view.findViewById(R.id.headerTextView)
    }

    sealed class ListItem {
        data class Header(val title: String) : ListItem()
        data class ActionItem(val position: Int) : ListItem()
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }
}
