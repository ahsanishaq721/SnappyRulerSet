package com.snappyrulerset.viewmodel

import androidx.lifecycle.ViewModel
import com.snappyrulerset.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DrawingViewModel : ViewModel() {
    private val past = ArrayDeque<DrawingState>()   // undo
    private val future = ArrayDeque<DrawingState>() // redo

    private val _state = MutableStateFlow(DrawingState())
    val state: StateFlow<DrawingState> = _state

    init {
        push(_state.value)
    }

    fun update(block: (DrawingState) -> DrawingState) {
        val s = block(_state.value)
        _state.value = s
        push(s)
    }

    fun undo() {
        if (past.size > 1) {
            val cur = past.removeLast()
            future.addLast(cur)
            _state.value = past.last()
        }
    }

    fun redo() {
        val next = future.removeLastOrNull() ?: return
        past.addLast(next)
        _state.value = next
    }

    private fun push(s: DrawingState) {
        past.addLast(s)
        if (past.size > 50) past.removeFirst()
        future.clear()
    }
}