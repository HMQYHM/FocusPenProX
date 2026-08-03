package io.github.hmqyhm.focuspenpro.hook

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import io.github.hmqyhm.focuspenpro.config.ConfigContract
import io.github.hmqyhm.focuspenpro.config.ModuleConfig
import java.util.concurrent.atomic.AtomicReference

internal class SystemConfigClient(
    private val resolver: ContentResolver,
    private val handler: Handler,
    private val onChanged: () -> Unit,
) {
    val snapshot = AtomicReference(ModuleConfig())

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            refresh()
        }
    }

    fun start() {
        resolver.registerContentObserver(ConfigContract.URI, true, observer)
        refresh()
    }

    fun refresh() {
        handler.post {
            val generation = ++refreshGeneration
            refreshAttempt(generation, 0)
        }
    }

    private fun refreshAttempt(generation: Int, attempt: Int) {
        if (generation != refreshGeneration) return
        val next = runCatching {
            resolver.call(
                ConfigContract.URI,
                ConfigContract.METHOD_GET_SNAPSHOT,
                null,
                null,
            )?.let(ModuleConfig::fromBundle)
        }.getOrNull()
        if (next != null) {
            val previous = snapshot.getAndSet(next)
            if (previous != next) onChanged()
            return
        }

        // During early system_server boot the package/provider may not be ready. Keep
        // retrying at a low fixed rate after the initial backoff so a late provider never
        // requires the user to toggle a whitelist item to wake configuration loading.
        val delay = RETRY_DELAYS_MS.getOrElse(attempt) { STEADY_RETRY_MS }
        handler.postDelayed(
            { refreshAttempt(generation, (attempt + 1).coerceAtMost(RETRY_DELAYS_MS.size)) },
            delay,
        )
    }

    private var refreshGeneration = 0

    private companion object {
        val RETRY_DELAYS_MS = longArrayOf(
            250L,
            500L,
            1_000L,
            2_000L,
            4_000L,
            8_000L,
            15_000L,
        )
        const val STEADY_RETRY_MS = 15_000L
    }
}
