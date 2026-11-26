package com.tabularai.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tabularai.model.Dataset;
import com.tabularai.model.DatasetStatus;

@Repository
public interface DatasetRepository extends JpaRepository<Dataset, Long> {

    Page<Dataset> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<Dataset> findByStatus(DatasetStatus status);
}
