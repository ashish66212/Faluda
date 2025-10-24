# UCI Grid Mapping System - Complete Documentation

## Overview
This document explains the comprehensive UCI (Universal Chess Interface) grid mapping system implemented in the ChessChatApp. The system maps all 64 chess squares to exact pixel coordinates on the screen for accurate move detection and execution.

## UCI Coordinate System

### Standard Chess Notation
Chess boards use algebraic notation with:
- **Files** (columns): a-h (ALWAYS left to right, NEVER flip)
- **Ranks** (rows): 1-8 (bottom to top, flip based on color selection)

### The 64 Squares

**IMPORTANT:** Files (a-h) ALWAYS go from left to right. Only the rank numbering changes based on which color you select.

```
WHITE ORIENTATION (White pieces at bottom, isFlipped=false):
┌─────────────────────────────────────────────┐
│ a8  b8  c8  d8  e8  f8  g8  h8  │ Rank 8 (top)
│ a7  b7  c7  d7  e7  f7  g7  h7  │ Rank 7
│ a6  b6  c6  d6  e6  f6  g6  h6  │ Rank 6
│ a5  b5  c5  d5  e5  f5  g5  h5  │ Rank 5
│ a4  b4  c4  d4  e4  f4  g4  h4  │ Rank 4
│ a3  b3  c3  d3  e3  f3  g3  h3  │ Rank 3
│ a2  b2  c2  d2  e2  f2  g2  h2  │ Rank 2
│ a1  b1  c1  d1  e1  f1  g1  h1  │ Rank 1 (bottom)
└─────────────────────────────────────────────┘
  File a→                      →File h
  (Files never change)

BLACK ORIENTATION (Black pieces at bottom, isFlipped=true):
┌─────────────────────────────────────────────┐
│ a1  b1  c1  d1  e1  f1  g1  h1  │ Rank 1 (bottom)
│ a2  b2  c2  d2  e2  f2  g2  h2  │ Rank 2
│ a3  b3  c3  d3  e3  f3  g3  h3  │ Rank 3
│ a4  b4  c4  d4  e4  f4  g4  h4  │ Rank 4
│ a5  b5  c5  d5  e5  f5  g5  h5  │ Rank 5
│ a6  b6  c6  d6  e6  f6  g6  h6  │ Rank 6
│ a7  b7  c7  d7  e7  f7  g7  h7  │ Rank 7
│ a8  b8  c8  d8  e8  f8  g8  h8  │ Rank 8 (top)
└─────────────────────────────────────────────┘
  File a→                      →File h
  (Files never change)
```

## Board Orientation

### White Orientation (isFlipped = false)
- **White pieces** on Rank 1 (bottom of screen)
- **Black pieces** on Rank 8 (top of screen)
- **Files**: a-h from left to right (never change)
- **Ranks**: 1-8 from bottom to top

### Black Orientation (isFlipped = true)
- **White pieces** on Rank 1 (bottom of screen from black's perspective)
- **Black pieces** on Rank 8 (top of screen from black's perspective)
- **Files**: a-h from left to right (SAME as white orientation)
- **Ranks**: 1-8 from bottom to top (SAME numbering, visually flipped)

## Pixel Coordinate Mapping

### How It Works

Given:
- `boardX` = X coordinate of board's left edge (e.g., 12 pixels)
- `boardY` = Y coordinate of board's top edge (e.g., 502 pixels)
- `boardSize` = Board width/height (e.g., 698 pixels)
- `squareSize` = boardSize / 8 (e.g., 87 pixels per square)

### Formula for White Orientation (isFlipped = false)

```kotlin
file = square[0] - 'a'       // Convert a-h to 0-7
rank = square[1] - '1'       // Convert 1-8 to 0-7

actualFile = file            // No flip needed
actualRank = 7 - rank        // Invert rank (screen coords go top-to-bottom)

pixelX = boardX + (actualFile * squareSize) + (squareSize / 2)
pixelY = boardY + (actualRank * squareSize) + (squareSize / 2)
```

### Formula for Black Orientation (isFlipped = true)

```kotlin
file = square[0] - 'a'       // Convert a-h to 0-7
rank = square[1] - '1'       // Convert 1-8 to 0-7

actualFile = 7 - file        // Flip horizontally
actualRank = rank            // No vertical flip needed

pixelX = boardX + (actualFile * squareSize) + (squareSize / 2)
pixelY = boardY + (actualRank * squareSize) + (squareSize / 2)
```

## Examples

### Example 1: Square e2 (White Orientation)

**Setup:**
- boardX = 12, boardY = 502, boardSize = 698
- squareSize = 698 / 8 = 87.25 pixels
- isFlipped = false (white on bottom)

**Calculation:**
```
Square: e2
file = 'e' - 'a' = 4
rank = '2' - '1' = 1

actualFile = 4 (no flip)
actualRank = 7 - 1 = 6 (invert for screen coords)

pixelX = 12 + (4 * 87.25) + 43.625 = 404.625 ≈ 405
pixelY = 502 + (6 * 87.25) + 43.625 = 1069.125 ≈ 1069
```

**Result:** Square e2 → Pixel (405, 1069)

### Example 2: Square e4 (White Orientation)

**Calculation:**
```
Square: e4
file = 4, rank = 3

actualFile = 4
actualRank = 7 - 3 = 4

pixelX = 12 + (4 * 87.25) + 43.625 = 405
pixelY = 502 + (4 * 87.25) + 43.625 = 894
```

**Result:** Square e4 → Pixel (405, 894)

### Example 3: Square e2 (Black Orientation)

**Setup:**
- Same board dimensions
- isFlipped = true (black on bottom)

**Calculation:**
```
Square: e2
file = 4, rank = 1

actualFile = 7 - 4 = 3 (flip horizontally)
actualRank = 1 (no vertical flip)

pixelX = 12 + (3 * 87.25) + 43.625 = 317.375 ≈ 317
pixelY = 502 + (1 * 87.25) + 43.625 = 632.875 ≈ 633
```

**Result:** Square e2 (flipped) → Pixel (317, 633)

## Move Execution Process

### 1. Move Detection
When a move is detected (e.g., "e2e4"), the system:
1. Receives UCI notation from Stockfish engine
2. Parses into fromSquare="e2", toSquare="e4"

### 2. Coordinate Calculation
```kotlin
val fromCoords = touchSimulator.getSquareCoordinates(
    "e2", boardX, boardY, boardSize, isFlipped
)
// Returns: (405, 1069) for white orientation

val toCoords = touchSimulator.getSquareCoordinates(
    "e4", boardX, boardY, boardSize, isFlipped
)
// Returns: (405, 894) for white orientation
```

### 3. Move Execution
```kotlin
// Step 1: Tap source square
touchSimulator.simulateTouch(405, 1069, 300ms)

// Step 2: Wait 600ms

// Step 3: Tap destination square
touchSimulator.simulateTouch(405, 894, 300ms)
```

## Comprehensive Logging

### Move Execution Logs
When a move is executed, the system logs:

```
╔═══════════════════════════════════════════════════╗
║         AUTOMATIC MOVE EXECUTION STARTED          ║
╠═══════════════════════════════════════════════════╣
Move (UCI notation): e2e4
─────────────────────────────────────────────────────
MOVE BREAKDOWN:
  • From Square: e2
  • To Square: e4
  • Orientation: WHITE pieces on bottom
─────────────────────────────────────────────────────
BOARD CONFIGURATION:
  • Board X: 12 pixels (left edge)
  • Board Y: 502 pixels (top edge)
  • Board Size: 698 pixels (width × height)
  • Square Size: 87 pixels
  • Flipped: false
─────────────────────────────────────────────────────
UCI SQUARE MAPPING (for e2):
  UCI Square: e2
  File: e (4) → Screen File: 4
  Rank: 2 (1) → Screen Rank: 6
  Board Orientation: WHITE on bottom
  Grid Position: Column 4, Row 6 (0-indexed)
  Pixel Coordinates: (405, 1069) [center of square]
─────────────────────────────────────────────────────
PIXEL TAP PLAN:
  • First Tap:  Pixel (405, 1069) ← e2
  • Second Tap: Pixel (405, 894) ← e4
  • Method: TAP-TAP (300ms press duration each)
  • Delay: 600ms between taps
═══════════════════════════════════════════════════
✓✓✓ MOVE EXECUTION COMPLETE ✓✓✓
Move: e2 → e4 (e2e4)
Pixels tapped:
  1st: (405, 1069)
  2nd: (405, 894)
═══════════════════════════════════════════════════
╚═══════════════════════════════════════════════════╝
```

### Grid Map Visualization
Call `touchSimulator.printUCIGridMap()` to see all 64 squares:

```
╔════════════════════════════════════════════════════════════╗
║           COMPLETE UCI GRID MAP (64 squares)              ║
╠════════════════════════════════════════════════════════════╣
║ Orientation: WHITE pieces on bottom
║ Board Area: X=12, Y=502, Size=698
║ Square Size: 87 pixels
╠════════════════════════════════════════════════════════════╣
║ Rank 8: a8:(55,545) b8:(143,545) c8:(230,545) d8:(318,545) e8:(405,545) f8:(493,545) g8:(580,545) h8:(668,545)
║ Rank 7: a7:(55,632) b7:(143,632) c7:(230,632) d7:(318,632) e7:(405,632) f7:(493,632) g7:(580,632) h7:(668,632)
... (continues for all 8 ranks)
╚════════════════════════════════════════════════════════════╝
```

## Implementation Details

### TouchSimulator.kt Enhancements

1. **Complete UCI Square List**
   - Defined all 64 squares in standard order
   - Easy reference for validation and debugging

2. **Enhanced getSquareCoordinates()**
   - Comprehensive logging of mapping process
   - Shows file/rank conversions
   - Displays orientation effects
   - Reports final pixel coordinates

3. **New Helper Functions**
   - `getSquareFromCoordinates()`: Reverse mapping (pixel → UCI)
   - `printUCIGridMap()`: Complete grid visualization

4. **Improved Touch Logging**
   - Shows exact pixels tapped
   - Duration of each touch
   - Success/failure status with symbols (✓/✗)

### MoveDetectionOverlayService.kt Enhancements

1. **Detailed Move Execution Logs**
   - Move breakdown (from/to squares)
   - Board configuration details
   - UCI-to-pixel mapping for both squares
   - Tap plan with precise coordinates
   - Step-by-step execution status
   - Success summary with all pixel coordinates

## Benefits

### For Users:
- **Clear visibility** into exactly where moves are being executed
- **Easy debugging** when moves fail or are misplaced
- **Understanding** of how board orientation affects coordinates

### For Developers:
- **Quick diagnosis** of coordinate mapping issues
- **Visual verification** via grid map printing
- **Comprehensive logs** for troubleshooting
- **Easy validation** of board setup

## Manual Board Setup Process

1. **Enable overlay permission** in Android settings
2. **Start detection** from main app
3. **Click "Manual Setup"** button
4. **Drag corners** to match your chess board perfectly
5. **Click DONE** to save board position
6. **Select your color**:
   - White → White pieces at bottom (isFlipped=false)
   - Black → Black pieces at bottom (isFlipped=true)
7. **Start automatic workflow** - system now knows all 64 square positions!

## Troubleshooting

### Wrong Square Tapped?

**Check logs for:**
1. Board configuration (X, Y, Size)
2. Square size calculation
3. UCI-to-pixel mapping
4. Orientation setting (isFlipped)

### Move Detection Not Working?

**Verify:**
1. Manual setup completed correctly
2. Color selection matches your actual position
3. Board hasn't moved on screen
4. All 64 squares are within screen bounds

## Technical Notes

- Screen coordinates start at (0, 0) in top-left corner
- Y increases downward (opposite of chess ranks)
- Each square center is calculated, not edges
- Pixel coordinates are integers (rounded from float)
- Orientation affects both move detection and execution

## Future Enhancements

Potential improvements:
- [ ] Visual overlay showing all 64 square boundaries
- [ ] Real-time UCI square display when tapping screen
- [ ] Automatic validation of board setup
- [ ] Save/load multiple board configurations
- [ ] Auto-calibration using computer vision

---

**Last Updated:** October 24, 2025  
**Version:** 2.0  
**Status:** Production Ready ✅
