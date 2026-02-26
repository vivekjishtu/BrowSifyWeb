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

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import androidx.core.os.BundleCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import com.google.android.material.snackbar.Snackbar
import jp.hazuki.yuzubrowser.legacy.R
import jp.hazuki.yuzubrowser.legacy.action.*
import jp.hazuki.yuzubrowser.legacy.utils.view.recycler.RecyclerFabFragment
import jp.hazuki.yuzubrowser.ui.dialog.DeleteDialogCompat
import jp.hazuki.yuzubrowser.ui.extensions.applyAppTheme
import jp.hazuki.yuzubrowser.ui.extensions.applyIconColor
import jp.hazuki.yuzubrowser.ui.widget.recycler.ArrayRecyclerAdapter
import jp.hazuki.yuzubrowser.ui.widget.recycler.OnRecyclerListener

class ActionListFragment : RecyclerFabFragment(), OnRecyclerListener, DeleteDialogCompat.OnDelete {

    private lateinit var mList: ActionList
    private lateinit var adapter: ActionListAdapter
    private lateinit var mActionNameArray: ActionNameArray
    private lateinit var names: ActionNameMap
    private lateinit var icons: ActionIconMap

    private val addLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val data = result.data!!
            val action = IntentCompat.getParcelableExtra(data, ActionActivity.EXTRA_ACTION, Action::class.java)!!
            mList.add(action)
            onActionListChanged()
            adapter.notifyDataSetChanged()
        }
    }

    private val editLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val data = result.data!!
            val action = IntentCompat.getParcelableExtra(data, ActionActivity.EXTRA_ACTION, Action::class.java)!!
            if (action.isEmpty()) {
                Snackbar.make(rootView, R.string.action_cant_empty, Snackbar.LENGTH_SHORT)
                    .applyAppTheme()
                    .show()
                return@registerForActivityResult
            }
            val position = data.getBundleExtra(ActionActivity.EXTRA_RETURN)!!.getInt(EXTRA_POSITION)
            mList[position] = action
            onActionListChanged()
            adapter.notifyDataSetChanged()
        }
    }

    private val addEasyLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val data = result.data!!
            val action = IntentCompat.getParcelableExtra(data, ActionActivity.EXTRA_ACTION, Action::class.java)!!
            action.forEach {
                mList.add(Action(it))
            }
            onActionListChanged()
            adapter.notifyDataSetChanged()
        }
    }

    private val jsonLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val data = result.data!!
            val actionList = IntentCompat.getParcelableExtra(data, ActionStringActivity.EXTRA_ACTION, ActionList::class.java)!!
            mList.clear()
            mList.addAll(actionList)
            onActionListChanged()
            adapter.notifyDataSetChanged()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.action_list, menu)
                applyIconColor(menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                when (menuItem.itemId) {
                    R.id.sort -> {
                        val next = !adapter.isSortMode
                        adapter.isSortMode = next

                        Toast.makeText(activity, if (next) R.string.start_sort else R.string.end_sort, Toast.LENGTH_SHORT).show()
                        return true
                    }
                    R.id.actionToJson -> {
                        val intent = Intent(activity, ActionStringActivity::class.java)
                        intent.putExtra(ActionStringActivity.EXTRA_ACTION, mList as Parcelable?)
                        jsonLauncher.launch(intent)
                        return true
                    }
                }
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        val arguments = arguments ?: return

        mActionNameArray = BundleCompat.getParcelable(arguments, ActionNameArray.INTENT_EXTRA, ActionNameArray::class.java)!!
        names = ActionNameMap(resources)
        icons = ActionIconMap(resources)

        val actions = BundleCompat.getParcelable(arguments, EXTRA_ACTION_LIST, ActionList::class.java)
        setActionList(actions)
    }

    override fun onRecyclerItemClicked(v: View, position: Int) {
        val bundle = Bundle()
        bundle.putInt(EXTRA_POSITION, position)
        val intent = ActionActivity.Builder(activity ?: return)
                .setDefaultAction(mList[position])
                .setActionNameArray(mActionNameArray)
                .setTitle(R.string.edit_action)
                .setReturnData(bundle)
                .create()
        editLauncher.launch(intent)
    }

    override fun onRecyclerItemLongClicked(v: View, position: Int): Boolean {
        if (childFragmentManager.findFragmentByTag(TAG_DELETE_FRAGMENT) == null) {
            DeleteDialogCompat.newInstance(activity, R.string.confirm, R.string.confirm_delete_action, position)
                    .show(childFragmentManager, TAG_DELETE_FRAGMENT)
        }
        return true
    }

    override fun onDelete(position: Int) {
        mList.removeAt(position)
        adapter.notifyDataSetChanged()
        onActionListChanged()
    }

    override fun onAddButtonClick() {
        val intent = ActionActivity.Builder(activity ?: return)
                .setTitle(R.string.edit_action)
                .setActionNameArray(mActionNameArray)
                .create()

        addLauncher.launch(intent)
    }

    override fun onAddButtonLongClick(): Boolean {
        startEasyAdd()
        return true
    }

    private fun setActionList(list: ActionList?) {
        val activity = activity ?: return

        mList = list ?: ActionList()

        adapter = ActionListAdapter(activity, mList, names, icons, this)
        setRecyclerViewAdapter(adapter)
    }

    private fun onActionListChanged() {
        val activity = activity ?: return

        if (activity is ActionListActivity) {
            activity.onActionListChanged(mList)
        }
        val data = Intent()
        data.putExtra(EXTRA_ACTION_LIST, mList as Parcelable?)
        activity.setResult(RESULT_OK, data)
    }

    override fun onMove(recyclerView: androidx.recyclerview.widget.RecyclerView, fromIndex: Int, toIndex: Int): Boolean {
        adapter.move(fromIndex, toIndex)
        onActionListChanged()
        return true
    }

    override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, index: Int) {
        val action = mList.removeAt(index)
        adapter.notifyDataSetChanged()
        onActionListChanged()
        Snackbar.make(rootView, R.string.deleted, Snackbar.LENGTH_SHORT)
                .applyAppTheme()
                .setAction(R.string.undo) {
                    mList.add(index, action)
                    adapter.notifyDataSetChanged()
                    onActionListChanged()
                }
                .show()
    }

    private fun startEasyAdd() {
        val intent = ActionActivity.Builder(activity ?: return)
                .setTitle(R.string.action_easy_add)
                .setActionNameArray(mActionNameArray)
                .create()

        addEasyLauncher.launch(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return false
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
    }

    override val isNeedDivider: Boolean
        get() = true

    override val isLongPressDragEnabled
        get() = adapter.isSortMode

    private class ActionListAdapter(
        context: Context,
        actionList: ActionList,
        private val names: ActionNameMap,
        private val icons: ActionIconMap,
        recyclerListener: OnRecyclerListener
    ) : ArrayRecyclerAdapter<Action, ActionListAdapter.Holder>(context, actionList, recyclerListener) {

        override fun onBindViewHolder(holder: Holder, item: Action, position: Int) {
            holder.apply {
                textView.text = names[item]
                imageView.setImageDrawable(icons[item])
            }
        }

        override fun onCreateViewHolder(inflater: LayoutInflater, parent: ViewGroup?, viewType: Int): Holder {
            return Holder(
                inflater.inflate(R.layout.action_list_item, parent, false),
                this)
        }

        private class Holder(
            view: View,
            adapter: ActionListAdapter
        ) : ArrayViewHolder<Action>(view, adapter) {
            val textView: TextView = view.findViewById(R.id.textView)
            val imageView: ImageView = view.findViewById(R.id.imageView)
        }
    }

    companion object {
        const val EXTRA_ACTION_LIST = "ActionListActivity.extra.actionList"
        private const val EXTRA_POSITION = "pos"
        private const val RESULT_REQUEST_ADD = 1
        private const val RESULT_REQUEST_EDIT = 2
        private const val RESULT_REQUEST_ADD_EASY = 3
        private const val RESULT_REQUEST_JSON = 4

        private const val TAG_DELETE_FRAGMENT = "delete"

        fun newInstance(actionList: ActionList, nameArray: ActionNameArray): androidx.fragment.app.Fragment {
            return ActionListFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(EXTRA_ACTION_LIST, actionList)
                    putParcelable(ActionNameArray.INTENT_EXTRA, nameArray)
                }
            }
        }
    }
}
