package io.agents.pokeclaw.agent.computeruse

import android.graphics.Bitmap
import android.util.Base64
import io.agents.pokeclaw.agent.interaction.EnvironmentSnapshot
import io.agents.pokeclaw.service.ClawAccessibilityService
import io.agents.pokeclaw.utils.XLog
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface EnvironmentSnapshotProvider {
    suspend fun captureSnapshot(): EnvironmentSnapshot?
}

class DefaultEnvironmentSnapshotProvider : EnvironmentSnapshotProvider {

    companion object {
        private const val TAG = "DefaultEnvironmentSnapshotProvider"
    }

    override suspend fun captureSnapshot(): EnvironmentSnapshot? = withContext(Dispatchers.IO) {
        val service = ClawAccessibilityService.getInstance()
        if (service == null) {
            XLog.e(TAG, "ClawAccessibilityService is not running")
            return@withContext null
        }

        var base64: String? = null
        try {
            val bitmap = service.takeScreenshot(3000L) // Wait up to 3s
            if (bitmap != null) {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream) // JPEG is faster and smaller for LLM
                base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            } else {
                XLog.w(TAG, "Failed to capture screenshot")
            }
        } catch (e: Exception) {
            XLog.e(TAG, "Error capturing screenshot", e)
        }

        // For viewTreeJson, we'd ideally dump the tree from ClawAccessibilityService
        // Assuming there is a method for it, or we leave it empty for now
        var viewTreeJson: String? = null
        try {
            // Attempt to get node info if service supports it
            // This is a placeholder as the exact method depends on ClawAccessibilityService API
            val rootNode = service.rootInActiveWindow
            if (rootNode != null) {
                 // In a full implementation, we would recursively serialize rootNode to JSON
                 viewTreeJson = "{\"status\": \"tree_dump_not_implemented_fully\"}"
            }
        } catch(e: Exception) {
            XLog.e(TAG, "Error capturing view tree", e)
        }

        EnvironmentSnapshot(screenshotBase64 = base64, viewTreeJson = viewTreeJson)
    }
}
