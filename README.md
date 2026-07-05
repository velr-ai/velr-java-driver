# Velr

Velr is an embedded property-graph database from Velr.ai, written in Rust,
built on top of SQLite (persisting to a standard SQLite database file) and
queried using the openCypher language.

It runs in-process and is designed for local, embedded, and edge use cases.

This package provides the **Java bindings** for Velr on JVM and Android. It wraps a bundled native runtime through a Rust JNI bridge and exposes an idiomatic API for executing Cypher queries, streaming result tables, working with transactions and savepoints, binding parameters, registering vector embedders, inspecting migrations, and working with Arrow C Data and Arrow IPC.

For the main Velr public entry point, see [velr-ai/velr](https://github.com/velr-ai/velr).
For the Velr website, see [velr.ai](https://velr.ai/).

## Community

- **Community and questions:** [GitHub Discussions](https://github.com/velr-ai/velr/discussions)
- **Bug reports and feature requests:** [GitHub Issues](https://github.com/velr-ai/velr/issues)
- **Java examples:** [velr-java-examples](https://github.com/velr-ai/velr-java-examples)

We would love to have you join the Velr community.

---

## Installation

Add the published driver artifact to your project.

### JVM

Gradle:

```kotlin
dependencies {
    implementation("ai.velr:velr-java-driver:VERSION")
}
```

Maven:

```xml
<dependency>
    <groupId>ai.velr</groupId>
    <artifactId>velr-java-driver</artifactId>
    <version>VERSION</version>
</dependency>
```

### Android

Gradle:

```kotlin
dependencies {
    implementation("ai.velr:velr-java-driver-android:VERSION")
}
```

Maven:

```xml
<dependency>
    <groupId>ai.velr</groupId>
    <artifactId>velr-java-driver-android</artifactId>
    <version>VERSION</version>
    <type>aar</type>
</dependency>
```

The JVM artifact ships native libraries for supported desktop platforms. The
Android artifact is an AAR that packages ABI-specific `libvelr_jni.so` files.
Applications install the Maven artifact and use the API directly; no separate
native runtime package or engine download is required.

### Licensing in simple terms

* The **Java binding source code** in this package is licensed under **MIT**.
* The **bundled native runtime binaries** may be **used and freely redistributed in unmodified form** under the terms of **`LICENSE.runtime`**.

---

## Quick start

```java
import ai.velr.Table;
import ai.velr.Velr;

try (Velr db = Velr.open()) {
    db.run("CREATE (:Person {name:'Keanu Reeves', born:1964})");
    try (Table table = db.execOne("MATCH (p:Person) RETURN p.name AS name, p.born AS born")) {
        System.out.println(table.columnNames());
        System.out.println(table.toMaps());
    }
}
```

Open a file-backed database instead of an in-memory database:

```java
import ai.velr.Velr;

try (Velr db = Velr.open("mygraph.db")) {
    db.run("CREATE (:Person {name:'Alice'})");
}
```

Open an existing database for reads only:

```java
import ai.velr.Velr;
import java.util.List;
import java.util.Map;

try (Velr db = Velr.openReadonly("mygraph.db")) {
    List<Map<String, Object>> rows = db.query("MATCH (n) RETURN count(n) AS count");
    System.out.println(rows);
}
```

`openReadonly()` never creates, initializes, migrates, or repairs a database.
The file must already exist and have a supported Velr schema version. Older
supported databases remain available for reads. Writes and features that
require the current schema fail with a normal query error until the database is
explicitly migrated.

---

## Schema migration

Velr does not migrate supported older databases automatically on open. Use the
driver migration API, or run `MIGRATE DATABASE`, from maintenance code when you
intend to update the on-disk schema.

```java
import ai.velr.MigrationReport;
import ai.velr.Velr;

try (Velr db = Velr.open("mygraph.db")) {
    if (db.needsMigration()) {
        MigrationReport report = db.migrate();
        System.out.println(report.status() + " " + report.fromVersion() + " -> " + report.toVersion());
    }
}
```

The equivalent Cypher command is useful for scripts and tools that already work
through query execution:

```cypher
MIGRATE DATABASE
```

---

## Introspection

Use `SHOW CURRENT GRAPH SHAPE` to inspect the observed schema of the graph. It
reports the shape present in stored data: node labels, relationship types,
properties, observed value types, and counts. It is an observed shape surface,
not a declared GQL graph type.

```java
try (Velr db = Velr.open("mygraph.db");
     Table table = db.execOne(
             "SHOW CURRENT GRAPH SHAPE " +
             "YIELD element_kind, element_name, property_name, observed_type, owner_count " +
             "WHERE element_kind = 'node_property' " +
             "RETURN element_name, property_name, observed_type, owner_count")) {
    System.out.println(table.toMaps());
}
```

Use `YIELD` to compose the command with `WHERE` and `RETURN`. Plain
`SHOW CURRENT GRAPH SHAPE` returns the default projection; `YIELD *` exposes the
full current row shape.

---

## Fulltext Search

Fulltext search is available through normal Cypher execution. Define indexes
with `CREATE FULLTEXT INDEX` and query them with
`CALL db.index.fulltext.queryNodes(...)`.

```java
try (Velr db = Velr.open("mygraph.db")) {
    db.run(
            "CREATE FULLTEXT INDEX paperText " +
            "FOR (n:Paper) ON EACH [n.title, n.abstract]");

    try (Table table = db.execOne(
            "CALL db.index.fulltext.queryNodes('paperText', 'abstract:vector') " +
            "YIELD node, score " +
            "RETURN node, score")) {
        System.out.println(table.toMaps());
    }
}
```

Fulltext indexes use a sidecar next to file-backed databases. The sidecar is
kept up to date by writes and rebuilt on open if it is missing or corrupt.

---

## Vector Search

Register an embedding callback, then reference it from `CREATE VECTOR INDEX`.
Velr invokes the callback for index maintenance when indexed source values
change and for text queries passed to `CALL db.index.vector.queryNodes(...)`.

```java
import ai.velr.Table;
import ai.velr.Velr;
import ai.velr.VelrValue;
import ai.velr.VelrValueType;
import ai.velr.VectorEmbeddingInput;
import ai.velr.VectorEmbeddingPurpose;
import java.util.function.BiFunction;

BiFunction<String, Integer, float[]> embedText = (text, dimensions) -> {
    // Call your embedding model here.
    return new float[dimensions];
};

try (Velr db = Velr.open("mygraph.db")) {
    db.registerVectorEmbedder("text", inputs -> {
        float[][] vectors = new float[inputs.size()][];
        for (int i = 0; i < inputs.size(); i++) {
            VectorEmbeddingInput input = inputs.get(i);
            String text =
                    input.fields().stream()
                            .map(field -> {
                                VelrValue value = field.velrValue();
                                if (value.type() == VelrValueType.STRING) {
                                    return value.asString();
                                }
                                return value.display();
                            })
                            .collect(java.util.stream.Collectors.joining("\n"));
            String prefix =
                    input.purpose() == VectorEmbeddingPurpose.QUERY ? "query: " : "passage: ";
            vectors[i] = embedText.apply(prefix + text, input.dimensions());
        }
        return vectors;
    });

    db.run(
            "CREATE VECTOR INDEX paperEmbedding IF NOT EXISTS " +
            "FOR (n:Paper) " +
            "ON EACH [n.title, n.abstract, n.published, n.tags] " +
            "OPTIONS { indexConfig: { dimensions: 384, metric: 'cosine', embedder: 'text' } }");

    try (Table table = db.execOne(
            "CALL db.index.vector.queryNodes('paperEmbedding', 10, 'paper about greek letters') " +
            "YIELD node, score " +
            "RETURN node, score")) {
        System.out.println(table.toMaps());
    }
}
```

The callback must return one finite `float[]` per input, with
exactly the dimension count requested by the index.

`ON EACH [n.title, n.abstract, n.published, n.tags]` passes property values to
the callback in that order. Query text is passed as one unnamed string field.

`VectorEmbeddingInput` exposes the index name, dimensions, purpose, entity
kind, entity id, and selected fields. `VectorEmbeddingField.velrValue()` returns
a typed `VelrValue` for the source property. It covers null, bool, int64,
double, string, date, local time, zoned time, local datetime, zoned datetime,
duration, point, geometry, geography, list, vector, and bytes. Scalars have
direct Java/Kotlin accessors; temporal values expose canonical text; spatial
values expose GeoJSON; list and vector values expose canonical JSON; byte values
expose copied byte arrays. The value also exposes the storage kind, canonical
JSON rendering, display text, and raw TEXT/BLOB storage bytes when available.
`VectorEmbeddingInput.text()` joins field display strings for simple local or
toy embedders.

Vector `score` is metric-dependent and non-normalized; higher scores are better
within a single query result set. Vector indexes use a sidecar next to
file-backed databases.

---

## Query model

A query may produce zero or more result tables.

Velr exposes three main ways to run Cypher:

* `run()` executes a query or script and drains all result tables.
* `exec()` returns a stream of result tables.
* `execOne()` expects exactly one result table.

Use `exec()` when a query or script may produce multiple result tables. Tables
pulled from `exec()` are stream-scoped and remain valid while the stream is
open. Tables returned by `execOne()` are parent-scoped and remain valid until
the owning connection or transaction closes, or until the table is closed.

### Query parameter binding

Use `QueryOptions` to bind openCypher parameters out of band. Query text uses
`$name`; parameter names in host code omit the leading `$`. Values are passed as
Cypher values, not interpolated into query text.

```java
QueryOptions options = QueryOptions.builder()
        .param("name", "Alice")
        .param("minAge", 18L)
        .maxResultRows(10)
        .build();

try (Velr db = Velr.open();
     Table table = db.execOne(
             "MATCH (p:Person) WHERE p.name = $name AND p.age >= $minAge RETURN p",
             options)) {
    System.out.println(table.toMaps());
}
```

`maxResultRows` caps rows returned by each result table. Existing Cypher
`LIMIT` clauses still apply.

---

## Transactions and savepoints

Use `beginTx()` to open a transaction. Closing a transaction without `commit()`
rolls it back.

```java
try (Velr db = Velr.open("mygraph.db")) {
    try (VelrTx tx = db.beginTx()) {
        tx.run("CREATE (:Movie {title:'Interstellar', released:2014})");
        tx.commit();
    }
}
```

Velr supports two savepoint styles:

* `savepoint()` creates a scoped, handle-owned savepoint.
* `savepointNamed(name)` creates a transaction-owned named savepoint.

`rollbackTo(name)` rolls back to a named savepoint, discards newer named
savepoints, and keeps the target named savepoint active.
`releaseSavepoint(name)` releases a named savepoint by name.

---

## Arrow

Velr can bind live Arrow C Data Interface columns under a logical name and
query them from Cypher with `UNWIND BIND(...)`.

```java
import ai.velr.ArrowColumn;
import ai.velr.Table;
import ai.velr.Velr;
import org.apache.arrow.c.ArrowArray;
import org.apache.arrow.c.ArrowSchema;
import org.apache.arrow.c.Data;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.BigIntVector;

try (RootAllocator allocator = new RootAllocator(Long.MAX_VALUE);
     Velr db = Velr.open();
     BigIntVector id = new BigIntVector("id", allocator);
     ArrowArray array = ArrowArray.allocateNew(allocator);
     ArrowSchema schema = ArrowSchema.allocateNew(allocator)) {
    id.allocateNew(3);
    id.setSafe(0, 1L);
    id.setSafe(1, 2L);
    id.setSafe(2, 3L);
    id.setValueCount(3);

    Data.exportVector(allocator, id, null, array, schema);
    try {
        db.bindArrow(
                "_ids",
                ArrowColumn.cData("id", schema.memoryAddress(), array.memoryAddress()));
    } finally {
        schema.release();
    }

    try (Table table =
            db.execOne("UNWIND BIND('_ids') AS row RETURN row.id AS id ORDER BY id")) {
        System.out.println(table.toMaps());
    }
}
```

`bindArrow()` accepts single-chunk `ArrowColumn` values built from
`ArrowSchema` and `ArrowArray` struct addresses. `bindArrowChunks()` accepts
one or more chunks per column using `ArrowColumn.chunks(...)`. The schema is
borrowed for the duration of the call. The array payload is transferred to Velr
by the call; after calling, close wrapper objects that own the struct memory,
but do not call the `ArrowArray` release callback.

Velr can also export result tables as Arrow IPC file bytes and bind Arrow IPC
input under a logical name.

```java
try (Velr db = Velr.open()) {
    byte[] ipc;
    try (Table table = db.execOne("UNWIND [1,2,3] AS id RETURN id AS id ORDER BY id")) {
        ipc = table.toArrowIpc();
    }

    db.bindArrowIpc("_ids", ipc);
    try (Table table = db.execOne("UNWIND BIND('_ids') AS row RETURN row.id AS id ORDER BY id")) {
        System.out.println(table.toMaps());
    }
}
```

`bindArrowIpc()` accepts Arrow IPC file / Feather v2 bytes and borrows the
provided byte array for the duration of the call. Arrow C Data and Arrow IPC
binding are available on both database connections and transactions.

---

## Explain support

Use `explain()` and `explainAnalyze()` to inspect query plans.

```java
try (Velr db = Velr.open();
     ExplainTrace trace = db.explain("MATCH (p:Person) RETURN p.name AS name")) {
    System.out.println(trace.compact());
}
```

---

## Query language support

Velr supports the openCypher query language and passes all positive openCypher
TCK tests. Exact error semantics are not guaranteed to match other openCypher
implementations.

Fulltext search and vector search are available through Cypher DDL and `CALL`
syntax.

---

## Thread safety

Velr connections and active result handles are not safe for concurrent use from
multiple threads. Open one connection per thread and do not share active
connections, transactions, streams, tables, row iterators, savepoints, or
explain traces across threads.

The API is synchronous. Applications that need scheduling can run database work
through standard JVM executors, Android dispatchers, or Kotlin coroutines while
preserving the ownership rules above.

---

## License

See [`LICENSE`](LICENSE) and [`LICENSE.runtime`](LICENSE.runtime).
