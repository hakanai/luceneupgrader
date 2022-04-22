
var FileOutputStream  = Java.type("java.io.FileOutputStream");
var Files             = Java.type("java.nio.file.Files");
var FileVisitResult   = Java.type("java.nio.file.FileVisitResult");
var Paths             = Java.type("java.nio.file.Paths");
var SimpleFileVisitor = Java.type("java.nio.file.SimpleFileVisitor");
var ZipEntry          = Java.type("java.util.zip.ZipEntry");
var ZipOutputStream   = Java.type("java.util.zip.ZipOutputStream");

var SimpleFileVisitorExtender = Java.extend(SimpleFileVisitor);

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
  var zipStream = new ZipOutputStream(new FileOutputStream(relativePath + ".zip"));
  var dir = Paths.get(relativePath);
  try {
    Files.walkFileTree(dir, new SimpleFileVisitorExtender({
      visitFile: function(file, _attributes) {
        var entry = new ZipEntry(file.getFileName().toString());
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
