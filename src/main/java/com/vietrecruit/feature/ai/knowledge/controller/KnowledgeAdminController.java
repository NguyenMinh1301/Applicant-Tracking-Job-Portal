package com.vietrecruit.feature.ai.knowledge.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.vietrecruit.common.base.BaseController;
import com.vietrecruit.common.enums.ApiSuccessCode;
import com.vietrecruit.common.response.ApiResponse;
import com.vietrecruit.common.response.PageResponse;
import com.vietrecruit.common.security.SecurityUtils;
import com.vietrecruit.feature.ai.knowledge.dto.KnowledgeDocumentResponse;
import com.vietrecruit.feature.ai.knowledge.dto.KnowledgeUploadResponse;
import com.vietrecruit.feature.ai.knowledge.service.KnowledgeIngestionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/vietrecruit/admin/knowledge")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class KnowledgeAdminController extends BaseController {

    private final KnowledgeIngestionService knowledgeIngestionService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<KnowledgeUploadResponse>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam("category") String category) {

        UUID uploadedBy = SecurityUtils.getCurrentUserId();
        KnowledgeUploadResponse response =
                knowledgeIngestionService.upload(file, title, category, uploadedBy);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(ApiSuccessCode.KNOWLEDGE_UPLOAD_ACCEPTED, response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<KnowledgeDocumentResponse>>> list(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<KnowledgeDocumentResponse> result =
                knowledgeIngestionService.list(
                        category,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.KNOWLEDGE_LIST_SUCCESS, PageResponse.from(result)));
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID documentId) {
        knowledgeIngestionService.delete(documentId);
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.KNOWLEDGE_DELETE_SUCCESS));
    }
}
