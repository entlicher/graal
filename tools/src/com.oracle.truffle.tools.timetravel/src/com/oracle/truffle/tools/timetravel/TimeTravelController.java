/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.tools.timetravel;

import java.util.List;
import java.util.Objects;

/**
 * Controller interface for time-travel debugging operations.
 * 
 * <p>
 * This class provides the primary API for navigating execution history in a Truffle program.
 * It supports operations similar to GDB's record/replay functionality:
 * </p>
 * 
 * <ul>
 * <li><b>Backward stepping</b>: Move to the previous execution point</li>
 * <li><b>History navigation</b>: Jump to specific points in execution history</li>
 * <li><b>State inspection</b>: Examine program state at any recorded point</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * <pre>
 * // Obtain controller from instrument
 * TimeTravelController controller = engine.getInstruments()
 *     .get("timetravel")
 *     .lookup(TimeTravelController.class);
 * 
 * // Navigate execution history
 * controller.stepBackward();
 * ExecutionSnapshot snapshot = controller.getCurrentSnapshot();
 * System.out.println("At: " + snapshot.getDescription());
 * 
 * // List all recorded points
 * List&lt;ExecutionSnapshot&gt; history = controller.getExecutionHistory();
 * 
 * // Jump to specific point
 * controller.jumpToSnapshot(history.get(10));
 * </pre>
 * 
 * <h2>Integration with Debugger API</h2>
 * <p>
 * This controller is designed to work alongside the standard Truffle debugger API.
 * It can be used in combination with {@link com.oracle.truffle.api.debug.DebuggerSession}
 * to provide a complete debugging experience.
 * </p>
 * 
 * @see TimeTravelRecorder
 * @see ExecutionSnapshot
 */
public final class TimeTravelController {

    private final Object recorderState;
    private int currentSnapshotIndex;

    /**
     * Creates a new controller (package-private, constructed by the recorder).
     * 
     * @param recorderState the internal state from the recorder
     */
    TimeTravelController(Object recorderState) {
        this.recorderState = Objects.requireNonNull(recorderState);
        this.currentSnapshotIndex = -1;
    }

    /**
     * Returns the list of all recorded execution snapshots.
     * 
     * <p>
     * The snapshots are ordered chronologically from earliest to latest execution.
     * Each snapshot represents a checkpoint where program state was captured.
     * </p>
     * 
     * @return an immutable list of execution snapshots
     */
    public List<ExecutionSnapshot> getExecutionHistory() {
        return getState().getSnapshots();
    }

    /**
     * Returns the current snapshot in the navigation history.
     * 
     * <p>
     * If no navigation has occurred, returns the most recent snapshot.
     * After calling {@link #stepBackward()}, returns the snapshot at the previous position.
     * </p>
     * 
     * @return the current snapshot, or null if no snapshots exist
     */
    public ExecutionSnapshot getCurrentSnapshot() {
        List<ExecutionSnapshot> snapshots = getExecutionHistory();
        
        if (snapshots.isEmpty()) {
            return null;
        }
        
        if (currentSnapshotIndex < 0) {
            // Default to most recent
            currentSnapshotIndex = snapshots.size() - 1;
        }
        
        if (currentSnapshotIndex >= snapshots.size()) {
            currentSnapshotIndex = snapshots.size() - 1;
        }
        
        return snapshots.get(currentSnapshotIndex);
    }

    /**
     * Moves backward to the previous execution snapshot.
     * 
     * <p>
     * This operation is analogous to GDB's "reverse-step" command. It moves the
     * current position to the previous checkpoint in execution history.
     * </p>
     * 
     * <p>
     * <b>Note:</b> The current implementation only navigates between checkpoints.
     * Statement-level backward stepping requires additional replay logic and will
     * be implemented in a future version.
     * </p>
     * 
     * @return true if stepped backward successfully, false if already at the earliest snapshot
     */
    public boolean stepBackward() {
        List<ExecutionSnapshot> snapshots = getExecutionHistory();
        
        if (snapshots.isEmpty()) {
            return false;
        }
        
        if (currentSnapshotIndex < 0) {
            currentSnapshotIndex = snapshots.size() - 1;
        }
        
        if (currentSnapshotIndex > 0) {
            currentSnapshotIndex--;
            return true;
        }
        
        return false;
    }

    /**
     * Moves forward to the next execution snapshot.
     * 
     * <p>
     * This operation moves the current position to the next checkpoint in execution history.
     * It's the inverse of {@link #stepBackward()}.
     * </p>
     * 
     * @return true if stepped forward successfully, false if already at the latest snapshot
     */
    public boolean stepForward() {
        List<ExecutionSnapshot> snapshots = getExecutionHistory();
        
        if (snapshots.isEmpty()) {
            return false;
        }
        
        if (currentSnapshotIndex < 0) {
            currentSnapshotIndex = 0;
            return true;
        }
        
        if (currentSnapshotIndex < snapshots.size() - 1) {
            currentSnapshotIndex++;
            return true;
        }
        
        return false;
    }

    /**
     * Jumps to a specific snapshot in execution history.
     * 
     * <p>
     * This allows direct navigation to any recorded checkpoint, similar to
     * GDB's ability to jump to a specific execution point using bookmarks.
     * </p>
     * 
     * @param snapshot the target snapshot to jump to
     * @return true if the jump was successful, false if the snapshot is not in history
     * @throws NullPointerException if snapshot is null
     */
    public boolean jumpToSnapshot(ExecutionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        
        List<ExecutionSnapshot> snapshots = getExecutionHistory();
        int index = snapshots.indexOf(snapshot);
        
        if (index >= 0) {
            currentSnapshotIndex = index;
            return true;
        }
        
        return false;
    }

    /**
     * Jumps to the snapshot at the specified step number.
     * 
     * <p>
     * This method finds the snapshot closest to (but not exceeding) the given step number.
     * If no such snapshot exists, returns false.
     * </p>
     * 
     * @param stepNumber the target step number
     * @return true if a suitable snapshot was found and jumped to
     */
    public boolean jumpToStep(long stepNumber) {
        List<ExecutionSnapshot> snapshots = getExecutionHistory();
        
        // Find the snapshot at or before the target step
        int targetIndex = -1;
        for (int i = snapshots.size() - 1; i >= 0; i--) {
            if (snapshots.get(i).getStepNumber() <= stepNumber) {
                targetIndex = i;
                break;
            }
        }
        
        if (targetIndex >= 0) {
            currentSnapshotIndex = targetIndex;
            return true;
        }
        
        return false;
    }

    /**
     * Resets navigation to the most recent snapshot.
     * 
     * <p>
     * This effectively "returns to the present" after navigating backward through history.
     * </p>
     */
    public void resetToPresent() {
        List<ExecutionSnapshot> snapshots = getExecutionHistory();
        if (!snapshots.isEmpty()) {
            currentSnapshotIndex = snapshots.size() - 1;
        }
    }

    /**
     * Returns whether there are any recorded snapshots.
     * 
     * @return true if execution history is available
     */
    public boolean hasHistory() {
        return !getExecutionHistory().isEmpty();
    }

    /**
     * Returns whether backward navigation is possible from the current position.
     * 
     * @return true if {@link #stepBackward()} would succeed
     */
    public boolean canStepBackward() {
        List<ExecutionSnapshot> snapshots = getExecutionHistory();
        return !snapshots.isEmpty() && 
               (currentSnapshotIndex < 0 || currentSnapshotIndex > 0);
    }

    /**
     * Returns whether forward navigation is possible from the current position.
     * 
     * @return true if {@link #stepForward()} would succeed
     */
    public boolean canStepForward() {
        List<ExecutionSnapshot> snapshots = getExecutionHistory();
        return !snapshots.isEmpty() && 
               currentSnapshotIndex >= 0 && 
               currentSnapshotIndex < snapshots.size() - 1;
    }

    /**
     * Returns the total number of recorded snapshots.
     * 
     * @return the number of snapshots in execution history
     */
    public int getSnapshotCount() {
        return getExecutionHistory().size();
    }

    /**
     * Returns the current navigation position as an index into the history.
     * 
     * @return the current index, or -1 if at the default (most recent) position
     */
    public int getCurrentPosition() {
        return currentSnapshotIndex;
    }

    @SuppressWarnings("unchecked")
    private <T> T getState() {
        return (T) recorderState;
    }

    @Override
    public String toString() {
        return "TimeTravelController{" +
               "snapshots=" + getSnapshotCount() +
               ", position=" + currentSnapshotIndex +
               '}';
    }
}
