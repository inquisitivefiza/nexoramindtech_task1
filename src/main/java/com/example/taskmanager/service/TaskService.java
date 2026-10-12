package com.example.taskmanager.service;

import com.example.taskmanager.dto.PageResponse;
import com.example.taskmanager.dto.TaskRequest;
import com.example.taskmanager.dto.TaskResponse;
import com.example.taskmanager.entity.Task;
import com.example.taskmanager.entity.TaskPriority;
import com.example.taskmanager.entity.TaskStatus;
import com.example.taskmanager.exception.BadRequestException;
import com.example.taskmanager.exception.ResourceNotFoundException;
import com.example.taskmanager.repository.TaskRepository;
import com.example.taskmanager.repository.TaskSpecifications;
import com.example.taskmanager.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class TaskService {

    public static final int MAX_PAGE_SIZE = 100;

    /** API sort keys -> entity property names (whitelist; never sort by raw user input). */
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "created_at", "createdAt",
            "updated_at", "updatedAt",
            "title", "title");

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    public TaskService(TaskRepository taskRepository, UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TaskResponse create(Long userId, TaskRequest request) {
        Task task = new Task(
                userRepository.getReferenceById(userId),
                request.title().trim(),
                cleanDescription(request.description()),
                request.status() != null ? request.status() : TaskStatus.PENDING,
                request.priority() != null ? request.priority() : TaskPriority.MEDIUM);
        return TaskResponse.from(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> list(Long userId, int page, int size,
                                           TaskStatus status, TaskPriority priority,
                                           String sort, String order) {
        if (page < 0) {
            throw new BadRequestException("Query parameter 'page' must be 0 or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("Query parameter 'size' must be between 1 and " + MAX_PAGE_SIZE);
        }
        String property = SORT_FIELDS.get(sort);
        if (property == null) {
            throw new BadRequestException(
                    "Invalid sort field '%s'. Allowed values: created_at, updated_at, title".formatted(sort));
        }
        Sort.Direction direction;
        if ("asc".equalsIgnoreCase(order)) {
            direction = Sort.Direction.ASC;
        } else if ("desc".equalsIgnoreCase(order)) {
            direction = Sort.Direction.DESC;
        } else {
            throw new BadRequestException("Invalid order '%s'. Allowed values: asc, desc".formatted(order));
        }

        // id is the tie-breaker so pages are stable when many rows share the same sort value.
        Sort sorting = Sort.by(new Sort.Order(direction, property), new Sort.Order(direction, "id"));

        Specification<Task> spec = Specification.where(TaskSpecifications.ownedBy(userId))
                .and(TaskSpecifications.hasStatus(status))
                .and(TaskSpecifications.hasPriority(priority));

        Page<TaskResponse> result = taskRepository
                .findAll(spec, PageRequest.of(page, size, sorting))
                .map(TaskResponse::from);
        return PageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long userId, Long taskId) {
        return TaskResponse.from(findOwned(userId, taskId));
    }

    /**
     * Full update of title/description; status and priority are only changed when provided.
     */
    @Transactional
    public TaskResponse update(Long userId, Long taskId, TaskRequest request) {
        Task task = findOwned(userId, taskId);
        task.setTitle(request.title().trim());
        task.setDescription(cleanDescription(request.description()));
        if (request.status() != null) {
            task.setStatus(request.status());
        }
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        // saveAndFlush so @PreUpdate has run (and updated_at is fresh) before we build the response.
        return TaskResponse.from(taskRepository.saveAndFlush(task));
    }

    @Transactional
    public void delete(Long userId, Long taskId) {
        taskRepository.delete(findOwned(userId, taskId));
    }

    /**
     * Another user's task is reported as 404 (not 403) so that task IDs can't be probed.
     */
    private Task findOwned(Long userId, Long taskId) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Task with id " + taskId + " not found"));
    }

    private static String cleanDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }
}
