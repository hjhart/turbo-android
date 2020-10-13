package com.basecamp.turbolinks.session

/**
 * Used as a wrapper for data that is exposed via a LiveData that represents an event.
 *
 * @param T
 * @property content Content of the event.
 * @constructor Create empty Turbolinks session event
 */
open class TurbolinksSessionEvent<out T>(private val content: T) {
    var hasBeenHandled = false
        private set // Allow external read but not write

    /**
     * Returns the content and prevents its use again.
     *
     * @return
     */
    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }


    /**
     * Returns the content, even if it's already been handled.
     *
     * @return
     */
    fun peekContent(): T = content
}
