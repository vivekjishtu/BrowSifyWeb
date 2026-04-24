/*
 * Copyright (C) 2026 Vivek Jishtu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package jp.hazuki.yuzubrowser.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import jp.hazuki.yuzubrowser.core.THEME_DIR
import jp.hazuki.yuzubrowser.ui.settings.AppPrefs
import okio.buffer
import okio.source
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale

data class ThemeOption(
    val id: String,
    val name: String,
    val builtIn: Boolean
)

data class ResolvedTheme(
    val manifest: ThemeManifest,
    val colors: Map<String, Int>,
    val flags: Map<String, Boolean>
) {
    val isLight: Boolean
        get() = manifest.base == ThemeRepository.THEME_LIGHT

    fun color(name: String): Int = colors[name] ?: 0

    fun flag(name: String): Boolean = flags[name] ?: false
}

data class ThemeValidationResult(
    val isValid: Boolean,
    val reason: String? = null
)

object ThemeRepository {
    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    private const val THEME_SETTING_KEY = "theme_setting"
    private const val ASSET_THEME_ROOT = "themes"
    private const val THEME_FILE = "theme.json"
    private const val MAX_FILE_COUNT = 100
    private const val MAX_TOTAL_BYTES = 10L * 1024L * 1024L
    private const val MAX_FILE_BYTES = 5L * 1024L * 1024L
    private const val MAX_IMAGE_SIDE = 4096

    private const val LEGACY_AUTO = "auto"
    private const val LEGACY_LIGHT = "theme://internal/light"
    private const val LEGACY_DARK = ""

    const val VALIDATION_NOT_DIRECTORY = "not_directory"
    const val VALIDATION_TOO_MANY_FILES = "too_many_files"
    const val VALIDATION_UNSAFE_PATH = "unsafe_path"
    const val VALIDATION_FILE_TOO_LARGE = "file_too_large"
    const val VALIDATION_PACKAGE_TOO_LARGE = "package_too_large"
    const val VALIDATION_UNSUPPORTED_FILE_TYPE = "unsupported_file_type"
    const val VALIDATION_INVALID_IMAGE = "invalid_image"
    const val VALIDATION_INVALID_MANIFEST = "invalid_manifest"
    const val VALIDATION_MISSING_MANIFEST = "missing_manifest"
    const val VALIDATION_RESERVED_ID = "reserved_id"
    const val VALIDATION_MISSING_THEME_DATA = "missing_theme_data"
    const val VALIDATION_THEME_NOT_OBJECT = "theme_not_object"
    const val VALIDATION_INVALID_COLORS = "invalid_colors"
    const val VALIDATION_INVALID_FLAGS = "invalid_flags"
    const val VALIDATION_INVALID_COLOR = "invalid_color"
    const val VALIDATION_INVALID_FLAG = "invalid_flag"
    const val VALIDATION_UNKNOWN_TOKEN = "unknown_token"
    const val VALIDATION_UNREADABLE_THEME_DATA = "unreadable_theme_data"
    const val VALIDATION_INVALID_THEME_DATA = "invalid_theme_data"

    private val reservedThemeIds = setOf(THEME_SYSTEM, THEME_LIGHT, THEME_DARK)
    private val allowedExtensions = setOf("json", "png", "jpg", "jpeg", "webp")

    @JvmStatic
    fun normalizeThemeId(id: String?): String {
        return when (id) {
            null, LEGACY_AUTO, THEME_SYSTEM -> THEME_SYSTEM
            LEGACY_DARK, THEME_DARK -> THEME_DARK
            LEGACY_LIGHT, THEME_LIGHT -> THEME_LIGHT
            else -> id
        }
    }

    @JvmStatic
    fun migrateStoredThemeSetting(context: Context): String {
        val preferences = context.getSharedPreferences(AppPrefs.PREFERENCE_NAME, Context.MODE_PRIVATE)
        val storedTheme = preferences.getString(THEME_SETTING_KEY, THEME_SYSTEM)
        val normalizedTheme = normalizeThemeId(storedTheme)

        if (storedTheme != normalizedTheme) {
            preferences.edit()
                .putString(THEME_SETTING_KEY, normalizedTheme)
                .apply()
        }

        return normalizedTheme
    }

    @JvmStatic
    fun resolve(context: Context, selectedTheme: String?): ResolvedTheme? {
        val normalized = normalizeThemeId(selectedTheme)
        val resolvedId = if (normalized == THEME_SYSTEM) {
            if (isSystemInLightMode(context)) THEME_LIGHT else THEME_DARK
        } else {
            normalized
        }

        return loadBuiltInTheme(context, resolvedId)
            ?: loadInstalledTheme(context, resolvedId)
            ?: loadBuiltInTheme(context, THEME_DARK)
    }

    @JvmStatic
    fun listThemes(context: Context): List<ThemeOption> {
        val themes = mutableListOf(
            ThemeOption(THEME_SYSTEM, "System", true)
        )

        themes += listBuiltInThemes(context)
        themes += listInstalledThemes(context)

        return themes.distinctBy { it.id }
    }

    @JvmStatic
    fun validateThemeFolder(themeFolder: File): ThemeValidationResult {
        if (!themeFolder.isDirectory) {
            return ThemeValidationResult(false, VALIDATION_NOT_DIRECTORY)
        }

        val files = themeFolder.walkTopDown()
            .filter { it.isFile }
            .toList()

        if (files.size > MAX_FILE_COUNT) {
            return ThemeValidationResult(false, VALIDATION_TOO_MANY_FILES)
        }

        var totalBytes = 0L
        val rootPath = themeFolder.canonicalPath + File.separator
        files.forEach { file ->
            if (!file.canonicalPath.startsWith(rootPath)) {
                return ThemeValidationResult(false, VALIDATION_UNSAFE_PATH)
            }

            if (file.length() > MAX_FILE_BYTES) {
                return ThemeValidationResult(false, VALIDATION_FILE_TOO_LARGE)
            }
            totalBytes += file.length()
            if (totalBytes > MAX_TOTAL_BYTES) {
                return ThemeValidationResult(false, VALIDATION_PACKAGE_TOO_LARGE)
            }

            val extension = file.extension.lowercase(Locale.US)
            if (extension !in allowedExtensions) {
                return ThemeValidationResult(false, VALIDATION_UNSUPPORTED_FILE_TYPE)
            }

            if (extension in setOf("png", "jpg", "jpeg", "webp") && !isValidImage(file)) {
                return ThemeValidationResult(false, VALIDATION_INVALID_IMAGE)
            }
        }

        val manifest = try {
            ThemeManifest.decodeManifest(File(themeFolder, ThemeManifest.MANIFEST))
        } catch (e: ThemeManifest.IllegalManifestException) {
            return ThemeValidationResult(false, VALIDATION_INVALID_MANIFEST)
        } ?: return ThemeValidationResult(false, VALIDATION_MISSING_MANIFEST)

        if (manifest.id in reservedThemeIds) {
            return ThemeValidationResult(false, VALIDATION_RESERVED_ID)
        }

        val themeFile = File(themeFolder, THEME_FILE)
        if (!themeFile.isFile) {
            return ThemeValidationResult(false, VALIDATION_MISSING_THEME_DATA)
        }

        return validateThemeJson(themeFile)
    }

    private fun listBuiltInThemes(context: Context): List<ThemeOption> {
        return listOf(THEME_DARK, THEME_LIGHT).mapNotNull { id ->
            loadBuiltInManifest(context, id)?.let { manifest ->
                ThemeOption(manifest.id, manifest.name, true)
            }
        }
    }

    private fun listInstalledThemes(context: Context): List<ThemeOption> {
        val root = context.getExternalFilesDir(THEME_DIR) ?: return emptyList()
        val files = root.listFiles() ?: return emptyList()
        return files.asSequence()
            .filter { it.isDirectory && it.name != ".nomedia" }
            .filter { validateThemeFolder(it).isValid }
            .mapNotNull { folder ->
                ThemeManifest.getManifest(folder)?.let { manifest ->
                    ThemeOption(manifest.id, manifest.name, false)
                }
            }
            .toList()
    }

    private fun loadBuiltInTheme(context: Context, id: String): ResolvedTheme? {
        return try {
            val manifest = context.assets.open("$ASSET_THEME_ROOT/$id/${ThemeManifest.MANIFEST}").use {
                ThemeManifest.decodeManifest(it)
            }
            context.assets.open("$ASSET_THEME_ROOT/$id/$THEME_FILE").use {
                decodeTheme(manifest, it)
            }
        } catch (e: IOException) {
            null
        } catch (e: ThemeManifest.IllegalManifestException) {
            null
        }
    }

    private fun loadBuiltInManifest(context: Context, id: String): ThemeManifest? {
        return try {
            context.assets.open("$ASSET_THEME_ROOT/$id/${ThemeManifest.MANIFEST}").use {
                ThemeManifest.decodeManifest(it)
            }
        } catch (e: IOException) {
            null
        } catch (e: ThemeManifest.IllegalManifestException) {
            null
        }
    }

    private fun loadInstalledTheme(context: Context, id: String): ResolvedTheme? {
        val root = context.getExternalFilesDir(THEME_DIR) ?: return null
        val directFolder = File(root, id)
        val folder = if (directFolder.isDirectory) {
            directFolder
        } else {
            root.listFiles()
                ?.firstOrNull { it.isDirectory && ThemeManifest.getManifest(it)?.id == id }
                ?: return null
        }
        if (!validateThemeFolder(folder).isValid) return null

        val manifest = ThemeManifest.getManifest(folder) ?: return null
        return try {
            val theme = File(folder, THEME_FILE).inputStream().use {
                decodeTheme(manifest, it)
            }
            val baseTheme = loadBuiltInTheme(context, manifest.base)
            if (baseTheme != null) {
                theme.copy(
                    colors = baseTheme.colors + theme.colors,
                    flags = baseTheme.flags + theme.flags
                )
            } else {
                theme
            }
        } catch (e: IOException) {
            null
        } catch (e: JsonDataException) {
            null
        }
    }

    @Throws(IOException::class)
    private fun decodeTheme(manifest: ThemeManifest, inputStream: InputStream): ResolvedTheme {
        JsonReader.of(inputStream.source().buffer()).use { reader ->
            if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
                return ResolvedTheme(manifest, emptyMap(), emptyMap())
            }

            val colors = mutableMapOf<String, Int>()
            val flags = mutableMapOf<String, Boolean>()

            reader.beginObject()
            while (reader.hasNext()) {
                when (val field = reader.nextName()) {
                    "colors" -> readColors(reader, colors)
                    "flags" -> readFlags(reader, flags)
                    else -> {
                        if (isColorField(field)) {
                            readColor(reader)?.let { colors[field] = it }
                        } else if (isFlagField(field)) {
                            readFlag(reader)?.let { flags[field] = it }
                        } else {
                            reader.skipValue()
                        }
                    }
                }
            }
            reader.endObject()

            return ResolvedTheme(manifest, colors, flags)
        }
    }

    private fun readColors(reader: JsonReader, colors: MutableMap<String, Int>) {
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            reader.skipValue()
            return
        }
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            readColor(reader)?.let { colors[key] = it }
        }
        reader.endObject()
    }

    private fun readFlags(reader: JsonReader, flags: MutableMap<String, Boolean>) {
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            reader.skipValue()
            return
        }
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            readFlag(reader)?.let { flags[key] = it }
        }
        reader.endObject()
    }

    private fun readColor(reader: JsonReader): Int? {
        return try {
            val value = reader.nextString().trim()
            if (value.startsWith("0x", ignoreCase = true)) {
                java.lang.Long.decode(value).toInt()
            } else {
                Color.parseColor(value)
            }
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: JsonDataException) {
            null
        }
    }

    private fun readFlag(reader: JsonReader): Boolean? {
        return try {
            if (reader.peek() == JsonReader.Token.BOOLEAN) reader.nextBoolean() else reader.nextString().toBoolean()
        } catch (e: JsonDataException) {
            null
        }
    }

    private fun validateThemeJson(themeFile: File): ThemeValidationResult {
        return try {
            JsonReader.of(themeFile.source().buffer()).use { reader ->
                if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
                    return ThemeValidationResult(false, VALIDATION_THEME_NOT_OBJECT)
                }

                reader.beginObject()
                while (reader.hasNext()) {
                    when (val field = reader.nextName()) {
                        "colors" -> {
                            if (!validateColorObject(reader)) {
                                return ThemeValidationResult(false, VALIDATION_INVALID_COLORS)
                            }
                        }
                        "flags" -> {
                            if (!validateFlagObject(reader)) {
                                return ThemeValidationResult(false, VALIDATION_INVALID_FLAGS)
                            }
                        }
                        else -> {
                            if (isColorField(field)) {
                                if (readColor(reader) == null) {
                                    return ThemeValidationResult(false, VALIDATION_INVALID_COLOR)
                                }
                            } else if (isFlagField(field)) {
                                if (readFlag(reader) == null) {
                                    return ThemeValidationResult(false, VALIDATION_INVALID_FLAG)
                                }
                            } else {
                                return ThemeValidationResult(false, VALIDATION_UNKNOWN_TOKEN)
                            }
                        }
                    }
                }
                reader.endObject()
            }
            ThemeValidationResult(true)
        } catch (e: IOException) {
            ThemeValidationResult(false, VALIDATION_UNREADABLE_THEME_DATA)
        } catch (e: JsonDataException) {
            ThemeValidationResult(false, VALIDATION_INVALID_THEME_DATA)
        }
    }

    private fun validateColorObject(reader: JsonReader): Boolean {
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            reader.skipValue()
            return false
        }
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            if (!isColorField(key) || readColor(reader) == null) {
                return false
            }
        }
        reader.endObject()
        return true
    }

    private fun validateFlagObject(reader: JsonReader): Boolean {
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            reader.skipValue()
            return false
        }
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            if (!isFlagField(key) || readFlag(reader) == null) {
                return false
            }
        }
        reader.endObject()
        return true
    }

    private fun isValidImage(file: File): Boolean {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth in 1..MAX_IMAGE_SIDE && options.outHeight in 1..MAX_IMAGE_SIDE
    }

    private fun isColorField(field: String): Boolean {
        return field.endsWith("Color") ||
            field in setOf(
                "accent",
                "scrollbarAccent",
                "progress",
                "progressIndeterminate",
                "statusBar",
                "toolbarBackground",
                "toolbarText",
                "toolbarIcon",
                "toolbarButtonPress",
                "tabAccent",
                "tabDivider",
                "tabTextNormal",
                "tabTextLock",
                "tabTextPin",
                "tabTextSelected",
                "qcItemBackgroundNormal",
                "qcItemBackgroundSelected",
                "qcItem"
            )
    }

    private fun isFlagField(field: String): Boolean {
        return field in setOf("showTabDivider", "statusBarDarkIcon", "pullToRefreshDark")
    }

    private fun isSystemInLightMode(context: Context): Boolean {
        val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMask != Configuration.UI_MODE_NIGHT_YES
    }
}
