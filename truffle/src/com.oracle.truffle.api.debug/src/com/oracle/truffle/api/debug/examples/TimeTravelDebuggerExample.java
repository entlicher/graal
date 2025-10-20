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
package com.oracle.truffle.api.debug.examples;

import java.util.List;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.debug.TimeTravelSnapshot;

/**
 * Example demonstrating the back-in-time debugger capabilities.
 * 
 * This example shows how to:
 * <ul>
 * <li>Enable time-travel recording</li>
 * <li>Step through code and record execution history</li>
 * <li>Examine recorded snapshots</li>
 * <li>Navigate backward through execution history</li>
 * </ul>
 */
public class TimeTravelDebuggerExample {

    public static void main(String[] args) {
        // Create a simple program to debug
        String code = """
            function calculateSum(n) {
                let sum = 0;
                for (let i = 1; i <= n; i++) {
                    sum = sum + i;
                }
                return sum;
            }
            
            calculateSum(5);
            """;

        try (Context context = Context.create()) {
            // Get the debugger instance
            Debugger debugger = context.getEngine().getInstruments()
                    .get("debugger")
                    .lookup(Debugger.class);

            // Create a debugging session with a callback
            try (DebuggerSession session = debugger.startSession((SuspendedEvent event) -> {
                System.out.println("Suspended at: " + event.getSourceSection());
                
                // Print current stack frame info
                System.out.println("  Top frame: " + event.getTopStackFrame().getName());
                
                // Continue stepping
                event.prepareStepInto(1);
            })) {

                // Enable time-travel recording
                session.setTimeTravelEnabled(true);
                System.out.println("Time-travel debugging enabled: " + session.isTimeTravelEnabled());

                // Create and load the source
                Source source = Source.newBuilder("js", code, "example.js").build();

                // Set a breakpoint at the function entry
                Breakpoint breakpoint = Breakpoint.newBuilder(source).lineIs(2).build();
                session.install(breakpoint);

                // Start execution - this will suspend at the breakpoint
                session.suspendNextExecution();
                context.eval(source);

                // After execution completes, examine the recorded history
                System.out.println("\n=== Execution History ===");
                System.out.println("Total snapshots recorded: " + session.getHistorySize());
                System.out.println("Current position: " + session.getHistoryPosition());

                List<TimeTravelSnapshot> history = session.getExecutionHistory();
                for (int i = 0; i < Math.min(history.size(), 10); i++) {
                    TimeTravelSnapshot snapshot = history.get(i);
                    System.out.println("\nSnapshot " + i + ":");
                    System.out.println("  Location: " + snapshot.getSourceSection());
                    System.out.println("  Anchor: " + snapshot.getSuspendAnchor());
                    System.out.println("  Timestamp: " + snapshot.getTimestamp());
                    
                    if (!snapshot.getStackFrames().isEmpty()) {
                        TimeTravelSnapshot.FrameSnapshot topFrame = snapshot.getStackFrames().get(0);
                        System.out.println("  Top Frame: " + topFrame.getName());
                        System.out.println("  Variables: " + topFrame.getVariables());
                    }
                }

                // Demonstrate stepping backward capability
                if (session.canStepBackward()) {
                    System.out.println("\nCan step backward: true");
                    System.out.println("Previous states are available for inspection");
                }

                // Show how to clear history
                session.clearExecutionHistory();
                System.out.println("\nHistory cleared. Size is now: " + session.getHistorySize());
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
