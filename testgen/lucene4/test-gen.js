'use strict';

const File                = Java.type("java.io.File");
const Thread              = Java.type("java.lang.Thread");
const WhitespaceAnalyzer  = Java.type("org.apache.lucene.analysis.core.WhitespaceAnalyzer");
const Document            = Java.type("org.apache.lucene.document.Document");
const Field               = Java.type("org.apache.lucene.document.Field");
const IndexWriter         = Java.type("org.apache.lucene.index.IndexWriter");
const IndexWriterConfig   = Java.type("org.apache.lucene.index.IndexWriterConfig");
const FSDirectory         = Java.type("org.apache.lucene.store.FSDirectory");
const Version             = Java.type("org.apache.lucene.util.Version");

const version = arguments[0];

load("../common/common.js");


// Trying to work around a bug in early versions of Lucene 4.x where it wouldn't use
// the right class loader to look for codecs.
Thread.currentThread().setContextClassLoader(IndexWriter.class.getClassLoader());


function createIndex(variant, writerFunction) {
  const name = "build/lucene-" + version + "-" + variant;
  const path = new File(name);
  recursiveDelete(path.toPath());

  print("Creating " + name + " ...");
  const directory = FSDirectory.open(path);
  try {
    const writerConfig = new IndexWriterConfig(Version.LUCENE_CURRENT, new WhitespaceAnalyzer(Version.LUCENE_CURRENT));
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
  document.add(new Field("field", "value", Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.WITH_POSITIONS_OFFSETS));
  writer.addDocument(document);
});
