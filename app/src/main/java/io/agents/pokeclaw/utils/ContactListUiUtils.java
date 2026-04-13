// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.utils;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.agents.pokeclaw.service.ClawAccessibilityService;

/**
 * Generic contact/chat-list search helpers.
 *
 * The goal is to avoid app-specific assumptions:
 * - search current screen first
 * - prefer text matches over contentDescription-only matches
 * - scroll multiple times if needed
 * - stop once the screen stops changing
 */
public final class ContactListUiUtils {
    private static final String TAG = "ContactListUiUtils";

    private ContactListUiUtils() {}

    public static boolean scrollAndFindAndClick(
        ClawAccessibilityService service,
        LinkedHashSet<String> normalizedAliases,
        LinkedHashSet<String> digitAliases,
        int maxScrolls,
        long settleMs
    ) throws InterruptedException {
        int attempts = Math.min(Math.max(maxScrolls, 1), 20);
        String lastScreen = safeScreenSnapshot(service);

        for (int attempt = 0; attempt <= attempts; attempt++) {
            AccessibilityNodeInfo root = service.getRootInActiveWindow();
            AccessibilityNodeInfo bestMatch = findBestVisibleContactNode(root, normalizedAliases, digitAliases);
            if (bestMatch != null) {
                XLog.i(TAG, "scrollAndFindAndClick: matched node text=" + bestMatch.getText() + " desc=" + bestMatch.getContentDescription() + " on attempt=" + attempt);
                return service.clickNode(bestMatch);
            }

            if (attempt == attempts || root == null) {
                return false;
            }

            Rect rootBounds = new Rect();
            root.getBoundsInScreen(rootBounds);
            int centerX = rootBounds.centerX();
            int fromY = rootBounds.top + (int) (rootBounds.height() * 0.72f);
            int toY = rootBounds.top + (int) (rootBounds.height() * 0.28f);
            boolean swiped = service.performSwipe(centerX, fromY, centerX, toY, 320);
            XLog.i(TAG, "scrollAndFindAndClick: swipe attempt=" + (attempt + 1) + " result=" + swiped);
            if (!swiped) {
                return false;
            }

            Thread.sleep(settleMs);

            String currentScreen = safeScreenSnapshot(service);
            if (currentScreen != null && currentScreen.equals(lastScreen)) {
                XLog.i(TAG, "scrollAndFindAndClick: screen did not change after scroll, reached end of list");
                return false;
            }
            lastScreen = currentScreen;
        }

        return false;
    }

    public static boolean searchOrScrollAndFindAndClick(
        ClawAccessibilityService service,
        String rawQuery,
        LinkedHashSet<String> normalizedAliases,
        LinkedHashSet<String> digitAliases,
        int maxScrolls,
        long settleMs
    ) throws InterruptedException {
        if (trySearchAndClick(service, rawQuery, normalizedAliases, digitAliases, settleMs)) {
            return true;
        }
        return scrollAndFindAndClick(service, normalizedAliases, digitAliases, maxScrolls, settleMs);
    }

    public static AccessibilityNodeInfo findBestVisibleContactNode(
        AccessibilityNodeInfo root,
        Set<String> normalizedAliases,
        Set<String> digitAliases
    ) {
        if (root == null) return null;

        List<AccessibilityNodeInfo> matches = new ArrayList<>();
        collectNodesWithText(root, normalizedAliases, digitAliases, matches);
        if (matches.isEmpty()) return null;

        AccessibilityNodeInfo bestTextMatch = null;
        AccessibilityNodeInfo bestDescMatch = null;
        int bestTextScore = Integer.MIN_VALUE;
        int bestDescScore = Integer.MIN_VALUE;

        for (AccessibilityNodeInfo node : matches) {
            if (!node.isVisibleToUser()) continue;

            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            int score = scoreCandidate(node, bounds);

            if (ContactMatchUtils.matchesCandidate(
                node.getText() != null ? node.getText().toString() : null,
                normalizedAliases,
                digitAliases
            )) {
                if (score > bestTextScore) {
                    bestTextScore = score;
                    bestTextMatch = node;
                }
            } else if (score > bestDescScore) {
                bestDescScore = score;
                bestDescMatch = node;
            }
        }

        return bestTextMatch != null ? bestTextMatch : bestDescMatch;
    }

    private static int scoreCandidate(AccessibilityNodeInfo node, Rect bounds) {
        int score = 0;
        if (node.isClickable()) score += 20;
        if (node.getText() != null && node.getText().length() > 0) score += 25;
        if (node.getContentDescription() != null && node.getContentDescription().length() > 0) score += 5;
        if (bounds.centerY() > 0) score += Math.min(bounds.centerY() / 10, 80);
        return score;
    }

    private static void collectNodesWithText(
        AccessibilityNodeInfo node,
        Set<String> normalizedAliases,
        Set<String> digitAliases,
        List<AccessibilityNodeInfo> results
    ) {
        if (node == null) return;

        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        if (ContactMatchUtils.matchesTarget(text, desc, normalizedAliases, digitAliases)) {
            results.add(node);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectNodesWithText(child, normalizedAliases, digitAliases, results);
            }
        }
    }

    private static boolean trySearchAndClick(
        ClawAccessibilityService service,
        String rawQuery,
        Set<String> normalizedAliases,
        Set<String> digitAliases,
        long settleMs
    ) throws InterruptedException {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) {
            return false;
        }

        AccessibilityNodeInfo searchField = UiActionMatchUtils.findBestSearchField(root);
        if (searchField == null) {
            AccessibilityNodeInfo searchAction = UiActionMatchUtils.findBestSearchAction(root);
            if (searchAction != null) {
                boolean clicked = service.clickNode(searchAction);
                XLog.i(TAG, "trySearchAndClick: tapped search action, clicked=" + clicked);
                if (clicked) {
                    Thread.sleep(Math.max(settleMs, 500L));
                    root = service.getRootInActiveWindow();
                    searchField = UiActionMatchUtils.findBestSearchField(root);
                }
            }
        }

        if (searchField == null) {
            XLog.i(TAG, "trySearchAndClick: no search field available");
            return false;
        }

        if (!setText(searchField, rawQuery)) {
            XLog.i(TAG, "trySearchAndClick: failed to type query into search field");
            return false;
        }

        Thread.sleep(Math.max(settleMs, 600L));

        root = service.getRootInActiveWindow();
        AccessibilityNodeInfo bestMatch = findBestVisibleResultNode(root, normalizedAliases, digitAliases);
        if (bestMatch != null) {
            XLog.i(TAG, "trySearchAndClick: matched filtered result text=" + bestMatch.getText() + " desc=" + bestMatch.getContentDescription());
            return service.clickNode(bestMatch);
        }

        XLog.i(TAG, "trySearchAndClick: no filtered result matched query");
        return false;
    }

    private static boolean setText(AccessibilityNodeInfo node, String text) {
        if (node == null) return false;
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        clearText(node);

        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        boolean success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        if (!success) return false;

        CharSequence currentText = node.getText();
        return currentText != null && currentText.toString().contains(text);
    }

    private static void clearText(AccessibilityNodeInfo node) {
        Bundle clearArgs = new Bundle();
        clearArgs.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "");
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs);
    }

    private static AccessibilityNodeInfo findBestVisibleResultNode(
        AccessibilityNodeInfo root,
        Set<String> normalizedAliases,
        Set<String> digitAliases
    ) {
        AccessibilityNodeInfo candidate = findBestVisibleContactNode(root, normalizedAliases, digitAliases);
        if (candidate == null) return null;

        CharSequence className = candidate.getClassName();
        String classNameString = className != null ? className.toString() : "";
        if (candidate.isEditable() || classNameString.contains("EditText") || classNameString.contains("AutoComplete")) {
            return null;
        }
        return candidate;
    }

    private static String safeScreenSnapshot(ClawAccessibilityService service) {
        try {
            return service.getScreenTree();
        } catch (Exception e) {
            return null;
        }
    }
}
