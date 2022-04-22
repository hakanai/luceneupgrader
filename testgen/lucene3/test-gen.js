'use strict';

const File                = Java.type("java.io.File");
const WhitespaceAnalyzer  = Java.type("org.apache.lucene.analysis.WhitespaceAnalyzer");
const Document            = Java.type("org.apache.lucene.document.Document");
const Field               = Java.type("org.apache.lucene.document.Field");
const IndexWriter         = Java.type("org.apache.lucene.index.IndexWriter");
const FSDirectory         = Java.type("org.apache.lucene.store.FSDirectory");

const version = arguments[0];

load("../common/common.js");

function createIndex(variant, writerFunction) {
  const name = "build/lucene-" + version + "-" + variant;
  const path = new File(name);
  recursiveDelete(path.toPath());

  print("Creating " + name + " ...");
  const directory = FSDirectory.open(path);
  try {
    const writer = new IndexWriter(directory, new WhitespaceAnalyzer(), true, IndexWriter.MaxFieldLength.UNLIMITED);
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
  document.add(new Field("field", "value", Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.WITH_POSITIONS_OFFSETS));
  writer.addDocument(document);
});
