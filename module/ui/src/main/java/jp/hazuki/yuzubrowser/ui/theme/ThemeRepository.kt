/*
 * Copyright (C) 2026 Vivek Jishtu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package jp.hazuki.yuzubrowser.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import jp.hazuki.yuzubrowser.core.THEME_DIR
import okio.buffer
import okio.source
import java.io.File
import java.io.IOException
import java.io.InputStream

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

object ThemeRepository {
    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    private const val ASSET_THEME_ROOT = "themes"
    private const val THEME_FILE = "theme.json"

    private const val LEGACY_AUTO = "auto"
    private const val LEGACY_LIGHT = "theme://internal/light"
    private const val LEGACY_DARK = ""

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
