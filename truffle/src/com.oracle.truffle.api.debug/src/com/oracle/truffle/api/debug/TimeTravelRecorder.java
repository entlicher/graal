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
package com.oracle.truffle.api.debug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.debug.TimeTravelSnapshot.FrameSnapshot;

/**
 * Records execution history for back-in-time debugging. Captures snapshots of execution state at
 * each suspension point, allowing the debugger to step backwards through execution history.
 *
 * @since 24.2.0
 */
final class TimeTravelRecorder {

    private final LinkedList<TimeTravelSnapshot> history;
    private final int maxHistorySize;
    private long sequenceNumber;
    private int currentPosition;
    private boolean enabled;

    TimeTravelRecorder() {
        this(1000); // Default to storing 1000 snapshots
    }

    TimeTravelRecorder(int maxHistorySize) {
        this.history = new LinkedList<>();
        this.maxHistorySize = maxHistorySize;
        this.sequenceNumber = 0;
        this.currentPosition = -1;
        this.enabled = false;
    }

    /**
     * Enable or disable recording.
     */
    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Check if recording is enabled.
     */
    boolean isEnabled() {
        return enabled;
    }

    /**
     * Record a snapshot of the current execution state.
     */
    void recordSnapshot(SuspendedEvent event) {
        if (!enabled) {
            return;
        }

        // If we're not at the end of history, remove future snapshots
        while (currentPosition < history.size() - 1 && !history.isEmpty()) {
            history.removeLast();
        }

        // Capture the current state
        List<FrameSnapshot> frames = captureFrames(event);
        TimeTravelSnapshot snapshot = new TimeTravelSnapshot(
                        sequenceNumber++,
                        event.getSourceSection(),
                        event.getSuspendAnchor(),
                        frames);

        history.add(snapshot);
        currentPosition = history.size() - 1;

        // Limit history size
        while (history.size() > maxHistorySize) {
            history.removeFirst();
            currentPosition--;
        }
    }

    /**
     * Check if we can step backward.
     */
    boolean canStepBackward() {
        return enabled && currentPosition > 0;
    }

    /**
     * Check if we can step forward (when in history, not at current time).
     */
    boolean canStepForward() {
        return enabled && currentPosition < history.size() - 1;
    }

    /**
     * Get the previous snapshot (step backward).
     */
    TimeTravelSnapshot stepBackward() {
        if (!canStepBackward()) {
            throw new IllegalStateException("Cannot step backward: no previous snapshot available");
        }
        currentPosition--;
        return history.get(currentPosition);
    }

    /**
     * Get the next snapshot (step forward in recorded history).
     */
    TimeTravelSnapshot stepForward() {
        if (!canStepForward()) {
            throw new IllegalStateException("Cannot step forward: already at current time");
        }
        currentPosition++;
        return history.get(currentPosition);
    }

    /**
     * Get the current snapshot.
     */
    TimeTravelSnapshot getCurrentSnapshot() {
        if (currentPosition >= 0 && currentPosition < history.size()) {
            return history.get(currentPosition);
        }
        return null;
    }

    /**
     * Get the entire history.
     */
    List<TimeTravelSnapshot> getHistory() {
        return new ArrayList<>(history);
    }

    /**
     * Clear all recorded history.
     */
    void clear() {
        history.clear();
        currentPosition = -1;
        sequenceNumber = 0;
    }

    /**
     * Get the current position in history.
     */
    int getCurrentPosition() {
        return currentPosition;
    }

    /**
     * Get the total number of snapshots in history.
     */
    int getHistorySize() {
        return history.size();
    }

    /**
     * Capture stack frames from a suspended event.
     */
    private List<FrameSnapshot> captureFrames(SuspendedEvent event) {
        List<FrameSnapshot> frames = new ArrayList<>();
        Iterable<DebugStackFrame> stackFrames = event.getStackFrames();
        
        for (DebugStackFrame frame : stackFrames) {
            Map<String, Object> variables = new HashMap<>();
            DebugScope scope = frame.getScope();
            
            // Capture all visible variables in the scope
            for (DebugValue value : scope.getDeclaredValues()) {
                try {
                    // Store a string representation of the value to avoid keeping references
                    String valueStr = value.toDisplayString();
                    variables.put(value.getName(), valueStr);
                } catch (Exception e) {
                    // If we can't capture the value, store an error message
                    variables.put(value.getName(), "<unavailable>");
                }
            }
            
            frames.add(new FrameSnapshot(
                            frame.getName(),
                            frame.getSourceSection(),
                            variables));
        }
        
        return frames;
    }
}
