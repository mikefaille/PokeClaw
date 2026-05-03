// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent

import io.agents.pokeclaw.service.ClawAccessibilityService
import io.agents.pokeclaw.utils.XLog
import java.util.ArrayDeque

/**
 * Finite State Machine (FSM) Error Recovery for PokeClaw.
 *
 * Tracks a stack of UI states (represented by screen hashes and/or node counts).
 * If the agent gets stuck, this manager attempts autonomous recovery by
 * triggering inverse actions (e.g., pressing "Back") to return to a
 * previously stable state, rather than relying solely on the LLM to guess.
 */
class FsmRecoveryManager {

    data class UiState(
        val screenHash: Int
    )

    private val stateStack = ArrayDeque<UiState>()
    private var consecutiveRollbacks = 0
    private var lastRollbackTime = 0L

    companion object {
        private const val TAG = "FsmRecoveryManager"
        private const val MAX_STACK_SIZE = 15
        private const val ROLLBACK_COOLDOWN_MS = 2000L
    }

    /**
     * Records a new UI state before an action is taken.
     */
    fun recordState(screenHash: Int) {
        val top = stateStack.peekLast()
        if (top == null || top.screenHash != screenHash) {
            // Only reset if we reached a state we haven't seen in the stack (making actual forward progress)
            if (stateStack.none { it.screenHash == screenHash }) {
                consecutiveRollbacks = 0
            }

            // New state
            stateStack.addLast(UiState(screenHash))
            if (stateStack.size > MAX_STACK_SIZE) {
                stateStack.removeFirst()
            }
        }
    }

    /**
     * Checks if we need to perform an autonomous rollback and executes it if necessary.
     * Returns a string describing the action taken, or null if no action was taken.
     */
    fun checkAndRollback(stuckDetectionSignal: StuckDetector.Signal?): String? {
        if (stuckDetectionSignal == null) return null

        val now = System.currentTimeMillis()
        if (now - lastRollbackTime < ROLLBACK_COOLDOWN_MS) {
            XLog.d(TAG, "Rollback cooldown active, skipping")
            return null
        }

        XLog.w(TAG, "Stuck state detected (${stuckDetectionSignal.description}). Attempting FSM recovery.")

        val service = ClawAccessibilityService.getInstance()
        if (service == null) {
            XLog.w(TAG, "ClawAccessibilityService not available for FSM rollback.")
            return null
        }

        // Try to go back
        return tryRollback(service)
    }

    private fun tryRollback(service: ClawAccessibilityService): String {
        consecutiveRollbacks++
        lastRollbackTime = System.currentTimeMillis()

        // If we are rolling back too many times consecutively, try home
        if (consecutiveRollbacks >= 3) {
            XLog.w(TAG, "Multiple rollbacks failed to unstick. Pressing Home.")
            service.pressHome()
            stateStack.clear()
            consecutiveRollbacks = 0
            return "[FSM Recovery] Autonomous intervention: Executed 'Home' action because the agent was stuck in a deep navigation loop. You are now on the home screen. Please re-evaluate and try a different strategy."
        }

        XLog.i(TAG, "Executing 'Back' action for FSM recovery.")
        service.pressBack()

        // Pop the current state off the stack
        if (stateStack.isNotEmpty()) {
            stateStack.removeLast()
        }

        return "[FSM Recovery] Autonomous intervention: Automatically pressed 'Back' to revert to the previous stable state because the last actions failed to progress. Please try a completely different path or tool."
    }

    fun reset() {
        stateStack.clear()
        consecutiveRollbacks = 0
        lastRollbackTime = 0L
    }
}
