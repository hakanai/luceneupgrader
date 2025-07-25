Lucene Upgrader
===============

This project attempts to make a single tool to upgrade Lucene indexes all the
way through multiple versions. It does this by repackaging older versions of
Lucene and using the IndexUpgrader tool from each version in turn.


Usage as a Library
------------------

Getting the dependency:

```kotlin
dependencies {
    implementation("org.trypticon.luceneupgrader:luceneupgrader:VERSION")
}
```

To upgrade an index all the way to version 9:

```java
new IndexUpgrader(textIndexPath, null)
    .upgradeTo(LuceneVersion.VERSION_9);
```

The upgrade will be a no-op if the index is already at that version.

Upgrades can be done one step at a time by passing a different target version.


Usage of Command-Line Interface
-------------------------------

Running the tool:

```shell
java -jar luceneupgrader-VERSION.jar
```

Given no additional arguments, the tool will output the available commands.


Building
--------

To build and run all tests:

    ./gradlew build

To generate test Lucene indices, e.g. from one of the `lucene` subdirectories
under `testgen/`:

    ../../gradlew runAll

