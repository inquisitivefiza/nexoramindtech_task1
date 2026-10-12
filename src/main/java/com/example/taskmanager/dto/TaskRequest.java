package com.example.taskmanager.dto;

import com.example.taskmanager.entity.TaskPriority;
import com.example.taskmanager.entity.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for POST /api/tasks and PUT /api/tasks/{id}.
 * status / priority are optional: defaults on create (pending / medium),
 * "leave unchanged" on update.
 */
public record TaskRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        TaskStatus status,

        TaskPriority priority
) {
}
