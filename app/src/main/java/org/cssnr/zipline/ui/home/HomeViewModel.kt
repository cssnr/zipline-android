package org.cssnr.zipline.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HomeViewModel : ViewModel() {
    private val _urlToLoad = MutableLiveData<Event<String>>()
    val urlToLoad: LiveData<Event<String>> = _urlToLoad
    val webViewUrl = MutableLiveData<String>()

    fun navigateTo(url: String) {
        _urlToLoad.value = Event(url)
    }

    class Event<out T>(private val content: T) {
        private var hasBeenHandled = false

        fun getContentIfNotHandled(): T? {
            return if (hasBeenHandled) null else {
                hasBeenHandled = true
                content
            }
        }
    }
}
