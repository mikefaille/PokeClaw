#!/bin/bash
find app/src/main/java/io/agents/pokeclaw/agent/interaction -type f -name "*.kt" -exec grep -H "^import" {} +
find app/src/main/java/io/agents/pokeclaw/agent/tools -type f -name "*.kt" -exec grep -H "^import" {} +
