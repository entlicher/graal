/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

# Back-in-Time Debugger Example

This example demonstrates the usage of the Truffle back-in-time debugger.

## Basic Usage

```java
import com.oracle.truffle.api.debug.*;
import org.graalvm.polyglot.*;

// Create context and get debugger
Context context = Context.create();
Debugger debugger = Debugger.find(context.getEngine());

// Start session with time-travel enabled
try (DebuggerSession session = debugger.startSession((event) -> {
    // Handle suspended event
    event.prepareStepInto(1);
})) {
    // Enable time-travel recording
    session.setTimeTravelEnabled(true);
    
    // Execute code - snapshots will be recorded at each suspension
    context.eval(Source.create("js", "let x = 1; x = x + 1;"));
    
    // Access execution history
    System.out.println("Recorded " + session.getHistorySize() + " snapshots");
    
    // Examine snapshots
    for (TimeTravelSnapshot snapshot : session.getExecutionHistory()) {
        System.out.println("Location: " + snapshot.getSourceSection());
        for (TimeTravelSnapshot.FrameSnapshot frame : snapshot.getStackFrames()) {
            System.out.println("  Variables: " + frame.getVariables());
        }
    }
}
```

## Key Features

1. **Enable Recording**: Call `session.setTimeTravelEnabled(true)` to start recording
2. **Automatic Capture**: Snapshots are automatically recorded at each suspension point
3. **Query History**: Use `getExecutionHistory()` to examine all recorded states
4. **Check Navigation**: Use `canStepBackward()` and `canStepForward()` to check navigation options
5. **Inspect State**: Each snapshot contains source location, stack frames, and variable values

## Example Output

When running the example, you'll see output like:

```
Time-travel debugging enabled: true
Suspended at: example.js:2:4
  Top frame: calculateSum

=== Execution History ===
Total snapshots recorded: 15
Current position: 14

Snapshot 0:
  Location: example.js:2:4
  Anchor: BEFORE
  Timestamp: 1698765432000
  Top Frame: calculateSum
  Variables: {n=5, sum=0}

Snapshot 1:
  Location: example.js:3:8
  Anchor: BEFORE
  Timestamp: 1698765432010
  Top Frame: calculateSum
  Variables: {n=5, sum=0, i=1}

...
```

## Advanced Features

### Clearing History

```java
session.clearExecutionHistory();
```

### Checking Position

```java
int position = session.getHistoryPosition();
int total = session.getHistorySize();
System.out.println("At position " + position + " of " + total);
```

### Dynamic Enable/Disable

```java
// Start without recording
session.setTimeTravelEnabled(false);

// Enable only when needed
if (someCondition) {
    session.setTimeTravelEnabled(true);
}
```

## Performance Notes

- Recording is disabled by default to avoid overhead
- Variable values are stored as strings to prevent memory leaks
- History is limited to 1000 snapshots by default (configurable)
- Snapshots only capture state at suspension points, not every instruction
