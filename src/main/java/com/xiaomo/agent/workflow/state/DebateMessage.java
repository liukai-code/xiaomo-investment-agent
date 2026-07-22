package com.xiaomo.agent.workflow.state;

import java.time.Instant;

public record DebateMessage(String speakerName, String argument, Instant timestamp) {}
