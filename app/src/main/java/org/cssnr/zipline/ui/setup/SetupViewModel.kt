package org.cssnr.zipline.ui.setup

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SetupViewModel : ViewModel() {
    //val hostname = MutableLiveData<String>()
    //val workInterval = MutableLiveData<String>("0")
    val confettiShown = MutableLiveData<Boolean>(false)

    val totp = MutableLiveData<Boolean>(false)
}
