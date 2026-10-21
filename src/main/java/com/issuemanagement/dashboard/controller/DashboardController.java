package com.issuemanagement.dashboard.controller;

import com.issuemanagement.common.dto.ApiResponse;
import com.issuemanagement.dashboard.dto.EmployeeDashboardResponse;
import com.issuemanagement.dashboard.dto.ProjectManagerDashboardResponse;
import com.issuemanagement.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Dashboards", description = "Endpoints for employee personal workload and project manager project dashboards")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/api/v1/dashboard/employee")
    @Operation(summary = "Get personal workload dashboard for authenticated employee")
    public ResponseEntity<ApiResponse<EmployeeDashboardResponse>> getEmployeeDashboard() {
        EmployeeDashboardResponse response = dashboardService.getEmployeeDashboard();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/api/v1/projects/{projectId}/dashboard")
    @Operation(summary = "Get project-level dashboard for assigned project manager or admin")
    public ResponseEntity<ApiResponse<ProjectManagerDashboardResponse>> getProjectManagerDashboard(
            @PathVariable Long projectId) {
        ProjectManagerDashboardResponse response = dashboardService.getProjectManagerDashboard(projectId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
