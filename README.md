Lucene Upgrader
===============

This project attempts to make a single tool to upgrade Lucene indexes all the
way through multiple versions. It does this by repackaging older versions of
Lucene and using the IndexUpgrader tool from each version in turn.


Usage
-----

To upgrade an index all the way to version 8:

    new IndexUpgrader(textIndexPath, null)
        .upgradeTo(LuceneVersion.VERSION_8);

The upgrade will be a no-op if the index is already at that version.

Upgrades can be done one step at a time by passing a different target version.


Building
--------

Lucene Upgrader is using [Apache Buildr](https://buildr.apache.org/).
To build and run all tests:

    buildr

To build the jar and pom files for a distribution:

    buildr package

To generate test Lucene indices, e.g. from one of the `lucene` subdirectories
under `testgen/`:

    buildr run

