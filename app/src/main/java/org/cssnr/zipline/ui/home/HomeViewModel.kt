package org.cssnr.zipline.ui.home

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HomeViewModel : ViewModel() {
    val tapTargetActive = MutableLiveData<Boolean>(false)
}
