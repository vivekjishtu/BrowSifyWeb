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

package jp.hazuki.yuzubrowser.legacy.debug.file

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import jp.hazuki.yuzubrowser.core.lifecycle.KotlinLiveData
import java.io.File

class FileBrowserViewModel(
    val root: File
) : ViewModel() {

    val currentRoot = KotlinLiveData(root)

    val contents = KotlinLiveData(emptyList<FileItem>())

    var copy: File? = null

    var exportFile: File? = null

    init {
        setDir(root)
    }

    fun setDir(dir: File) {
        val list = mutableListOf<FileItem>()
        if (dir != root) {
            list += FileItem("...", dir.parentFile!!)
        }
        list.addAll(dir.listFiles()!!.asSequence().map {
            if (it.isDirectory) {
                FileItem("${it.name}/", it)
            } else {
                FileItem(it.name, it)
            }
        }.sortedWith { a, b -> a.name.compareTo(b.name) })
        contents *= list

        currentRoot *= dir
    }

    fun reload() {
        setDir(currentRoot.value)
    }

    class Factory(
        private val root: File
    ) :ViewModelProvider.AndroidViewModelFactory(){
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FileBrowserViewModel(root) as T
        }
    }
}
