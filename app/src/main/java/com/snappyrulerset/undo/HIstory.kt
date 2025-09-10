package com.snappyrulerset.undo

import com.snappyrulerset.model.DrawingState


class History(private val max: Int = 50) {
    private val past = ArrayDeque<DrawingState>()
    private val future = ArrayDeque<DrawingState>()


    fun push(state: DrawingState) {
        past.addLast(state)
        if (past.size > max) past.removeFirst()
        future.clear()
    }


    fun canUndo() = past.size > 1
    fun canRedo() = future.isNotEmpty()


    fun undo(current: DrawingState): DrawingState {
        if (!canUndo()) return current
        val last = past.removeLast()
        future.addLast(last)
        return past.last()
    }


    fun redo(current: DrawingState): DrawingState {
        val next = future.removeLastOrNull() ?: return current
        past.addLast(next)
        return next
    }
}