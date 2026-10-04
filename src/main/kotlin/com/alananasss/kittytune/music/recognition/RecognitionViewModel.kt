package com.alananasss.kittytune.music.recognition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.RecognitionHistoryRepository
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.Track
import com.metrolist.shazamkit.Shazam
import com.metrolist.shazamkit.models.RecognitionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    private var activeControl: RecordControl? = null
    private var recognitionJob: Job? = null

    @Volatile
    private var isChecking = false

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

    @Synchronized
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

        recognitionJob?.cancel()
        recognitionJob = viewModelScope.launch(Dispatchers.Main) {
            try {
                _state.value = RecognitionState.Recording
                
                val control = RecordControl()
                activeControl = control
                var finalSuccess: RecognitionState.Success? = null
                isChecking = false
                
                val totalDurationMs = 9000L
                val pcmData = audioRecorder.recordAudio(
                    durationMs = totalDurationMs,
                    control = control,
                    selectedDeviceName = _selectedDevice.value?.id,
                    onProgress = { currentPcm ->
                        if (!control.shouldStop && !isChecking && _state.value !is RecognitionState.Success) {
                            isChecking = true
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    if (!control.shouldStop && _state.value !is RecognitionState.Success) {
                                        val durationOfChunk = currentPcm.size / (16000 * 2) * 1000L
                                        val result = checkRecognition(currentPcm, durationOfChunk)
                                        if (result != null && !control.shouldStop && _state.value !is RecognitionState.Success) {
                                            finalSuccess = result
                                            control.shouldStop = true
                                            launch(Dispatchers.Main) {
                                                // Still the live run (not canceled/restarted since)?
                                                if (control === activeControl) updateToSuccess(result)
                                            }
                                        }
                                    }
                                } finally {
                                    isChecking = false
                                }
                            }
                        }
                    }
                )

                // Canceled while capture was still draining (blocking read / parec loop):
                // bail out instead of running the final check that would land on Error.
                if (!isActive || control.shouldStop || control !== activeControl) return@launch

                if (pcmData.size < 1000 && finalSuccess == null && _state.value !is RecognitionState.Success) {
                    if (_state.value !is RecognitionState.Success) {
                        _state.value = RecognitionState.Error(str("error_generic"))
                    }
                    return@launch
                }

                if (finalSuccess == null && _state.value !is RecognitionState.Success) {
                    val durationOfChunk = pcmData.size / (16000 * 2) * 1000L
                    val result = checkRecognition(pcmData, durationOfChunk)
                    if (result != null) {
                        if (control === activeControl) updateToSuccess(result)
                    } else {
                        if (control !== activeControl) return@launch
                        if (_state.value !is RecognitionState.Success) {
                            _state.value = RecognitionState.Error(str("recognition_track_not_found"))
                        }
                    }
                } else if (finalSuccess != null) {
                    if (control === activeControl) updateToSuccess(finalSuccess)
                }

            } catch (e: CancellationException) {
                // Recognition canceled (back button / bloomed button tap): like Android,
                // never surface this as an error.
                throw e
            } catch (e: Exception) {
                println("ERROR: Error during audio recognition: ${e.message}")
                if (_state.value !is RecognitionState.Success) {
                    _state.value = RecognitionState.Error(e.message ?: "Unknown error")
                }
            } finally {
                activeControl = null
            }
        }
    }

    /** Matches Android: tapping the bloomed button (or back) stops capture and returns to idle. */
    fun cancelRecognition() {
        activeControl?.shouldStop = true
        activeControl = null
        recognitionJob?.cancel()
        recognitionJob = null
        _state.value = RecognitionState.Idle
    }

    fun reset() {
        cancelRecognition()
    }
}

