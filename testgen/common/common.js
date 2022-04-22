'use strict';

const FileOutputStream  = Java.type("java.io.FileOutputStream");
const Files             = Java.type("java.nio.file.Files");
const FileVisitResult   = Java.type("java.nio.file.FileVisitResult");
const Paths             = Java.type("java.nio.file.Paths");
const SimpleFileVisitor = Java.type("java.nio.file.SimpleFileVisitor");
const ZipEntry          = Java.type("java.util.zip.ZipEntry");
const ZipOutputStream   = Java.type("java.util.zip.ZipOutputStream");

const SimpleFileVisitorExtender = Java.extend(SimpleFileVisitor);

/**
 * Deletes a file or directory, including descendants.
 *
 * @param file [Path] the path to the item to delete..
 */
function recursiveDelete(file) {
  Files.walkFileTree(file, new SimpleFileVisitorExtender({
    postVisitDirectory: function(dir, exception) {
      if (exception != null) {
        throw exception;
      }
      Files.deleteIfExists(dir);
      return FileVisitResult.CONTINUE;
    },
    visitFile: function(file, _attributes) {
      Files.deleteIfExists(file);
      return FileVisitResult.CONTINUE;
    }
  }));
}

/**
 * Zips a directory, creating a zip at the same location with the same name.
 *
 * @param relativePath [String] the relative path from the current working directory.
 */
function zip(relativePath) {
  const zipStream = new ZipOutputStream(new FileOutputStream(relativePath + ".zip"));
  const dir = Paths.get(relativePath);
  try {
    Files.walkFileTree(dir, new SimpleFileVisitorExtender({
      visitFile: function(file, _attributes) {
        const entry = new ZipEntry(file.getFileName().toString());
        zipStream.putNextEntry(entry);
        Files.copy(file, zipStream);
        zipStream.closeEntry();
        return FileVisitResult.CONTINUE;
      }
    }))
  } finally {
    zipStream.close();
  }
}
