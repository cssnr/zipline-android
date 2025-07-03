package org.cssnr.zipline.ui.files

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.cssnr.zipline.api.ServerApi.FileResponse

class FilesViewModel : ViewModel() {

    val filesData = MutableLiveData<List<FileResponse>>()
    val currentPage = MutableLiveData<Int>(1)
    val atEnd = MutableLiveData<Boolean>()
    val deleteId = MutableLiveData<Int>()
    //val editRequest = MutableLiveData<FileEditRequest>()
    val updateRequest = MutableLiveData<List<Int>>()

    val meterHidden = MutableLiveData<Boolean>().apply { value = false }
    val selected = MutableLiveData<MutableSet<Int>>()

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
