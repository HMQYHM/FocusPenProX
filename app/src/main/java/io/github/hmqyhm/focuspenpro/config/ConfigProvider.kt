package io.github.hmqyhm.focuspenpro.config

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import java.util.ArrayDeque

class ConfigProvider : ContentProvider() {
    private lateinit var store: ConfigStore
    private val runtimeLock = Any()
    private var runtime = Bundle()
    private val events = ArrayDeque<String>()

    override fun onCreate(): Boolean {
        store = ConfigStore(requireNotNull(context))
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        return when (method) {
            ConfigContract.METHOD_GET_SNAPSHOT -> {
                enforceReader()
                store.read().toBundle()
            }
            ConfigContract.METHOD_REPORT_RUNTIME -> {
                enforceSystem()
                synchronized(runtimeLock) {
                    runtime = Bundle(extras ?: Bundle())
                }
                Bundle.EMPTY
            }
            ConfigContract.METHOD_APPEND_EVENT -> {
                enforceSystem()
                extras?.getString("event")?.take(300)?.let { event ->
                    synchronized(runtimeLock) {
                        while (events.size >= 80) events.removeFirst()
                        events.addLast(event)
                    }
                }
                Bundle.EMPTY
            }
            ConfigContract.METHOD_GET_RUNTIME -> {
                enforceReader()
                synchronized(runtimeLock) {
                    Bundle(runtime).apply {
                        putStringArrayList(
                            ConfigContract.KEY_RUNTIME_EVENTS,
                            ArrayList(events),
                        )
                    }
                }
            }
            else -> super.call(method, arg, extras) ?: Bundle.EMPTY
        }
    }

    private fun enforceReader() {
        val uid = Binder.getCallingUid()
        if (uid != Process.SYSTEM_UID && uid != Process.myUid()) {
            throw SecurityException("Configuration is only available to system_server and this app")
        }
    }

    private fun enforceSystem() {
        if (Binder.getCallingUid() != Process.SYSTEM_UID) {
            throw SecurityException("Runtime reports are only accepted from system_server")
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
