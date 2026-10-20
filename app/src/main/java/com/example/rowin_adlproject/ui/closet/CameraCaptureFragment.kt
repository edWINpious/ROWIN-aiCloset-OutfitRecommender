package com.example.rowin_adlproject.ui.closet

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.TorchState
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.rowin_adlproject.R
import com.example.rowin_adlproject.databinding.ScreenCameraCaptureBinding
import com.example.rowin_adlproject.ui.view.HostedFragment
import com.example.rowin_adlproject.ui.view.Views
import java.io.File

/**
 * Feature #20 - REAL camera capture (practical #7 multimedia): a full-screen
 * CameraX viewfinder with tap-to-shoot, front/back flip and flash toggle.
 * The captured JPEG is returned to the caller through the fragment-result
 * API and lands back on the Add Clothing screen.
 */
class CameraCaptureFragment : HostedFragment(R.layout.screen_camera_capture) {

    private var _b: ScreenCameraCaptureBinding? = null
    private val b get() = _b!!

    private var controller: LifecycleCameraController? = null
    private var hasPermission = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        renderState()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _b = ScreenCameraCaptureBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewReady(view: View) {
        hasPermission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

        b.cameraClose.setOnClickListener { host.back() }
        b.cameraGrant.setOnClickListener { permissionLauncher.launch(Manifest.permission.CAMERA) }
        b.cameraFlash.setOnClickListener { toggleTorch() }
        b.cameraShutter.setOnClickListener { capture() }
        b.cameraFlip.setOnClickListener { flipCamera() }

        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
        renderState()
    }

    private fun renderState() {
        if (hasPermission) {
            Views.hide(b.cameraPermission)
            bindCamera()
        } else {
            Views.show(b.cameraPermission)
        }
    }

    private fun bindCamera() {
        if (controller != null) return
        val camController = LifecycleCameraController(requireContext()).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
        camController.bindToLifecycle(viewLifecycleOwner)
        controller = camController
        b.cameraPreview.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        b.cameraPreview.scaleType = PreviewView.ScaleType.FILL_CENTER
        b.cameraPreview.controller = camController
    }

    private fun toggleTorch() {
        val cam = controller ?: return
        val torchOn = cam.cameraInfo?.torchState?.value == TorchState.ON
        cam.cameraControl?.enableTorch(!torchOn)
    }

    private fun flipCamera() {
        val cam = controller ?: return
        cam.cameraSelector =
            if (cam.cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }
    }

    private fun capture() {
        val cam = controller ?: return
        val dir = File(requireContext().cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "rowin_shot_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        cam.takePicture(
            options,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    if (!isAdded) return
                    // Hand the capture back to Add Clothing (fragment-result API).
                    parentFragmentManager.setFragmentResult(
                        CameraResult.REQUEST_KEY,
                        Bundle().apply { putString(CameraResult.EXTRA_PATH, file.absolutePath) },
                    )
                    host.back()
                }

                override fun onError(exception: ImageCaptureException) {
                    if (!isAdded) return
                    b.cameraError.text = exception.message ?: getString(R.string.capture_failed)
                    Views.show(b.cameraError)
                }
            },
        )
    }

    override fun onDestroyView() {
        controller?.unbind()
        controller = null
        super.onDestroyView()
        _b = null
    }
}
