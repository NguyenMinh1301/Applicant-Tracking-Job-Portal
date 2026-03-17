package com.vietrecruit.feature.ai.knowledge.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.vietrecruit.feature.ai.knowledge.entity.KnowledgeDocument;

@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, UUID> {

    Page<KnowledgeDocument> findByCategory(String category, Pageable pageable);
}
