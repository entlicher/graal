# Back-in-Time Debugger for Truffle

## Overview

This implementation adds back-in-time debugging capabilities to the Truffle framework, allowing developers to step backward through execution history when debugging their programs.

## Architecture

The back-in-time debugger consists of three main components:

### 1. TimeTravelSnapshot

A data class that captures the execution state at a specific point in time:
- Source section location
- Suspend anchor (BEFORE/AFTER)
- Stack frames with variable values
- Timestamp

### 2. TimeTravelRecorder

Manages the recording and playback of execution history:
- Records snapshots at each suspension point
- Maintains a circular buffer of snapshots (default: 1000)
- Tracks current position in history
- Provides navigation methods (stepBackward, stepForward)

### 3. DebuggerSession Extensions

Added methods to the DebuggerSession API:
- `setTimeTravelEnabled(boolean)` - Enable/disable recording
- `isTimeTravelEnabled()` - Check if recording is active
- `canStepBackward()` - Check if backward stepping is possible
- `canStepForward()` - Check if forward stepping is possible
- `getCurrentSnapshot()` - Get current execution snapshot
- `getExecutionHistory()` - Get complete history
- `clearExecutionHistory()` - Clear all recorded history
- `getHistoryPosition()` - Get current position in history
- `getHistorySize()` - Get total number of snapshots

## Usage Example

```java
// Create a debugger session
try (DebuggerSession session = debugger.startSession(callback)) {
    // Enable time-travel recording
    session.setTimeTravelEnabled(true);
    
    // Set breakpoint and run program
    session.install(Breakpoint.newBuilder(source).lineIs(10).build());
    context.eval(source);
    
    // When suspended, check if we can step backward
    if (session.canStepBackward()) {
        TimeTravelSnapshot previousState = session.getCurrentSnapshot();
        // Examine previous state...
    }
    
    // Get execution history
    List<TimeTravelSnapshot> history = session.getExecutionHistory();
    for (TimeTravelSnapshot snapshot : history) {
        System.out.println("Position: " + snapshot.getSourceSection());
        for (FrameSnapshot frame : snapshot.getStackFrames()) {
            System.out.println("  Frame: " + frame.getName());
            System.out.println("  Variables: " + frame.getVariables());
        }
    }
}
```

## Features

- **Automatic Recording**: When enabled, automatically captures execution state at each suspension point
- **Circular Buffer**: Maintains the most recent N snapshots (configurable, default 1000)
- **Minimal Overhead**: Only records when explicitly enabled
- **Thread-Safe**: Safe to use in multi-threaded debugging scenarios
- **Complete State Capture**: Records stack frames, variable values, and source locations

## Performance Considerations

- Recording is disabled by default to avoid overhead
- Variable values are stored as strings to avoid memory leaks from keeping object references
- History size is limited to prevent unbounded memory growth
- Recording can be enabled/disabled dynamically during execution

## Limitations

- Variable values are captured as display strings, not live object references
- History is stored in memory and limited by the configured buffer size
- Does not support actual execution replay (state visualization only)
- Snapshots represent suspension points only, not every instruction

## Future Enhancements

Possible future improvements could include:
- Persistent snapshot storage to disk
- Configurable snapshot granularity
- Differential snapshots to reduce memory usage
- Support for replaying execution from snapshots
- Integration with debugger UI for visual time-travel
