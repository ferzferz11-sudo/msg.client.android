package lavender.client.android.ui.calls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CallViewModel(
    scope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = scope ?: viewModelScope

    private val _timerText = MutableStateFlow("00:00")
    val timerText: StateFlow<String> = _timerText.asStateFlow()

    private var elapsedSeconds = 0
    private var timerJob: Job? = null

    fun startTimer() {
        if (timerJob != null) return
        timerJob = this.scope.launch {
            while (true) {
                updateTimerText()
                delay(1000)
                elapsedSeconds++
            }
        }
    }

    private fun updateTimerText() {
        val hours = elapsedSeconds / 3600
        val minutes = (elapsedSeconds % 3600) / 60
        val seconds = elapsedSeconds % 60
        _timerText.value = if (hours > 0) {
            String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        elapsedSeconds = 0
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
    }
}
