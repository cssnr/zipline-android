package org.cssnr.zipline.ui.user

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.cssnr.zipline.db.UserEntity

class UserViewModel : ViewModel() {
    val user = MutableLiveData<UserEntity>()
}
