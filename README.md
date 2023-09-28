# Storage Benchmarker

This is a small utility for measuring the time to write Google Cloud Storage objects in Java.

## Building

    # Will create a JAR at `target/storage-write-benchmarker-1.0-SNAPSHOT-shade.jar`
    mvn -Pshade clean package

## Running

1. Create a local file of the size you want to test. You could create a random 1 Gigabyte file using `dd if=/dev/urandom of=1G bs=1G count=1`.
2. Create a Cloud Storage bucket that is writable for the service account you want to use.
3. Copy the program to the machine you want to test with. Ensure that Default Application Credentials are appropriately configured for that environment.
4. Run tests for APIs:

    ```
    # Tests upload performance using the JSON API.
    java -jar target/storage-write-benchmarker-1.0-SNAPSHOT-shade.jar localFileName.bin gs://bucketname/destFile.bin

    # Tests upload performance using the gRPC API.
    java -jar target/storage-write-benchmarker-1.0-SNAPSHOT-shade.jar -grpc localFileName.bin gs://bucketname/destFile.bin
    ```

Program options:

    -n #    Specifies how many times to upload the object
    -grpc   Uploads using gRPC (and Direct Path if available)

Program arguments:

    localFile.bin -- name of the 