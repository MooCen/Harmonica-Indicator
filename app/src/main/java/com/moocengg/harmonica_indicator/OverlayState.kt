package com.moocengg.harmonica_indicator

import android.view.View
import android.widget.FrameLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object OverlayState {
    var OverlayAlive: Boolean = false
    lateinit var calibrationContainer: MutableStateFlow<FrameLayout>
    var pointDict = mapOf<Pair<Int, Int>,View>()
    // 全谱信息
    private val _WholeINFO = MutableStateFlow<INFO>(INFO("",0,0,emptyList()))
    val WholeINFO: StateFlow<INFO> = _WholeINFO.asStateFlow()
    // note信息
    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()
    // 计时器
    private val _currentTimeMs = MutableStateFlow(0L)
    val currentTimeMs: StateFlow<Long> = _currentTimeMs.asStateFlow()
    private val _combo = MutableStateFlow(0)
    val combo: StateFlow<Int> = _combo.asStateFlow()

    fun updateINFO(info: INFO){
        _WholeINFO.value = info
    }
    fun updateNotes(list: List<Note>) {
        _notes.value = list.toList()
    }
    fun updateCurrentTime(ms: Long) {
        _currentTimeMs.value = ms
    }
    fun updateCombo(comboNext: Boolean, jmpValue: Int = -1){
        _combo.value = if(comboNext) _combo.value + 1 else 0
        if(jmpValue>=0){
            _combo.value = jmpValue
        }
    }
}