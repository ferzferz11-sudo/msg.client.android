package lavender.client.android.ui.conference

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lavender.client.android.data.calls.CallManager
import lavender.client.android.data.proto.CallMessageProto
import lavender.client.android.data.grpc.GrpcClient
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ConferenceLobbyUiState(
    val isLoading: Boolean = false,
    val roomId: String = "",
    val topic: String = "",
    val startTime: Long = System.currentTimeMillis() + 5 * 60 * 1000,
    val isCreator: Boolean = false,
    val conferenceCreatorId: String = "",
    val isTopicManual: Boolean = false,
    val participants: List<String> = emptyList(),
    val invited: List<String> = emptyList(),
    val participantCount: Int = 0,
    val avatarCache: Map<String, String> = emptyMap(),
    val isMicEnabled: Boolean = true,
    val isCameraEnabled: Boolean = true,
    val error: String? = null,
    val successMessage: String? = null
)

class ConferenceLobbyViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ConferenceLobbyUiState())
    val uiState: StateFlow<ConferenceLobbyUiState> = _uiState.asStateFlow()

    companion object {
        // Guard against duplicate INITIATE_CONFERENCE signals per room
        private val initiatedRooms = mutableSetOf<String>()
    }

    private val sdf = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())

    fun init(roomId: String) {
        _uiState.value = _uiState.value.copy(roomId = roomId)
        updateDefaultTopic()
        observeConferenceStatus()
        observeAvatarCache()
        // Only send INITIATE_CONFERENCE once per room per app session
        if (initiatedRooms.add(roomId)) {
            CallManager.initiateConference(roomId)
        }
    }

    private fun updateDefaultTopic() {
        val topic = getApplication<Application>().getString(
            lavender.client.android.R.string.new_conference_format,
            sdf.format(Date(_uiState.value.startTime))
        )
        _uiState.value = _uiState.value.copy(topic = topic)
    }

    fun updateTopic(newTopic: String) {
        if (newTopic.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(topic = newTopic, isTopicManual = true)
            updateMetadataOnServer()
        }
    }

    fun addFiveMinutes() {
        val newTime = _uiState.value.startTime + 5 * 60 * 1000
        _uiState.value = _uiState.value.copy(startTime = newTime)
        if (!_uiState.value.isTopicManual) updateDefaultTopic()
        updateMetadataOnServer()
    }

    fun setTime(timeInMillis: Long) {
        _uiState.value = _uiState.value.copy(startTime = timeInMillis)
        if (!_uiState.value.isTopicManual) updateDefaultTopic()
        updateMetadataOnServer()
    }

    fun getTimeFormatted(): String {
        return getApplication<Application>().getString(
            lavender.client.android.R.string.starts_at,
            sdf.format(Date(_uiState.value.startTime))
        )
    }

    private fun updateMetadataOnServer() {
        if (!_uiState.value.isCreator) return
        CallManager.updateConferenceMetadata(
            _uiState.value.roomId,
            _uiState.value.topic,
            _uiState.value.startTime
        )
    }

    fun sendNotification() {
        if (!_uiState.value.isCreator) return
        val payload = JSONObject().apply {
            put("topic", _uiState.value.topic)
            put("start_time", _uiState.value.startTime)
            put("trigger_notify", true)
        }.toString()
        CallManager.sendWebRtcSignal("", CallMessageProto.Type.UPDATE_CONFERENCE, payload)
    }

    fun toggleMic() {
        _uiState.value = _uiState.value.copy(isMicEnabled = !_uiState.value.isMicEnabled)
    }

    fun toggleCamera() {
        _uiState.value = _uiState.value.copy(isCameraEnabled = !_uiState.value.isCameraEnabled)
    }

    fun joinConference() {
        updateMetadataOnServer()
    }

    fun deleteConference() {
        CallManager.endConference(_uiState.value.roomId)
    }

    fun leaveConference() {
        CallManager.leaveConference(_uiState.value.roomId)
        _uiState.value = _uiState.value.copy(successMessage = "Left conference")
    }

    fun inviteToConference(userId: String) {
        CallManager.inviteToConference(_uiState.value.roomId, userId, userId)
    }

    fun removeFromConference(userId: String) {
        if (_uiState.value.isCreator) {
            CallManager.removeFromConference(_uiState.value.roomId, userId)
        }
    }

    private fun observeConferenceStatus() {
        viewModelScope.launch {
            CallManager.incomingSignals.collect { signal ->
                if (signal.roomId == _uiState.value.roomId) {
                    when (signal.type) {
                        CallMessageProto.Type.JOIN_CONFERENCE -> handlePresence(signal)
                        CallMessageProto.Type.END_CONFERENCE -> {
                            _uiState.value = _uiState.value.copy(successMessage = "Conference ended")
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    private fun handlePresence(signal: CallMessageProto) {
        try {
            val response = JSONObject(signal.payload)

            val isEnded = response.optBoolean("ended", false) || response.optBoolean("is_deleted", false)
            if (isEnded) {
                _uiState.value = _uiState.value.copy(successMessage = "Conference ended")
                return
            }

            val participants = response.optJSONObject("participants") ?: JSONObject()
            val invited = response.optJSONObject("invited") ?: JSONObject()
            val creatorId = response.optString("creator_id", "")
            val topic = response.optString("topic", "")
            val sTime = response.optLong("start_time", 0)

            val myId = GrpcClient.getUserId() ?: GrpcClient.getCurrentUsername()
            val isCreator = myId == creatorId

            val invitedList = mutableListOf<String>()
            val iKeys = invited.keys()
            while (iKeys.hasNext()) invitedList.add(invited.getString(iKeys.next()))

            val participantCount = participants.length()

            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    conferenceCreatorId = creatorId,
                    isCreator = isCreator,
                    topic = if (topic.isNotEmpty()) topic else _uiState.value.topic,
                    startTime = if (sTime > 0) sTime else _uiState.value.startTime,
                    invited = invitedList,
                    participantCount = participantCount
                )
            }
        } catch (e: Exception) {
            Log.e("ConferenceLobby", "Failed to parse participants", e)
        }
    }

    private fun observeAvatarCache() {
        viewModelScope.launch {
            GrpcClient.avatarCacheFlow.collect { cache ->
                _uiState.value = _uiState.value.copy(avatarCache = cache)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSuccess() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
}
