'use strict';

const File                = Java.type("java.io.File");
const WhitespaceAnalyzer  = Java.type("org.apache.lucene.analysis.WhitespaceAnalyzer");
const Document            = Java.type("org.apache.lucene.document.Document");
const Field               = Java.type("org.apache.lucene.document.Field");
const IndexWriter         = Java.type("org.apache.lucene.index.IndexWriter");

const version = arguments[0];

load("../common/common.js");

function createIndex(variant, writerFunction) {
  const name = "build/lucene-" + version + "-" + variant;
  const path = new File(name);
  recursiveDelete(path.toPath());


  print("Creating " + name + " ...");
  const writer = new IndexWriter(path, new WhitespaceAnalyzer(), true);
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
  const document = new Document();
  document.add(new Field("field", "value", true, true, true));
  writer.addDocument(document);
});
