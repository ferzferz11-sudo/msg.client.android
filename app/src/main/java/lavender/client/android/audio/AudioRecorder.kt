package lavender.client.android.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class AudioRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var outputFile: File? = null
    private var startTime: Long = 0
    
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    
    fun startRecording(): File? {
        if (isRecording) {
            return null
        }
        
        // Create output file
        val fileName = "voice_${dateFormat.format(Date())}.m4a"
        outputFile = File(context.cacheDir, fileName)
        
        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile?.absolutePath)
                
                prepare()
                start()
            }
            
            isRecording = true
            startTime = System.currentTimeMillis()
            
            return outputFile
            
        } catch (e: IOException) {
            e.printStackTrace()
            cleanup()
            return null
        }
    }
    
    fun stopRecording(): Pair<File?, Int>? {
        if (!isRecording || mediaRecorder == null) {
            return null
        }
        
        return try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            
            val duration = ((System.currentTimeMillis() - startTime) / 1000).toInt()
            isRecording = false
            mediaRecorder = null
            
            Pair(outputFile, duration)
            
        } catch (e: Exception) {
            e.printStackTrace()
            cleanup()
            null
        }
    }
    
    fun cancelRecording() {
        if (isRecording) {
            try {
                mediaRecorder?.stop()
            } catch (e: Exception) {
                // Ignore stop exceptions during cancellation
            }
        }
        cleanup()
    }
    
    private fun cleanup() {
        mediaRecorder?.release()
        mediaRecorder = null
        isRecording = false
        
        // Delete output file if it exists
        outputFile?.let { file ->
            if (file.exists()) {
                file.delete()
            }
        }
        outputFile = null
    }
    
    fun isCurrentlyRecording(): Boolean = isRecording
    
    fun getMaxAmplitude(): Int {
        return if (isRecording) {
            mediaRecorder?.maxAmplitude ?: 0
        } else {
            0
        }
    }
    
    fun getRecordingDuration(): Int {
        return if (isRecording) {
            ((System.currentTimeMillis() - startTime) / 1000).toInt()
        } else {
            0
        }
    }
}
