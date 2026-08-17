package com.secondbrain.common.repository;

import com.secondbrain.common.entity.ProjectDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectDocumentRepository extends JpaRepository<ProjectDocument, UUID> {

    @Query("SELECT d FROM ProjectDocument d WHERE d.project.id = :projectId ORDER BY d.createdAt DESC")
    List<ProjectDocument> findByProjectId(@Param("projectId") UUID projectId);

    @Query("SELECT d FROM ProjectDocument d WHERE d.project.id = :projectId AND d.fileType = :fileType ORDER BY d.createdAt DESC")
    List<ProjectDocument> findByProjectIdAndFileType(@Param("projectId") UUID projectId, @Param("fileType") String fileType);

    @Query("SELECT d FROM ProjectDocument d ORDER BY d.createdAt DESC")
    List<ProjectDocument> findAllOrderByCreatedAtDesc();
}
