package com.connectsphere.notification.resource;

import com.connectsphere.notification.dto.ContentReportResponse;
import com.connectsphere.notification.dto.CreateContentReportRequest;
import com.connectsphere.notification.dto.UpdateContentReportStatusRequest;
import com.connectsphere.notification.entity.ContentReportStatus;
import com.connectsphere.notification.service.ContentReportService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports")
public class ReportResource {

    private final ContentReportService contentReportService;

    public ReportResource(ContentReportService contentReportService) {
        this.contentReportService = contentReportService;
    }

    @PostMapping
    public ResponseEntity<ContentReportResponse> createReport(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody CreateContentReportRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contentReportService.createReport(authorization, request));
    }

    @GetMapping
    public ResponseEntity<List<ContentReportResponse>> getAllReports(
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        return ResponseEntity.ok(contentReportService.getAllReports(authorization));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<ContentReportResponse>> getReportsByStatus(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable ContentReportStatus status
    ) {
        return ResponseEntity.ok(contentReportService.getReportsByStatus(authorization, status));
    }

    @PutMapping("/{reportId}/status")
    public ResponseEntity<ContentReportResponse> updateStatus(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long reportId,
            @Valid @RequestBody UpdateContentReportStatusRequest request
    ) {
        return ResponseEntity.ok(contentReportService.updateReportStatus(authorization, reportId, request));
    }
}