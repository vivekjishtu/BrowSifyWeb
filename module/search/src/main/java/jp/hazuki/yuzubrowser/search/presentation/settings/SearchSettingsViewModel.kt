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

package jp.hazuki.yuzubrowser.search.presentation.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.hazuki.yuzubrowser.search.domain.usecase.SearchSettingsViewUseCase
import jp.hazuki.yuzubrowser.search.model.provider.SearchUrl
import jp.hazuki.yuzubrowser.ui.widget.recycler.RecyclerMenu
import javax.inject.Inject

@HiltViewModel
internal class SearchSettingsViewModel @Inject constructor(
    application: Application,
    private val useCase: SearchSettingsViewUseCase
) : AndroidViewModel(application), RecyclerMenu.OnRecyclerMoveListener {

    val removedItem = MutableLiveData<RemovedItem>()

    val list = MutableLiveData<List<SearchUrl>>()

    val onFabClick = MutableLiveData<Any?>()

    val touchHelperCallback: ItemTouchHelper.Callback
        get() = Touch()

    val size: Int
        get() = useCase.list.size

    fun init() {
        list.value = useCase.list
    }

    fun update(index: Int, url: SearchUrl) {
        useCase[index] = url
        updateList()
    }

    fun add(url: SearchUrl) {
        useCase.add(url)
        updateList()
    }

    fun remove(index: Int) {
        useCase.removeAt(index)
        updateList()
    }

    fun resetRemovedItem() {
        val removed = removedItem.value ?: return

        useCase.add(removed.index, removed.item)
        updateList()
    }

    fun onPause() {
        useCase.commitIfNeed()
    }

    fun onFabClick() {
        onFabClick.value = null
    }

    override fun onMoveUp(position: Int) {
        useCase.moveUp(position)
        updateList()
    }

    override fun onMoveDown(position: Int) {
        useCase.moveDown(position)
        updateList()
    }

    private fun updateList() {
        list.value = useCase.list
    }

    inner class Touch : ItemTouchHelper.Callback() {
        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            return if (useCase.list.size > 1) {
                makeFlag(ItemTouchHelper.ACTION_STATE_SWIPE, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) or
                    makeFlag(ItemTouchHelper.ACTION_STATE_DRAG, ItemTouchHelper.DOWN or ItemTouchHelper.UP)
            } else {
                0
            }
        }

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            useCase.move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
            updateList()
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.bindingAdapterPosition
            removedItem.value = RemovedItem(position, useCase.removeAt(position))
            updateList()
        }

    }

    class RemovedItem(val index: Int, val item: SearchUrl)
}
