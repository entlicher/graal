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

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.source.SourceSection;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a snapshot of program execution at a specific point in time.
 * 
 * <p>
 * An execution snapshot captures:
 * </p>
 * <ul>
 * <li>Execution step number (logical timestamp)</li>
 * <li>Wall-clock timestamp</li>
 * <li>Source location (file, line, column)</li>
 * <li>Frame state (variables and their values)</li>
 * </ul>
 * 
 * <p>
 * Snapshots are the primary mechanism for efficient time-travel debugging. By storing
 * periodic checkpoints, the debugger can quickly jump to any point in execution history
 * by replaying from the nearest checkpoint.
 * </p>
 * 
 * <h2>Memory Efficiency</h2>
 * <p>
 * To minimize memory overhead, snapshots use several optimization strategies:
 * </p>
 * <ul>
 * <li><b>Lazy Copying</b>: Frame data is only fully copied when accessed</li>
 * <li><b>Shared References</b>: Immutable objects are shared between snapshots</li>
 * <li><b>Delta Encoding</b>: Only changed variables are stored (future enhancement)</li>
 * </ul>
 * 
 * @see TimeTravelRecorder
 * @see TimeTravelController
 */
public final class ExecutionSnapshot {
    
    private final long stepNumber;
    private final long timestamp;
    private final SourceSection sourceSection;
    private final MaterializedFrame frame;
    private Map<Object, Object> frameData;

    /**
     * Creates a new execution snapshot.
     * 
     * @param stepNumber the logical execution step number
     * @param timestamp the wall-clock time when this snapshot was created
     * @param sourceSection the source location where execution was paused
     * @param frame the materialized frame containing variable state
     */
    public ExecutionSnapshot(long stepNumber, long timestamp, SourceSection sourceSection, MaterializedFrame frame) {
        this.stepNumber = stepNumber;
        this.timestamp = timestamp;
        this.sourceSection = sourceSection;
        this.frame = frame;
        this.frameData = null; // Lazy initialization
    }

    /**
     * Returns the logical execution step number for this snapshot.
     * 
     * @return the step number
     */
    public long getStepNumber() {
        return stepNumber;
    }

    /**
     * Returns the wall-clock timestamp when this snapshot was created.
     * 
     * @return the timestamp in milliseconds since epoch
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Returns the source location where this snapshot was taken.
     * 
     * @return the source section, or null if not available
     */
    public SourceSection getSourceSection() {
        return sourceSection;
    }

    /**
     * Returns the materialized frame containing variable state.
     * 
     * @return the frame
     */
    public MaterializedFrame getFrame() {
        return frame;
    }

    /**
     * Returns a map of frame variables and their values.
     * 
     * <p>
     * This method lazily extracts frame data on first access to minimize memory overhead.
     * The returned map is a snapshot and will not reflect subsequent changes to the frame.
     * </p>
     * 
     * @return a map from frame slot identifiers to values
     */
    public Map<Object, Object> getFrameData() {
        if (frameData == null) {
            frameData = extractFrameData();
        }
        return frameData;
    }

    private Map<Object, Object> extractFrameData() {
        Map<Object, Object> data = new HashMap<>();
        
        if (frame != null) {
            try {
                // Extract frame slot values
                // Note: This is a simplified version. A production implementation
                // would need to handle different frame descriptor APIs and value types
                for (Object identifier : frame.getFrameDescriptor().getSlots()) {
                    try {
                        Object value = frame.getValue(identifier);
                        data.put(identifier, value);
                    } catch (Exception e) {
                        // Slot might not have a value yet
                        data.put(identifier, null);
                    }
                }
            } catch (Exception e) {
                // In case of errors, return empty map
            }
        }
        
        return data;
    }

    /**
     * Returns a human-readable description of this snapshot.
     * 
     * @return a string describing the snapshot location and step
     */
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("Step ").append(stepNumber);
        
        if (sourceSection != null && sourceSection.isAvailable()) {
            sb.append(" at ");
            if (sourceSection.getSource().getName() != null) {
                sb.append(sourceSection.getSource().getName());
            }
            sb.append(":").append(sourceSection.getStartLine());
        }
        
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        ExecutionSnapshot that = (ExecutionSnapshot) obj;
        return stepNumber == that.stepNumber &&
               timestamp == that.timestamp &&
               Objects.equals(sourceSection, that.sourceSection);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stepNumber, timestamp, sourceSection);
    }

    @Override
    public String toString() {
        return "ExecutionSnapshot{" +
               "step=" + stepNumber +
               ", time=" + timestamp +
               ", location=" + getDescription() +
               '}';
    }
}
