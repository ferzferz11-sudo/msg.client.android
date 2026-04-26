package lavender.client.android.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class AudioUploadResult(
    val success: Boolean,
    val url: String = "",
    val duration: Int = 0,
    val error: String = ""
)

class AudioUploader(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private var serverAddress: String = "159.195.38.145"
    private var serverPort: String = "8082"

    fun setServerAddress(address: String, port: String = "8082") {
        this.serverAddress = address
        this.serverPort = port
    }

    suspend fun uploadAudio(audioFile: File, duration: Int): AudioUploadResult {
        return withContext(Dispatchers.IO) {
            try {
                val uploadUrl = "http://$serverAddress:$serverPort/upload-audio"
                
                // Create multipart request
                val audioRequestBody = audioFile.readBytes()
                    .toRequestBody("audio/m4a".toMediaType())
                
                val audioPart = MultipartBody.Part.createFormData(
                    "audio",
                    audioFile.name,
                    audioRequestBody
                )
                
                val durationPart = MultipartBody.Part.createFormData(
                    "duration",
                    duration.toString()
                )
                
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addPart(audioPart)
                    .addPart(durationPart)
                    .build()
                
                val request = Request.Builder()
                    .url(uploadUrl)
                    .post(requestBody)
                    .build()
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                
                if (response.isSuccessful && responseBody.isNotEmpty()) {
                    try {
                        val json = JSONObject(responseBody)
                        val url = json.getString("url")
                        val returnedDuration = json.optInt("duration", duration)
                        
                        AudioUploadResult(
                            success = true,
                            url = url,
                            duration = returnedDuration
                        )
                    } catch (e: Exception) {
                        AudioUploadResult(
                            success = false,
                            error = "Failed to parse response: ${e.message}"
                        )
                    }
                } else {
                    AudioUploadResult(
                        success = false,
                        error = "Upload failed: ${response.code} $responseBody"
                    )
                }
                
            } catch (e: Exception) {
                AudioUploadResult(
                    success = false,
                    error = "Upload error: ${e.message}"
                )
            }
        }
    }
    
    suspend fun deleteAudio(audioUrl: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Note: Server doesn't have a delete endpoint for audio yet
                // This would need to be implemented on the server side
                // For now, we'll just return true
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}
