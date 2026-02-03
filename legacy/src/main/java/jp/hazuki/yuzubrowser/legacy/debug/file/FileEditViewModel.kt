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

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import jp.hazuki.yuzubrowser.core.lifecycle.KotlinLiveData
import java.io.File
import java.io.IOException

class FileEditViewModel(application: Application) : AndroidViewModel(application) {
    val text = KotlinLiveData("")
    private var file: File? = null

    fun init(file: File) {
        if (this.file?.path == file.path) return
        this.file = file
        load()
    }

    private fun load() {
        try {
            val target = file ?: return
            text *= target.readText()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun save() {
        try {
            val target = file ?: return
            target.writeText(text.value)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
