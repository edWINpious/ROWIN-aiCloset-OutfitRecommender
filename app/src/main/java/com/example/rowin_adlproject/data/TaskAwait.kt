package com.example.rowin_adlproject.data

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Suspends until a Firebase [Task] completes (a tiny stand-in for
 * kotlinx-coroutines-play-services, so no extra dependency is needed).
 *
 * Note: a Firestore write's task completes only once the server confirms
 * it, so awaiting one while offline waits until the device reconnects.
 */
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            cont.resume(task.result)
        } else {
            cont.resumeWithException(task.exception ?: IllegalStateException("Firebase task failed"))
        }
    }
}
