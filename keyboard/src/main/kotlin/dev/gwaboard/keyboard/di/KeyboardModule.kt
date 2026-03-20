package dev.gwaboard.keyboard.di

import dev.gwaboard.keyboard.ipc.SmsContextProvider
import dev.gwaboard.shared.ipc.SmsProviderClient
import org.koin.dsl.module

/**
 * Koin dependency injection module for the keyboard app.
 *
 * Provides the IPC layer components needed for communication with the companion app.
 * [SmsProviderClient] is the low-level ContentProvider client from shared-ipc.
 * [SmsContextProvider] is the keyboard-side wrapper that adds caching and Flow-based API.
 */
val keyboardModule = module {

    /** Low-level ContentProvider client for querying the companion app */
    single { SmsProviderClient(context = get()) }

    /** Keyboard-side IPC wrapper with caching and reactive profile updates */
    single { SmsContextProvider(smsProviderClient = get()) }
}
