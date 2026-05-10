package pl.cramber.assetstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
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
        String targetBucket = fileKey.startsWith("images/") || fileKey.startsWith("assets/") || fileKey.startsWith("avatars/")
                ? publicBucketName : privateBucketName;

        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(targetBucket)
                .key(fileKey)
                .build();

        s3Client.deleteObject(deleteObjectRequest);
    }

    public String uploadAvatarFromUrl(String sourceUrl, String platform, String userId) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(sourceUrl))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                return sourceUrl;
            }

            String extension = "webp";
            if (sourceUrl.contains(".png")) extension = "png";
            else if (sourceUrl.contains(".jpg") || sourceUrl.contains(".jpeg")) extension = "jpeg";

            String fileKey = "avatars/" + platform + "/" + userId + "." + extension;

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(publicBucketName)
                    .key(fileKey)
                    .contentType("image/" + extension)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(response.body()));

            return publicUrl + "/" + fileKey;
        } catch (Exception e) {
            return sourceUrl;
        }
    }

    public ImageUploadTicket generateImageUploadUrl(String originalFilename, String type, UUID assetId, String folder) {
        String objectKey;
        String extension = getFileExtension(originalFilename);

        if ("thumbnail".equalsIgnoreCase(type)) {
            if (assetId == null) throw new IllegalArgumentException("ASSET_ID_REQUIRED");
            objectKey = "assets/" + assetId + "/thumbnail." + extension;
        } else if ("gallery".equalsIgnoreCase(type)) {
            if (assetId == null) throw new IllegalArgumentException("ASSET_ID_REQUIRED");
            objectKey = "assets/" + assetId + "/gallery/" + originalFilename;
        } else {
            String targetFolder = (folder != null && !folder.trim().isEmpty()) ? folder : "images";
            objectKey = targetFolder + "/" + originalFilename;
        }

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(publicBucketName)
                .key(objectKey)
                .contentType("image/" + extension)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .putObjectRequest(objectRequest)
                .build();

        String presignedUploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();
        String finalPublicUrl = publicUrl + "/" + objectKey;

        return new ImageUploadTicket(presignedUploadUrl, finalPublicUrl);
    }

    public List<ImageUploadTicket> generateImageUploadUrls(List<String> originalFilenames, String type, UUID assetId, String folder) {
        return originalFilenames.stream()
                .map(filename -> generateImageUploadUrl(filename, type, assetId, folder))
                .toList();
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "jpeg";
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    public record ImageUploadTicket(String uploadUrl, String finalUrl) {}
}