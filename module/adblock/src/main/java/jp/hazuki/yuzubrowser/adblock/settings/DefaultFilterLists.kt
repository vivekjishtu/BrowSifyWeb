package jp.hazuki.yuzubrowser.adblock.settings

import android.content.Context
import jp.hazuki.yuzubrowser.adblock.repository.abp.AbpDatabase
import jp.hazuki.yuzubrowser.adblock.repository.abp.AbpEntity
import kotlinx.coroutines.runBlocking

/** Seeds subscriptions only when the app creates its initial settings database. */
fun initializeDefaultFilterLists(context: Context, database: AbpDatabase) = runBlocking {
    val entries = context.assets.open("adblock/default_filter_list.txt").bufferedReader().use { it.readLines() }
    database.abpDao().inset(entries.map { line ->
        val fields = line.split(',', limit = 3)
        require(fields.size == 3) { "Invalid default filter entry" }
        AbpEntity(url = fields[2], title = fields[1], enabled = fields[0] == "1")
    })
}
