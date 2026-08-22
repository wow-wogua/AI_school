package com.aischool.server.service.report;

import com.aischool.server.common.BizException;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** PDF 归档：MinIO put/get/delete（对象路径即 t_report.file_url） */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfStoreService {

    private final MinioClient minioClient;

    @Value("${aischool.minio.bucket}")
    private String bucket;

    public String upload(String objectName, Path pdfFile) {
        try (InputStream in = Files.newInputStream(pdfFile)) {
            return upload(objectName, in, Files.size(pdfFile), "application/pdf");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("MinIO 上传失败 {}: {}", objectName, e.getMessage());
            throw new BizException(500, "PDF 归档失败: " + e.getMessage());
        }
    }

    public String upload(String objectName, InputStream in, long size, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket).object(objectName)
                    .stream(in, size, -1)
                    .contentType(contentType)
                    .build());
            return objectName;
        } catch (Exception e) {
            log.error("MinIO 上传失败 {}: {}", objectName, e.getMessage());
            throw new BizException(500, "文件上传失败: " + e.getMessage());
        }
    }

    public InputStream download(String objectName) {
        try {
            return minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(objectName).build());
        } catch (Exception e) {
            throw new BizException(404, "报告文件不存在或已删除");
        }
    }

    public void delete(String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectName).build());
        } catch (Exception e) {
            log.warn("MinIO 删除失败 {}: {}", objectName, e.getMessage());
        }
    }
}
