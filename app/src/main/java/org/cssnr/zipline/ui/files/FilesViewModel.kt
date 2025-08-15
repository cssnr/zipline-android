package org.cssnr.zipline.ui.files

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.cssnr.zipline.api.ServerApi.FileEditRequest
import org.cssnr.zipline.api.ServerApi.FileResponse

class FilesViewModel : ViewModel() {

    var savedUrl: String? = null
        private set

    fun setUrl(newUrl: String) {
        Log.d("setUrl", "newUrl: $newUrl")
        if (savedUrl == null) {
            savedUrl = newUrl
        }
    }

    private val _snackbarMessage = MutableLiveData<String?>()
    val snackbarMessage: LiveData<String?> = _snackbarMessage

    fun showSnackbar(message: String) {
        _snackbarMessage.value = message
    }

    fun snackbarShown() {
        _snackbarMessage.value = null
    }


    val filesData = MutableLiveData<List<FileResponse>>()
    val activeFile = MutableLiveData<FileResponse>()
    val currentPage = MutableLiveData(1)
    val atEnd = MutableLiveData<Boolean>()
    val deleteId = MutableLiveData<String>()

    val editRequest = MutableLiveData<FileEditRequest>()
    //val updateRequest = MutableLiveData<List<Int>>()

    val meterHidden = MutableLiveData<Boolean>().apply { value = false }
    val selected = MutableLiveData<MutableSet<Int>>()
    //val savedUrl = MutableLiveData<MutableSet<String>>()

    fun getRawUrl(file: FileResponse): String {
        return "$savedUrl/raw/${file.name}"
    }

    fun getViewUrl(file: FileResponse): String {
        return "$savedUrl${file.url}"
    }

    fun getThumbUrl(file: FileResponse): String? {
        return if (file.thumbnail != null) "${savedUrl}/raw/${file.thumbnail.path}" else null
    }

    //// Note: this will not work without a filesData observer to update data on changes
    //fun deleteById(fileId: Int) {
    //    Log.d("deleteById", "fileId: $fileId")
    //    val currentList = filesData.value.orEmpty()
    //    Log.d("deleteById", "currentList: ${currentList.size}")
    //    val updatedList = currentList.filter { it.id != fileId }
    //    Log.d("deleteById", "updatedList: ${updatedList.size}")
    //    filesData.value = updatedList
    //}
}
