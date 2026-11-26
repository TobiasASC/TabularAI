package com.tabularai.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tabularai.model.AiReport;

@Repository
public interface AiReportRepository extends JpaRepository<AiReport, Long> {

    List<AiReport> findByDatasetId(Long datasetId);

    List<AiReport> findByDatasetIdOrderByCreatedAtDesc(Long datasetId);
}
