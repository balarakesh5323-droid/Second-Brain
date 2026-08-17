package com.secondbrain.service;

import com.secondbrain.common.entity.Project;
import com.secondbrain.common.entity.ProjectDocument;
import com.secondbrain.common.repository.ProjectDocumentRepository;
import com.secondbrain.common.repository.ProjectRepository;
import com.secondbrain.config.MinioConfig;
import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final MinioConfig minioConfig;
    private final ProjectRepository projectRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final VectorStoreService vectorStoreService;
    private final EmbeddingService embeddingService;
    private final GraphService graphService;

    private MinioClient minioClient;
    private static final String BUCKET_NAME = "second-brain-documents";

    private static final Set<String> IMAGE_MIME_TYPES = Set.of(
        "image/png", "image/jpeg", "image/jpg", "image/webp", "image/gif", "image/svg+xml", "image/bmp"
    );

    @PostConstruct
    public void init() {
        minioClient = minioConfig.minioClient();
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(BUCKET_NAME).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(BUCKET_NAME).build());
                log.info("Created MinIO bucket: {}", BUCKET_NAME);
            }
        } catch (Exception e) {
            log.error("Failed to initialize MinIO bucket: {}", e.getMessage());
        }
    }

    /**
     * Uploads a document or image file to a Project, stores in MinIO, embeds into Qdrant, and links in Neo4j.
     */
    @Transactional
    public ProjectDocument uploadProjectDocument(UUID projectId, MultipartFile file, String title, String description, Set<String> tags) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found with ID: " + projectId));

        try {
            String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed_file";
            String docTitle = (title != null && !title.isBlank()) ? title : originalName;
            String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
            long size = file.getSize();

            boolean isImage = isImageFile(originalName, contentType);
            String fileType = isImage ? "IMAGE" : "DOCUMENT";

            String storageKey = String.format("projects/%s/%s_%s", projectId, UUID.randomUUID().toString().substring(0, 8), originalName);

            // 1. Upload to MinIO
            try {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(BUCKET_NAME)
                        .object(storageKey)
                        .stream(file.getInputStream(), size, -1)
                        .contentType(contentType)
                        .build());
            } catch (Exception e) {
                log.warn("MinIO putObject failed (non-fatal): {}", e.getMessage());
            }

            // 2. Generate Presigned URL
            String presignedUrl = getPresignedUrl(storageKey);

            // 3. Extract text for text-based documents
            String extractedText = "";
            if (!isImage && isTextBased(originalName, contentType)) {
                try {
                    extractedText = new String(file.getBytes(), StandardCharsets.UTF_8);
                } catch (Exception ignored) {}
            }

            // 4. Save entity in PostgreSQL
            ProjectDocument doc = ProjectDocument.builder()
                    .title(docTitle)
                    .fileName(originalName)
                    .fileType(fileType)
                    .contentType(contentType)
                    .sizeBytes(size)
                    .storageKey(storageKey)
                    .url(presignedUrl)
                    .description(description)
                    .extractedText(extractedText)
                    .project(project)
                    .tags(tags != null ? tags : new HashSet<>())
                    .build();

            doc = projectDocumentRepository.save(doc);

            // 5. Index into Qdrant vector store
            String textToEmbed = String.format("Project: %s | %s: %s | %s\n%s",
                    project.getName(), fileType, docTitle, description != null ? description : "",
                    extractedText.length() > 2000 ? extractedText.substring(0, 2000) : extractedText);

            try {
                float[] embedding = embeddingService.embed(textToEmbed);
                vectorStoreService.upsert("documentation", doc.getId().toString(), embedding,
                        Map.of(
                                "projectId", projectId.toString(),
                                "projectName", project.getName(),
                                "type", fileType.toLowerCase(),
                                "title", docTitle,
                                "fileName", originalName,
                                "storageKey", storageKey,
                                "url", presignedUrl,
                                "description", description != null ? description : ""
                        ),
                        Map.of()
                );
            } catch (Exception e) {
                log.warn("Failed to vectorize document into Qdrant (non-fatal): {}", e.getMessage());
            }

            // 6. Connect in Neo4j Knowledge Graph
            try {
                String nodeLabel = isImage ? "Image" : "Document";
                String docNodeId = "doc::" + doc.getId().toString();
                graphService.batchCreateNodes(nodeLabel, List.of(
                        Map.of("id", docNodeId, "props", Map.of(
                                "name", docTitle,
                                "fileName", originalName,
                                "fileType", fileType,
                                "contentType", contentType,
                                "size", size,
                                "url", presignedUrl,
                                "description", description != null ? description : ""
                        ))
                ));
                String relType = isImage ? "HAS_IMAGE" : "HAS_DOCUMENT";
                graphService.batchCreateRelationshipsTyped(relType, List.of(
                        Map.of("fromId", project.getName(), "toId", docNodeId, "props", Map.of())
                ));
            } catch (Exception e) {
                log.warn("Failed to connect document in Neo4j (non-fatal): {}", e.getMessage());
            }

            log.info("Successfully added {} '{}' to project '{}'", fileType, docTitle, project.getName());
            return doc;

        } catch (Exception e) {
            log.error("Failed to upload project document: {}", e.getMessage(), e);
            throw new RuntimeException("Document upload failed: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a text/markdown note directly for a Project.
     */
    @Transactional
    public ProjectDocument createProjectNote(UUID projectId, String title, String markdownContent, String description, Set<String> tags) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found with ID: " + projectId));

        try {
            String fileName = (title.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase()) + ".md";
            String storageKey = String.format("projects/%s/notes/%s_%s", projectId, UUID.randomUUID().toString().substring(0, 8), fileName);
            byte[] bytes = markdownContent.getBytes(StandardCharsets.UTF_8);
            try {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(BUCKET_NAME)
                        .object(storageKey)
                        .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                        .contentType("text/markdown")
                        .build());
            } catch (Exception e) {
                log.warn("MinIO putObject failed (non-fatal): {}", e.getMessage());
            }

            String presignedUrl = getPresignedUrl(storageKey);

            ProjectDocument doc = ProjectDocument.builder()
                    .title(title)
                    .fileName(fileName)
                    .fileType("DOCUMENT")
                    .contentType("text/markdown")
                    .sizeBytes((long) bytes.length)
                    .storageKey(storageKey)
                    .url(presignedUrl)
                    .description(description)
                    .extractedText(markdownContent)
                    .project(project)
                    .tags(tags != null ? tags : new HashSet<>())
                    .build();

            doc = projectDocumentRepository.save(doc);

            // Embed into Qdrant
            try {
                String textToEmbed = String.format("Project Note: %s | %s\n%s", title, description != null ? description : "", markdownContent);
                float[] embedding = embeddingService.embed(textToEmbed);
                vectorStoreService.upsert("documentation", doc.getId().toString(), embedding,
                        Map.of(
                                "projectId", projectId.toString(),
                                "projectName", project.getName(),
                                "type", "note",
                                "title", title,
                                "fileName", fileName,
                                "url", presignedUrl
                        ),
                        Map.of()
                );
            } catch (Exception ignored) {}

            // Neo4j Node
            try {
                String docNodeId = "doc::" + doc.getId().toString();
                graphService.batchCreateNodes("Document", List.of(
                        Map.of("id", docNodeId, "props", Map.of(
                                "name", title,
                                "fileName", fileName,
                                "fileType", "DOCUMENT",
                                "contentType", "text/markdown",
                                "size", bytes.length,
                                "url", presignedUrl
                        ))
                ));
                graphService.batchCreateRelationshipsTyped("HAS_DOCUMENT", List.of(
                        Map.of("fromId", project.getName(), "toId", docNodeId, "props", Map.of())
                ));
            } catch (Exception ignored) {}

            return doc;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create project note: " + e.getMessage(), e);
        }
    }

    public List<ProjectDocument> getProjectDocuments(UUID projectId) {
        List<ProjectDocument> docs = projectDocumentRepository.findByProjectId(projectId);
        for (ProjectDocument doc : docs) {
            try {
                doc.setUrl(getPresignedUrl(doc.getStorageKey()));
            } catch (Exception ignored) {}
        }
        return docs;
    }

    public List<ProjectDocument> getAllDocuments() {
        List<ProjectDocument> docs = projectDocumentRepository.findAll();
        for (ProjectDocument doc : docs) {
            try {
                doc.setUrl(getPresignedUrl(doc.getStorageKey()));
            } catch (Exception ignored) {}
        }
        return docs;
    }

    public ProjectDocument getDocumentById(UUID id) {
        ProjectDocument doc = projectDocumentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found: " + id));
        doc.setUrl(getPresignedUrl(doc.getStorageKey()));
        return doc;
    }

    @Transactional
    public void deleteProjectDocument(UUID id) {
        ProjectDocument doc = projectDocumentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found: " + id));

        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(BUCKET_NAME)
                    .object(doc.getStorageKey())
                    .build());
        } catch (Exception ignored) {}

        try {
            vectorStoreService.delete("documentation", doc.getId().toString());
        } catch (Exception ignored) {}

        try {
            graphService.deleteNode("doc::" + doc.getId().toString());
        } catch (Exception ignored) {}

        projectDocumentRepository.delete(doc);
        log.info("Deleted document: {}", doc.getTitle());
    }

    public String getPresignedUrl(String storageKey) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(BUCKET_NAME)
                    .object(storageKey)
                    .expiry(7, TimeUnit.DAYS)
                    .build());
        } catch (Exception e) {
            return "/api/v1/documents/raw/" + storageKey;
        }
    }

    public InputStream downloadDocumentStream(String storageKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(BUCKET_NAME)
                    .object(storageKey)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to download document: " + e.getMessage(), e);
        }
    }

    private boolean isImageFile(String filename, String contentType) {
        if (contentType != null && IMAGE_MIME_TYPES.contains(contentType.toLowerCase())) return true;
        String lower = filename.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
               lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".svg") ||
               lower.endsWith(".bmp") || lower.endsWith(".ico");
    }

    private boolean isTextBased(String filename, String contentType) {
        if (contentType != null && (contentType.startsWith("text/") || contentType.contains("json") || contentType.contains("yaml") || contentType.contains("xml"))) return true;
        String lower = filename.toLowerCase();
        return lower.endsWith(".md") || lower.endsWith(".txt") || lower.endsWith(".json") ||
               lower.endsWith(".yaml") || lower.endsWith(".yml") || lower.endsWith(".csv") ||
               lower.endsWith(".html") || lower.endsWith(".xml") || lower.endsWith(".log");
    }
}
