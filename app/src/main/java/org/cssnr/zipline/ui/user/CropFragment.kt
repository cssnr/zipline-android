package org.cssnr.zipline.ui.user

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.scale
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import org.cssnr.zipline.MainActivity
import org.cssnr.zipline.R
import org.cssnr.zipline.databinding.FragmentCropBinding
import org.cssnr.zipline.ui.settings.headers.HeadersFragment
import java.io.File
import java.io.FileOutputStream

class CropFragment : Fragment() {

    private var _binding: FragmentCropBinding? = null
    private val binding get() = _binding!!

    private val bottomNav by lazy { requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav) }

    companion object {
        const val LOG_TAG = "CropFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(LOG_TAG, "onCreateView: ${savedInstanceState?.size()}")
        _binding = FragmentCropBinding.inflate(inflater, container, false)
        val root: View = binding.root
        return root
    }

    override fun onDestroyView() {
        Log.d(LOG_TAG, "onDestroyView")
        super.onDestroyView()
        _binding = null
    }

    override fun onStart() {
        super.onStart()
        Log.d(HeadersFragment.Companion.LOG_TAG, "onStart - Hide UI and Lock Drawer")
        bottomNav.visibility = View.GONE
        (activity as? MainActivity)?.setDrawerLockMode(false)
    }

    override fun onStop() {
        Log.d(HeadersFragment.Companion.LOG_TAG, "onStop - Show UI and Unlock Drawer")
        bottomNav.visibility = View.VISIBLE
        (activity as? MainActivity)?.setDrawerLockMode(true)
        super.onStop()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(LOG_TAG, "onViewCreated: ${savedInstanceState?.size()}")

        val ctx = requireContext()
        val cropImageView = binding.cropImageView

        cropImageView.setAspectRatio(1, 1)

        //val file = File(ctx.filesDir, "avatar.png")
        //if (file.exists()) {
        //    Log.d(LOG_TAG, "onViewCreated - Set UserFragment Image: $file")
        //    cropImageView.setAspectRatio(1, 1)
        //    cropImageView.setImageUriAsync(file.toUri())
        //}

        val uri = arguments?.getString("uri")?.toUri()
        Log.d(LOG_TAG, "uri: $uri")
        cropImageView.setImageUriAsync(uri)

        binding.goBack.setOnClickListener {
            Log.d(HeadersFragment.Companion.LOG_TAG, "binding.goBack: navController.navigateUp()")
            findNavController().navigateUp()
        }

        binding.cropBtn.setOnClickListener {
            try {
                var cropped = cropImageView.getCroppedImage() ?: throw Error("cropped null")
                Log.d(LOG_TAG, "cropped size: ${cropped.width}x${cropped.height}")
                if (cropped.width > 512) {
                    cropped = cropped.scale(512, 512)
                    Log.d(LOG_TAG, "cropped scale size: ${cropped.width}x${cropped.height}")
                }
                val file = File(ctx.filesDir, "cropped.png")
                FileOutputStream(file).use { out ->
                    val result = cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
                    Log.d(LOG_TAG, "result: $result - file.name: ${file.name}")
                    setFragmentResult("CropFragment", bundleOf("fileName" to file.name))
                    findNavController().navigateUp()
                }
            } catch (e: Throwable) {
                Log.e(LOG_TAG, "Exception:", e)
                Snackbar.make(view, "Error Cropping Image.", Snackbar.LENGTH_SHORT)
                    .setTextColor("#D32F2F".toColorInt()).show()
            }
        }
    }
}
