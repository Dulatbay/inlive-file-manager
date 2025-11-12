package kz.ai.lab.inlivefilemanager.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

@Slf4j
@Configuration
public class StorageConfig {
    @Value("${aws.region}")
    private String region;

    @Value("${aws.credentials.access-key}")
    private String accessKey;

    @Value("${aws.credentials.secret-key}")
    private String secretKey;

    @Value("${aws.s3.endpoint:}")
    private String endpoint;

    @Bean
    public S3Client s3Client() {
        try {
            log.info("Initializing S3 Client with region: {}", region);
            log.debug("Access Key: {}...", accessKey != null && accessKey.length() > 4 ? accessKey.substring(0, 4) : "null");

            if (accessKey == null || accessKey.isBlank()) {
                throw new IllegalStateException("AWS Access Key is not configured. Please set AWS_ACCESS_KEY_ID environment variable.");
            }

            if (secretKey == null || secretKey.isBlank()) {
                throw new IllegalStateException("AWS Secret Key is not configured. Please set AWS_SECRET_ACCESS_KEY environment variable.");
            }

            AwsBasicCredentials creds = AwsBasicCredentials.create(accessKey, secretKey);

            S3ClientBuilder builder = S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(creds));

            // Support for custom endpoints (e.g., LocalStack, MinIO)
            if (endpoint != null && !endpoint.isBlank()) {
                log.info("Using custom S3 endpoint: {}", endpoint);
                builder.endpointOverride(URI.create(endpoint));
            }

            S3Client client = builder.build();
            log.info("S3 Client initialized successfully");
            return client;

        } catch (Exception e) {
            log.error("Failed to initialize S3 Client: {}", e.getMessage(), e);
            throw new IllegalStateException("Could not initialize S3 Client. Please check your AWS credentials and configuration.", e);
        }
    }
}