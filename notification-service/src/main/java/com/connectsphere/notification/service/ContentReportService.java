package com.connectsphere.notification.service;

import com.connectsphere.notification.dto.ContentReportResponse;
import com.connectsphere.notification.dto.CreateContentReportRequest;
import com.connectsphere.notification.dto.UpdateContentReportStatusRequest;
import com.connectsphere.notification.entity.ContentReportStatus;
import java.util.List;

public interface ContentReportService {

    ContentReportResponse createReport(String authorizationHeader, CreateContentReportRequest request);

    List<ContentReportResponse> getAllReports(String authorizationHeader);

    List<ContentReportResponse> getReportsByStatus(String authorizationHeader, ContentReportStatus status);

    ContentReportResponse updateReportStatus(String authorizationHeader, Long reportId, UpdateContentReportStatusRequest request);
}