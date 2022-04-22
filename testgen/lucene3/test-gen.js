
'use strict';

var File                = Java.type("java.io.File");
var WhitespaceAnalyzer  = Java.type("org.apache.lucene.analysis.WhitespaceAnalyzer");
var Document            = Java.type("org.apache.lucene.document.Document");
var Field               = Java.type("org.apache.lucene.document.Field");
var IndexWriter         = Java.type("org.apache.lucene.index.IndexWriter");
var FSDirectory         = Java.type("org.apache.lucene.store.FSDirectory");

var version = arguments[0];

load("../common/common.js");

function createIndex(variant, writerFunction) {
  var name = "build/lucene-" + version + "-" + variant;
  var path = new File(name);
  recursiveDelete(path.toPath());

  print("Creating " + name + " ...");
  var directory = FSDirectory.open(path);
  try {
    var writer = new IndexWriter(directory, new WhitespaceAnalyzer(), true, IndexWriter.MaxFieldLength.UNLIMITED);
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

