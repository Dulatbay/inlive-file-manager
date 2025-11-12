package kz.ai.lab.inlivefilemanager.services;

import kz.ai.lab.inlivefilemanager.dto.S3Blob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class StorageService {
    private final S3Client s3Client;
    private final String bucketName;

    public StorageService(S3Client s3Client,
                          @Value("${aws.s3.bucket}") String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        log.info("StorageService initialized with bucket: {}", bucketName);
    }

    public String uploadFile(InputStream inputStream,
                             String filename,
                             String directory,
                             boolean generateFileName) {
        try {
            log.debug("Starting upload for file: {} to directory: {}", filename, directory);

            byte[] fileBytes = inputStream.readAllBytes();
            long fileSize = fileBytes.length;

            log.debug("File size: {} bytes", fileSize);

            if (generateFileName) {
                String extension = "";
                int dotIdx = filename.lastIndexOf(".");
                if (dotIdx != -1) {
                    extension = filename.substring(dotIdx);
                }
                filename = UUID.randomUUID() + extension;
                log.debug("Generated new filename: {}", filename);
            }

            String contentType = Files.probeContentType(new File(filename).toPath());
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            String storagePath = String.format("%s/%s", directory, filename);

            Map<String, String> metadata = new HashMap<>();
            metadata.put("filename", filename);
            metadata.put("content-type", contentType);
            metadata.put("content-length", String.valueOf(fileSize));

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storagePath)
                    .contentType(contentType)
                    .contentLength(fileSize)
                    .metadata(metadata)
                    .build();

            log.debug("Uploading to S3 - Bucket: {}, Key: {}", bucketName, storagePath);
            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(fileBytes));

            log.info("File [{}] uploaded successfully to key [{}].", filename, storagePath);
            return filename;

        } catch (S3Exception s3e) {
            log.error("S3 error while uploading file [{}]: Code: {}, Message: {}",
                    filename, s3e.awsErrorDetails().errorCode(), s3e.awsErrorDetails().errorMessage(), s3e);
            throw new RuntimeException("Could not obtain the keys", s3e);
        } catch (IOException ioe) {
            log.error("IO error while uploading file [{}]: {}", filename, ioe.getMessage(), ioe);
            throw new RuntimeException("Failed to read file content: " + ioe.getMessage(), ioe);
        } catch (Exception e) {
            log.error("Unexpected error while uploading file [{}]: {}", filename, e.getMessage(), e);
            throw new RuntimeException("Failed to upload file: " + e.getMessage(), e);
        }
    }

    public String deleteFile(String filename, String directory) {
        try {
            String storagePath = String.format("%s/%s", directory, filename);

            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storagePath)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);

            log.info("File [{}] deleted successfully at key [{}]", filename, storagePath);

            return String.format("File [%s] deleted successfully", filename);

        } catch (Exception ex) {
            log.error("Error Occurred: [{}]", ex.getMessage(), ex);
        }

        return "Unable to delete file: " + filename;
    }

    public String deleteFolder(String folderName) {
        try {
            // Префикс как "folder/" — как твой "directory"
            String prefix = folderName.endsWith("/") ? folderName : folderName + "/";

            ListObjectsV2Request listReq = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .build();

            ListObjectsV2Response listRes = s3Client.listObjectsV2(listReq);

            if (listRes.contents().isEmpty()) {
                log.info("Folder [{}] is empty or does not exist", folderName);
            } else {
                List<ObjectIdentifier> toDelete = listRes.contents().stream()
                        .map(obj -> ObjectIdentifier.builder().key(obj.key()).build())
                        .toList();

                Delete del = Delete.builder().objects(toDelete).build();

                DeleteObjectsRequest delReq = DeleteObjectsRequest.builder()
                        .bucket(bucketName)
                        .delete(del)
                        .build();

                s3Client.deleteObjects(delReq);
            }

            log.info("Folder [{}] deleted successfully", folderName);
            return String.format("Folder [%s] deleted successfully", folderName);

        } catch (Exception ex) {
            log.error("Error Occurred: [{}]", ex.getMessage(), ex);
        }

        return "Unable to delete folder: " + folderName;
    }

    public S3Blob getFile(String filename, String directory) {
        try {
            String storagePath = String.format("%s/%s", directory, filename);

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storagePath)
                    .build();

            ResponseBytes<GetObjectResponse> s3Object = s3Client.getObjectAsBytes(getObjectRequest);

            String contentType = s3Object.response().contentType();
            if (contentType == null || contentType.isBlank()) {
                contentType = "application/octet-stream";
            }

            byte[] content = s3Object.asByteArray();

            log.info("File [{}] fetched from key [{}].", filename, storagePath);
            return new S3Blob(content, contentType);

        } catch (Exception ex) {
            log.error("Error Occurred: [{}]", ex.getMessage(), ex);
        }

        return null;
    }
}
