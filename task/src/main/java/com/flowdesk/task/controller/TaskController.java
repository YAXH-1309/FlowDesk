package com.flowdesk.task.controller;

import com.flowdesk.task.domain.Project;
import com.flowdesk.task.domain.Task;
import com.flowdesk.task.dto.*;
import com.flowdesk.task.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public Project createProject(@Valid @RequestBody CreateProjectRequest req) {
        return taskService.createProject(req);
    }

    @GetMapping("/projects")
    public List<Project> listProjects() {
        return taskService.listProjects();
    }

    @PostMapping("/projects/{projectId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public Task createTask(@PathVariable UUID projectId,
                           @Valid @RequestBody CreateTaskRequest req) {
        return taskService.createTask(projectId, req);
    }

    @GetMapping("/projects/{projectId}/tasks")
    public List<Task> listTasks(@PathVariable UUID projectId) {
        return taskService.listTasks(projectId);
    }

    @PutMapping("/tasks/{taskId}")
    public Task updateTask(@PathVariable UUID taskId,
                           @Valid @RequestBody UpdateTaskRequest req) {
        return taskService.updateTask(taskId, req);
    }

    @DeleteMapping("/tasks/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@PathVariable UUID taskId) {
        taskService.deleteTask(taskId);
    }

    @PutMapping("/tasks/{taskId}/assign")
    public Task assignTask(@PathVariable UUID taskId,
                           @Valid @RequestBody AssignTaskRequest req) {
        return taskService.assignTask(taskId, req);
    }
}
