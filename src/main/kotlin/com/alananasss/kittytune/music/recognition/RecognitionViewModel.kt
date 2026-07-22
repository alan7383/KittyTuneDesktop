package com.alananasss.kittytune.music.recognition

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.data.RecognitionHistoryRepository
import com.alananasss.kittytune.domain.Track
import com.metrolist.shazamkit.Shazam
import com.metrolist.shazamkit.models.RecognitionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import com.alananasss.kittytune.core.str

sealed class RecognitionState {
    object Idle : RecognitionState()
    object Recording : RecognitionState()
    object Processing : RecognitionState()
    data class Success(val result: RecognitionResult, val soundcloudTrack: Track?) : RecognitionState()
    data class Error(val message: String) : RecognitionState()
}

class RecognitionViewModel : ViewModel() {
    private val _state = MutableStateFlow<RecognitionState>(RecognitionState.Idle)
    val state: StateFlow<RecognitionState> = _state.asStateFlow()

    private val _availableDevices = MutableStateFlow<List<AudioInputDevice>>(emptyList())
    val availableDevices: StateFlow<List<AudioInputDevice>> = _availableDevices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<AudioInputDevice?>(null)
    val selectedDevice: StateFlow<AudioInputDevice?> = _selectedDevice.asStateFlow()

    private val audioRecorder = AudioRecorder()
    private val api by lazy { RetrofitClient.create() }

    init {
        refreshDevices()
    }

    fun refreshDevices() {
        val devices = AudioDeviceManager.getAvailableInputDevices()
        _availableDevices.value = devices
        if (_selectedDevice.value == null || devices.none { it.id == _selectedDevice.value?.id }) {
            _selectedDevice.value = devices.firstOrNull()
        }
    }

    fun selectDevice(device: AudioInputDevice) {
        _selectedDevice.value = device
    }

    private suspend fun checkRecognition(pcmData: ByteArray, durationMs: Long): RecognitionState.Success? {
        if (pcmData.size < 1000) return null
        val signature = ShazamSignatureGenerator.fromI16(pcmData)
        val recognitionResult = Shazam.recognize(signature, durationMs)
        var successResult: RecognitionState.Success? = null
        recognitionResult.onSuccess { shazamResult ->
            val searchQuery = "${shazamResult.title} ${shazamResult.artist}"
            val track = try {
                val searchResponse = api.searchTracks(query = searchQuery, limit = 5)
                searchResponse.collection.firstOrNull()
            } catch (e: Exception) {
                println("ERROR: Failed to search track on SoundCloud: ${e.message}")
                null
            }
            successResult = RecognitionState.Success(shazamResult, track)
        }.onFailure { error ->
            println("DEBUG: " + "Step check failed: ${error.message}")
        }
        return successResult
    }

    private fun updateToSuccess(successState: RecognitionState.Success) {
        if (_state.value is RecognitionState.Success) return
        _state.value = successState

        val shazamResult = successState.result
        val soundcloudTrack = successState.soundcloudTrack
        val imageUrl = soundcloudTrack?.fullResArtwork ?: shazamResult.coverArtHqUrl ?: shazamResult.coverArtUrl
        val title = soundcloudTrack?.title ?: shazamResult.title
        val artist = soundcloudTrack?.user?.username ?: shazamResult.artist

        RecognitionHistoryRepository.addToHistory(
            trackId = soundcloudTrack?.id,
            title = title,
            artist = artist,
            artworkUrl = imageUrl
        )
    }

    fun startRecognition() {
        if (_state.value is RecognitionState.Recording || _state.value is RecognitionState.Processing) {
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                _state.value = RecognitionState.Recording
                
                val control = RecordControl()
                var finalSuccess: RecognitionState.Success? = null
                
                val totalDurationMs = 9000L
                val pcmData = audioRecorder.recordAudio(
                    durationMs = totalDurationMs,
                    control = control,
                    selectedDeviceName = _selectedDevice.value?.id,
                    onProgress = { currentPcm ->
                        if (!control.shouldStop) {
                            CoroutineScope(Dispatchers.Main).launch {
                                if (!control.shouldStop) {
                                    val durationOfChunk = currentPcm.size / (16000 * 2) * 1000L
                                    val result = checkRecognition(currentPcm, durationOfChunk)
                                    if (result != null && !control.shouldStop) {
                                        finalSuccess = result
                                        control.shouldStop = true
                                        updateToSuccess(result)
                                    }
                                }
                            }
                        }
                    }
                )

                if (pcmData.size < 1000 && finalSuccess == null) {
                    if (_state.value !is RecognitionState.Success) {
                        _state.value = RecognitionState.Error(str("error_generic"))
                    }
                    return@launch
                }

                if (finalSuccess == null) {
                    val durationOfChunk = pcmData.size / (16000 * 2) * 1000L
                    val result = checkRecognition(pcmData, durationOfChunk)
                    if (result != null) {
                        updateToSuccess(result)
                    } else {
                        if (_state.value !is RecognitionState.Success) {
                            _state.value = RecognitionState.Error(str("recognition_track_not_found"))
                        }
                    }
                } else {
                    updateToSuccess(finalSuccess)
                }

            } catch (e: Exception) {
                println("ERROR: Error during audio recognition: ${e.message}")
                if (_state.value !is RecognitionState.Success) {
                    _state.value = RecognitionState.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    fun reset() {
        _state.value = RecognitionState.Idle
    }
}
