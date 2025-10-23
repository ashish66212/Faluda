package com.chesschat.app

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.abs

/**
 * Advanced chess board detector using OpenCV
 * Detects board boundaries, extracts squares, and identifies piece positions
 */
class ChessBoardDetector {
    
    companion object {
        private const val TAG = "ChessBoardDetector"
        
        init {
            try {
                System.loadLibrary("opencv_java4")
                Log.d(TAG, "OpenCV loaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "OpenCV load failed: ${e.message}")
            }
        }
    }
    
    /**
     * Extract 64 squares from the board bitmap
     */
    fun extractSquares(boardBitmap: Bitmap): List<Bitmap> {
        val squares = mutableListOf<Bitmap>()
        val squareSize = boardBitmap.width / 8
        
        try {
            for (row in 0 until 8) {
                for (col in 0 until 8) {
                    val x = col * squareSize
                    val y = row * squareSize
                    
                    val square = Bitmap.createBitmap(
                        boardBitmap,
                        x, y,
                        squareSize, squareSize
                    )
                    squares.add(square)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting squares: ${e.message}")
        }
        
        return squares
    }
    
    /**
     * Detect if a square is occupied by analyzing color variance
     * Empty squares have low variance, occupied squares have high variance
     */
    fun isSquareOccupied(square: Bitmap): Boolean {
        return try {
            val mat = Mat()
            Utils.bitmapToMat(square, mat)
            
            // Convert to grayscale
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY)
            
            // Calculate standard deviation (variance)
            val mean = MatOfDouble()
            val stdDev = MatOfDouble()
            Core.meanStdDev(mat, mean, stdDev)
            
            val variance = stdDev.get(0, 0)[0]
            
            mat.release()
            mean.release()
            stdDev.release()
            
            // Occupied squares have higher variance due to piece edges
            variance > 15.0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking square occupancy: ${e.message}")
            false
        }
    }
    
    /**
     * Determine if a piece is likely white or black based on average brightness
     */
    fun isPieceWhite(square: Bitmap): Boolean {
        return try {
            val mat = Mat()
            Utils.bitmapToMat(square, mat)
            
            // Convert to grayscale
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY)
            
            // Get average brightness
            val mean = Core.mean(mat)
            val brightness = mean.`val`[0]
            
            mat.release()
            
            // White pieces are generally brighter
            brightness > 128.0
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting piece color: ${e.message}")
            true
        }
    }
    
    /**
     * Enhanced board state detection using OpenCV edge detection
     */
    fun detectBoardState(boardBitmap: Bitmap): Array<IntArray> {
        val state = Array(8) { IntArray(8) { 0 } }  // 0 = empty, 1 = white, 2 = black
        
        try {
            val squares = extractSquares(boardBitmap)
            
            for (row in 0 until 8) {
                for (col in 0 until 8) {
                    val index = row * 8 + col
                    if (index < squares.size) {
                        val square = squares[index]
                        
                        if (isSquareOccupied(square)) {
                            state[row][col] = if (isPieceWhite(square)) 1 else 2
                        }
                        
                        square.recycle()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting board state: ${e.message}")
        }
        
        return state
    }
    
    /**
     * Compare two board states and find the move
     */
    fun detectMove(oldState: Array<IntArray>, newState: Array<IntArray>, isFlipped: Boolean): String? {
        val changes = mutableListOf<Pair<Int, Int>>()
        
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                if (oldState[row][col] != newState[row][col]) {
                    changes.add(Pair(row, col))
                }
            }
        }
        
        Log.d(TAG, "Detected ${changes.size} changed squares")
        
        // Standard move: exactly 2 squares changed
        if (changes.size == 2) {
            // Determine which square lost a piece (from) and which gained (to)
            val from = changes.firstOrNull { (r, c) -> 
                oldState[r][c] != 0 && newState[r][c] == 0 
            }
            val to = changes.firstOrNull { (r, c) -> 
                oldState[r][c] == 0 && newState[r][c] != 0 
            }
            
            if (from != null && to != null) {
                val fromUCI = squareToUCI(from.first, from.second, isFlipped)
                val toUCI = squareToUCI(to.first, to.second, isFlipped)
                val move = fromUCI + toUCI
                
                Log.d(TAG, "Move detected: $move (from $fromUCI to $toUCI)")
                return move
            } else {
                // Capture move: both squares were occupied
                val moveFrom = changes[0]
                val moveTo = changes[1]
                
                // Check which one still has a piece (that's the destination)
                val actualFrom = if (newState[moveFrom.first][moveFrom.second] == 0) moveFrom else moveTo
                val actualTo = if (newState[moveTo.first][moveTo.second] != 0) moveTo else moveFrom
                
                val fromUCI = squareToUCI(actualFrom.first, actualFrom.second, isFlipped)
                val toUCI = squareToUCI(actualTo.first, actualTo.second, isFlipped)
                val move = fromUCI + toUCI
                
                Log.d(TAG, "Capture detected: $move")
                return move
            }
        }
        
        // Castling: 4 squares change (king and rook on both sides)
        if (changes.size == 4) {
            Log.d(TAG, "Possible castling detected with ${changes.size} changes")
            // Simplified castling detection - look for king movement of 2 squares
            val kingMoves = changes.filter { (r, c) ->
                abs(changes.map { it.second }.distinct().size) >= 2
            }
            if (kingMoves.size >= 2) {
                // Return king's movement as the move
                return detectMove(oldState, newState, isFlipped)
            }
        }
        
        if (changes.isNotEmpty()) {
            Log.d(TAG, "Unusual change pattern: ${changes.size} squares")
        }
        
        return null
    }
    
    private fun squareToUCI(row: Int, col: Int, isFlipped: Boolean): String {
        val actualRow: Int
        val actualCol: Int
        
        if (isFlipped) {
            actualRow = row
            actualCol = 7 - col
        } else {
            actualRow = 7 - row
            actualCol = col
        }
        
        val file = ('a' + actualCol).toString()
        val rank = (actualRow + 1).toString()
        
        return "$file$rank"
    }
}
