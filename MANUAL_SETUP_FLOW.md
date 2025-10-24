# Manual Board Setup - Complete Flow Analysis

## The Problem (BEFORE FIX)
When user manually set board position and pressed "Auto Start", the board would shift upward because auto-detection ran again and overwrote the manual position.

## The Solution (AFTER FIX)
Manual setup is now the "FINAL BOSS" - once configured, it's preserved everywhere.

## Complete Flow Trace

### 1. Manual Setup Process
```
User clicks "📐 Manual Setup" button
  ↓
startManualBoardSetup() creates draggable green overlay
  ↓
User drags corners/edges to position board perfectly
  ↓
User clicks "✓ DONE"
  ↓
finishManualSetup() asks: "Which color is at bottom?"
  ↓
User selects "⬜ WHITE" or "⬛ BLACK"
  ↓
confirmManualSetup(isWhiteBottom) executes:
  - boardX = manualBoardX          ← Saves manual X position
  - boardY = manualBoardY          ← Saves manual Y position  
  - boardSize = manualBoardSize    ← Saves manual size
  - isFlipped = !isWhiteBottom     ← Saves board orientation
  - boardAutoDetected = true       ← 🔑 KEY FLAG (marks as configured)
  - saveSettings()                 ← Persists to SharedPreferences
  - Shows red border preview for 3 seconds
```

### 2. Auto Start Flow (USES MANUAL SETUP)
```
User clicks "🤖 Auto Start" button
  ↓
startAutomaticWorkflow() checks:
  if (boardAutoDetected && boardX > 0 && boardY > 0 && boardSize > 0)
    ✓ TRUE - because manual setup set boardAutoDetected = true
  ↓
SKIPS automatic detection entirely
  ↓
Uses manual board position:
  - Position: X=boardX, Y=boardY, Size=boardSize (from manual setup)
  - Color: based on isFlipped (from manual setup)
  ↓
Shows red border at MANUAL position (not shifted!)
  ↓
Sends "start" to API
  ↓
Sends color to API  
  ↓
startDetection() begins move detection loop
```

### 3. Move Detection Loop (RESPECTS MANUAL SETUP)
```
detectionRunnable calls captureScreen() every 100ms
  ↓
captureScreen() checks:
  if (!boardAutoDetected)
    ✗ FALSE - because boardAutoDetected = true from manual setup
  ↓
SKIPS automatic detection in captureScreen too
  ↓
extractBoardArea() uses manual coordinates:
  - Extracts region at X=boardX, Y=boardY, Size=boardSize
  ↓
Detects moves using square-by-square comparison
  ↓
Sends detected moves to API
```

## Key Protection Mechanisms

### Protection Point #1: startAutomaticWorkflow()
```kotlin
// Check if board was already manually configured
if (boardAutoDetected && boardX > 0 && boardY > 0 && boardSize > 0) {
    addLog("✓ Using MANUAL board position (skipping auto-detection)")
    // Use manual position, skip detection
    return
}
```

### Protection Point #2: captureScreen()
```kotlin
// AUTOMATIC BOARD DETECTION on first capture
if (!boardAutoDetected) {
    // Only runs if NOT manually configured
    addLog("=== AUTOMATIC BOARD DETECTION ===")
    // ... detection code
}
```

### Protection Point #3: Persistence
```kotlin
private fun saveSettings() {
    prefs.edit()
        .putBoolean("board_auto_detected", boardAutoDetected)  // ← Saved!
        .apply()
}

private fun loadSettings() {
    boardAutoDetected = prefs.getBoolean("board_auto_detected", false)  // ← Loaded!
}
```

## The Fix Summary

✅ **FIXED**: Added `boardAutoDetected` to SharedPreferences persistence
✅ **FIXED**: `startAutomaticWorkflow()` checks flag and uses manual position
✅ **FIXED**: `captureScreen()` checks flag and skips auto-detection
✅ **FIXED**: Manual setup overlay properly removed in `onDestroy()`
✅ **ADDED**: Copy logs button (📋 icon) to copy all logs to clipboard

## Manual Setup is the FINAL BOSS! 👑

Once you manually configure the board:
- ✅ Position is saved permanently
- ✅ Auto Start respects your manual position
- ✅ Move detection uses your manual position
- ✅ Survives app restarts (saved to SharedPreferences)
- ✅ Never gets overwritten by auto-detection
