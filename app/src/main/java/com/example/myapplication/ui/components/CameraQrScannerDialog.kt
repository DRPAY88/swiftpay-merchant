package com.example.myapplication.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
@androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
fun CameraQrScannerDialog(
    onQrDetected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        cameraError = if (granted) null else "Camera permission is required to scan a QR code."
    }
    val uploadQrLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                scanner.process(InputImage.fromFilePath(context, uri))
                    .addOnSuccessListener { barcodes ->
                        val value = barcodes.firstNotNullOfOrNull { it.rawValue }
                        if (value == null) {
                            cameraError = "No QR code was found in that image. Choose another image or scan with your camera."
                        } else {
                            onQrDetected(value)
                        }
                    }
                    .addOnFailureListener {
                        cameraError = "Could not read the QR image. Choose another image and try again."
                    }
            } catch (_: Exception) {
                cameraError = "Could not open that image. Choose a different QR image and try again."
            }
        }
    }
    DisposableEffect(scanner) {
        onDispose { scanner.close() }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(lifecycleOwner, previewView, hasCameraPermission) {
        val view = previewView
        if (!hasCameraPermission || view == null) {
            onDispose { }
        } else {
            val analysisExecutor = Executors.newSingleThreadExecutor()
            val completed = AtomicBoolean(false)
            val disposed = AtomicBoolean(false)
            var cameraProvider: ProcessCameraProvider? = null
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                try {
                    if (disposed.get()) return@addListener
                    val provider = providerFuture.get()
                    cameraProvider = provider
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = view.surfaceProvider
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage == null || completed.get()) {
                            imageProxy.close()
                        } else {
                            val image = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )
                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    val value = barcodes.firstNotNullOfOrNull { it.rawValue }
                                    if (value != null && completed.compareAndSet(false, true)) {
                                        onQrDetected(value)
                                    }
                                }
                                .addOnFailureListener {
                                    cameraError = "Could not read this QR code. Try again."
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        }
                    }
                    provider.unbindAll()
                    cameraControl = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    ).cameraControl
                } catch (_: Exception) {
                    cameraError = "Unable to start the camera. Check camera availability and try again."
                }
            }, ContextCompat.getMainExecutor(context))

            onDispose {
                disposed.set(true)
                completed.set(true)
                cameraProvider?.unbindAll()
                analysisExecutor.shutdown()
                cameraControl = null
                torchEnabled = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF071429))
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { viewContext ->
                        PreviewView(viewContext).also { previewView = it }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        cameraError ?: "Allow camera access to scan a QR code.",
                        color = Color.White
                    )
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Allow camera")
                    }
                }
            }

            if (hasCameraPermission) {
                Surface(
                    modifier = Modifier.align(Alignment.Center).size(248.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = Color.Transparent,
                    border = BorderStroke(2.dp, Color.White.copy(alpha = 0.88f))
                ) {}
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(
                        start = 12.dp,
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                        end = 12.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Close QR scanner",
                        tint = Color.White
                    )
                }
                Text(
                    "DRPAY",
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge
                )
                if (hasCameraPermission) {
                    IconButton(onClick = {
                        val enableTorch = !torchEnabled
                        val operation = cameraControl?.enableTorch(enableTorch)
                        if (operation == null) {
                            cameraError = "Flashlight is unavailable on this camera."
                        } else {
                            operation.addListener({
                                try {
                                    operation.get()
                                    torchEnabled = enableTorch
                                } catch (_: Exception) {
                                    cameraError = "Flashlight is unavailable on this camera."
                                }
                            }, ContextCompat.getMainExecutor(context))
                        }
                    }) {
                        Icon(
                            imageVector = if (torchEnabled) Icons.Rounded.FlashOff else Icons.Rounded.FlashOn,
                            contentDescription = if (torchEnabled) "Turn flashlight off" else "Turn flashlight on",
                            tint = Color.White
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = Color.White
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCode2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "QR PH  ·  INSTAPAY",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text("Scan QR", style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(
                        onClick = {
                            cameraError = null
                            uploadQrLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Upload, contentDescription = null)
                        Text("Upload QR", modifier = Modifier.padding(start = 8.dp))
                    }
                    cameraError?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.semantics {
                                liveRegion = LiveRegionMode.Polite
                            }
                        )
                    }
                }
            }
        }
    }
}
