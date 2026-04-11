// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.debug;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import io.agents.pokeclaw.ui.chat.ComposeChatActivity;
import io.agents.pokeclaw.utils.XLog;

/**
 * Debug broadcast receiver to trigger tasks via ADB without manual UI interaction.
 *
 * Usage:
 *   adb shell am broadcast -a io.agents.pokeclaw.TASK --es task "send hi to Mom on WhatsApp" -p io.agents.pokeclaw
 *   adb shell am broadcast -a io.agents.pokeclaw.TASK --es chat "read my clipboard and explain what it says" -p io.agents.pokeclaw
 *
 * Launches ComposeChatActivity with the matching extra — works even after reinstall.
 */
public class TaskTriggerReceiver extends BroadcastReceiver {

    private static final String TAG = "TaskTriggerReceiver";
    public static final String ACTION = "io.agents.pokeclaw.TASK";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!io.agents.pokeclaw.BuildConfig.DEBUG) return;
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        String task = intent.getStringExtra("task");
        String chat = intent.getStringExtra("chat");
        boolean hasTask = task != null && !task.isEmpty();
        boolean hasChat = chat != null && !chat.isEmpty();
        if (!hasTask && !hasChat) {
            XLog.w(TAG, "Received broadcast with no task extra");
            return;
        }
        XLog.i(TAG, hasTask ? "Received task via broadcast: " + task : "Received chat via broadcast: " + chat);

        // Auto-reply commands: "autoreply on Mom" / "autoreply off"
        if (hasTask && task.startsWith("autoreply ")) {
            String cmd = task.substring(10).trim();
            if (cmd.equals("off")) {
                io.agents.pokeclaw.service.AutoReplyManager.getInstance().stopAll();
                XLog.i(TAG, "Auto-reply disabled");
            } else if (cmd.startsWith("on ")) {
                String target = cmd.substring(3).trim();
                String lowerTarget = target.toLowerCase();
                if (lowerTarget.contains(" on whatsapp")
                        || lowerTarget.contains(" on telegram")
                        || lowerTarget.contains(" on messages")
                        || lowerTarget.contains(" on line")
                        || lowerTarget.contains(" on wechat")) {
                    task = "monitor " + target;
                } else {
                    task = "monitor " + target + " on WhatsApp";
                }
                XLog.i(TAG, "Rewriting debug auto-reply command to task flow: " + task);
            }
        }

        // Launch ComposeChatActivity with task/chat extra — this always works
        Intent launch = new Intent(context, ComposeChatActivity.class);
        if (hasTask) {
            launch.putExtra("task", task);
        }
        if (hasChat) {
            launch.putExtra("chat", chat);
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(launch);
    }
}
