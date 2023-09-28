# Storage Benchmarker

This is a small utility for measuring the time to write Google Cloud Storage objects in Java.

## Building

    # Will create a JAR at `target/storage-write-benchmarker-1.0-SNAPSHOT-shade.jar`
    mvn -Pshade clean package

## Running

1. Create a Cloud Storage bucket that is writable for the service account you want to use.
2. Copy the program to the machine you want to test with. Ensure that Default Application Credentials are appropriately configured for that environment.
3. Run tests for APIs:

    ```
    # Tests upload performance using the JSON API.
    java -jar target/storage-write-benchmarker-1.0-SNAPSHOT-shade.jar gs://bucketname/destFile.bin

    # Tests upload performance using the gRPC API.
    java -jar target/storage-write-benchmarker-1.0-SNAPSHOT-shade.jar -grpc gs://bucketname/destFile.bin
    ```

Program options:

    -n #          How many uploads to perform (default: 10)
    -warmup #     Number of uploads to perform before measuring (default: 1)
    -size #       Size of the object to upload, in bytes (default: 1 GiB)
    -grpc         Upload using the gRPC API (and Direct Path if available), instead of JSON.

Program arguments:

    localFile.bin -- name of the 