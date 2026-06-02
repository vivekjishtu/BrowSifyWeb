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

package jp.hazuki.yuzubrowser.search.presentation.search

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.*
import android.widget.AdapterView
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import jp.hazuki.yuzubrowser.core.utility.extensions.clipboardText
import jp.hazuki.yuzubrowser.favicon.FaviconManager
import jp.hazuki.yuzubrowser.search.R
import jp.hazuki.yuzubrowser.search.databinding.SearchActivityBinding
import jp.hazuki.yuzubrowser.search.databinding.SearchSeachBarBinding
import jp.hazuki.yuzubrowser.search.presentation.widget.SearchButton
import jp.hazuki.yuzubrowser.ui.INTENT_EXTRA_MODE_FULLSCREEN
import jp.hazuki.yuzubrowser.ui.app.ThemeActivity
import jp.hazuki.yuzubrowser.ui.extensions.applyIconColor
import jp.hazuki.yuzubrowser.ui.settings.AppPrefs
import jp.hazuki.yuzubrowser.ui.theme.ThemeData
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

@AndroidEntryPoint
class SearchActivity : ThemeActivity(), SearchButton.Callback, SearchSuggestAdapter.OnSearchSelectedListener, SuggestDeleteDialog.OnDeleteQuery {

    @Inject
    internal lateinit var faviconManager: FaviconManager

    private val viewModel by viewModels<SearchViewModel>()
    private lateinit var binding: SearchActivityBinding
    private lateinit var barBinding: SearchSeachBarBinding

    private var appData: Bundle? = null
    private var openNewTab: Int = 0

    override fun shouldApplySystemBarPadding(): Boolean = false

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SearchActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.topBox.updatePadding(top = insets.top)
            binding.bottomBox.updatePadding(bottom = insets.bottom)
            binding.root.updatePadding(left = insets.left, right = insets.right)
            windowInsets
        }

        barBinding = SearchSeachBarBinding.inflate(layoutInflater, binding.rootLayout, false)

        val intent = intent ?: throw IllegalStateException("Intent is null")

        val fullscreen = intent.getBooleanExtra(INTENT_EXTRA_MODE_FULLSCREEN, AppPrefs.fullscreen.get())
        if (fullscreen) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.hide(WindowInsets.Type.statusBars())
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }
        }

        val bottomBoxMode = intent.getBooleanExtra(EXTRA_REVERSE, false)
        if (bottomBoxMode) {
            binding.bottomBox.addView(barBinding.root)
        } else {
            binding.topBox.addView(barBinding.root)
        }
        barBinding.searchButton.setActionCallback(this)

        val suggestAdapter = SearchSuggestAdapter().also { it.listener = this }
        binding.recyclerView.adapter = suggestAdapter

        viewModel.suggestModels.observe(this) {
            if (it != null) {
                suggestAdapter.list.clear()
                suggestAdapter.list.addAll(it)
                suggestAdapter.notifyDataSetChanged()
                val layoutManager = binding.recyclerView.layoutManager as LinearLayoutManager
                if (layoutManager.reverseLayout && suggestAdapter.itemCount > 0) {
                    binding.recyclerView.scrollToPosition(0)
                }
            }
        }

        viewModel.also {
            barBinding.searchUrlSpinner.adapter = SearchUrlSpinnerAdapter(
                this, it.suggestProviders.urls, faviconManager)
            barBinding.searchUrlSpinner.setSelection(it.providerSelection.value ?: -1)
            barBinding.searchUrlSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    it.providerSelection.value = position
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        viewModel.providerSelection.observe(this) { position ->
            if (position >= 0) {
                viewModel.suggestProviders.selectedId = position
                if (AppPrefs.searchUrlShowIcon.get() && AppPrefs.searchUrlSaveSwitching.get()) {
                    AppPrefs.search_url.set(viewModel.suggestProviders[position].url)
                    AppPrefs.commit(this, AppPrefs.search_url)
                    viewModel.saveProvider()
                }
            }
        }

        if (!AppPrefs.searchUrlShowIcon.get()) {
            barBinding.searchUrlSpinner.visibility = View.GONE
        }

        val searchButton = barBinding.searchButton
        val editText = barBinding.editText
        val recyclerView = binding.recyclerView

        editText.doAfterTextChanged {
            viewModel.setQuery(it?.toString() ?: "")
        }

        editText.setOnEditorActionListener { _, actionId, _ ->
            if (android.view.inputmethod.EditorInfo.IME_ACTION_GO == actionId) {
                autoSearch()
                return@setOnEditorActionListener true
            }
            false
        }

        ThemeData.getInstance()?.let { themeData ->
            if (themeData.toolbarBackgroundColor != 0)
                barBinding.root.setBackgroundColor(themeData.toolbarBackgroundColor)
            val textColor = themeData.toolbarTextColor
            if (textColor != 0) {
                editText.setTextColor(textColor)
                editText.setHintTextColor(textColor and 0xffffff or 0x55000000)
            }
            if (themeData.toolbarImageColor != 0)
                searchButton.setColorFilter(themeData.toolbarImageColor)
        }

        searchButton.setSense(AppPrefs.swipebtn_sensitivity.get())

        editText.customSelectionActionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu) = true

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = true

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                val text = viewModel.query

                var min = 0
                var max = text.length

                if (editText.isFocused) {
                    val selStart = editText.selectionStart
                    val selEnd = editText.selectionEnd


                    min = max(0, min(selStart, selEnd))
                    max = max(0, max(selStart, selEnd))
                }

                when (item.itemId) {
                    android.R.id.copy -> if (min == 0 && max == text.length && viewModel.decodedInitQuery == text) {
                        clipboardText = viewModel.initQuery!!
                        mode.finish()
                        return true
                    }
                    android.R.id.cut -> if (min == 0 && max == text.length && viewModel.decodedInitQuery == text) {
                        clipboardText = viewModel.initQuery!!
                        editText.setText("")
                        mode.finish()
                        return true
                    }
                }
                return false
            }

            override fun onDestroyActionMode(mode: ActionMode) = Unit
        }

        appData = intent.getBundleExtra(EXTRA_APP_DATA)
        openNewTab = intent.getIntExtra(EXTRA_OPEN_NEW_TAB, 0)

        recyclerView.layoutManager = LinearLayoutManager(this).apply {
            if (bottomBoxMode) reverseLayout = true
        }
        recyclerView.setOnOutSideClickListener { finish() }
        recyclerView.setOnClickListener { finish() }

        val initQuery = intent.getStringExtra(EXTRA_QUERY)
        if (initQuery != null) {
            viewModel.setInitQuery(initQuery)
            editText.setText(initQuery)
            viewModel.setQuery(initQuery)
        } else {
            editText.setText("")
            viewModel.setQuery("")
        }

        if (intent.getBooleanExtra(EXTRA_SELECT_INITIAL_QUERY, true)) {
            editText.selectAll()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.search_activity, menu)
        menu.applyIconColor(this)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.voiceSearch -> {
                recognizeSpeech()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun forceOpenUrl() {
        finish(SearchViewModel.SEARCH_MODE_URL)
    }

    override fun forceSearchWord() {
        finish(SearchViewModel.SEARCH_MODE_WORD)
    }

    override fun autoSearch() {
        finish(SearchViewModel.SEARCH_MODE_AUTO)
    }

    private fun finish(mode: Int) {
        val result = viewModel.getFinishResult(mode)
        if (result != null) {
            setResult(RESULT_OK, Intent().apply {
                putExtra(EXTRA_QUERY, result.query)
                putExtra(EXTRA_SEARCH_MODE, mode)
                putExtra(EXTRA_SEARCH_URL, result.url)
                putExtra(EXTRA_OPEN_NEW_TAB, openNewTab)
                appData?.let { putExtra(EXTRA_APP_DATA, it) }
            })
        } else {
            setResult(RESULT_CANCELED)
        }
        finish()
    }

    override fun recognizeSpeech() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            startActivityForResult(intent, RESULT_REQUEST_SPEECH)
        } catch (e: ActivityNotFoundException) {
            e.printStackTrace()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            RESULT_REQUEST_SPEECH -> {
                if (resultCode != RESULT_OK || data == null) return
                val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                if (!results.isNullOrEmpty()) {
                    val query = results[0]
                    barBinding.editText.run {
                        setText(query)
                        selectAll()
                    }
                }
            }
            else -> {
                super.onActivityResult(requestCode, resultCode, data)
            }
        }
    }

    override fun onSelectedQuery(query: String) {
        viewModel.setQuery(query)
        finish(SearchViewModel.SEARCH_MODE_AUTO)
    }

    override fun onInputQuery(query: String) {
        barBinding.editText.run {
            setText(query)
            setSelection(query.length)
        }
    }

    override fun onDeleteQuery(query: String) {
        SuggestDeleteDialog.newInstance(query).show(supportFragmentManager, "delete")
    }

    override fun onDelete(query: String) {
        viewModel.deleteQuery(query)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            finish()
        }
        return super.onTouchEvent(event)
    }

    companion object {
        const val EXTRA_QUERY = "jp.hazuki.yuzubrowser.legacy.search.SearchActivity.extra.query"
        const val EXTRA_SELECT_INITIAL_QUERY = "jp.hazuki.yuzubrowser.legacy.search.SearchActivity.extra.selectinitquery"
        const val EXTRA_APP_DATA = "jp.hazuki.yuzubrowser.legacy.search.SearchActivity.extra.appdata"
        const val EXTRA_SEARCH_MODE = "jp.hazuki.yuzubrowser.legacy.search.SearchActivity.extra.searchmode"
        const val EXTRA_SEARCH_URL = "jp.hazuki.yuzubrowser.legacy.search.SearchActivity.extra.searchUrl"
        const val EXTRA_OPEN_NEW_TAB = "jp.hazuki.yuzubrowser.legacy.search.SearchActivity.extra.openNewTab"
        const val EXTRA_REVERSE = "jp.hazuki.yuzubrowser.legacy.search.SearchActivity.extra.reverse"

        const val SEARCH_MODE_AUTO = SearchViewModel.SEARCH_MODE_AUTO
        const val SEARCH_MODE_URL = SearchViewModel.SEARCH_MODE_URL
        const val SEARCH_MODE_WORD = SearchViewModel.SEARCH_MODE_WORD

        const val TAB_TYPE_CURRENT = 0
        const val TAB_TYPE_NEW_TAB = 1
        const val TAB_TYPE_NEW_RIGHT_TAB = 2

        private const val RESULT_REQUEST_SPEECH = 1
    }
}
