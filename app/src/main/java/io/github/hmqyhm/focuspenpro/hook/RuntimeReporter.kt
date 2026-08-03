package io.github.hmqyhm.focuspenpro.hook

import android.content.ContentResolver
import android.os.Bundle
import android.os.Handler
import io.github.hmqyhm.focuspenpro.config.ConfigContract
import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal class RuntimeReporter(
    private val resolver: ContentResolver,
    private val handler: Handler,
) {
    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    fun status(
        active: Boolean,
        compatible: Boolean,
        message: String,
        laser: Boolean,
        foreground: String?,
    ) {
        val extras = Bundle().apply {
            putBoolean(ConfigContract.KEY_RUNTIME_ACTIVE, active)
            putBoolean(ConfigContract.KEY_RUNTIME_COMPATIBLE, compatible)
            putString(ConfigContract.KEY_RUNTIME_MESSAGE, message.take(300))
            putBoolean(ConfigContract.KEY_RUNTIME_LASER, laser)
            putString(ConfigContract.KEY_RUNTIME_FOREGROUND, foreground.orEmpty())
        }
        handler.post {
            runCatching {
                resolver.call(
                    ConfigContract.URI,
                    ConfigContract.METHOD_REPORT_RUNTIME,
                    null,
                    extras,
                )
            }
        }
    }

    fun event(message: String) {
        val text = "${LocalTime.now().format(formatter)}  ${message.take(240)}"
        handler.post {
            runCatching {
                resolver.call(
                    ConfigContract.URI,
                    ConfigContract.METHOD_APPEND_EVENT,
                    null,
                    Bundle().apply { putString("event", text) },
                )
            }
        }
    }
}
