
'use strict';

var Files               = Java.type("java.nio.file.Files");
var Paths               = Java.type("java.nio.file.Paths");
var Analyzer            = Java.type("org.apache.lucene.analysis.Analyzer");
var WhitespaceAnalyzer  = Java.type("org.apache.lucene.analysis.core.WhitespaceAnalyzer");
var Document            = Java.type("org.apache.lucene.document.Document");
var Field               = Java.type("org.apache.lucene.document.Field");
var TextField           = Java.type("org.apache.lucene.document.TextField");
var IndexWriter         = Java.type("org.apache.lucene.index.IndexWriter");
var IndexWriterConfig   = Java.type("org.apache.lucene.index.IndexWriterConfig");
var FSDirectory         = Java.type("org.apache.lucene.store.FSDirectory");
var Version             = Java.type("org.apache.lucene.util.Version");

var version = arguments[0];

load("../common/common.js");

function createIndex(variant, writerFunction) {
  var name = "target/lucene-" + version + "-" + variant;
  var path = Paths.get(name);
  recursiveDelete(path);

  print("Creating " + name + " ...");
  var directory = FSDirectory.open(path);
  try {
    var writerConfig = new IndexWriterConfig(new WhitespaceAnalyzer());
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
  document.add(new TextField("field", "value", Field.Store.YES));
  writer.addDocument(document);
});

