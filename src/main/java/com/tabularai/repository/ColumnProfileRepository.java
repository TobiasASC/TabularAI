package com.tabularai.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tabularai.model.ColumnDataType;
import com.tabularai.model.ColumnProfile;

@Repository
public interface ColumnProfileRepository extends JpaRepository<ColumnProfile, Long> {

    List<ColumnProfile> findByDatasetId(Long datasetId);

    List<ColumnProfile> findByDatasetIdAndDataType(Long datasetId, ColumnDataType dataType);
}
