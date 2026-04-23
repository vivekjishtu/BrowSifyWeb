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

package jp.hazuki.yuzubrowser.ui.theme

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import okio.buffer
import okio.source
import java.io.File
import java.io.IOException
import java.io.InputStream

class ThemeManifest @Throws(IllegalManifestException::class, IOException::class)
private constructor(
    val version: String,
    val name: String,
    val id: String,
    val base: String,
    val author: String?,
    val description: String?,
    val preview: String?,
    val builtIn: Boolean
) {

    class IllegalManifestException internal constructor(message: String, val errorType: Int) : Exception(message)

    companion object {
        const val MANIFEST = "manifest.json"

        private const val FORMAT_VERSION = 1

        private const val FIELD_FORMAT_VERSION = "format_version"
        private const val FIELD_VERSION = "version"
        private const val FIELD_NAME = "name"
        private const val FIELD_ID = "id"
        private const val FIELD_BASE = "base"
        private const val FIELD_AUTHOR = "author"
        private const val FIELD_DESCRIPTION = "description"
        private const val FIELD_PREVIEW = "preview"
        private const val FIELD_BUILTIN = "builtin"

        fun getManifest(themeFolder: File): ThemeManifest? {
            try {
                return decodeManifest(File(themeFolder, MANIFEST))
            } catch (e: IllegalManifestException) {
                e.printStackTrace()
            }

            return null
        }

        @Throws(IllegalManifestException::class)
        fun decodeManifest(manifestFile: File): ThemeManifest? {
            if (manifestFile.exists() && manifestFile.isFile) {
                try {
                    JsonReader.of(manifestFile.source().buffer()).use { return decode(it) }
                } catch (e: IOException) {
                    e.printStackTrace()
                    throw IllegalManifestException("unknown error", 0)
                }

            } else {
                return null
            }
        }

        @Throws(IllegalManifestException::class)
        fun decodeManifest(inputStream: InputStream): ThemeManifest {
            try {
                JsonReader.of(inputStream.source().buffer()).use { return decode(it) }
            } catch (e: IOException) {
                e.printStackTrace()
                throw IllegalManifestException("unknown error", 0)
            }
        }

        @Throws(IllegalManifestException::class, IOException::class)
        private fun decode(reader: JsonReader): ThemeManifest {
            if (reader.peek() != JsonReader.Token.BEGIN_OBJECT)
                throw IllegalManifestException("broken manifest file", 1)
            reader.beginObject()

            var version: String? = null
            var name: String? = null
            var id: String? = null
            var base: String? = null
            var author: String? = null
            var description: String? = null
            var preview: String? = null
            var builtIn = false
            while (reader.hasNext()) {
                if (reader.peek() != JsonReader.Token.NAME)
                    throw IllegalManifestException("broken manifest file", 1)

                val field = reader.nextName()
                if (FIELD_FORMAT_VERSION.equals(field, ignoreCase = true)) {
                    try {
                        if (reader.nextInt() > FORMAT_VERSION)
                            throw IllegalManifestException("unknown version of format", 2)
                    } catch (e: JsonDataException) {
                        throw IllegalManifestException("broken manifest file", 1)
                    }
                    continue
                }

                if (FIELD_VERSION.equals(field, ignoreCase = true)) {
                    try {
                        version = reader.nextString().trim { it <= ' ' }
                    } catch (e: JsonDataException) {
                        throw IllegalManifestException("broken manifest file", 1)
                    }
                    continue
                }

                if (FIELD_NAME.equals(field, ignoreCase = true)) {
                    try {
                        name = reader.nextString().trim { it <= ' ' }
                    } catch (e: JsonDataException) {
                        throw IllegalManifestException("broken manifest file", 1)
                    }
                    continue
                }

                if (FIELD_ID.equals(field, ignoreCase = true)) {
                    try {
                        id = reader.nextString().trim { it <= ' ' }
                    } catch (e: JsonDataException) {
                        throw IllegalManifestException("broken manifest file", 1)
                    }
                    continue
                }
                if (FIELD_BASE.equals(field, ignoreCase = true)) {
                    try {
                        base = reader.nextString().trim { it <= ' ' }
                    } catch (e: JsonDataException) {
                        throw IllegalManifestException("broken manifest file", 1)
                    }
                    continue
                }
                if (FIELD_AUTHOR.equals(field, ignoreCase = true)) {
                    author = reader.nextString().trim { it <= ' ' }
                    continue
                }
                if (FIELD_DESCRIPTION.equals(field, ignoreCase = true)) {
                    description = reader.nextString().trim { it <= ' ' }
                    continue
                }
                if (FIELD_PREVIEW.equals(field, ignoreCase = true)) {
                    preview = reader.nextString().trim { it <= ' ' }
                    continue
                }
                if (FIELD_BUILTIN.equals(field, ignoreCase = true)) {
                    builtIn = try {
                        if (reader.peek() == JsonReader.Token.BOOLEAN) reader.nextBoolean() else reader.nextString().toBoolean()
                    } catch (e: JsonDataException) {
                        throw IllegalManifestException("broken manifest file", 1)
                    }
                    continue
                }
                reader.skipValue()
            }
            reader.endObject()

            if (version == null || name == null || id == null)
                throw IllegalManifestException("broken manifest file", 1)

            val resolvedBase = base ?: ThemeRepository.THEME_DARK
            if (resolvedBase != ThemeRepository.THEME_LIGHT && resolvedBase != ThemeRepository.THEME_DARK)
                throw IllegalManifestException("broken manifest file", 1)

            return ThemeManifest(version, name, id, resolvedBase, author, description, preview, builtIn)
        }
    }
}
