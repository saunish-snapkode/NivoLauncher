package com.snapkode.nivolauncher.pagelet

import kotlinx.coroutines.flow.Flow

/**
 * What a Pagelet is showing. Every Pagelet always renders something: its data, the last cached data, an empty state, or a
 * permission prompt. It is never a blank page.
 */
sealed interface PageletUiState<out T> {
    data object Loading : PageletUiState<Nothing>

    /** A permission is missing. [rationale] is shown to the user in plain language before they are sent to Settings. */
    data class NeedsPermission(val permission: String, val rationale: String) : PageletUiState<Nothing>

    data object Empty : PageletUiState<Nothing>

    data class Ready<T>(val data: T, val updatedAt: Long) : PageletUiState<T>

    /** Something failed; [cached] is the last good value if there is one, and is still what the page shows. */
    data class Error<T>(val message: String, val cached: T? = null) : PageletUiState<T>
}

/** The value to draw from a state, or null when the state carries none (loading, empty, permission). */
fun <T> PageletUiState<T>.dataOrNull(): T? = when (this) {
    is PageletUiState.Ready -> data
    is PageletUiState.Error -> cached
    else -> null
}

/** Source of a Pagelet's data. Collection only happens while the Pagelet is visible (see [PageletVisibility]). */
interface PageletDataProvider<T> {
    val state: Flow<PageletUiState<T>>

    /** Ask for fresh data now (pull to refresh, page became visible with stale data). */
    suspend fun refresh()
}
