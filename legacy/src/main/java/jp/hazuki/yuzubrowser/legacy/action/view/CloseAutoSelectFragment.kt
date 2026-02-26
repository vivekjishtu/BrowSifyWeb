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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.content.IntentCompat
import androidx.core.os.BundleCompat
import jp.hazuki.yuzubrowser.legacy.R
import jp.hazuki.yuzubrowser.legacy.action.Action
import jp.hazuki.yuzubrowser.legacy.databinding.ActionActivityBinding
import jp.hazuki.yuzubrowser.ui.extensions.addCallback
import jp.hazuki.yuzubrowser.ui.widget.recycler.OnRecyclerListener

class CloseAutoSelectFragment : Fragment(), OnRecyclerListener {

    private lateinit var defaultAction: Action
    private lateinit var intentAction: Action
    private lateinit var windowAction: Action

    private var viewBinding: ActionActivityBinding? = null

    private val binding: ActionActivityBinding
        get() = viewBinding!!

    private val defaultActionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            IntentCompat.getParcelableExtra(result.data!!, ActionActivity.EXTRA_ACTION, Action::class.java)?.let {
                defaultAction = it
            }
        }
    }

    private val intentActionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            IntentCompat.getParcelableExtra(result.data!!, ActionActivity.EXTRA_ACTION, Action::class.java)?.let {
                intentAction = it
            }
        }
    }

    private val windowActionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            IntentCompat.getParcelableExtra(result.data!!, ActionActivity.EXTRA_ACTION, Action::class.java)?.let {
                windowAction = it
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewBinding = ActionActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewBinding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val activity = requireActivity()
        val arguments = arguments ?: throw IllegalArgumentException()

        defaultAction = BundleCompat.getParcelable(arguments, DEFAULT, Action::class.java) ?: Action()
        intentAction = BundleCompat.getParcelable(arguments, INTENT, Action::class.java) ?: Action()
        windowAction = BundleCompat.getParcelable(arguments, WINDOW, Action::class.java) ?: Action()


        binding.resetButton.visibility = View.INVISIBLE
        binding.cancelButton.setOnClickListener {
            requireActivity().run {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        }
        binding.okButton.setOnClickListener {
            requireActivity().run {
                val intent = Intent()
                intent.putExtra(DEFAULT, defaultAction as Parcelable?)
                intent.putExtra(INTENT, intentAction as Parcelable?)
                intent.putExtra(WINDOW, windowAction as Parcelable?)
                setResult(Activity.RESULT_OK, intent)
                finish()
            }
        }
        activity.onBackPressedDispatcher.addCallback(this) {
            requireActivity().run {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            true
        }

        val items = mutableListOf(getString(R.string.pref_close_default),
            getString(R.string.pref_close_intent),
            getString(R.string.pref_close_window))

        binding.recyclerView.layoutManager = LinearLayoutManager(activity)
        binding.recyclerView.adapter = CloseAutoSelectAdapter(activity, items, this)
    }

    override fun onRecyclerItemClicked(v: View, position: Int) {
        val builder = ActionActivity.Builder(requireActivity())
        when (position) {
            0 -> defaultActionLauncher.launch(builder.setDefaultAction(defaultAction)
                    .setTitle(R.string.pref_close_default)
                    .create())
            1 -> intentActionLauncher.launch(builder.setDefaultAction(intentAction)
                    .setTitle(R.string.pref_close_intent)
                    .create())
            2 -> windowActionLauncher.launch(builder.setDefaultAction(windowAction)
                    .setTitle(R.string.pref_close_window)
                    .create())
            else -> throw IllegalArgumentException("Unknown position:$position")
        }
    }

    override fun onRecyclerItemLongClicked(v: View, position: Int): Boolean {
        return false
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
    }

    companion object {
        private const val REQUEST_DEFAULT = 0
        private const val REQUEST_INTENT = 1
        private const val REQUEST_WINDOW = 2

        const val DEFAULT = "0"
        const val INTENT = "1"
        const val WINDOW = "2"

        operator fun invoke(defAction: Action?, intentAction: Action?, windowAction: Action?): Fragment {
            return CloseAutoSelectFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(DEFAULT, defAction)
                    putParcelable(INTENT, intentAction)
                    putParcelable(WINDOW, windowAction)
                }
            }
        }
    }
}
