import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class UploadOptions(
    var folderId: String? = null,
    var password: String? = null,
    var deletesAt: String? = null,
    var maxViews: Int? = null,
) : Parcelable
