package com.aischool.server.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class MinioConfig {

    @Bean
    public MinioClient minioClient(@Value("${aischool.minio.endpoint}") String endpoint,
                                   @Value("${aischool.minio.access-key}") String accessKey,
                                   @Value("${aischool.minio.secret-key}") String secretKey,
                                   @Value("${aischool.minio.bucket}") String bucket) {
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint).credentials(accessKey, secretKey).build();
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO 桶已创建: {}", bucket);
            }
        } catch (Exception e) {
            log.warn("MinIO 不可用（启动不阻塞，稍后重试）: {}", e.getMessage());
        }
        return client;
    }
}
