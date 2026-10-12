package com.example.taskmanager.controller;

import com.example.taskmanager.dto.MessageResponse;
import com.example.taskmanager.dto.PageResponse;
import com.example.taskmanager.dto.TaskRequest;
import com.example.taskmanager.dto.TaskResponse;
import com.example.taskmanager.entity.TaskPriority;
import com.example.taskmanager.entity.TaskStatus;
import com.example.taskmanager.security.AuthenticatedUser;
import com.example.taskmanager.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Tasks")
@SecurityRequirement(name = "bearerAuth")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @Operation(summary = "Create a task")
    public ResponseEntity<TaskResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                               @Valid @RequestBody TaskRequest request) {
        TaskResponse created = taskService.create(user.id(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @Operation(summary = "List the current user's tasks (paginated, filterable, sortable)")
    public ResponseEntity<PageResponse<TaskResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "status", required = false) TaskStatus status,
            @RequestParam(name = "priority", required = false) TaskPriority priority,
            @RequestParam(name = "sort", defaultValue = "created_at") String sort,
            @RequestParam(name = "order", defaultValue = "desc") String order) {
        return ResponseEntity.ok(taskService.list(user.id(), page, size, status, priority, sort, order));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single task")
    public ResponseEntity<TaskResponse> get(@AuthenticationPrincipal AuthenticatedUser user,
                                            @PathVariable("id") Long id) {
        return ResponseEntity.ok(taskService.get(user.id(), id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a task")
    public ResponseEntity<TaskResponse> update(@AuthenticationPrincipal AuthenticatedUser user,
                                               @PathVariable("id") Long id,
                                               @Valid @RequestBody TaskRequest request) {
        return ResponseEntity.ok(taskService.update(user.id(), id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a task")
    public ResponseEntity<MessageResponse> delete(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @PathVariable("id") Long id) {
        taskService.delete(user.id(), id);
        return ResponseEntity.ok(new MessageResponse("Task deleted successfully"));
    }
}
