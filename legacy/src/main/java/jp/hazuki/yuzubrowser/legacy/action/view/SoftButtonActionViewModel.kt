/*
 * Copyright (C) 2017-2021 Hazuki
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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import jp.hazuki.yuzubrowser.legacy.action.ActionIconMap
import jp.hazuki.yuzubrowser.legacy.action.ActionNameMap

class SoftButtonActionViewModel(
    val actionNames: ActionNameMap,
    val actionIcons: ActionIconMap,
) : ViewModel() {
    class Factory(
        private val actionNames: ActionNameMap,
        private val actionIcons: ActionIconMap,
    ) : ViewModelProvider.AndroidViewModelFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SoftButtonActionViewModel(actionNames, actionIcons) as T
        }
    }
}
