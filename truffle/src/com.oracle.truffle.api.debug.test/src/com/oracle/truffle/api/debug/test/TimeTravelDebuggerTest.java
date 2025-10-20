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
package com.oracle.truffle.api.debug.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.polyglot.Source;
import org.junit.Test;

import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.debug.TimeTravelSnapshot;

/**
 * Tests for back-in-time debugging functionality.
 */
public class TimeTravelDebuggerTest extends AbstractDebugTest {

    @Test
    public void testTimeTravelRecording() {
        final Source source = testSource("ROOT(\n" +
                        "  DEFINE(foo, \n" +
                        "    STATEMENT(CONSTANT(1)),\n" +
                        "    STATEMENT(CONSTANT(2)),\n" +
                        "    STATEMENT(CONSTANT(3))\n" +
                        "  ),\n" +
                        "  CALL(foo)\n" +
                        ")\n");

        try (DebuggerSession session = startSession()) {
            // Enable time travel recording
            session.setTimeTravelEnabled(true);
            assertTrue(session.isTimeTravelEnabled());
            
            session.suspendNextExecution();
            startEval(source);

            final AtomicInteger suspendCount = new AtomicInteger(0);
            
            // Step through and count suspensions
            expectSuspended((SuspendedEvent event) -> {
                suspendCount.incrementAndGet();
                event.prepareStepInto(1);
            });
            
            expectSuspended((SuspendedEvent event) -> {
                suspendCount.incrementAndGet();
                event.prepareStepInto(1);
            });
            
            expectSuspended((SuspendedEvent event) -> {
                suspendCount.incrementAndGet();
                event.prepareContinue();
            });

            expectDone();
            
            // Check that snapshots were recorded
            assertTrue("Expected some suspensions", suspendCount.get() > 0);
            assertTrue("Expected history to be recorded", session.getHistorySize() > 0);
            assertEquals("History size should match suspension count", 
                        suspendCount.get(), session.getHistorySize());
        }
    }

    @Test
    public void testTimeTravelDisabled() {
        final Source source = testSource("ROOT(\n" +
                        "  STATEMENT(CONSTANT(1)),\n" +
                        "  STATEMENT(CONSTANT(2))\n" +
                        ")\n");

        try (DebuggerSession session = startSession()) {
            // Time travel is disabled by default
            assertFalse(session.isTimeTravelEnabled());
            
            session.suspendNextExecution();
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                event.prepareContinue();
            });

            expectDone();
            
            // No history should be recorded when disabled
            assertEquals("Expected no history when disabled", 0, session.getHistorySize());
        }
    }

    @Test
    public void testSnapshotCapture() {
        final Source source = testSource("ROOT(\n" +
                        "  DEFINE(foo, \n" +
                        "    STATEMENT(CONSTANT(42))\n" +
                        "  ),\n" +
                        "  CALL(foo)\n" +
                        ")\n");

        try (DebuggerSession session = startSession()) {
            session.setTimeTravelEnabled(true);
            session.suspendNextExecution();
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                event.prepareContinue();
            });

            expectDone();
            
            // Check snapshot details
            List<TimeTravelSnapshot> history = session.getExecutionHistory();
            assertFalse("Expected at least one snapshot", history.isEmpty());
            
            TimeTravelSnapshot snapshot = history.get(0);
            assertNotNull("Snapshot should have source section", snapshot.getSourceSection());
            assertNotNull("Snapshot should have suspend anchor", snapshot.getSuspendAnchor());
            assertNotNull("Snapshot should have stack frames", snapshot.getStackFrames());
            assertTrue("Snapshot timestamp should be positive", snapshot.getTimestamp() > 0);
        }
    }

    @Test
    public void testCanStepBackward() {
        final Source source = testSource("ROOT(\n" +
                        "  STATEMENT(CONSTANT(1)),\n" +
                        "  STATEMENT(CONSTANT(2)),\n" +
                        "  STATEMENT(CONSTANT(3))\n" +
                        ")\n");

        try (DebuggerSession session = startSession()) {
            session.setTimeTravelEnabled(true);
            
            // Initially cannot step backward
            assertFalse(session.canStepBackward());
            
            session.suspendNextExecution();
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                // Still cannot step backward at first suspension
                assertFalse(session.canStepBackward());
                event.prepareStepInto(1);
            });
            
            expectSuspended((SuspendedEvent event) -> {
                // Now we should be able to step backward
                assertTrue("Should be able to step backward after first step", 
                          session.canStepBackward());
                event.prepareContinue();
            });

            expectDone();
        }
    }

    @Test
    public void testClearHistory() {
        final Source source = testSource("ROOT(\n" +
                        "  STATEMENT(CONSTANT(1)),\n" +
                        "  STATEMENT(CONSTANT(2))\n" +
                        ")\n");

        try (DebuggerSession session = startSession()) {
            session.setTimeTravelEnabled(true);
            session.suspendNextExecution();
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                event.prepareContinue();
            });

            expectDone();
            
            // Verify history was recorded
            assertTrue("Expected history to be recorded", session.getHistorySize() > 0);
            
            // Clear history
            session.clearExecutionHistory();
            
            // Verify history is cleared
            assertEquals("Expected empty history after clear", 0, session.getHistorySize());
            assertFalse("Should not be able to step backward after clear", 
                       session.canStepBackward());
        }
    }

    @Test
    public void testHistoryPosition() {
        final Source source = testSource("ROOT(\n" +
                        "  STATEMENT(CONSTANT(1)),\n" +
                        "  STATEMENT(CONSTANT(2)),\n" +
                        "  STATEMENT(CONSTANT(3))\n" +
                        ")\n");

        try (DebuggerSession session = startSession()) {
            session.setTimeTravelEnabled(true);
            session.suspendNextExecution();
            startEval(source);

            final AtomicInteger expectedPosition = new AtomicInteger(0);
            
            expectSuspended((SuspendedEvent event) -> {
                assertEquals("Position should match", expectedPosition.get(), 
                           session.getHistoryPosition());
                expectedPosition.incrementAndGet();
                event.prepareStepInto(1);
            });
            
            expectSuspended((SuspendedEvent event) -> {
                assertEquals("Position should match", expectedPosition.get(), 
                           session.getHistoryPosition());
                event.prepareContinue();
            });

            expectDone();
        }
    }

    @Test
    public void testEnableDisableRecording() {
        final Source source = testSource("ROOT(\n" +
                        "  STATEMENT(CONSTANT(1)),\n" +
                        "  STATEMENT(CONSTANT(2)),\n" +
                        "  STATEMENT(CONSTANT(3))\n" +
                        ")\n");

        try (DebuggerSession session = startSession()) {
            // Start disabled
            assertFalse(session.isTimeTravelEnabled());
            
            session.suspendNextExecution();
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                // Enable recording mid-execution
                session.setTimeTravelEnabled(true);
                assertTrue(session.isTimeTravelEnabled());
                event.prepareStepInto(1);
            });
            
            expectSuspended((SuspendedEvent event) -> {
                // This suspension should be recorded
                event.prepareContinue();
            });

            expectDone();
            
            // Should have recorded the second suspension
            assertTrue("Expected at least one snapshot", session.getHistorySize() > 0);
        }
    }
}
