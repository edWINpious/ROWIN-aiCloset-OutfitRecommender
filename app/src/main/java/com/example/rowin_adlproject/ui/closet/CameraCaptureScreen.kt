package com.example.rowin_adlproject.ui.closet

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.rowin_adlproject.ui.components.RowinIcons
import java.io.File

/**
 * Feature #20 - REAL camera capture (practical #7 multimedia): a full-screen
 * CameraX viewfinder with tap-to-shoot, front/back flip and flash toggle.
 * The captured JPEG is returned to the caller as a File.
 */
@Composable
fun CameraCaptureScreen(
    onCaptured: (File) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var controller by remember { mutableStateOf<LifecycleCameraController?>(null) }
    val captureFile = remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (!hasPermission) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "ROWIN needs camera access to photograph your wardrobe items.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(16.dp))
                Surface(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text(
                        text = "Grant camera access",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
        } else {
            // Full-bleed live viewfinder
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        val camController = LifecycleCameraController(ctx)
                        camController.bindToLifecycle(lifecycleOwner)
                        controller = camController
                        this.controller = camController
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Top bar: close
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = RowinIcons.Close,
                    contentDescription = "Close camera",
                    tint = Color.White,
                )
            }
        }

        // Bottom controls: shutter + flip
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Flash toggle
            Surface(
                onClick = {
                    controller?.let {
                        val torchOn =
                            it.cameraInfo?.torchState?.value == androidx.camera.core.TorchState.ON
                        it.cameraControl?.enableTorch(!torchOn)
                    }
                },
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.15f),
            ) {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = RowinIcons.AutoAwesome,
                        contentDescription = "Flash",
                        tint = Color.White,
                    )
                }
            }

            // Shutter
            Surface(
                onClick = {
                    val cam = controller ?: return@Surface
                    val file = captureFile.value ?: File(context.cacheDir, "camera/rowin_shot.jpg")
                    file.parentFile?.mkdirs()
                    captureFile.value = file
                    val options = ImageCapture.OutputFileOptions.Builder(file).build()
                    cam.takePicture(
                        options,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                onCaptured(file)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                error = exception.message ?: "Capture failed"
                            }
                        },
                    )
                },
                shape = CircleShape,
                color = Color.White,
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                CircleShape,
                            ),
                    )
                }
            }

            // Flip camera
            Surface(
                onClick = {
                    controller?.cameraSelector =
                        if (controller?.cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        } else {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        }
                },
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.15f),
            ) {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = RowinIcons.Sync,
                        contentDescription = "Flip camera",
                        tint = Color.White,
                    )
                }
            }
        }

        error?.let {
            Text(
                text = it,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
            )
        }
    }
}
