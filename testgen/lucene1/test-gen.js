
'use strict';

var File                = Java.type("java.io.File");
var Files               = Java.type("java.nio.file.Files");
var Analyzer            = Java.type("org.apache.lucene.analysis.Analyzer");
var WhitespaceAnalyzer  = Java.type("org.apache.lucene.analysis.WhitespaceAnalyzer");
var Document            = Java.type("org.apache.lucene.document.Document");
var Field               = Java.type("org.apache.lucene.document.Field");
var IndexWriter         = Java.type("org.apache.lucene.index.IndexWriter");

var version = arguments[0];

load("../common/common.js");

function createIndex(variant, writerFunction) {
  var name = "target/lucene-" + version + "-" + variant;
  var path = new File(name);
  recursiveDelete(path.toPath());

  print("Creating " + name + " ...");
  var writer = new IndexWriter(path, new WhitespaceAnalyzer(), true);
  try {
    writerFunction(writer);
  } finally {
    writer.close();
  }

  print("Creating " + name + ".zip ...");
  zip(name);
}

createIndex("empty", function(writer) {
  // Add nothing.
});

createIndex("nonempty", function(writer) {
  var document = new Document();
  document.add(new Field("field", "value", true, true, true));
  writer.addDocument(document);
});

