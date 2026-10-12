package com.example.taskmanager.entity;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TaskStatus {
    PENDING("pending"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed");

    private final String value;

    TaskStatus(String value) {
        this.value = value;
    }

    /** JSON / API representation, e.g. "in_progress". */
    @JsonValue
    public String getValue() {
        return value;
    }

    public static TaskStatus fromValue(String raw) {
        String candidate = raw == null ? "" : raw.trim();
        for (TaskStatus s : values()) {
            if (s.value.equalsIgnoreCase(candidate)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown task status: " + raw);
    }

    @Override
    public String toString() {
        return value;
    }
}
