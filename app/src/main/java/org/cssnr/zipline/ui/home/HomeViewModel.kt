package org.cssnr.zipline.ui.home

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HomeViewModel : ViewModel() {
    val tapTargetStep = MutableLiveData<Int>(0)
}
