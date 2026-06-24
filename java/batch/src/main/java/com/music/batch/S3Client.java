package com.music.batch;

import com.music.common.SharedConfig;
import java.net.URI;
import java.time.Duration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Configuration;

public final class S3Client {
  private S3Client() {
    /* This utility class should not be instantiated */
  }

  public static S3AsyncClient get() {
    SdkAsyncHttpClient httpClient =
        NettyNioAsyncHttpClient.builder()
            .maxConcurrency(50)
            .connectionTimeout(Duration.ofSeconds(60))
            .readTimeout(Duration.ofSeconds(60))
            .writeTimeout(Duration.ofSeconds(60))
            .build();

    ClientOverrideConfiguration overrideConfig =
        ClientOverrideConfiguration.builder()
            .apiCallTimeout(Duration.ofMinutes(2))
            .apiCallAttemptTimeout(Duration.ofSeconds(90))
            .retryStrategy(RetryMode.STANDARD)
            .build();

    var serviceConfig = S3Configuration.builder().pathStyleAccessEnabled(true).build();
    var s3CredentialsProvider =
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create(
                SharedConfig.AWS_ACCESS_KEY_ID, SharedConfig.AWS_SECRET_ACCESS_KEY));

    return S3AsyncClient.builder()
        .region(Region.of(SharedConfig.AWS_DEFAULT_REGION))
        .httpClient(httpClient)
        .endpointOverride(URI.create(SharedConfig.S3_ENDPOINT))
        .credentialsProvider(s3CredentialsProvider)
        .serviceConfiguration(serviceConfig)
        .overrideConfiguration(overrideConfig)
        .build();
  }
}
