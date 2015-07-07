
var FileOutputStream  = Java.type("java.io.FileOutputStream");
var Files             = Java.type("java.nio.file.Files");
var Paths             = Java.type("java.nio.file.Paths");
var ZipEntry          = Java.type("java.util.zip.ZipEntry");
var ZipOutputStream   = Java.type("java.util.zip.ZipOutputStream");

/**
 * Deletes a file or directory, including descendants.
 *
 * @param file [Path] the path to the item to delete..
 */
function recursiveDelete(file) {
  if (Files.isDirectory(file)) {
    var directoryStream = Files.newDirectoryStream(file);
    try {
      for each (child in directoryStream) {
        recursiveDelete(child);
      }
    } finally {
      directoryStream.close();
    }
  }
  Files.deleteIfExists(file);
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
    var recurse = function(name, file) {
      if (Files.isDirectory(file)) {
        var directoryStream = Files.newDirectoryStream(file);
        try {
          for each (child in directoryStream) {
            var childName = child.getFileName().toString();
            var childPath = name.isEmpty() ? childName : (name + "/" + childName);
            recurse(childPath, child);
          }
        } finally {
          directoryStream.close();
        }
      } else { // regular file
        var entry = new ZipEntry(name);
        zipStream.putNextEntry(entry);
        Files.copy(file, zipStream);
        zipStream.closeEntry();
      }
    }
    recurse("", dir);
  } finally {
    zipStream.close();
  }
}
