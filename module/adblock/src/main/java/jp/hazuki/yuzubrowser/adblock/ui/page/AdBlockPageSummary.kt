package jp.hazuki.yuzubrowser.adblock.ui.page

import android.content.Intent
import android.graphics.Typeface
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.fragment.app.FragmentActivity
import jp.hazuki.yuzubrowser.adblock.PageBlockingStats
import jp.hazuki.yuzubrowser.adblock.R
import jp.hazuki.yuzubrowser.adblock.ui.abp.AbpActivity
import jp.hazuki.yuzubrowser.adblock.ui.original.AddAdBlockDialog

object AdBlockPageSummary {
    fun show(
        activity: FragmentActivity,
        host: String,
        enabled: Boolean,
        snapshot: PageBlockingStats.Snapshot,
        onEnabledChanged: (Boolean) -> Unit,
    ) {
        val dialog = BottomSheetDialog(activity)
        val padding = activity.resources.getDimensionPixelSize(R.dimen.adblock_sheet_padding)
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        content.addView(TextView(activity).apply {
            text = activity.getString(R.string.adblock_page_title)
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
        })
        content.addView(TextView(activity).apply {
            text = host.ifBlank { activity.getString(R.string.adblock_no_site) }
            textSize = 14f
            setPadding(0, 8, 0, 8)
        })
        content.addView(Switch(activity).apply {
            text = activity.getString(R.string.adblock_enable)
            isChecked = enabled
            setOnCheckedChangeListener { _, isEnabled ->
                onEnabledChanged(isEnabled)
                dialog.dismiss()
            }
        })
        content.addView(TextView(activity).apply {
            text = activity.getString(R.string.adblock_blocked_count, if (enabled) snapshot.total else 0)
            textSize = 16f
            setPadding(0, 8, 0, 8)
        })

        val rows = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        if (!enabled || snapshot.hosts.isEmpty()) {
            rows.addView(TextView(activity).apply {
                text = activity.getString(R.string.adblock_empty_state)
                setPadding(0, 8, 0, 12)
            })
        } else {
            snapshot.hosts.forEach { blocked ->
                rows.addView(TextView(activity).apply {
                    text = activity.getString(R.string.adblock_host_count, blocked.host, blocked.count)
                    textSize = 14f
                    setPadding(0, 8, 0, 2)
                })
                blocked.rules.forEach { rule ->
                    rows.addView(TextView(activity).apply {
                        text = rule
                        textSize = 12f
                        setPadding(12, 2, 0, 2)
                    })
                }
            }
        }
        content.addView(ScrollView(activity).apply { addView(rows) }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            activity.resources.getDimensionPixelSize(R.dimen.adblock_sheet_list_height),
        ))

        content.addView(Button(activity).apply {
            text = activity.getString(R.string.adblock_manage_lists)
            setOnClickListener {
                activity.startActivity(Intent(activity, AbpActivity::class.java))
                dialog.dismiss()
            }
        })
        content.addView(Button(activity).apply {
            text = activity.getString(R.string.adblock_pause_site)
            isEnabled = host.isNotBlank()
            setOnClickListener {
                AddAdBlockDialog.addWhitePageListInstance(host).show(activity.supportFragmentManager, "adblock_pause_site")
                dialog.dismiss()
            }
        })
        dialog.setContentView(content)
        dialog.show()
    }
}
