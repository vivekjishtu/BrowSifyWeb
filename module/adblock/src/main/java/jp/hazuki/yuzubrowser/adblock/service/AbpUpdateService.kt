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

package jp.hazuki.yuzubrowser.adblock.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Handler
import androidx.core.content.getSystemService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import jp.hazuki.yuzubrowser.adblock.BROADCAST_ACTION_UPDATE_AD_BLOCK_DATA
import jp.hazuki.yuzubrowser.adblock.filter.abp.getAbpBlackListFile
import jp.hazuki.yuzubrowser.adblock.filter.abp.getAbpElementListFile
import jp.hazuki.yuzubrowser.adblock.filter.abp.getAbpWhiteListFile
import jp.hazuki.yuzubrowser.adblock.filter.abp.getAbpWhitePageListFile
import jp.hazuki.yuzubrowser.adblock.filter.abp.isNeedUpdate
import jp.hazuki.yuzubrowser.adblock.filter.unified.UnifiedFilter
import jp.hazuki.yuzubrowser.adblock.filter.unified.element.ElementFilter
import jp.hazuki.yuzubrowser.adblock.filter.unified.getFilterDir
import jp.hazuki.yuzubrowser.adblock.filter.unified.io.ElementWriter
import jp.hazuki.yuzubrowser.adblock.filter.unified.io.FilterWriter
import jp.hazuki.yuzubrowser.adblock.repository.AdBlockPref
import jp.hazuki.yuzubrowser.adblock.repository.abp.AbpDatabase
import jp.hazuki.yuzubrowser.adblock.repository.abp.AbpEntity
import jp.hazuki.yuzubrowser.core.eventbus.LocalEventBus
import jp.hazuki.yuzubrowser.core.utility.extensions.isConnectedWifi
import jp.hazuki.yuzubrowser.core.utility.log.ErrorReport
import jp.hazuki.yuzubrowser.ui.settings.AppPrefs
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val AN_HOUR = 60 * 60 * 1000
private const val A_DAY = 24 * AN_HOUR
private const val ACTION_UPDATE_ALL = "update_all"
private const val ACTION_UPDATE_ABP = "update_abp"

private const val KEY_ACTION = "abp.action"
private const val KEY_FORCE_UPDATE = "abp.force_update"
private const val KEY_ENTITY_ID = "abp.entity_id"
private const val KEY_CALLBACK_ID = "abp.callback_id"

private const val WORK_NAME_UPDATE_ALL = "abp_update_all"
private const val WORK_NAME_UPDATE_PREFIX = "abp_update_"

class AbpUpdateService private constructor() {

    companion object {
        private val callbacks = ConcurrentHashMap<String, UpdateResult>()

        fun updateAll(context: Context, forceUpdate: Boolean = false, result: UpdateResult? = null) {
            if (!forceUpdate) {
                val prefs = AdBlockPref.get(context.applicationContext)
                if (prefs.abpNextUpdateTime < System.currentTimeMillis()) return

                if (AppPrefs.abpUpdateWifiOnly.get()) {
                    val cm = context.getSystemService<ConnectivityManager>()!!
                    if (!cm.isConnectedWifi()) return
                }
            }

            val callbackId = registerCallback(result)
            val input = Data.Builder()
                .putString(KEY_ACTION, ACTION_UPDATE_ALL)
                .putBoolean(KEY_FORCE_UPDATE, forceUpdate)
                .apply {
                    callbackId?.let { putString(KEY_CALLBACK_ID, it) }
                }
                .build()

            val request = OneTimeWorkRequestBuilder<AbpUpdateWorker>()
                .setInputData(input)
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(WORK_NAME_UPDATE_ALL, ExistingWorkPolicy.REPLACE, request)
        }

        fun update(context: Context, abpEntity: AbpEntity, result: UpdateResult? = null) {
            if (abpEntity.entityId <= 0) return

            val callbackId = registerCallback(result)
            val input = Data.Builder()
                .putString(KEY_ACTION, ACTION_UPDATE_ABP)
                .putInt(KEY_ENTITY_ID, abpEntity.entityId)
                .apply {
                    callbackId?.let { putString(KEY_CALLBACK_ID, it) }
                }
                .build()

            val request = OneTimeWorkRequestBuilder<AbpUpdateWorker>()
                .setInputData(input)
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    WORK_NAME_UPDATE_PREFIX + abpEntity.entityId,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
        }

        private fun registerCallback(callback: UpdateResult?): String? {
            if (callback == null) return null
            val callbackId = UUID.randomUUID().toString()
            callbacks[callbackId] = callback
            return callbackId
        }

        internal fun notifyUpdated(callbackId: String?, entity: AbpEntity) {
            if (callbackId == null) return
            callbacks.remove(callbackId)?.dispatchUpdated(entity)
        }

        internal fun notifyFailed(callbackId: String?, entity: AbpEntity) {
            if (callbackId == null) return
            callbacks.remove(callbackId)?.dispatchFailed(entity)
        }

        internal fun notifyUpdateAll(callbackId: String?) {
            if (callbackId == null) return
            callbacks.remove(callbackId)?.dispatchUpdateAll()
        }
    }

    abstract class UpdateResult(handler: Handler?) {
        private val handler = handler

        internal fun dispatchFailed(entity: AbpEntity) {
            post { onFailedUpdate(entity) }
        }

        internal fun dispatchUpdated(entity: AbpEntity) {
            post { onUpdated(entity) }
        }

        internal fun dispatchUpdateAll() {
            post { onUpdateAll() }
        }

        private fun post(action: () -> Unit) {
            if (handler != null) {
                handler.post(action)
            } else {
                action()
            }
        }

        abstract fun onFailedUpdate(entity: AbpEntity)

        abstract fun onUpdated(entity: AbpEntity)

        abstract fun onUpdateAll()
    }
}

class AbpUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val entryPoint = EntryPointAccessors.fromApplication(
        appContext,
        AbpUpdateWorkerEntryPoint::class.java
    )

    private val okHttpClient: OkHttpClient
        get() = entryPoint.okHttpClient()

    private val abpDatabase: AbpDatabase
        get() = entryPoint.abpDatabase()

    override suspend fun doWork(): Result {
        val callbackId = inputData.getString(KEY_CALLBACK_ID)
        return when (inputData.getString(KEY_ACTION)) {
            ACTION_UPDATE_ALL -> updateAll(inputData.getBoolean(KEY_FORCE_UPDATE, false), callbackId)
            ACTION_UPDATE_ABP -> {
                val entityId = inputData.getInt(KEY_ENTITY_ID, -1)
                if (entityId <= 0) {
                    Result.failure()
                } else {
                    updateAbpEntity(entityId, callbackId)
                }
            }
            else -> Result.failure()
        }
    }

    private suspend fun updateAll(forceUpdate: Boolean, callbackId: String?): Result {
        var result = false
        var nextUpdateTime = Long.MAX_VALUE
        val now = System.currentTimeMillis()
        abpDatabase.abpDao().getAll().forEach {
            if (forceUpdate || it.isNeedUpdate()) {
                val localResult = updateInternal(it, forceUpdate)
                if (localResult && it.expires > 0) {
                    val nextTime = it.expires * AN_HOUR + now
                    if (nextTime < nextUpdateTime) nextUpdateTime = nextTime
                }
                result = result or localResult
            }
        }

        AdBlockPref.get(applicationContext).abpNextUpdateTime = if (nextUpdateTime != Long.MAX_VALUE) {
            nextUpdateTime
        } else {
            System.currentTimeMillis() + A_DAY
        }
        if (result) {
            LocalEventBus.getDefault().notify(BROADCAST_ACTION_UPDATE_AD_BLOCK_DATA)
        }
        AbpUpdateService.notifyUpdateAll(callbackId)
        return Result.success()
    }

    private suspend fun updateAbpEntity(entityId: Int, callbackId: String?): Result {
        val entity = abpDatabase.abpDao().getById(entityId) ?: AbpEntity(entityId = entityId)
        return if (entity.url.isNotEmpty() && updateInternal(entity)) {
            AbpUpdateService.notifyUpdated(callbackId, entity)
            LocalEventBus.getDefault().notify(BROADCAST_ACTION_UPDATE_AD_BLOCK_DATA)
            Result.success()
        } else {
            AbpUpdateService.notifyFailed(callbackId, entity)
            Result.failure()
        }
    }

    private suspend fun updateInternal(entity: AbpEntity, forceUpdate: Boolean = false): Boolean {
        return when {
            entity.url == "bsw://adblock/filter" -> updateAssets(entity)
            entity.url.startsWith("http") -> updateHttp(entity, forceUpdate)
            entity.url.startsWith("file") -> updateFile(entity)
            else -> false
        }
    }

    private suspend fun updateHttp(entity: AbpEntity, forceUpdate: Boolean): Boolean {

        val request = try {
            Request.Builder()
                .url(entity.url)
                .get()
        } catch (_: IllegalArgumentException) {
            return false
        }

        if (!forceUpdate) {
            entity.lastModified?.let {
                val dir = applicationContext.getFilterDir()

                if (dir.getAbpBlackListFile(entity).exists() ||
                    dir.getAbpWhiteListFile(entity).exists() ||
                    dir.getAbpWhitePageListFile(entity).exists()
                ) {
                    request.addHeader("If-Modified-Since", it)
                }
            }
        }

        val call = okHttpClient.newCall(request.build())
        try {
            val response = call.execute()

            if (response.code == 304) {
                entity.lastLocalUpdate = System.currentTimeMillis()
                abpDatabase.abpDao().update(entity)
                return false
            }
            response.body?.run {
                val charset = contentType()?.charset() ?: Charsets.UTF_8
                source().inputStream().bufferedReader(charset).use { reader ->
                    if (decode(reader, charset, entity)) {
                        entity.lastLocalUpdate = System.currentTimeMillis()
                        entity.lastModified = response.header("Last-Modified")
                        abpDatabase.abpDao().update(entity)
                        return true
                    }
                }
            }
        } catch (_: IOException) {
        }
        return false
    }

    private suspend fun updateFile(entity: AbpEntity): Boolean {
        val path = Uri.parse(entity.url).path ?: return false
        val file = File(path)
        if (file.lastModified() < entity.lastLocalUpdate) return false

        try {
            file.inputStream().bufferedReader().use { reader ->
                return decode(reader, Charsets.UTF_8, entity)
            }
        } catch (_: IOException) {
        }
        return false
    }

    private suspend fun updateAssets(entity: AbpEntity): Boolean {
        if (entity.version == "2") {
            val dir = applicationContext.getFilterDir()

            if (dir.getAbpBlackListFile(entity).exists() &&
                dir.getAbpWhiteListFile(entity).exists() &&
                dir.getAbpWhitePageListFile(entity).exists()
            ) return false
        }

        applicationContext.assets.open("adblock/yuzu_filter.txt").bufferedReader().use {
            return decode(it, Charsets.UTF_8, entity)
        }
    }

    private suspend fun decode(reader: BufferedReader, charset: Charset, entity: AbpEntity): Boolean {
        val decoder = jp.hazuki.yuzubrowser.adblock.filter.abp.AbpFilterDecoder()
        if (!decoder.checkHeader(reader, charset)) return false

        val set = decoder.decode(reader, entity.url)

        val info = set.filterInfo
        entity.title = info.title
        entity.expires = info.expires ?: -1
        entity.homePage = info.homePage
        entity.version = info.version
        entity.lastUpdate = info.lastUpdate
        entity.lastLocalUpdate = System.currentTimeMillis()
        val dir = applicationContext.getFilterDir()

        val writer = FilterWriter()
        writer.write(dir.getAbpBlackListFile(entity), set.blackList)
        writer.write(dir.getAbpWhiteListFile(entity), set.whiteList)
        writer.write(dir.getAbpWhitePageListFile(entity), set.elementDisableFilter)

        val elementWriter = ElementWriter()
        elementWriter.write(dir.getAbpElementListFile(entity), set.elementList)

        abpDatabase.abpDao().update(entity)
        return true
    }

    private fun FilterWriter.write(file: File, list: List<UnifiedFilter>) {
        if (list.isNotEmpty()) {
            try {
                file.outputStream().buffered().use {
                    write(it, list)
                }
            } catch (e: IOException) {
                ErrorReport.printAndWriteLog(e)
            }
        } else {
            if (file.exists()) file.delete()
        }
    }

    private fun ElementWriter.write(file: File, list: List<ElementFilter>) {
        if (list.isNotEmpty()) {
            try {
                file.outputStream().buffered().use {
                    write(it, list)
                }
            } catch (e: IOException) {
                ErrorReport.printAndWriteLog(e)
            }
        } else {
            if (file.exists()) file.delete()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AbpUpdateWorkerEntryPoint {
    fun okHttpClient(): OkHttpClient
    fun abpDatabase(): AbpDatabase
}
