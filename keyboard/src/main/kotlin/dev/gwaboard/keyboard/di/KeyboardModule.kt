package dev.gwaboard.keyboard.di

import dev.gwaboard.keyboard.engine.HybridSuggestionEngine
import dev.gwaboard.keyboard.ipc.SmsContextProvider
import dev.gwaboard.keyboard.ui.SuggestionBarViewModel
import dev.gwaboard.shared.ipc.SmsProviderClient
import org.koin.dsl.module

/**
 * Koin dependency injection module for the keyboard app.
 *
 * Provides the IPC layer, suggestion engine, and UI components needed
 * for the keyboard service and suggestion bar.
 * [SmsProviderClient] is the low-level ContentProvider client from shared-ipc.
 * [SmsContextProvider] is the keyboard-side wrapper that adds caching and Flow-based API.
 * [SuggestionBarViewModel] bridges the engine layer with the Compose suggestion bar.
 */
val keyboardModule = module {

    /** Low-level ContentProvider client for querying the companion app */
    single { SmsProviderClient(context = get()) }

    /** Keyboard-side IPC wrapper with caching and reactive profile updates */
    single { SmsContextProvider(smsProviderClient = get()) }

    /** Suggestion bar ViewModel — scoped to the IME service lifecycle */
    factory { (engine: HybridSuggestionEngine) ->
        SuggestionBarViewModel(engine = engine)
    }
}
