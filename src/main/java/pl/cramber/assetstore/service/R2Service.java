package pl.cramber.assetstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class R2Service {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${cloud.r2.bucket-name}")
    private String privateBucketName;

    @Value("${cloud.r2.public-bucket-name}")
    private String publicBucketName;

    @Value("${cloud.r2.public-url}")
    private String publicUrl;

    public String uploadFile(MultipartFile file, UUID uploaderId) throws IOException {
        String originalFilename = file.getOriginalFilename();

        if (originalFilename == null || (!originalFilename.endsWith(".rbxm") && !originalFilename.endsWith(".zip"))) {
            throw new RuntimeException("INVALID_FILE_EXTENSION");
        }

        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        String fileKey = "assets/" + uploaderId.toString() + "/" + UUID.randomUUID() + extension;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(privateBucketName)
                .key(fileKey)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        return fileKey;
    }

    public void deleteFile(String fileKey) {
        String targetBucket = fileKey.startsWith("images/") ? publicBucketName : privateBucketName;

        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(targetBucket)
                .key(fileKey)
                .build();

        s3Client.deleteObject(deleteObjectRequest);
    }

    public ImageUploadTicket generateImageUploadUrl(UUID userId, String originalFilename) {
        String objectKey = "images/" + userId + "/" + UUID.randomUUID() + "-" + originalFilename;

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(publicBucketName)
                .key(objectKey)
                .contentType("image/" + getFileExtension(originalFilename))
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .putObjectRequest(objectRequest)
                .build();

        String presignedUploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();
        String finalPublicUrl = publicUrl + "/" + objectKey;

        return new ImageUploadTicket(presignedUploadUrl, finalPublicUrl);
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "jpeg";
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    public record ImageUploadTicket(String uploadUrl, String finalUrl) {}
}