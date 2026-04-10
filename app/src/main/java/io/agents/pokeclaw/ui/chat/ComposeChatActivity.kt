// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.chat

import io.agents.pokeclaw.TaskEvent
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import io.agents.pokeclaw.agent.llm.ModelConfigRepository
import io.agents.pokeclaw.appViewModel
import io.agents.pokeclaw.floating.FloatingCircleManager
import io.agents.pokeclaw.service.ClawAccessibilityService
import io.agents.pokeclaw.ui.settings.LlmConfigActivity
import io.agents.pokeclaw.ui.settings.SettingsActivity
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import java.util.concurrent.Executors

/**
 * PokeClaw Chat Activity — Compose shell for the chat screen.
 *
 * Chat runtime ownership lives in [ChatSessionController].
 * This activity keeps lifecycle wiring, task flows, and sidebar/history UI state.
 */
class ComposeChatActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ComposeChatActivity"
    }

    private var conversationId = "chat_${System.currentTimeMillis()}"
    private val executor = Executors.newSingleThreadExecutor()

    // Compose state — observed by ChatScreen
    private val _messages = mutableStateListOf<ChatMessage>()
    private val _modelStatus = mutableStateOf("No model loaded")
    private val _needsPermission = mutableStateOf(false)
    private val _isProcessing = mutableStateOf(false)   // True ONLY when a chat/task is actively running
    private val _inputEnabled = mutableStateOf(true)    // False when model not ready (no task running)
    private val _conversations = mutableStateListOf<ChatHistoryManager.ConversationSummary>()
    private val _isDownloading = mutableStateOf(false)
    private val _downloadProgress = mutableStateOf(0)

    // Session-level token tracking for chat mode
    private val _sessionTokens = mutableStateOf(0)
    private val _sessionCost = mutableStateOf(0.0)

    private val chatSessionController by lazy {
        ChatSessionController(
            activity = this,
            executor = executor,
            uiState = ChatSessionUiState(
                messages = _messages,
                modelStatus = _modelStatus,
                isProcessing = _isProcessing,
                inputEnabled = _inputEnabled,
                isDownloading = _isDownloading,
                downloadProgress = _downloadProgress,
                sessionTokens = _sessionTokens,
                sessionCost = _sessionCost,
            ),
            onPersistConversation = { saveChat() },
            onRefreshSidebarHistory = { loadSidebarHistory() }
        )
    }

    // Permission polling
    private val permHandler = Handler(Looper.getMainLooper())
    private val permPoller = object : Runnable {
        override fun run() {
            _needsPermission.value = !ClawAccessibilityService.isRunning()
            permHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        // Hide floating circle only when no task is running
        // (task running = keep floating pill visible for step/token status)
        try {
            if (!appViewModel.isTaskRunning()) {
                FloatingCircleManager.hide()
            } else {
                XLog.d(TAG, "onCreate: task running, keeping floating circle visible")
            }
        } catch (_: Exception) {}

        // Check for updates
        io.agents.pokeclaw.utils.UpdateChecker.checkForUpdate(this)

        // Status bar color
        val themeColors = ThemeManager.getColors()
        window.statusBarColor = themeColors.toolbarBg

        // Build Compose colors from ThemeManager
        val composeColors = with(ThemeManager) { themeColors.toComposeColors() }

        setContent {
            // Poll AutoReplyManager state every 2 seconds
            val autoReplyManager = io.agents.pokeclaw.service.AutoReplyManager.getInstance()
            var activeTasks by remember { mutableStateOf(listOf<String>()) }
            LaunchedEffect(Unit) {
                while (true) {
                    activeTasks = if (autoReplyManager.isEnabled) {
                        autoReplyManager.monitoredContacts.map { it.replaceFirstChar { c -> c.uppercase() } }
                    } else {
                        emptyList()
                    }
                    kotlinx.coroutines.delay(2000)
                }
            }

            ChatScreen(
                messages = _messages.toList(),
                modelStatus = _modelStatus.value,
                needsPermission = _needsPermission.value,
                isProcessing = _isProcessing.value,
                inputEnabled = _inputEnabled.value,
                isDownloading = _isDownloading.value,
                downloadProgress = _downloadProgress.value,
                isLocalModel = ModelConfigRepository.isLocalActive(),
                sessionTokens = _sessionTokens.value,
                sessionCost = _sessionCost.value,
                onSendChat = { sendChat(it) },
                onSendTask = { sendTask(it) },
                onStartMonitor = { contact -> handleMonitorTask("monitor $contact on WhatsApp") },
                onSendDirectMessage = { contact, app, message ->
                    sendTask("send \"$message\" to $contact on $app")
                },
                onNewChat = { newChat() },
                onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                onOpenModels = { startActivity(Intent(this, LlmConfigActivity::class.java)) },
                onFixPermissions = { startActivity(Intent(this, SettingsActivity::class.java)) },
                onAttach = { Toast.makeText(this, "Image upload coming soon", Toast.LENGTH_SHORT).show() },
                conversations = _conversations.toList(),
                onSelectConversation = { loadConversation(it) },
                onDeleteConversation = { conv ->
                    val deleted = ChatHistoryManager.delete(conv.file)
                    XLog.i(TAG, "Delete conversation: ${conv.file.absolutePath} deleted=$deleted")
                    loadSidebarHistory()
                },
                onRenameConversation = { conv, newName ->
                    val renamed = ChatHistoryManager.rename(conv.file, newName)
                    XLog.i(TAG, "Rename conversation: '${conv.title}' → '$newName' renamed=$renamed")
                    loadSidebarHistory()
                },
                activeTasks = activeTasks,
                onStopTask = { contact ->
                    autoReplyManager.removeContact(contact.lowercase())
                    if (autoReplyManager.monitoredContacts.isEmpty()) {
                        autoReplyManager.isEnabled = false
                    }
                    Toast.makeText(this, "Stopped monitoring $contact", Toast.LENGTH_SHORT).show()
                },
                onStopAllTasks = {
                    // Cancel running agent task
                    var requestedTaskStop = false
                    if (appViewModel.isTaskRunning()) {
                        appViewModel.stopTask()
                        requestedTaskStop = true
                    }
                    // Stop all monitoring
                    var stoppedMonitoring = false
                    if (autoReplyManager.isEnabled) {
                        autoReplyManager.stopAll()
                        stoppedMonitoring = true
                    }
                    val message = when {
                        requestedTaskStop -> "Stopping current task..."
                        stoppedMonitoring -> "All tasks stopped"
                        else -> "No active tasks"
                    }
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                },
                onModelSwitch = { modelId, displayName -> switchModel(modelId, displayName) },
                colors = composeColors,
            )
        }

        loadSidebarHistory()

        // Restore last conversation if Activity was recreated (e.g., system killed it during a task)
        if (_messages.isEmpty()) {
            val savedConvId = KVUtils.getString("CURRENT_CONVERSATION_ID", "")
            if (savedConvId.isNotEmpty()) {
                val convos = ChatHistoryManager.listConversations(this)
                val match = convos.firstOrNull { it.id == savedConvId }
                if (match != null) {
                    conversationId = savedConvId
                    val restored = ChatHistoryManager.load(match.file)
                    if (restored.isNotEmpty()) {
                        _messages.addAll(restored)
                        XLog.i(TAG, "Restored ${restored.size} messages from conversation $savedConvId")
                    }
                }
            }
        }

        chatSessionController.loadModelIfReady()

        // Release local LLM conversation before task starts so the agent can use the engine
        // (LiteRT-LM only supports 1 session at a time)
        appViewModel.onBeforeTask = {
            chatSessionController.releaseForTask()
        }

        // Debug: auto-trigger task from ADB intent
        // Usage: adb shell am start -n io.agents.pokeclaw/.ui.chat.ComposeChatActivity --es task "open my camera"
        intent?.getStringExtra("task")?.let { taskText ->
            XLog.i(TAG, "Auto-task from intent: $taskText")
            // Wait for model to load, then send task
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed(object : Runnable {
                override fun run() {
                    if (chatSessionController.isModelReady()) {
                        sendTask(taskText)
                    } else {
                        handler.postDelayed(this, 1000)
                    }
                }
            }, 2000)
        }

    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle task from broadcast receiver (SINGLE_TOP re-delivery)
        intent.getStringExtra("task")?.let { taskText ->
            XLog.i(TAG, "Task from onNewIntent: $taskText")
            if (chatSessionController.isModelReady()) {
                sendTask(taskText)
            } else {
                val handler = Handler(Looper.getMainLooper())
                handler.postDelayed(object : Runnable {
                    override fun run() {
                        if (chatSessionController.isModelReady()) sendTask(taskText)
                        else handler.postDelayed(this, 1000)
                    }
                }, 1000)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        _needsPermission.value = !ClawAccessibilityService.isRunning()
        loadSidebarHistory()
        permHandler.removeCallbacks(permPoller)
        permHandler.postDelayed(permPoller, 1000)
        chatSessionController.onResume()
    }

    override fun onPause() {
        super.onPause()
        saveChat()
        permHandler.removeCallbacks(permPoller)
        chatSessionController.onPause(conversationId)
    }

    override fun onDestroy() {
        super.onDestroy()
        chatSessionController.onDestroy()
        executor.shutdown()
    }

    // ==================== CHAT ====================

    private fun sendChat(text: String) {
        chatSessionController.sendChat(text)
    }

    private var sendTaskRetryCount = 0

    private fun sendTask(text: String) {
        // Java keyword routing FIRST — monitor/auto-reply don't need accessibility service
        val lower = text.lowercase()
        if (lower.contains("monitor") || lower.contains("auto-reply") || lower.contains("auto reply")
            || lower.contains("watch") && (lower.contains("message") || lower.contains("reply"))) {
            handleMonitorTask(text)
            return
        }

        // Accessibility check — only for tasks that need phone control (agent loop)
        if (!ClawAccessibilityService.isRunning()) {
            if (!ClawAccessibilityService.isEnabledInSettings(this)) {
                // Show Toast first, then navigate to PokeClaw Settings (not Android Settings directly)
                Toast.makeText(this, "Enable Accessibility Service to run tasks", Toast.LENGTH_LONG).show()
                addSystem("⚠️ Task mode needs Accessibility Service enabled. Opening Settings...")
                startActivity(Intent(this, SettingsActivity::class.java))
                sendTaskRetryCount = 0
                return
            }
            // Enabled in settings but service not yet connected — wait briefly
            if (sendTaskRetryCount >= 1) {
                Toast.makeText(this, "Accessibility service not connected. Try toggling it off and on.", Toast.LENGTH_LONG).show()
                addSystem("Accessibility service didn't connect. Try toggling it off and on in Settings.")
                startActivity(Intent(this, SettingsActivity::class.java))
                sendTaskRetryCount = 0
                return
            }
            sendTaskRetryCount++
            addSystem("Accessibility service connecting, please wait...")
            executor.submit {
                val connected = ClawAccessibilityService.awaitRunning(5000)
                runOnUiThread {
                    if (connected) {
                        sendTask(text)
                    } else {
                        Toast.makeText(this, "Accessibility service didn't connect", Toast.LENGTH_LONG).show()
                        addSystem("Accessibility service didn't connect. Go to Settings and toggle it off then on.")
                        sendTaskRetryCount = 0
                    }
                }
            }
            return
        }
        sendTaskRetryCount = 0

        // Ensure notification permission + foreground service for task progress visibility
        ensureNotificationPermission()

        // Reset stuck processing state from previous task
        _isProcessing.value = false

        if (!KVUtils.hasLlmConfig()) {
            Toast.makeText(this, "Configure LLM in Settings first", Toast.LENGTH_LONG).show()
            return
        }

        addUser(text)
        _isProcessing.value = true
        XLog.i(TAG, "sendTask: isProcessing=TRUE")
        _messages.add(ChatMessage(ChatMessage.Role.ASSISTANT, "..."))

        val taskText = text
        val taskId = "task_${System.currentTimeMillis()}"

        // Release chat conversation so task agent can use the engine
        executor.submit {
            try { appViewModel.stopTask(); Thread.sleep(200) } catch (_: Exception) {}
            chatSessionController.prepareForTaskStart()

            runOnUiThread {
                try {
                    appViewModel.startTask(taskText, taskId) { event ->
                        runOnUiThread { handleTaskEvent(event) }
                    }
                } catch (e: Exception) {
                    XLog.e(TAG, "sendTask failed: ${e.message}", e)
                    addSystem("Error: ${e.message}")
                    cleanupAfterTask()
                }
            }
        }
    }

    /** Handle typed events from TaskOrchestrator — no string parsing. */
    private fun handleTaskEvent(event: TaskEvent) {
        try {
            when (event) {
                is TaskEvent.Completed -> {
                    replaceTypingIndicator(event.answer, event.modelName)
                    cleanupAfterTask()
                    checkAutoReplyConfirmation()
                }
                is TaskEvent.Failed -> {
                    replaceTypingIndicator("Error: ${event.error}")
                    cleanupAfterTask()
                }
                is TaskEvent.Cancelled -> {
                    removeTypingIndicator()
                    cleanupAfterTask()
                }
                is TaskEvent.Blocked -> {
                    replaceTypingIndicator("Blocked by system dialog.")
                    cleanupAfterTask()
                }
                is TaskEvent.ToolAction -> {
                    if (!event.toolName.contains("Finish", ignoreCase = true)) {
                        // First real tool action = this is a task, remove typing "..."
                        removeTypingIndicator()
                        addSystem("${event.toolName}...")
                    }
                }
                is TaskEvent.ToolResult -> {
                    if (!event.success) addSystem("${event.toolName} failed")
                }
                is TaskEvent.Response -> {
                    replaceTypingIndicator(event.text)
                }
                is TaskEvent.Progress -> {
                    addSystem(event.description)
                }
                // These are handled by floating button / notification, not chat
                is TaskEvent.LoopStart, is TaskEvent.TokenUpdate, is TaskEvent.Thinking -> { }
            }
        } catch (e: Exception) {
            XLog.w(TAG, "handleTaskEvent error", e)
        }
    }

    /** Replace "..." typing indicator with actual text, or add new message. */
    /**
     * Replace "..." typing indicator with actual response.
     * @param text the response text
     * @param actualModelName the real model that generated this response (from API).
     *                        If null, falls back to parsing _modelStatus (less reliable).
     */
    private fun replaceTypingIndicator(text: String, actualModelName: String? = null) {
        val modelTag = actualModelName
            ?: _modelStatus.value.removePrefix("● ").split(" ·").firstOrNull()?.trim()
            ?: ""
        val idx = _messages.indexOfLast { it.role == ChatMessage.Role.ASSISTANT && it.content == "..." }
        if (idx >= 0) {
            _messages[idx] = ChatMessage(ChatMessage.Role.ASSISTANT, text, modelName = modelTag)
        } else {
            _messages.add(ChatMessage(ChatMessage.Role.ASSISTANT, text, modelName = modelTag))
        }
        saveChat()
    }

    /** Remove "..." typing indicator if it exists. */
    private fun removeTypingIndicator() {
        val idx = _messages.indexOfLast { it.role == ChatMessage.Role.ASSISTANT && it.content == "..." }
        if (idx >= 0) _messages.removeAt(idx)
    }

    /** Clean up state after any task finishes. */
    private fun cleanupAfterTask() {
        XLog.i(TAG, "cleanupAfterTask: isProcessing=FALSE")
        _isProcessing.value = false
        appViewModel.clearTaskCallback()
        Handler(Looper.getMainLooper()).postDelayed({
            try { chatSessionController.loadModelIfReady() } catch (e: Exception) {
                XLog.e(TAG, "cleanupAfterTask: loadModel error", e)
            }
        }, 500)
    }

    /** If auto-reply was enabled by the task, show confirmation and keep the user in PokeClaw. */
    private fun checkAutoReplyConfirmation() {
        val arm = io.agents.pokeclaw.service.AutoReplyManager.getInstance()
        if (!arm.isEnabled) return
        val contacts = arm.monitoredContacts.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }
        addSystem("✓ Auto-reply active for $contacts.\nMonitoring in background — stop from bar above.")
        XLog.i(TAG, "checkAutoReplyConfirmation: monitor active, staying in PokeClaw")
    }

    private fun syncTaskAgentConfig() {
        if (!appViewModel.updateAgentConfig()) {
            XLog.w(TAG, "syncTaskAgentConfig: failed to update task agent config")
        }
    }

    private fun switchModel(modelId: String, displayName: String) {
        chatSessionController.switchModel(modelId, displayName)
        if (modelId != "NONE") {
            syncTaskAgentConfig()
        }
        XLog.i(TAG, "Model switched to: $modelId ($displayName)")
    }

    private fun newChat() {
        saveChat()
        conversationId = "chat_${System.currentTimeMillis()}"
        _messages.clear()
        _sessionTokens.value = 0
        _sessionCost.value = 0.0
        chatSessionController.startNewConversationRuntime()
    }

    private fun loadConversation(conv: ChatHistoryManager.ConversationSummary) {
        saveChat()
        conversationId = conv.id
        _messages.clear()
        val messages = ChatHistoryManager.load(conv.file)
        _messages.addAll(messages)
        chatSessionController.restoreConversationRuntime(conv.id, messages)
    }

    // ==================== MONITOR (Java routing, no LLM) ====================

    /**
     * Handle monitor/auto-reply tasks directly via Java — no LLM needed.
     * Extracts contact name from user input, enables AutoReplyManager,
     * shows confirmation, then goes to home so notifications can fire.
     */
    private fun handleMonitorTask(text: String) {
        val contact = extractContactName(text)
        if (contact.isEmpty()) {
            addUser(text)
            addSystem("Could not figure out who to monitor. Try: \"Monitor Mom on WhatsApp\"")
            return
        }

        addUser(text)

        // Check required permissions before starting monitor
        val needsAccessibility = !ClawAccessibilityService.isRunning()
        val needsNotifAccess = !io.agents.pokeclaw.service.ClawNotificationListener.isConnected()
        if (needsAccessibility || needsNotifAccess) {
            val missing = mutableListOf<String>()
            if (needsAccessibility) missing.add("Accessibility")
            if (needsNotifAccess) missing.add("Notification Access")
            Toast.makeText(this, "Enable ${missing.joinToString(" & ")} in Settings first", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, io.agents.pokeclaw.ui.settings.SettingsActivity::class.java))
            return
        }

        _isProcessing.value = true
        addSystem("Setting up auto-reply for $contact...")

        val arm = io.agents.pokeclaw.service.AutoReplyManager.getInstance()
        arm.addContact(contact)
        arm.setEnabled(true)
        XLog.i(TAG, "handleMonitorTask: enabled auto-reply for '$contact'")

        Handler(Looper.getMainLooper()).postDelayed({
            _isProcessing.value = false
            addSystem("✓ Auto-reply is now active for $contact.\nMonitoring in background — you can stop anytime from the bar above.")
            // Stay in PokeClaw — ClawNotificationListener catches notifications
            // regardless of which app is in foreground. No need to press Home.
            XLog.i(TAG, "handleMonitorTask: monitor active, staying in PokeClaw")
        }, 1500)
    }

    /**
     * Check if PokeClaw's NotificationListenerService is enabled.
     * Required for monitor/auto-reply to detect incoming messages.
     */
    private fun isNotificationListenerEnabled(): Boolean {
        val flat = android.provider.Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        return flat.contains(packageName)
    }

    /**
     * Extract contact name from monitor task text.
     * Handles: "monitor Mom on WhatsApp", "auto-reply to Mom's messages", "watch Mom", etc.
     */
    private fun extractContactName(text: String): String {
        val lower = text.lowercase()
        // Remove known keywords to isolate the contact name
        var cleaned = lower
        val removeWords = listOf(
            "monitoring", "monitor", "auto-reply", "auto reply", "watching", "watch",
            "on whatsapp", "on telegram", "on messages", "on wechat", "on line",
            "messages", "message", "'s", "'s", "for", "from",
            "please", "can you", "start", "enable", "begin", "help me",
        )
        for (word in removeWords) {
            cleaned = cleaned.replace(word, " ")
        }
        // Collapse whitespace and trim
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim()
        // What's left should be the contact name
        return if (cleaned.isNotEmpty()) cleaned.replaceFirstChar { it.uppercase() } else ""
    }

    // ==================== HELPERS ====================

    /**
     * Request notification permission (Android 13+) and start ForegroundService
     * so the user sees task progress in the status bar while PokeClaw is in background.
     */
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        // Start foreground service if not running
        if (!io.agents.pokeclaw.service.ForegroundService.isRunning()) {
            io.agents.pokeclaw.service.ForegroundService.start(this)
        }
    }

    private fun addUser(text: String) { _messages.add(ChatMessage(ChatMessage.Role.USER, text)) }
    private fun addSystem(text: String) { _messages.add(ChatMessage(ChatMessage.Role.SYSTEM, text)) }

    private fun saveChat() {
        val modelName = KVUtils.getLocalModelPath().substringAfterLast('/').substringBeforeLast('.')
        ChatHistoryManager.save(this, conversationId, _messages, modelName)
        // Persist current conversation ID so we can restore on Activity recreation
        KVUtils.putString("CURRENT_CONVERSATION_ID", conversationId)
        loadSidebarHistory()
    }

    private fun loadSidebarHistory() {
        val convos = ChatHistoryManager.listConversations(this)
        _conversations.clear()
        _conversations.addAll(convos)
    }
}
