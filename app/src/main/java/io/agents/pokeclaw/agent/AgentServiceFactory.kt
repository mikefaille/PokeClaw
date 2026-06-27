// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent

object AgentServiceFactory {

    @JvmStatic
    fun create(provider: LlmProvider? = null): AgentService {
        return if (provider == LlmProvider.GEMINI_UNIFIED) {
            UnifiedAgentService()
        } else {
            DefaultAgentService()
        }
    }
}
