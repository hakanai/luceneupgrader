
'use strict';

var File                = Java.type("java.io.File");
var ClassLoader         = Java.type("java.lang.ClassLoader");
var Thread              = Java.type("java.lang.Thread");
var Files               = Java.type("java.nio.file.Files");
var Analyzer            = Java.type("org.apache.lucene.analysis.Analyzer");
var WhitespaceAnalyzer  = Java.type("org.apache.lucene.analysis.core.WhitespaceAnalyzer");
var Document            = Java.type("org.apache.lucene.document.Document");
var Field               = Java.type("org.apache.lucene.document.Field");
var IndexWriter         = Java.type("org.apache.lucene.index.IndexWriter");
var IndexWriterConfig   = Java.type("org.apache.lucene.index.IndexWriterConfig");
var FSDirectory         = Java.type("org.apache.lucene.store.FSDirectory");
var Version             = Java.type("org.apache.lucene.util.Version");

var version = arguments[0];

load("../common/common.js");


// Trying to work around a bug in early versions of Lucene 4.x where it wouldn't use
// the right class loader to look for codecs.
Thread.currentThread().setContextClassLoader(IndexWriter.class.getClassLoader());


function createIndex(variant, writerFunction) {
  var name = "build/lucene-" + version + "-" + variant;
  var path = new File(name);
  recursiveDelete(path.toPath());

  print("Creating " + name + " ...");
  var directory = FSDirectory.open(path);
  try {
    var writerConfig = new IndexWriterConfig(Version.LUCENE_CURRENT, new WhitespaceAnalyzer(Version.LUCENE_CURRENT));
    var writer = new IndexWriter(directory, writerConfig);
    try {
      writerFunction(writer);
      writer.commit();
    } finally {
      writer.close();
    }
  } finally {
    directory.close();
  }

  print("Creating " + name + ".zip ...");
  zip(name);
}

createIndex("empty", function(writer) {
  // Add nothing.
});

createIndex("nonempty", function(writer) {
  var document = new Document();
  document.add(new Field("field", "value", Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.WITH_POSITIONS_OFFSETS));
  writer.addDocument(document);
});

