package com.connectsphere.notification.repository;

import com.connectsphere.notification.entity.ContentReport;
import com.connectsphere.notification.entity.ContentReportStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentReportRepository extends JpaRepository<ContentReport, Long> {

    List<ContentReport> findAllByOrderByCreatedAtDesc();

    List<ContentReport> findByStatusOrderByCreatedAtDesc(ContentReportStatus status);
}