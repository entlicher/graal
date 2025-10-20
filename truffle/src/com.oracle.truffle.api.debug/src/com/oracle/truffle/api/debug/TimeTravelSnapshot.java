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
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.source.SourceSection;

/**
 * Represents a snapshot of execution state at a specific point in time. Used by the back-in-time
 * debugger to record and restore execution state.
 *
 * @since 24.2.0
 */
public final class TimeTravelSnapshot {

    private final long sequenceNumber;
    private final SourceSection sourceSection;
    private final SuspendAnchor suspendAnchor;
    private final List<FrameSnapshot> stackFrames;
    private final long timestamp;

    TimeTravelSnapshot(long sequenceNumber, SourceSection sourceSection, SuspendAnchor suspendAnchor,
                    List<FrameSnapshot> stackFrames) {
        this.sequenceNumber = sequenceNumber;
        this.sourceSection = sourceSection;
        this.suspendAnchor = suspendAnchor;
        this.stackFrames = new ArrayList<>(stackFrames);
        this.timestamp = System.currentTimeMillis();
    }

    long getSequenceNumber() {
        return sequenceNumber;
    }

    /**
     * Returns the source section where this snapshot was taken.
     *
     * @return the source section
     * @since 24.2.0
     */
    public SourceSection getSourceSection() {
        return sourceSection;
    }

    /**
     * Returns the suspend anchor (BEFORE or AFTER) for this snapshot.
     *
     * @return the suspend anchor
     * @since 24.2.0
     */
    public SuspendAnchor getSuspendAnchor() {
        return suspendAnchor;
    }

    /**
     * Returns the stack frames captured in this snapshot.
     *
     * @return list of frame snapshots
     * @since 24.2.0
     */
    public List<FrameSnapshot> getStackFrames() {
        return stackFrames;
    }

    /**
     * Returns the timestamp when this snapshot was taken.
     *
     * @return timestamp in milliseconds
     * @since 24.2.0
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Represents a snapshot of a single stack frame.
     *
     * @since 24.2.0
     */
    public static final class FrameSnapshot {
        private final String name;
        private final SourceSection sourceSection;
        private final Map<String, Object> variables;

        FrameSnapshot(String name, SourceSection sourceSection, Map<String, Object> variables) {
            this.name = name;
            this.sourceSection = sourceSection;
            this.variables = new HashMap<>(variables);
        }

        /**
         * Returns the name of this frame.
         *
         * @return the frame name
         * @since 24.2.0
         */
        public String getName() {
            return name;
        }

        /**
         * Returns the source section for this frame.
         *
         * @return the source section
         * @since 24.2.0
         */
        public SourceSection getSourceSection() {
            return sourceSection;
        }

        /**
         * Returns the variables captured in this frame.
         *
         * @return map of variable names to values
         * @since 24.2.0
         */
        public Map<String, Object> getVariables() {
            return variables;
        }
    }
}
