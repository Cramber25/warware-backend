package pl.cramber.assetstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class R2StorageService {

    private final S3Presigner s3Presigner;

    @Value("${cloud.r2.bucket-name}")
    private String bucketName;

    @Value("${cloud.r2.public-url}")
    private String publicUrl;

    public String generatePresignedUrl(String fileKey, int expirationMinutes) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(fileKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(expirationMinutes))
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        String rawUrl = presignedRequest.url().toString();
        String pathAndSignature = rawUrl.substring(rawUrl.indexOf("/" + fileKey));

        return publicUrl + pathAndSignature;
    }
}