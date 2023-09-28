// Copyright 2023 Google LLC

package com.google.cloud.test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.slf4j.bridge.SLF4JBridgeHandler;

import com.google.api.gax.rpc.ApiExceptions;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BlobWriteSession;
import com.google.cloud.storage.BlobWriteSessionConfig;
import com.google.cloud.storage.BlobWriteSessionConfigs;
import com.google.cloud.storage.GrpcStorageOptions;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.storage.TransportCompatibility.Transport;

public final class WriteBenchmark {
    static {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }

    static byte[] randomBytes = new byte[64 * 1024 * 1024];
    static {
        Random r = new Random(0);
        r.nextBytes(randomBytes);
    }

    private static StorageOptions buildStorageOptions(Transport transport) throws IOException {
        if (transport == Transport.HTTP) {
            return StorageOptions.http().build();
        } else {
            BlobWriteSessionConfig blobWriteSessionConfig;
            blobWriteSessionConfig = BlobWriteSessionConfigs.getDefault().withChunkSize(64 * 1024 * 1024);
            GrpcStorageOptions.Builder builder = StorageOptions.grpc();
            builder.setAttemptDirectPath(true);
            return builder
                    .setBlobWriteSessionConfig(blobWriteSessionConfig)
                    .build();
        }
    }

    public static void uploadObject(Storage s, long fileSize, String destination) throws IOException {
        BlobId id = BlobId.fromGsUtilUri(destination);
        BlobInfo info = BlobInfo.newBuilder(id).build();
        BlobWriteSession session = s.blobWriteSession(info);
        long total = 0;
        try (WritableByteChannel w = session.open()) {
            while (total < fileSize) {
                int bytesToWrite = Math.toIntExact(Math.min(fileSize - total, Math.toIntExact(randomBytes.length)));
                total += w.write(ByteBuffer.wrap(randomBytes, 0, bytesToWrite));
            }
        }
        BlobInfo blobInfo = ApiExceptions.callAndTranslateApiException(session.getResult());
        if (blobInfo.getSize() != fileSize) {
            throw new RuntimeException(
                    "Wrote file with wrong size, wanted " + fileSize + " but got " + blobInfo.getSize());
        }
    }

    public static void summarize(DescriptiveStatistics stats, long fileSize) {
        System.out.printf(
                "\nResults:\n" +
                        "\tMean: %.1fms (%.1f Mbps)\n" +
                        "\tp50: %.1fms (%.1f Mbps)\n" +
                        "\tMin: %.1fms (%.1f Mbps)\n" +
                        "\tMax: %.1fms (%.1f Mbps)\n",
                stats.getMean(),
                fileSize / (stats.getMean() * 1000.0f),
                stats.getPercentile(50),
                fileSize / (stats.getPercentile(50) * 1000.0f),
                stats.getMin(),
                fileSize / (stats.getMin() * 1000.0f),
                stats.getMax(),
                fileSize / (stats.getMax() * 1000.0f));
    }

    public static void main(String[] args) throws Exception {
        // Parse Input

        Options options = new Options();
        options.addOption("grpc", false, "Use gRPC API");
        options.addOption("n", "runs", true, "Number of times to do the upload (default: 10)");
        options.addOption("size", true, "Length of each object to write, in bytes (default: 1Gib)");
        options.addOption("warmup", true, "Number of writes to do before measuring (default: 1)");
        CommandLine line = new DefaultParser().parse(options, args);
        int runs = Integer.parseInt(line.getOptionValue("runs", "10"));
        int warmupRuns = Integer.parseInt(line.getOptionValue("warmup", "1"));
        long objectSize = Long.parseLong(line.getOptionValue("size", "134217728"));
        boolean useGrpc = line.hasOption("grpc");

        List<String> parsedArgs = line.getArgList();
        if (parsedArgs.size() != 1) {
            System.err.println(
                    "Usage: WriteBenchmark [-grpc] [-n #] [-size ####] gs://destination_bucket/path/filename");
            System.exit(1);
        }
        String destGsPath = parsedArgs.get(0);
        System.out.printf("Writing (%d bytes) to %s with %s API, %d times.\n",
                objectSize, destGsPath, useGrpc ? "gRPC" : "JSON", runs);

        // Perform upload tests

        Storage s = buildStorageOptions(Transport.GRPC).getService();
        DescriptiveStatistics stats = new DescriptiveStatistics();
        for (int i = 0; i < runs + warmupRuns; i++) {
            boolean warmup = i < warmupRuns;
            if (warmup) {
                System.out.printf("Warmup Upload %d...", i);
            } else {
                System.out.printf("Upload %d...", i - warmupRuns);
            }
            Instant begin = Instant.now();
            uploadObject(s, objectSize, destGsPath);
            Duration diff = Duration.between(begin, Instant.now());
            System.out.printf("Done. Took %d milliseconds (%.1f Mbps).\n", diff.toMillis(),
                    (objectSize / (diff.toMillis() * 1000.0f)));
            if (!warmup) {
                stats.addValue(diff.toMillis());
            }
        }
        summarize(stats, objectSize);
        s.close();
    }
}