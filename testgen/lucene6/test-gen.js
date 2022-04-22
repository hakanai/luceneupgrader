'use strict';

const WhitespaceAnalyzer  = Java.type("org.apache.lucene.analysis.core.WhitespaceAnalyzer");
const Document            = Java.type("org.apache.lucene.document.Document");
const Field               = Java.type("org.apache.lucene.document.Field");
const TextField           = Java.type("org.apache.lucene.document.TextField");
const IndexWriter         = Java.type("org.apache.lucene.index.IndexWriter");
const IndexWriterConfig   = Java.type("org.apache.lucene.index.IndexWriterConfig");
const FSDirectory         = Java.type("org.apache.lucene.store.FSDirectory");

const version = arguments[0];

load("../common/common.js");

function createIndex(variant, writerFunction) {
  const name = "build/lucene-" + version + "-" + variant;
  const path = Paths.get(name);
  recursiveDelete(path);

  print("Creating " + name + " ...");
  const directory = FSDirectory.open(path);
  try {
    const writerConfig = new IndexWriterConfig(new WhitespaceAnalyzer());
    const writer = new IndexWriter(directory, writerConfig);
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
  const document = new Document();
  document.add(new TextField("field", "value", Field.Store.YES));
  writer.addDocument(document);
});
