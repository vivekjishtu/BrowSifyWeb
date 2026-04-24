/*
 * Copyright (C) 2026 Vivek Jishtu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package jp.hazuki.yuzubrowser.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
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
    val builtIn: Boolean,
    val version: String? = null,
    val author: String? = null,
    val description: String? = null,
    val base: String? = null,
    val preview: String? = null,
    val deleteKey: String? = null
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
    const val VALIDATION_MISSING_PREVIEW = "missing_preview"
    const val VALIDATION_INVALID_PREVIEW = "invalid_preview"
    const val VALIDATION_THEME_NOT_OBJECT = "theme_not_object"
    const val VALIDATION_INVALID_COLORS = "invalid_colors"
    const val VALIDATION_INVALID_FLAGS = "invalid_flags"
    const val VALIDATION_INVALID_COLOR = "invalid_color"
    const val VALIDATION_INVALID_FLAG = "invalid_flag"
    const val VALIDATION_UNKNOWN_TOKEN = "unknown_token"
    const val VALIDATION_UNREADABLE_THEME_DATA = "unreadable_theme_data"
    const val VALIDATION_INVALID_THEME_DATA = "invalid_theme_data"
    const val VALIDATION_LOW_CONTRAST = "low_contrast"

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
    fun createPreviewDrawable(context: Context, themeId: String): Drawable? {
        loadPreviewDrawable(context, themeId)?.let { return it }
        return createGeneratedPreviewDrawable(context, themeId)
    }

    @JvmStatic
    fun loadPreviewDrawable(context: Context, themeId: String): Drawable? {
        val normalized = normalizeThemeId(themeId)
        val resolvedId = if (normalized == THEME_SYSTEM) {
            if (isSystemInLightMode(context)) THEME_LIGHT else THEME_DARK
        } else {
            normalized
        }
        return loadBuiltInPreview(context, resolvedId)
            ?: loadInstalledPreview(context, resolvedId)
    }

    private fun createGeneratedPreviewDrawable(context: Context, themeId: String): Drawable? {
        val theme = resolve(context, themeId) ?: return null
        val density = context.resources.displayMetrics.density
        val width = (56 * density).toInt().coerceAtLeast(1)
        val height = (36 * density).toInt().coerceAtLeast(1)
        val corner = 6 * density

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val background = theme.color("toolbarBackground").orFallback(
            if (theme.isLight) 0xFFDDDDDD.toInt() else 0xFF121212.toInt()
        )
        val status = theme.color("statusBar").orFallback(background)
        val accent = theme.color("tabAccent")
            .orFallback(theme.color("progress"))
            .orFallback(theme.color("toolbarText"))
            .orFallback(if (theme.isLight) Color.BLACK else Color.WHITE)

        paint.color = background
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), corner, corner, paint)

        paint.color = status
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height * 0.28f, corner, corner, paint)

        paint.color = adjustColor(background, if (theme.isLight) 0.92f else 1.12f)
        canvas.drawRect(0f, height * 0.45f, width.toFloat(), height.toFloat(), paint)

        paint.color = accent
        val inset = 6 * density
        val lineTop = height - 8 * density
        canvas.drawRoundRect(inset, lineTop, width - inset, lineTop + 3 * density, density, density, paint)

        val dotRadius = 4 * density
        canvas.drawCircle(width - inset - dotRadius, height * 0.22f, dotRadius, paint)

        return BitmapDrawable(context.resources, bitmap)
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

        manifest.preview?.takeIf { it.isNotBlank() }?.let { preview ->
            val previewFile = File(themeFolder, preview)
            if (!previewFile.isFile) {
                return ThemeValidationResult(false, VALIDATION_MISSING_PREVIEW)
            }
            if (!previewFile.canonicalPath.startsWith(rootPath)) {
                return ThemeValidationResult(false, VALIDATION_UNSAFE_PATH)
            }
            val extension = previewFile.extension.lowercase(Locale.US)
            if (!isImageExtension(extension) || !isValidImage(previewFile)) {
                return ThemeValidationResult(false, VALIDATION_INVALID_PREVIEW)
            }
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
                manifest.toThemeOption(deleteKey = null)
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
                    manifest.toThemeOption(deleteKey = folder.name)
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

    private fun loadBuiltInPreview(context: Context, id: String): Drawable? {
        val manifest = loadBuiltInManifest(context, id) ?: return null
        val preview = manifest.preview?.takeIf { it.isNotBlank() } ?: return null
        return try {
            context.assets.open("$ASSET_THEME_ROOT/$id/$preview").use { input ->
                BitmapFactory.decodeStream(input)?.let { bitmap ->
                    BitmapDrawable(context.resources, bitmap)
                }
            }
        } catch (e: IOException) {
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

    private fun loadInstalledPreview(context: Context, id: String): Drawable? {
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
        val preview = manifest.preview?.takeIf { it.isNotBlank() } ?: return null
        val previewFile = File(folder, preview)
        if (!previewFile.isFile) return null
        return BitmapFactory.decodeFile(previewFile.absolutePath)?.let { bitmap ->
            BitmapDrawable(context.resources, bitmap)
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
                val name = reader.nextName()
                when (name) {
                    "colors", "tokens" -> readColors(reader, colors)
                    "flags", "systemBars", "web" -> readFlags(reader, flags)
                    else -> {
                        if (isColorField(name)) {
                            readColor(reader)?.let { colors[name] = it }
                        } else if (isFlagField(name)) {
                            readFlag(reader)?.let { flags[name] = it }
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
        val colors = mutableMapOf<String, Int>()
        try {
            JsonReader.of(themeFile.source().buffer()).use { reader ->
                if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
                    return ThemeValidationResult(false, VALIDATION_THEME_NOT_OBJECT)
                }

                reader.beginObject()
                while (reader.hasNext()) {
                    val field = reader.nextName()
                    when (field) {
                        "colors", "tokens" -> {
                            if (!validateColorObject(reader, colors)) {
                                return ThemeValidationResult(false, VALIDATION_INVALID_COLORS)
                            }
                        }
                        "flags", "systemBars", "web" -> {
                            if (!validateFlagObject(reader)) {
                                return ThemeValidationResult(false, VALIDATION_INVALID_FLAGS)
                            }
                        }
                        else -> {
                            if (isColorField(field)) {
                                val color = readColor(reader)
                                if (color == null) {
                                    return ThemeValidationResult(false, VALIDATION_INVALID_COLOR)
                                }
                                colors[field] = color
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

            if (!checkContrast(colors)) {
                return ThemeValidationResult(false, VALIDATION_LOW_CONTRAST)
            }

            return ThemeValidationResult(true)
        } catch (e: IOException) {
            return ThemeValidationResult(false, VALIDATION_UNREADABLE_THEME_DATA)
        } catch (e: JsonDataException) {
            return ThemeValidationResult(false, VALIDATION_INVALID_THEME_DATA)
        }
    }

    private fun validateColorObject(reader: JsonReader, colors: MutableMap<String, Int>): Boolean {
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            reader.skipValue()
            return false
        }
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            val color = readColor(reader)
            if (!isColorField(key) || color == null) {
                return false
            }
            colors[key] = color
        }
        reader.endObject()
        return true
    }

    private fun checkContrast(colors: Map<String, Int>): Boolean {
        val background = colors["toolbarBackground"] ?: return true
        val text = colors["toolbarText"] ?: return true

        val contrast = calculateContrast(background, text)
        return contrast >= 3.0 // WCAG AA for large text/icons
    }

    private fun calculateContrast(color1: Int, color2: Int): Double {
        val l1 = calculateLuminance(color1)
        val l2 = calculateLuminance(color2)
        return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05)
    }

    private fun calculateLuminance(color: Int): Double {
        var r = Color.red(color) / 255.0
        var g = Color.green(color) / 255.0
        var b = Color.blue(color) / 255.0

        r = if (r <= 0.03928) r / 12.92 else Math.pow((r + 0.055) / 1.055, 2.4)
        g = if (g <= 0.03928) g / 12.92 else Math.pow((g + 0.055) / 1.055, 2.4)
        b = if (b <= 0.03928) b / 12.92 else Math.pow((b + 0.055) / 1.055, 2.4)

        return 0.2126 * r + 0.7152 * g + 0.0722 * b
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

    private fun isImageExtension(extension: String): Boolean {
        return extension in setOf("png", "jpg", "jpeg", "webp")
    }

    private fun ThemeManifest.toThemeOption(deleteKey: String?): ThemeOption {
        return ThemeOption(
            id = id,
            name = name,
            builtIn = builtIn,
            version = version,
            author = author,
            description = description,
            base = base,
            preview = preview,
            deleteKey = deleteKey
        )
    }

    private fun Int.orFallback(fallback: Int): Int {
        return if (this != 0) this else fallback
    }

    private fun adjustColor(color: Int, factor: Float): Int {
        val alpha = Color.alpha(color)
        val red = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val green = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val blue = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(alpha, red, green, blue)
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
