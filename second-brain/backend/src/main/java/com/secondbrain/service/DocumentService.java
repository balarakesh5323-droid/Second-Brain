package com.secondbrain.service;

import com.secondbrain.config.MinioConfig;
import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final MinioConfig minioConfig;
    private MinioClient minioClient;

    private static final String BUCKET_NAME = "second-brain-documents";

    @PostConstruct
    public void init() {
        minioClient = minioConfig.minioClient();
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(BUCKET_NAME).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(BUCKET_NAME).build());
                log.info("Created bucket: {}", BUCKET_NAME);
            }
        } catch (Exception e) {
            log.error("Failed to initialize MinIO: {}", e.getMessage());
        }
    }

    public String uploadDocument(String name, InputStream data, String contentType, long size) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(BUCKET_NAME)
                    .object(name)
                    .stream(data, size, -1)
                    .contentType(contentType)
                    .build());
            log.info("Uploaded document: {}", name);
            return name;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload document: " + e.getMessage(), e);
        }
    }

    public InputStream downloadDocument(String name) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(BUCKET_NAME)
                    .object(name)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to download document: " + e.getMessage(), e);
        }
    }

    public String getDocumentUrl(String name) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(BUCKET_NAME)
                    .object(name)
                    .expiry(24, TimeUnit.HOURS)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to get URL: " + e.getMessage(), e);
        }
    }

    public List<String> listDocuments() {
        try {
            List<String> documents = new ArrayList<>();
            Iterable<Result<Item>> items = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(BUCKET_NAME)
                    .build());
            for (Result<Item> item : items) {
                documents.add(item.get().objectName());
            }
            return documents;
        } catch (Exception e) {
            throw new RuntimeException("Failed to list documents: " + e.getMessage(), e);
        }
    }

    public void deleteDocument(String name) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(BUCKET_NAME)
                    .object(name)
                    .build());
            log.info("Deleted document: {}", name);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete document: " + e.getMessage(), e);
        }
    }
}
