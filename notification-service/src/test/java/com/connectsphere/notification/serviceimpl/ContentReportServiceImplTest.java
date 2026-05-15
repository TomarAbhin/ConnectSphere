package com.connectsphere.notification.serviceimpl;

import com.connectsphere.notification.dto.AuthProfileResponse;
import com.connectsphere.notification.dto.CreateContentReportRequest;
import com.connectsphere.notification.dto.UpdateContentReportStatusRequest;
import com.connectsphere.notification.entity.ContentReport;
import com.connectsphere.notification.entity.ContentReportStatus;
import com.connectsphere.notification.entity.ContentReportTargetType;
import com.connectsphere.notification.repository.ContentReportRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentReportServiceImplTest {

    @Mock
    private ContentReportRepository contentReportRepository;

    @Mock
    private RestTemplate restTemplate;

    private ContentReportServiceImpl contentReportService;

    @BeforeEach
    void setUp() {
        contentReportService = new ContentReportServiceImpl(contentReportRepository, restTemplate, "http://auth");
    }

    @Test
    void createReportStoresReporterAndOpenStatus() {
        stubProfile(profile(4L, "USER"));
        when(contentReportRepository.save(any(ContentReport.class))).thenAnswer(invocation -> {
            ContentReport report = invocation.getArgument(0);
            report.setReportId(10L);
            return report;
        });

        var response = contentReportService.createReport(
                "Bearer token",
                new CreateContentReportRequest(ContentReportTargetType.POST, 99L, 7L, " spam ")
        );

        assertEquals(10L, response.reportId());
        assertEquals(4L, response.reporterId());
        assertEquals(ContentReportStatus.OPEN, response.status());
        assertEquals("spam", response.reason());
    }

    @Test
    void adminCanListAndUpdateReports() {
        stubProfile(profile(1L, "ADMIN"));
        ContentReport report = report(22L, ContentReportStatus.OPEN);
        when(contentReportRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(report));
        when(contentReportRepository.findByStatusOrderByCreatedAtDesc(ContentReportStatus.OPEN)).thenReturn(List.of(report));
        when(contentReportRepository.findById(22L)).thenReturn(Optional.of(report));
        when(contentReportRepository.save(any(ContentReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(1, contentReportService.getAllReports("Bearer admin").size());
        assertEquals(1, contentReportService.getReportsByStatus("Bearer admin", ContentReportStatus.OPEN).size());
        var updated = contentReportService.updateReportStatus(
                "Bearer admin",
                22L,
                new UpdateContentReportStatusRequest(ContentReportStatus.RESOLVED, " handled ")
        );

        assertEquals(ContentReportStatus.RESOLVED, updated.status());
        assertEquals("handled", updated.adminNotes());
        assertEquals(1L, updated.reviewedById());
        assertNotNull(updated.reviewedAt());
    }

    @Test
    void nonAdminCannotListReports() {
        stubProfile(profile(4L, "USER"));

        assertThrows(ResponseStatusException.class, () -> contentReportService.getAllReports("Bearer token"));
    }

    private void stubProfile(AuthProfileResponse profile) {
        when(restTemplate.exchange(
                eq("http://auth/auth/profile"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AuthProfileResponse.class)
        )).thenReturn(new ResponseEntity<>(profile, HttpStatus.OK));
    }

    private AuthProfileResponse profile(Long userId, String role) {
        return new AuthProfileResponse(
                userId,
                "user" + userId,
                "user" + userId + "@example.com",
                "User " + userId,
                null,
                null,
                role,
                "LOCAL",
                true,
                Instant.parse("2026-05-05T00:00:00Z")
        );
    }

    private ContentReport report(Long reportId, ContentReportStatus status) {
        ContentReport report = new ContentReport();
        report.setReportId(reportId);
        report.setReporterId(4L);
        report.setTargetType(ContentReportTargetType.POST);
        report.setTargetId(99L);
        report.setTargetUserId(7L);
        report.setReason("spam");
        report.setStatus(status);
        return report;
    }
}
