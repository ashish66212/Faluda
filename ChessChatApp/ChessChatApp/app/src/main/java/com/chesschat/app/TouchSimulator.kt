package com.chesschat.app

import android.util.Log

/**
 * Touch Simulator for automated move execution
 * Uses ChessAccessibilityService to dispatch gestures
 */
class TouchSimulator {
    
    companion object {
        private const val TAG = "TouchSimulator"
    }
    
    /**
     * Check if accessibility service is enabled
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        return ChessAccessibilityService.isServiceEnabled()
    }
    
    /**
     * Simulate a touch at the specified coordinates
     * Requires ChessAccessibilityService to be enabled
     */
    fun simulateTouch(x: Float, y: Float): Boolean {
        val service = ChessAccessibilityService.getInstance()
        
        if (service == null) {
            Log.w(TAG, "Accessibility service not enabled - touch simulation unavailable")
            return false
        }
        
        return try {
            val success = service.performTouch(x, y, 100)
            if (success) {
                Log.d(TAG, "Touch dispatched at ($x, $y)")
            } else {
                Log.w(TAG, "Failed to dispatch touch at ($x, $y)")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Exception simulating touch: ${e.message}")
            false
        }
    }
    
    /**
     * Simulate a drag gesture from one point to another
     * Requires ChessAccessibilityService to be enabled
     */
    fun simulateDrag(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long = 300): Boolean {
        val service = ChessAccessibilityService.getInstance()
        
        if (service == null) {
            Log.w(TAG, "Accessibility service not enabled - drag simulation unavailable")
            return false
        }
        
        return try {
            val success = service.performDrag(fromX, fromY, toX, toY, durationMs)
            if (success) {
                Log.d(TAG, "Drag dispatched from ($fromX, $fromY) to ($toX, $toY)")
            } else {
                Log.w(TAG, "Failed to dispatch drag")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Exception simulating drag: ${e.message}")
            false
        }
    }
    
    /**
     * Calculate screen coordinates for a chess square
     */
    fun getSquareCoordinates(
        square: String,  // e.g., "e2", "e4"
        boardX: Int,
        boardY: Int,
        boardSize: Int,
        isFlipped: Boolean
    ): Pair<Float, Float> {
        val file = square[0] - 'a'  // 0-7
        val rank = square[1] - '1'  // 0-7
        
        val actualFile = if (isFlipped) 7 - file else file
        val actualRank = if (isFlipped) rank else 7 - rank
        
        val squareSize = boardSize / 8
        val x = boardX + (actualFile * squareSize) + (squareSize / 2)
        val y = boardY + (actualRank * squareSize) + (squareSize / 2)
        
        return Pair(x.toFloat(), y.toFloat())
    }
}
