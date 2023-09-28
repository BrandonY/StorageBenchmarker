// Copyright 2023 Google LLC

package com.google.cloud.test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BlobWriteSessionConfig;
import com.google.cloud.storage.BlobWriteSessionConfigs;
import com.google.cloud.storage.GrpcStorageOptions;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.storage.TransportCompatibility.Transport;
import org.slf4j.bridge.SLF4JBridgeHandler;


public final class WriteBenchmark {
    static {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
      }

    private static StorageOptions buildStorageOptions(Transport transport) throws IOException {
        if (transport == Transport.HTTP) {
            return StorageOptions.http().build();
        } else {
            BlobWriteSessionConfig blobWriteSessionConfig;
            blobWriteSessionConfig = BlobWriteSessionConfigs.getDefault().withChunkSize(32 * 1024 * 1024);
            GrpcStorageOptions.Builder builder = StorageOptions.grpc();
            builder.setAttemptDirectPath(true);
            return builder
                    .setBlobWriteSessionConfig(blobWriteSessionConfig)
                    .build();
        }
    }

    public static void uploadObject(Storage s, String sourceFile, String destination) throws IOException {
        BlobId id = BlobId.fromGsUtilUri(destination);
        BlobInfo info = BlobInfo.newBuilder(id).build();
        s.createFrom(info, Paths.get(sourceFile));
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
                fileSize / ( stats.getPercentile(50) * 1000.0f),
                stats.getMin(),
                fileSize / ( stats.getMin() * 1000.0f),
                stats.getMax(),
                fileSize / ( stats.getMax() * 1000.0f));
    }

    public static void main(String[] args) throws Exception {
        // Parse Input

        Options options = new Options();
        options.addOption("grpc", false, "Use gRPC API");
        options.addOption("n", "runs", true, "Number of times to do the upload (default: 10)");
        CommandLine line = new DefaultParser().parse(options, args);
        List<String> parsedArgs = line.getArgList();
        if (parsedArgs.size() != 2) {
            System.err.println(
                    "Usage: WriteBenchmark [-grpc] [-n #] sourceFileName gs://destination_bucket/path/filename");
            System.exit(1);
        }
        int runs = Integer.parseInt(line.getOptionValue("runs", "10"));
        boolean useGrpc = line.hasOption("grpc");
        String sourceFile = parsedArgs.get(0);
        String destGsPath = parsedArgs.get(1);
        File sourceF = Paths.get(sourceFile).toFile();
        if (!sourceF.exists()) {
            System.err.printf("Local file %s not found\n", sourceFile);
        }
        long fileSize = sourceF.length();
        System.out.printf("Writing object %s (%d bytes) to %s with %s API, %d times.\n",
                sourceFile, fileSize, destGsPath, useGrpc ? "gRPC" : "JSON", runs);

        // Perform upload tests

        Storage s = buildStorageOptions(Transport.GRPC).getService();
        DescriptiveStatistics stats = new DescriptiveStatistics();
        for (int i = 0; i < runs; i++) {
            System.out.printf("Upload %d...", i);
            Instant begin = Instant.now();
            uploadObject(s, sourceFile, destGsPath);
            Duration diff = Duration.between(begin, Instant.now());
            System.out.printf("Done. Took %d milliseconds (%.1f Mbps).\n", diff.toMillis(),
                    (fileSize / (diff.toMillis() * 1000.0f)));
            stats.addValue(diff.toMillis());
        }
        summarize(stats, fileSize);
        s.close();
    }
}