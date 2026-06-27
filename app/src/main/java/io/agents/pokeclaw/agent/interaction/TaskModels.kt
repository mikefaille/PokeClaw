package io.agents.pokeclaw.agent.interaction

// Filled out models for task interaction
interface TaskState {
    val currentIteration: Int
    val hasError: Boolean
}

class DefaultTaskState(
    override val currentIteration: Int = 0,
    override val hasError: Boolean = false
) : TaskState

interface TaskRuntime {
    fun canContinue(): Boolean
    val state: TaskState
}

class DefaultTaskRuntime(
    private val maxIterations: Int = 10
) : TaskRuntime {
    private var iterationCount = 0
    private var errorOccurred = false

    override val state: TaskState
        get() = DefaultTaskState(iterationCount, errorOccurred)

    override fun canContinue(): Boolean {
        iterationCount++
        return iterationCount <= maxIterations && !errorOccurred
    }

    fun reportError() {
        errorOccurred = true
    }
}
