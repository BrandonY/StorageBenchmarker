package com.google.cloud.test;

import java.io.FileInputStream;
import java.io.IOException;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.io.IOUtils;

public class HdfsWriteBenchmark {
    public static void main(String[] args) throws Exception {
   if (args.length != 4) {
       System.err.println("Usage: FSWriteTest <localPath> <remotePath> <count> <changeFileName true|false>");
       System.exit(1);
   }
   String localPath = args[0];
   String remotePath = args[1];
   int count = Integer.parseInt(args[2]);
   boolean changeFileName = Boolean.parseBoolean(args[3]);
   Configuration conf = new Configuration();
   conf.addResource(new Path("./conf/core-site.xml"));
   conf.addResource(new Path("./conf/hdfs-site.xml"));


   FileSystem fs = FileSystem.get( conf);
   for (int i = 1; i <= count; i++) {
       String dest = changeFileName ? String.format("%s-%d", remotePath, i) : remotePath;
       long startTime = System.nanoTime();
       copyFromLocal(fs, conf, localPath, dest);
       long endTime = System.nanoTime();
       long durationMs = (endTime - startTime) / 1_000_000;
       System.out.println("Run: " + i + ", Duration: " + durationMs + " ms");
       fs.delete(new Path(dest), false);
   }
}


private static void copyFromLocal(FileSystem fs, Configuration conf, String src, String dest) throws IOException {
    FileInputStream in = new FileInputStream(src);
   FSDataOutputStream out = fs.create(new Path(dest), true);
   IOUtils.copyBytes(in, out, conf, true);
}

}
