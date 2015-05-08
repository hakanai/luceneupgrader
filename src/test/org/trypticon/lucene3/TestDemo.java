package org.trypticon.lucene3;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;

import org.trypticon.lucene3.analysis.Analyzer;
import org.trypticon.lucene3.analysis.MockAnalyzer;
import org.trypticon.lucene3.document.Document;
import org.trypticon.lucene3.document.Field;
import org.trypticon.lucene3.index.IndexReader;
import org.trypticon.lucene3.index.RandomIndexWriter;
import org.trypticon.lucene3.queryParser.ParseException;
import org.trypticon.lucene3.queryParser.QueryParser;
import org.trypticon.lucene3.search.TopDocs;
import org.trypticon.lucene3.search.IndexSearcher;
import org.trypticon.lucene3.search.Query;
import org.trypticon.lucene3.store.Directory;
import org.trypticon.lucene3.util.LuceneTestCase;

/**
 * A very simple demo used in the API documentation (src/java/overview.html).
 *
 * Please try to keep src/java/overview.html up-to-date when making changes
 * to this class.
 */
public class TestDemo extends LuceneTestCase {

  public void testDemo() throws IOException, ParseException {
    Analyzer analyzer = new MockAnalyzer(random);

    // Store the index in memory:
    Directory directory = newDirectory();
    // To store an index on disk, use this instead:
    //Directory directory = FSDirectory.open("/tmp/testindex");
    RandomIndexWriter iwriter = new RandomIndexWriter(random, directory, analyzer);
    iwriter.w.setInfoStream(VERBOSE ? System.out : null);
    Document doc = new Document();
    String text = "This is the text to be indexed.";
    doc.add(newField("fieldname", text, Field.Store.YES,
        Field.Index.ANALYZED));
    iwriter.addDocument(doc);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = IndexReader.open(directory); // read-only=true
    IndexSearcher isearcher = new IndexSearcher(ireader);
    // Parse a simple query that searches for "text":
    QueryParser parser = new QueryParser(TEST_VERSION_CURRENT, "fieldname", analyzer);
    Query query = parser.parse("text");
    TopDocs hits = isearcher.search(query, null, 1);
    assertEquals(1, hits.totalHits);
    // Iterate through the results:
    for (int i = 0; i < hits.scoreDocs.length; i++) {
      Document hitDoc = isearcher.doc(hits.scoreDocs[i].doc);
      assertEquals("This is the text to be indexed.", hitDoc.get("fieldname"));
    }

    // Test simple phrase query
    query = parser.parse("\"to be\"");
    assertEquals(1, isearcher.search(query, null, 1).totalHits);

    isearcher.close();
    ireader.close();
    directory.close();
  }
}
