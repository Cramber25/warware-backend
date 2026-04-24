package pl.cramber.assetstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class R2Service {

    private final S3Client s3Client;

    @Value("${cloud.r2.bucket-name}")
    private String bucketName;

    public String uploadFile(MultipartFile file, UUID uploaderId) throws IOException {
        String originalFilename = file.getOriginalFilename();

        if (originalFilename == null || (!originalFilename.endsWith(".rbxm") && !originalFilename.endsWith(".zip"))) {
            throw new RuntimeException("INVALID_FILE_EXTENSION");
        }

        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        String fileKey = "assets/" + uploaderId.toString() + "/" + UUID.randomUUID() + extension;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileKey)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        return fileKey;
    }

    public void deleteFile(String fileKey) {
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(fileKey)
                .build();

        s3Client.deleteObject(deleteObjectRequest);
    }
}