package com.carfbot.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

private class CaptureHolder { var cont: CancellableContinuation<Boolean>? = null }

/** Returns a function that opens the camera, waits for the photo and returns its Uri (null if cancelled). */
@Composable
fun rememberPhotoCapture(): suspend () -> Uri? {
    val ctx = LocalContext.current
    val holder = remember { CaptureHolder() }
    val file = remember { File(ctx.cacheDir, "photos/shot.jpg") }
    val uri = remember {
        file.parentFile?.mkdirs()
        FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        holder.cont?.resume(ok)
        holder.cont = null
    }
    return remember {
        val f: suspend () -> Uri? = {
            file.delete()
            val ok = suspendCancellableCoroutine<Boolean> { c ->
                holder.cont = c
                try {
                    launcher.launch(uri)
                } catch (e: Exception) {
                    holder.cont = null
                    c.resume(false)
                }
            }
            if (ok) uri else null
        }
        f
    }
}
