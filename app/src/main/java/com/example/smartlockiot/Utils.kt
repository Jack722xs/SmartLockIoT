package com.example.smartlockiot

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** Evita bloqueos indefinidos de Firestore en modo offline. */
suspend fun <T> Task<T>.awaitTimeout(ms: Long = 8000L): T = withTimeout(ms) { await() }
