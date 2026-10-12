package com.example.taskmanager.entity;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TaskPriority {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    private final String value;

    TaskPriority(String value) {
        this.value = value;
    }

    /** JSON / API representation, e.g. "high". */
    @JsonValue
    public String getValue() {
        return value;
    }

    public static TaskPriority fromValue(String raw) {
        String candidate = raw == null ? "" : raw.trim();
        for (TaskPriority p : values()) {
            if (p.value.equalsIgnoreCase(candidate)) {
                return p;
            }
        }
        throw new IllegalArgumentException("Unknown task priority: " + raw);
    }

    @Override
    public String toString() {
        return value;
    }
}
