package com.connectsphere.notification.serviceimpl;

import com.connectsphere.notification.dto.AuthProfileResponse;
import com.connectsphere.notification.dto.ContentReportResponse;
import com.connectsphere.notification.dto.CreateContentReportRequest;
import com.connectsphere.notification.dto.UpdateContentReportStatusRequest;
import com.connectsphere.notification.entity.ContentReport;
import com.connectsphere.notification.entity.ContentReportStatus;
import com.connectsphere.notification.repository.ContentReportRepository;
import com.connectsphere.notification.service.ContentReportService;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ContentReportServiceImpl implements ContentReportService {

    private final ContentReportRepository contentReportRepository;
    private final RestTemplate restTemplate;
    private final String authServiceUrl;

    public ContentReportServiceImpl(
            ContentReportRepository contentReportRepository,
            RestTemplate restTemplate,
            @Value("${app.services.auth-service.url:http://localhost:8081}") String authServiceUrl
    ) {
        this.contentReportRepository = contentReportRepository;
        this.restTemplate = restTemplate;
        this.authServiceUrl = authServiceUrl;
    }

    @Override
    public ContentReportResponse createReport(String authorizationHeader, CreateContentReportRequest request) {
        AuthProfileResponse reporter = resolveCurrentProfile(authorizationHeader);
        ContentReport report = new ContentReport();
        report.setReporterId(reporter.userId());
        report.setTargetType(request.targetType());
        report.setTargetId(request.targetId());
        report.setTargetUserId(request.targetUserId());
        report.setReason(request.reason().trim());
        report.setStatus(ContentReportStatus.OPEN);
        return toResponse(contentReportRepository.save(report));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContentReportResponse> getAllReports(String authorizationHeader) {
        ensureAdmin(authorizationHeader);
        return contentReportRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContentReportResponse> getReportsByStatus(String authorizationHeader, ContentReportStatus status) {
        ensureAdmin(authorizationHeader);
        return contentReportRepository.findByStatusOrderByCreatedAtDesc(status).stream().map(this::toResponse).toList();
    }

    @Override
    public ContentReportResponse updateReportStatus(String authorizationHeader, Long reportId, UpdateContentReportStatusRequest request) {
        AuthProfileResponse admin = resolveCurrentProfile(authorizationHeader);
        if (!isAdmin(admin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin privileges are required");
        }

        ContentReport report = contentReportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
        report.setStatus(request.status());
        report.setAdminNotes(request.adminNotes() == null || request.adminNotes().isBlank() ? null : request.adminNotes().trim());
        report.setReviewedById(admin.userId());
        report.setReviewedAt(java.time.Instant.now());
        return toResponse(contentReportRepository.save(report));
    }

    private void ensureAdmin(String authorizationHeader) {
        if (!isAdmin(resolveCurrentProfile(authorizationHeader))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin privileges are required");
        }
    }

    private boolean isAdmin(AuthProfileResponse profile) {
        return profile.role() != null && profile.role().equalsIgnoreCase("ADMIN");
    }

    private AuthProfileResponse resolveCurrentProfile(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authorizationHeader);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            AuthProfileResponse profile = restTemplate.exchange(
                    authServiceUrl + "/auth/profile",
                    HttpMethod.GET,
                    request,
                    AuthProfileResponse.class
            ).getBody();
            if (profile == null || profile.userId() == null || !profile.active()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unable to resolve authenticated user");
            }
            return profile;
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unable to resolve authenticated user");
        }
    }

    private ContentReportResponse toResponse(ContentReport report) {
        return new ContentReportResponse(
                report.getReportId(),
                report.getReporterId(),
                report.getTargetType(),
                report.getTargetId(),
                report.getTargetUserId(),
                report.getReason(),
                report.getStatus(),
                report.getAdminNotes(),
                report.getReviewedById(),
                report.getReviewedAt(),
                report.getCreatedAt(),
                report.getUpdatedAt()
        );
    }
}