package com.example.vehicare.data.di

import javax.inject.Qualifier

/**
 * Marks the coroutine dispatcher used for all database I/O. Injected rather than referenced
 * directly, so tests can substitute a deterministic dispatcher for `Dispatchers.IO`.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
