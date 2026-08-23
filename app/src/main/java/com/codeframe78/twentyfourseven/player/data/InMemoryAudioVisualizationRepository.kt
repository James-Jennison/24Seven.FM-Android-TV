package com.codeframe78.twentyfourseven.player.data

import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationRepository
import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemoryAudioVisualizationRepository : AudioVisualizationRepository {
    private val mutableState = MutableStateFlow(AudioVisualizationState())

    override val state: StateFlow<AudioVisualizationState> = mutableState.asStateFlow()

    override fun publish(state: AudioVisualizationState) {
        mutableState.value = state
    }

    override fun clear() {
        mutableState.value = AudioVisualizationState()
    }
}
