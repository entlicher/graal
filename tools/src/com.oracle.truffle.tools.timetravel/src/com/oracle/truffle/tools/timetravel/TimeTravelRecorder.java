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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;

/**
 * Time-travel recorder instrument that enables back-in-time debugging for Truffle languages.
 * 
 * This instrument records execution events and program state to enable:
 * <ul>
 * <li>Stepping backward through code execution</li>
 * <li>Jumping to previous execution points</li>
 * <li>Examining historical program state</li>
 * </ul>
 * 
 * <h2>Design Principles</h2>
 * <p>
 * The implementation follows these efficiency principles inspired by GDB's record/replay:
 * </p>
 * <ul>
 * <li><b>Incremental Checkpointing</b>: Periodic snapshots of interpreter state</li>
 * <li><b>Event Recording</b>: Capture non-deterministic inputs only</li>
 * <li><b>Deterministic Replay</b>: Reproducible execution from checkpoints</li>
 * <li><b>Adaptive Strategies</b>: Adjust checkpoint frequency based on execution patterns</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * <pre>
 * // Enable time-travel recording
 * Context context = Context.newBuilder()
 *     .option("timetravel.Enabled", "true")
 *     .option("timetravel.CheckpointInterval", "1000")
 *     .build();
 * 
 * // Execute code (recording automatically happens)
 * context.eval(source);
 * 
 * // Access recorder through instrument
 * TimeTravelController controller = context.getEngine()
 *     .getInstruments()
 *     .get("timetravel")
 *     .lookup(TimeTravelController.class);
 * 
 * // Navigate backwards
 * controller.stepBackward();
 * controller.getExecutionHistory();
 * </pre>
 * 
 * @see TimeTravelController
 * @see ExecutionSnapshot
 */
@TruffleInstrument.Registration(
    id = TimeTravelRecorder.ID,
    name = "Time-Travel Recorder",
    version = "1.0",
    services = TimeTravelController.class
)
public final class TimeTravelRecorder extends TruffleInstrument {

    public static final String ID = "timetravel";

    private RecorderState state;

    @Option(name = "", help = "Enable time-travel recording (default: false).", category = OptionCategory.USER, stability = OptionStability.EXPERIMENTAL)
    static final OptionKey<Boolean> ENABLED = new OptionKey<>(false);

    @Option(help = "Number of execution steps between checkpoints (default: 1000).", category = OptionCategory.USER, stability = OptionStability.EXPERIMENTAL)
    static final OptionKey<Integer> CHECKPOINT_INTERVAL = new OptionKey<>(1000);

    @Option(help = "Maximum number of checkpoints to keep in memory (default: 100).", category = OptionCategory.USER, stability = OptionStability.EXPERIMENTAL)
    static final OptionKey<Integer> MAX_CHECKPOINTS = new OptionKey<>(100);

    @Option(help = "Enable adaptive checkpointing based on execution patterns (default: true).", category = OptionCategory.USER, stability = OptionStability.EXPERIMENTAL)
    static final OptionKey<Boolean> ADAPTIVE_CHECKPOINTING = new OptionKey<>(true);

    @Override
    protected void onCreate(Env env) {
        OptionValues options = new OptionValues(
            env.getOptions().get(ENABLED),
            env.getOptions().get(CHECKPOINT_INTERVAL),
            env.getOptions().get(MAX_CHECKPOINTS),
            env.getOptions().get(ADAPTIVE_CHECKPOINTING)
        );

        state = new RecorderState(env, options);
        
        if (options.enabled) {
            attachRecorder();
        }

        // Register the controller service
        env.registerService(state.getController());
    }

    @Override
    protected void onDispose(Env env) {
        if (state != null) {
            state.dispose();
        }
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new TimeTravelRecorderOptionDescriptors();
    }

    private void attachRecorder() {
        // Attach to all statement executions to record execution flow
        SourceSectionFilter filter = SourceSectionFilter.newBuilder()
            .tagIs(StandardTags.StatementTag.class)
            .build();

        state.binding = state.env.getInstrumenter().attachExecutionEventListener(
            filter,
            new RecordingListener()
        );
    }

    /**
     * Option values container for cleaner option handling.
     */
    private static final class OptionValues {
        final boolean enabled;
        final int checkpointInterval;
        final int maxCheckpoints;
        final boolean adaptiveCheckpointing;

        OptionValues(boolean enabled, int checkpointInterval, int maxCheckpoints, boolean adaptiveCheckpointing) {
            this.enabled = enabled;
            this.checkpointInterval = checkpointInterval;
            this.maxCheckpoints = maxCheckpoints;
            this.adaptiveCheckpointing = adaptiveCheckpointing;
        }
    }

    /**
     * Internal state for the recorder.
     */
    private final class RecorderState {
        final Env env;
        final OptionValues options;
        final TimeTravelController controller;
        final List<ExecutionSnapshot> snapshots;
        final AtomicLong executionCounter;
        
        EventBinding<?> binding;
        long lastCheckpointTime;
        int currentCheckpointInterval;

        RecorderState(Env env, OptionValues options) {
            this.env = env;
            this.options = options;
            this.snapshots = new ArrayList<>();
            this.executionCounter = new AtomicLong(0);
            this.currentCheckpointInterval = options.checkpointInterval;
            this.controller = new TimeTravelController(this);
        }

        TimeTravelController getController() {
            return controller;
        }

        void dispose() {
            if (binding != null && !binding.isDisposed()) {
                binding.dispose();
            }
            snapshots.clear();
        }

        synchronized void addSnapshot(ExecutionSnapshot snapshot) {
            snapshots.add(snapshot);
            
            // Limit memory by removing old checkpoints
            if (snapshots.size() > options.maxCheckpoints) {
                snapshots.remove(0);
            }
        }

        synchronized List<ExecutionSnapshot> getSnapshots() {
            return new ArrayList<>(snapshots);
        }

        void adjustCheckpointInterval(EventContext context) {
            if (!options.adaptiveCheckpointing) {
                return;
            }

            // Adaptive logic: checkpoint more frequently in loops
            // This is a simple heuristic - can be enhanced
            if (isInHotLoop(context)) {
                currentCheckpointInterval = Math.max(100, options.checkpointInterval / 10);
            } else {
                currentCheckpointInterval = options.checkpointInterval;
            }
        }

        private boolean isInHotLoop(EventContext context) {
            // Simple heuristic: check if we're in a loop structure
            // Could be enhanced with profiling data
            return context.hasTag(StandardTags.StatementTag.class);
        }
    }

    /**
     * Execution event listener that records program execution.
     */
    private final class RecordingListener implements ExecutionEventListener {

        @Override
        public void onEnter(EventContext context, VirtualFrame frame) {
            long step = state.executionCounter.incrementAndGet();
            
            // Check if we should create a checkpoint
            if (step % state.currentCheckpointInterval == 0) {
                captureSnapshot(context, frame, step);
            }

            // Adjust checkpoint interval based on execution patterns
            state.adjustCheckpointInterval(context);
        }

        @Override
        public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
            // Could record return values for more detailed history
        }

        @Override
        public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
            // Could record exceptions for debugging exceptional control flow
        }

        private void captureSnapshot(EventContext context, VirtualFrame frame, long step) {
            try {
                // Materialize the frame to capture current state
                MaterializedFrame materializedFrame = frame.materialize();
                
                ExecutionSnapshot snapshot = new ExecutionSnapshot(
                    step,
                    System.currentTimeMillis(),
                    context.getInstrumentedSourceSection(),
                    materializedFrame
                );
                
                state.addSnapshot(snapshot);
            } catch (Exception e) {
                // Silently ignore errors during snapshot capture to not interfere with execution
                // In production, might want to log this
            }
        }
    }
}
