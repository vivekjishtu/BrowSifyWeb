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

package jp.hazuki.yuzubrowser.legacy.settings.activity

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.preference.Preference
import jp.hazuki.yuzubrowser.core.THEME_DIR
import jp.hazuki.yuzubrowser.core.utility.utils.ui
import jp.hazuki.yuzubrowser.legacy.R
import jp.hazuki.yuzubrowser.legacy.theme.Result
import jp.hazuki.yuzubrowser.legacy.theme.importTheme
import jp.hazuki.yuzubrowser.legacy.theme.importThemeDirectory
import jp.hazuki.yuzubrowser.ui.dialog.ConfirmDialog
import jp.hazuki.yuzubrowser.ui.extensions.registerForStartActivityForResult
import jp.hazuki.yuzubrowser.ui.settings.AppPrefs
import jp.hazuki.yuzubrowser.ui.theme.ThemeData
import jp.hazuki.yuzubrowser.ui.theme.ThemeOption
import jp.hazuki.yuzubrowser.ui.theme.ThemeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ThemeManagementFragment : YuzuPreferenceFragment(), ConfirmDialog.OnConfirmedListener {

    override fun onCreateYuzuPreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_theme_managment)

        findPreference<Preference>("import_theme")!!.setOnPreferenceClickListener {
            ImportThemeDialog().show(childFragmentManager, "")
            true
        }

        findPreference<Preference>("delete_theme")!!.setOnPreferenceClickListener {
            ManageThemeDialog().show(childFragmentManager, "")
            true
        }
    }

    private fun importThemeFromFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
        }
        try {
            importThemeFromFileLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun importThemeFromDirectory() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        try {
            importThemeFromDirectoryLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.failed, Toast.LENGTH_SHORT).show()
        }
    }

    private val importThemeFromFileLauncher = registerForStartActivityForResult {
        if (it.resultCode != Activity.RESULT_OK) return@registerForStartActivityForResult
        val uri = it.data!!.data ?: return@registerForStartActivityForResult

        ui {
            val result = withContext(Dispatchers.IO) { importTheme(requireContext(), uri) }
            showImportResult(result)
        }
    }

    private val importThemeFromDirectoryLauncher = registerForStartActivityForResult {
        if (it.resultCode != Activity.RESULT_OK) return@registerForStartActivityForResult
        val uri = it.data!!.data ?: return@registerForStartActivityForResult

        ui {
            val result = withContext(Dispatchers.IO) {
                importThemeDirectory(requireContext(), uri)
            }
            showImportResult(result)
        }
    }

    private fun showImportResult(result: Result) {
        val context = context ?: return
        if (result.isSuccess) {
            Toast.makeText(context, getString(R.string.theme_imported, result.message), Toast.LENGTH_SHORT).show()
            setFragmentResult(REQUEST_THEME_LIST_UPDATE, bundleOf(REQUEST_THEME_LIST_UPDATE to true))
        } else {
            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteThemes(themes: List<String>) {
        if (themes.isEmpty()) return

        val title = getString(R.string.delete_theme)
        val message = resources.getQuantityString(R.plurals.confirm_delete_multiple, themes.size)
        val data = Bundle().apply {
            putStringArray(CONFIRM_DATA, themes.toTypedArray())
        }
        ConfirmDialog(DELETE_THEME_CONFIRM, title, message, data)
            .show(childFragmentManager, "")
    }

    private fun showThemeDetails(theme: ThemeOption) {
        val context = requireContext()
        val view = LayoutInflater.from(context).inflate(R.layout.theme_detail_dialog, null, false)
        view.findViewById<ImageView>(R.id.previewImage).setImageDrawable(
            ThemeRepository.createPreviewDrawable(context, theme.id)
        )
        view.findViewById<TextView>(R.id.metadataView).text = buildMetadataText(theme)
        view.findViewById<TextView>(R.id.descriptionView).text =
            theme.description?.takeIf { it.isNotBlank() } ?: getString(R.string.theme_no_description)

        AlertDialog.Builder(context)
            .setTitle(theme.name)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .apply {
                val deleteKey = theme.deleteKey
                if (deleteKey != null) {
                    setNeutralButton(R.string.theme_delete) { _, _ ->
                        deleteThemes(listOf(deleteKey))
                    }
                }
            }
            .show()
    }

    private fun buildMetadataText(theme: ThemeOption): String {
        val lines = ArrayList<String>(5)
        lines += getString(R.string.theme_label_source, themeSource(theme))
        lines += getString(R.string.theme_label_base, themeBase(theme))
        theme.version?.let { lines += getString(R.string.theme_label_version, it) }
        lines += getString(
            R.string.theme_label_author,
            theme.author?.takeIf { it.isNotBlank() } ?: getString(R.string.theme_unknown_author)
        )
        if (ThemeRepository.normalizeThemeId(AppPrefs.theme_setting.get()) == theme.id) {
            lines += getString(R.string.theme_current_applied)
        }
        return lines.joinToString("\n")
    }

    private fun themeSource(theme: ThemeOption): String {
        return when {
            theme.id == ThemeData.THEME_AUTO -> getString(R.string.theme_source_system)
            theme.builtIn -> getString(R.string.theme_source_builtin)
            else -> getString(R.string.theme_source_imported)
        }
    }

    private fun themeBase(theme: ThemeOption): String {
        return when (theme.base) {
            ThemeRepository.THEME_LIGHT -> getString(R.string.theme_base_light)
            ThemeRepository.THEME_DARK -> getString(R.string.theme_base_dark)
            else -> getString(R.string.theme_base_system)
        }
    }

    override fun onConfirmed(id: Int, data: Bundle?) {
        if (data == null) return
        when (id) {
            DELETE_THEME_CONFIRM -> {
                val root = requireContext().getExternalFilesDir(THEME_DIR)!!
                val themes = data.getStringArray(CONFIRM_DATA)!!
                    .map { File(root, it) }
                themes.forEach {
                    it.deleteRecursively()
                }
                Toast.makeText(requireContext(), R.string.deleted, Toast.LENGTH_SHORT).show()
                setFragmentResult(REQUEST_THEME_LIST_UPDATE, bundleOf(REQUEST_THEME_LIST_UPDATE to true))
            }
        }
    }

    class ImportThemeDialog : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val items = arrayOf(getText(R.string.from_theme_file), getText(R.string.from_theme_folder))
            return AlertDialog.Builder(requireContext())
                .setTitle(R.string.theme_import)
                .setItems(items) { _, which ->
                    when (which) {
                        0 -> (parentFragment as ThemeManagementFragment).importThemeFromFile()
                        1 -> (parentFragment as ThemeManagementFragment).importThemeFromDirectory()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        }
    }

    class ManageThemeDialog : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val fragment = parentFragment as ThemeManagementFragment
            val context = requireContext()
            val items = ThemeRepository.listThemes(context)

            return AlertDialog.Builder(context)
                .setTitle(R.string.theme_management)
                .setAdapter(ThemeListAdapter(context, items)) { _, which ->
                    fragment.showThemeDetails(items[which])
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        }

        private class ThemeListAdapter(
            context: android.content.Context,
            objects: List<ThemeOption>
        ) : ArrayAdapter<ThemeOption>(context, 0, objects) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context)
                    .inflate(R.layout.theme_list_item, parent, false)

                val item = getItem(position) ?: return view
                view.findViewById<ImageView>(R.id.previewImage).setImageDrawable(
                    ThemeRepository.createPreviewDrawable(context, item.id)
                )
                view.findViewById<TextView>(R.id.titleView).text = item.name
                view.findViewById<TextView>(R.id.summaryView).text = buildSummary(item)

                return view
            }

            private fun buildSummary(item: ThemeOption): String {
                val parts = ArrayList<String>(4)
                parts += when {
                    item.id == ThemeData.THEME_AUTO -> context.getString(R.string.theme_source_system)
                    item.builtIn -> context.getString(R.string.theme_source_builtin)
                    else -> context.getString(R.string.theme_source_imported)
                }
                parts += when (item.base) {
                    ThemeRepository.THEME_LIGHT -> context.getString(R.string.theme_base_light)
                    ThemeRepository.THEME_DARK -> context.getString(R.string.theme_base_dark)
                    else -> context.getString(R.string.theme_base_system)
                }
                item.version?.let { parts += "v$it" }
                item.author?.takeIf { it.isNotBlank() }?.let { parts += it }
                return parts.joinToString(" • ")
            }
        }
    }

    companion object {
        private const val DELETE_THEME_CONFIRM = 1
        private const val CONFIRM_DATA = "data"

        const val REQUEST_THEME_LIST_UPDATE = "request_theme_list_update"
    }
}
