package ai.velr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.arrow.c.ArrowArray;
import org.apache.arrow.c.ArrowSchema;
import org.apache.arrow.c.Data;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.VarCharVector;

public final class FullApiSmokeTest {
    private FullApiSmokeTest() {}

    public static void main(String[] args) throws Exception {
        try (Velr db = Velr.open()) {
            velrValueApi();
            basicRoundTripAndCells(db);
            queryOptionsAndParams(db);
            multiTableStreams(db);
            transactionsAndCallbacks(db);
            scopedSavepoints(db);
            namedSavepoints(db);
            migrationApi(db);
            explainApi(db);
            arrowIpcRoundTrip(db);
        }
        arrowCDataRoundTrip();
        fileBackedAndReadonly();
        optionalFulltextSmoke();
        optionalVectorSmoke();
    }

    private static void velrValueApi() {
        assertEquals(VelrValueType.NULL, VelrValue.nullValue().type(), "VelrValue null type");
        assertEquals(VelrStorageType.NULL, VelrValue.nullValue().storageType(), "VelrValue null storage");
        assertEquals(null, VelrValue.nullValue().asObject(), "VelrValue null object");

        VelrValue bool = VelrValue.bool(true);
        assertEquals(VelrValueType.BOOL, bool.type(), "VelrValue bool type");
        assertEquals(true, bool.asBoolean(), "VelrValue bool accessor");
        assertEquals("true", bool.valueJson(), "VelrValue bool JSON");

        VelrValue int64 = VelrValue.int64(42L);
        assertEquals(VelrValueType.INT64, int64.type(), "VelrValue int64 type");
        assertEquals(42L, int64.asLong(), "VelrValue int64 accessor");

        VelrValue doubleValue = VelrValue.doubleValue(3.5d);
        assertEquals(VelrValueType.DOUBLE, doubleValue.type(), "VelrValue double type");
        assertDoubleEquals(3.5d, doubleValue.asDouble(), "VelrValue double accessor");

        VelrValue string = VelrValue.string("Ada");
        assertEquals(VelrValueType.STRING, string.type(), "VelrValue string type");
        assertEquals("Ada", string.asString(), "VelrValue string accessor");
        assertEquals("\"Ada\"", string.valueJson(), "VelrValue string JSON");
        assertTrue(
                Arrays.equals("Ada".getBytes(StandardCharsets.UTF_8), string.storageBytes()),
                "VelrValue string storage bytes");

        byte[] byteSource = new byte[] {1, 2, 3};
        VelrValue bytes = VelrValue.bytes(byteSource, "\"010203\"", "010203");
        byteSource[0] = 9;
        assertEquals(VelrValueType.BYTES, bytes.type(), "VelrValue bytes type");
        assertTrue(Arrays.equals(new byte[] {1, 2, 3}, bytes.asBytes()), "VelrValue bytes accessor");
        assertTrue(
                Arrays.equals(new byte[] {1, 2, 3}, bytes.storageBytes()),
                "VelrValue bytes storage");

        assertEquals(
                "2024-05-01",
                canonical(VelrValueType.DATE, "\"2024-05-01\"", "2024-05-01").asDateText(),
                "VelrValue date text");
        assertEquals(
                "10:35:00",
                canonical(VelrValueType.LOCAL_TIME, "\"10:35:00\"", "10:35:00")
                        .asLocalTimeText(),
                "VelrValue local time text");
        assertEquals(
                "10:35:00+01:00",
                canonical(VelrValueType.ZONED_TIME, "\"10:35:00+01:00\"", "10:35:00+01:00")
                        .asZonedTimeText(),
                "VelrValue zoned time text");
        assertEquals(
                "2024-05-01T10:35:00",
                canonical(
                                VelrValueType.LOCAL_DATETIME,
                                "\"2024-05-01T10:35:00\"",
                                "2024-05-01T10:35:00")
                        .asLocalDateTimeText(),
                "VelrValue local datetime text");
        assertEquals(
                "2024-05-01T10:35:00+01:00",
                canonical(
                                VelrValueType.ZONED_DATETIME,
                                "\"2024-05-01T10:35:00+01:00\"",
                                "2024-05-01T10:35:00+01:00")
                        .asZonedDateTimeText(),
                "VelrValue zoned datetime text");
        assertEquals(
                "P1DT2H",
                canonical(VelrValueType.DURATION, "\"P1DT2H\"", "P1DT2H").asDurationText(),
                "VelrValue duration text");
        assertEquals(
                "{\"type\":\"Point\",\"coordinates\":[1,2]}",
                canonical(
                                VelrValueType.POINT,
                                "{\"type\":\"Point\",\"coordinates\":[1,2]}",
                                "POINT(1 2)")
                        .asPointGeoJson(),
                "VelrValue point GeoJSON");
        assertEquals(
                "{\"type\":\"LineString\",\"coordinates\":[[0,0],[1,1]]}",
                canonical(
                                VelrValueType.GEOMETRY,
                                "{\"type\":\"LineString\",\"coordinates\":[[0,0],[1,1]]}",
                                "LINESTRING(0 0,1 1)")
                        .asGeometryGeoJson(),
                "VelrValue geometry GeoJSON");
        assertEquals(
                "{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,0]]]}",
                canonical(
                                VelrValueType.GEOGRAPHY,
                                "{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,0]]]}",
                                "POLYGON((0 0,1 0,1 1,0 0))")
                        .asGeographyGeoJson(),
                "VelrValue geography GeoJSON");
        assertEquals(
                "[1,2]",
                canonical(VelrValueType.LIST, "[1,2]", "[1,2]").asListJson(),
                "VelrValue list JSON");
        assertEquals(
                "[0.1,0.2]",
                canonical(VelrValueType.VECTOR, "[0.1,0.2]", "[0.1,0.2]").asVectorJson(),
                "VelrValue vector JSON");

        assertThrows(
                VelrException.class,
                new ThrowingRunnable() {
                    @Override
                    public void run() {
                        VelrValue.string("Ada").asLong();
                    }
                },
                "VelrValue rejects wrong accessor");
    }

    private static VelrValue canonical(VelrValueType type, String valueJson, String display) {
        return VelrValue.canonical(type, VelrStorageType.BLOB, valueJson, display, new byte[] {7});
    }

    private static void basicRoundTripAndCells(Velr db) {
        db.run(
                "CREATE "
                        + "(:SmokePerson {name:'Keanu Reeves', born:1964}), "
                        + "(:SmokeMovie {title:'The Matrix', released:1999, genres:['Sci-Fi','Action']})");

        try (Table table =
                db.execOne(
                        "MATCH (m:SmokeMovie {title:'The Matrix'}) "
                                + "RETURN m.title AS title, m.released AS released, m.genres AS genres")) {
            assertEquals(3, table.columnCount(), "movie column count");
            assertEquals(
                    Arrays.asList("title", "released", "genres"),
                    table.columnNames(),
                    "movie column names");
            List<List<Cell>> rows = table.collect();
            assertEquals(1, rows.size(), "movie row count");
            assertEquals("The Matrix", rows.get(0).get(0).asString(), "movie title");
            assertEquals(1999L, rows.get(0).get(1).asLong(), "movie released");
            assertEquals("[\"Sci-Fi\",\"Action\"]", rows.get(0).get(2).asString(), "movie genres");
        }

        try (Table table =
                db.execOne(
                        "RETURN null AS n, true AS b, 123 AS i, 3.75 AS f, "
                                + "'hello' AS t, ['a','b'] AS arr")) {
            List<Cell> row = singleRow(table);
            assertEquals(CellType.NULL, row.get(0).type(), "null cell type");
            assertEquals(null, row.get(0).asObject(), "null object");
            assertEquals(CellType.BOOL, row.get(1).type(), "bool cell type");
            assertEquals(true, row.get(1).asBoolean(), "bool value");
            assertEquals(CellType.INT64, row.get(2).type(), "int cell type");
            assertEquals(123L, row.get(2).asLong(), "int value");
            assertEquals(CellType.DOUBLE, row.get(3).type(), "double cell type");
            assertDoubleEquals(3.75, row.get(3).asDouble(), "double value");
            assertEquals(CellType.TEXT, row.get(4).type(), "text cell type");
            assertEquals("hello", row.get(4).asString(), "text value");
            assertEquals(CellType.JSON, row.get(5).type(), "list cell type");
            assertEquals("[\"a\",\"b\"]", row.get(5).asString(), "list json value");
        }

        List<Map<String, Object>> maps =
                db.query(
                        "MATCH (m:SmokeMovie) RETURN m.title AS title, m.released AS released");
        assertEquals(1, maps.size(), "query map row count");
        assertEquals("The Matrix", maps.get(0).get("title"), "query map title");
        assertEquals(1999L, maps.get(0).get("released"), "query map released");
    }

    private static void queryOptionsAndParams(Velr db) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("name", "Alice");
        props.put("score", 7L);
        props.put("active", true);
        props.put("tags", Arrays.asList("graph", "driver"));

        db.run(
                "CREATE (:SmokeParam $props)",
                QueryOptions.builder().param("props", props).build());

        try (Table table =
                db.execOne(
                        "MATCH (p:SmokeParam {name:$name}) "
                                + "RETURN p.score AS score, p.active AS active, p.tags AS tags",
                        QueryOptions.builder().param("name", "Alice").build())) {
            List<Cell> row = singleRow(table);
            assertEquals(7L, row.get(0).asLong(), "map param score");
            assertEquals(true, row.get(1).asBoolean(), "map param active");
            assertEquals("[\"graph\",\"driver\"]", row.get(2).asString(), "map param tags");
        }

        try (Table table =
                db.execOne(
                        "RETURN $name AS name, $literal AS literal",
                        QueryOptions.builder()
                                .param("name", "Alice")
                                .param("literal", "MATCH (n) RETURN n")
                                .build())) {
            List<Cell> row = singleRow(table);
            assertEquals("Alice", row.get(0).asString(), "string param");
            assertEquals("MATCH (n) RETURN n", row.get(1).asString(), "literal string param");
        }

        List<Map<String, Object>> capped =
                db.query(
                        "UNWIND [1,2,3,4] AS x WITH x WHERE x >= $min RETURN x ORDER BY x",
                        QueryOptions.builder().param("min", 2L).maxResultRows(2).build());
        assertEquals(Arrays.asList(2L, 3L), values(capped, "x"), "params plus max rows");

        try (Table empty = db.execOne("RETURN 1 AS one, 2 AS two", QueryOptions.maxResultRows(0))) {
            assertEquals(Arrays.asList("one", "two"), empty.columnNames(), "zero cap columns");
            assertEquals(0, empty.collect().size(), "zero cap row count");
        }

        assertThrows(
                IllegalArgumentException.class,
                new ThrowingRunnable() {
                    @Override
                    public void run() {
                        QueryOptions.builder().param("$bad", 1L);
                    }
                },
                "parameter names omit leading dollar");
    }

    private static void multiTableStreams(Velr db) {
        try (Stream stream =
                db.exec(
                        "UNWIND [1,2,3] AS x RETURN x ORDER BY x; "
                                + "UNWIND [10,20,30] AS y RETURN y ORDER BY y",
                        QueryOptions.maxResultRows(1))) {
            Table first = stream.nextTable();
            assertNotNull(first, "first stream table");
            try (Table table = first) {
                assertEquals(Arrays.asList("x"), table.columnNames(), "first stream columns");
                assertEquals(Arrays.asList(1L), firstColumn(table), "first stream capped rows");
            }

            Table second = stream.nextTable();
            assertNotNull(second, "second stream table");
            try (Table table = second) {
                assertEquals(Arrays.asList("y"), table.columnNames(), "second stream columns");
                assertEquals(Arrays.asList(10L), firstColumn(table), "second stream capped rows");
            }

            assertEquals(null, stream.nextTable(), "stream end");
        }

        assertThrows(
                VelrException.class,
                new ThrowingRunnable() {
                    @Override
                    public void run() {
                        try (Table ignored = db.execOne("RETURN 1 AS a; RETURN 2 AS b")) {
                            throw new AssertionError("unreachable");
                        }
                    }
                },
                "execOne rejects multiple result tables");
    }

    private static void transactionsAndCallbacks(Velr db) throws Exception {
        try (VelrTx tx = db.beginTx()) {
            tx.run("CREATE (:SmokeTx {k:'rolled-back'})");
        }
        assertEquals(0L, scalarLong(db, "MATCH (n:SmokeTx) RETURN count(n) AS c"), "tx close rollback");

        try (VelrTx tx = db.beginTx()) {
            tx.run("CREATE (:SmokeTx {k:'committed'})");
            tx.commit();
        }
        assertEquals(1L, scalarLong(db, "MATCH (n:SmokeTx) RETURN count(n) AS c"), "tx commit");

        String value =
                db.transaction(
                        new TransactionCallback<String>() {
                            @Override
                            public String run(VelrTx tx) {
                                tx.run("CREATE (:SmokeTxCallback {k:'kept'})");
                                return "ok";
                            }
                        });
        assertEquals("ok", value, "transaction callback value");
        assertEquals(
                1L,
                scalarLong(db, "MATCH (n:SmokeTxCallback) RETURN count(n) AS c"),
                "transaction callback commit");

        assertThrows(
                IllegalStateException.class,
                new ThrowingRunnable() {
                    @Override
                    public void run() throws Exception {
                        db.transaction(
                                new TransactionCallback<Void>() {
                                    @Override
                                    public Void run(VelrTx tx) {
                                        tx.run("CREATE (:SmokeTxCallback {k:'rolled-back'})");
                                        throw new IllegalStateException("boom");
                                    }
                                });
                    }
                },
                "transaction callback propagates error");
        assertEquals(
                1L,
                scalarLong(db, "MATCH (n:SmokeTxCallback) RETURN count(n) AS c"),
                "transaction callback rollback");

        try (VelrTx tx = db.beginTx()) {
            try (Table table =
                    tx.execOne(
                            "UNWIND [10,20,30] AS x RETURN x ORDER BY x",
                            QueryOptions.maxResultRows(1))) {
                assertEquals(Arrays.asList(10L), firstColumn(table), "tx max rows");
            }
            tx.rollback();
        }
    }

    private static void scopedSavepoints(Velr db) {
        try (VelrTx tx = db.beginTx()) {
            tx.run("CREATE (:SmokeScoped {k:'outer'})");
            try (Savepoint sp = tx.savepoint()) {
                tx.run("CREATE (:SmokeScoped {k:'inner'})");
                sp.rollback();
            }
            try (Savepoint sp = tx.savepoint()) {
                tx.run("CREATE (:SmokeScoped {k:'released'})");
                sp.release();
            }
            tx.commit();
        }
        assertEquals(
                Arrays.asList("outer", "released"),
                labelValues(db, "SmokeScoped"),
                "scoped savepoints");
    }

    private static void namedSavepoints(Velr db) {
        try (VelrTx tx = db.beginTx()) {
            Savepoint sp1 = tx.savepointNamed("sp1");
            tx.run("CREATE (:SmokeNamed {k:'a'})");
            tx.savepointNamed("sp2");
            tx.run("CREATE (:SmokeNamed {k:'b'})");
            tx.rollbackTo("sp1");
            tx.run("CREATE (:SmokeNamed {k:'c'})");
            tx.rollbackTo("sp1");
            tx.run("CREATE (:SmokeNamed {k:'d'})");
            sp1.release();
            tx.commit();
        }
        assertEquals(Arrays.asList("d"), labelValues(db, "SmokeNamed"), "rollbackTo keeps target active");

        try (VelrTx tx = db.beginTx()) {
            tx.savepointNamed("sp3");
            tx.run("CREATE (:SmokeNamedRelease {k:'kept'})");
            tx.releaseSavepoint("sp3");
            assertThrows(
                    VelrException.class,
                    new ThrowingRunnable() {
                        @Override
                        public void run() {
                            tx.rollbackTo("sp3");
                        }
                    },
                    "released named savepoint is gone");
            tx.commit();
        }
        assertEquals(
                Arrays.asList("kept"),
                labelValues(db, "SmokeNamedRelease"),
                "releaseSavepoint keeps changes");

        try (VelrTx tx = db.beginTx()) {
            tx.savepointNamed("sp4");
            tx.run("CREATE (:SmokeNamedCommit {k:'retained'})");
            tx.rollbackTo("sp4");
            tx.run("CREATE (:SmokeNamedCommit {k:'after'})");
            tx.commit();
        }
        assertEquals(
                Arrays.asList("after"),
                labelValues(db, "SmokeNamedCommit"),
                "commit releases active named savepoints");
    }

    private static void migrationApi(Velr db) {
        int schemaVersion = db.schemaVersion();
        int currentSchemaVersion = db.currentSchemaVersion();
        assertEquals(currentSchemaVersion, schemaVersion, "schema is current");
        assertEquals(false, db.needsMigration(), "needs migration");
        MigrationReport report = db.migrate();
        assertEquals(schemaVersion, report.fromVersion(), "migration from version");
        assertEquals(currentSchemaVersion, report.toVersion(), "migration to version");
        assertEquals(MigrationStatus.ALREADY_CURRENT, report.status(), "migration status");
        assertEquals(0, report.steps().size(), "migration steps");
    }

    private static void explainApi(Velr db) {
        try (ExplainTrace trace = db.explain("MATCH (n) RETURN count(n) AS c")) {
            assertNotEmpty(trace.compact(), "explain compact trace");
        }
        try (ExplainTrace trace = db.explainAnalyze("MATCH (n) RETURN count(n) AS c")) {
            assertNotEmpty(trace.toCompactString(), "explain analyze compact trace");
        }
    }

    private static void arrowIpcRoundTrip(Velr db) {
        byte[] ipc;
        try (Table source = db.execOne("UNWIND [1,2,3] AS id RETURN id AS id ORDER BY id")) {
            ipc = source.toArrowIpc();
        }
        assertTrue(ipc.length > 0, "arrow ipc bytes");

        db.bindArrowIpc("_ids_ipc", ipc);
        try (Table out =
                db.execOne("UNWIND BIND('_ids_ipc') AS row RETURN row.id AS id ORDER BY id")) {
            assertEquals(Arrays.asList(1L, 2L, 3L), firstColumn(out), "arrow ipc bind roundtrip");
        }

        try (VelrTx tx = db.beginTx()) {
            tx.bindArrowIpc("_ids_ipc_tx", ipc);
            try (Table out =
                    tx.execOne(
                            "UNWIND BIND('_ids_ipc_tx') AS row RETURN row.id AS id ORDER BY id")) {
                assertEquals(
                        Arrays.asList(1L, 2L, 3L),
                        firstColumn(out),
                        "tx arrow ipc bind roundtrip");
            }
            tx.rollback();
        }
    }

    private static void arrowCDataRoundTrip() {
        try (BufferAllocator allocator = new RootAllocator(Long.MAX_VALUE);
                Velr db = Velr.open()) {
            bindPeopleArrow(
                    allocator,
                    "_people_cdata",
                    new ArrowBinder() {
                        @Override
                        public void bind(String logical, List<ArrowColumn> columns) {
                            db.bindArrow(logical, columns);
                        }
                    });
            try (Table out =
                    db.execOne(
                            "UNWIND BIND('_people_cdata') AS row "
                                    + "RETURN row.id AS id, row.name AS name ORDER BY id")) {
                assertEquals(Arrays.asList("Ada", "Grace", "Linus"), values(out, "name"), "arrow c data names");
                assertEquals(Arrays.asList(1L, 2L, 3L), values(out, "id"), "arrow c data ids");
            }

            try (VelrTx tx = db.beginTx()) {
                bindPeopleArrow(
                        allocator,
                        "_people_cdata_tx",
                        new ArrowBinder() {
                            @Override
                            public void bind(String logical, List<ArrowColumn> columns) {
                                tx.bindArrow(logical, columns);
                            }
                        });
                try (Table out =
                        tx.execOne(
                                "UNWIND BIND('_people_cdata_tx') AS row "
                                        + "RETURN row.id AS id, row.name AS name ORDER BY id")) {
                    assertEquals(
                            Arrays.asList("Ada", "Grace", "Linus"),
                            values(out, "name"),
                            "tx arrow c data names");
                    assertEquals(Arrays.asList(1L, 2L, 3L), values(out, "id"), "tx arrow c data ids");
                }
                tx.rollback();
            }

            bindPeopleArrowChunks(
                    allocator,
                    "_people_chunks",
                    new ArrowBinder() {
                        @Override
                        public void bind(String logical, List<ArrowColumn> columns) {
                            db.bindArrowChunks(logical, columns);
                        }
                    });
            try (Table out =
                    db.execOne(
                            "UNWIND BIND('_people_chunks') AS row "
                                    + "RETURN row.id AS id, row.name AS name ORDER BY id")) {
                assertEquals(Arrays.asList("Ada", "Grace", "Linus"), values(out, "name"), "arrow chunk names");
                assertEquals(Arrays.asList(1L, 2L, 3L), values(out, "id"), "arrow chunk ids");
            }

            try (VelrTx tx = db.beginTx()) {
                bindPeopleArrowChunks(
                        allocator,
                        "_people_chunks_tx",
                        new ArrowBinder() {
                            @Override
                            public void bind(String logical, List<ArrowColumn> columns) {
                                tx.bindArrowChunks(logical, columns);
                            }
                        });
                try (Table out =
                        tx.execOne(
                                "UNWIND BIND('_people_chunks_tx') AS row "
                                        + "RETURN row.id AS id, row.name AS name ORDER BY id")) {
                    assertEquals(
                            Arrays.asList("Ada", "Grace", "Linus"),
                            values(out, "name"),
                            "tx arrow chunk names");
                    assertEquals(Arrays.asList(1L, 2L, 3L), values(out, "id"), "tx arrow chunk ids");
                }
                tx.rollback();
            }
        }
    }

    private static void optionalFulltextSmoke() throws IOException {
        Path dir = Files.createTempDirectory("velr-jvm-fulltext-");
        try (Velr db = Velr.open(dir.resolve("fulltext.db").toString())) {
            db.run(
                    "CREATE "
                            + "(:SmokePaper {title:'Vector Search', abstract:'graph retrieval with embeddings'}), "
                            + "(:SmokePaper {title:'Planner Notes', abstract:'query planning internals'})");
            db.run(
                    "CREATE FULLTEXT INDEX smokePaperText IF NOT EXISTS "
                            + "FOR (n:SmokePaper) ON EACH [n.title, n.abstract]");
            try (Table table =
                    db.execOne(
                            "CALL db.index.fulltext.queryNodes('smokePaperText', 'title:vector') "
                                    + "YIELD node, score RETURN score")) {
                List<Cell> row = singleRow(table);
                assertEquals(CellType.DOUBLE, row.get(0).type(), "fulltext score type");
            }
        } catch (VelrException e) {
            if (!e.getMessage().contains("requires the fulltext-tantivy feature")) {
                throw e;
            }
        } finally {
            deleteTree(dir);
        }
    }

    private static void optionalVectorSmoke() throws IOException {
        Path dir = Files.createTempDirectory("velr-jvm-vector-");
        List<VectorEmbeddingInput> seen = new ArrayList<>();
        try (Velr db = Velr.open(dir.resolve("vector.db").toString())) {
            db.run(
                    "CREATE "
                            + "(:SmokeVectorPaper {title:'Alpha Graphs', abstract:'alpha retrieval with embeddings', pages:12, active:true, published:date('2024-05-01'), tags:['graph','alpha']}), "
                            + "(:SmokeVectorPaper {title:'Beta Notes', abstract:'planner internals', pages:5, active:false, published:date('2024-05-02'), tags:['planner']})");

            db.registerVectorEmbedder(
                    "toy",
                    inputs -> {
                        seen.addAll(inputs);
                        float[][] out = new float[inputs.size()][];
                        for (int i = 0; i < inputs.size(); i++) {
                            VectorEmbeddingInput input = inputs.get(i);
                            assertEquals(3, input.dimensions(), "vector dimensions");
                            if (input.purpose() == VectorEmbeddingPurpose.INDEX_ENTITY) {
                                assertVectorIndexInput(input);
                            } else if (input.purpose() == VectorEmbeddingPurpose.QUERY) {
                                assertVectorQueryInput(input);
                            } else {
                                throw new AssertionError("unexpected vector purpose " + input.purpose());
                            }
                            out[i] = toyVector(input.text(), input.dimensions());
                        }
                        return out;
                    });

            db.run(
                    "CREATE VECTOR INDEX smokePaperEmbedding IF NOT EXISTS "
                            + "FOR (n:SmokeVectorPaper) "
                            + "ON EACH [n.title, n.abstract, n.pages, n.active, n.published, n.tags] "
                            + "OPTIONS { indexConfig: { dimensions: 3, metric: 'cosine', embedder: 'toy' } }");

            try (Table table =
                    db.execOne(
                            "CALL db.index.vector.queryNodes('smokePaperEmbedding', 1, 'alpha query') "
                                    + "YIELD node, score RETURN node, score")) {
                List<Cell> row = singleRow(table);
                assertTrue(row.get(0).asString().contains("Alpha Graphs"), "vector result node");
                assertEquals(CellType.DOUBLE, row.get(1).type(), "vector score type");
                assertTrue(row.get(1).asDouble() > 0.0, "vector score positive");
            }

            assertTrue(
                    sawVectorPurpose(seen, VectorEmbeddingPurpose.INDEX_ENTITY),
                    "saw vector index input");
            assertTrue(sawVectorPurpose(seen, VectorEmbeddingPurpose.QUERY), "saw vector query input");
        } catch (VelrException e) {
            if (!e.getMessage().contains("vector-usearch")) {
                throw e;
            }
        } finally {
            deleteTree(dir);
        }
    }

    private static void fileBackedAndReadonly() throws IOException {
        Path path = Files.createTempFile("velr-jvm-smoke-", ".db");
        Files.deleteIfExists(path);
        try {
            try (Velr db = Velr.open(path.toString())) {
                db.run("CREATE (:SmokeFile {k:'persisted'})");
            }

            try (Velr db = Velr.openReadonly(path.toString())) {
                assertEquals(
                        "persisted",
                        scalarObject(db, "MATCH (n:SmokeFile) RETURN n.k AS k"),
                        "readonly query");
                assertThrows(
                        VelrException.class,
                        new ThrowingRunnable() {
                            @Override
                            public void run() {
                                db.run("CREATE (:SmokeFile {k:'blocked'})");
                            }
                        },
                        "readonly write rejected");
            }
        } finally {
            deleteIfExists(path);
            deleteIfExists(path.resolveSibling(path.getFileName() + "-wal"));
            deleteIfExists(path.resolveSibling(path.getFileName() + "-shm"));
            deleteIfExists(path.resolveSibling(path.getFileName() + "-journal"));
        }
    }

    private static List<Cell> singleRow(Table table) {
        List<List<Cell>> rows = table.collect();
        assertEquals(1, rows.size(), "single row count");
        return rows.get(0);
    }

    private static long scalarLong(Velr db, String cypher) {
        Object value = scalarObject(db, cypher);
        if (!(value instanceof Long)) {
            throw new AssertionError("expected Long scalar, got " + value);
        }
        return ((Long) value).longValue();
    }

    private static Object scalarObject(Velr db, String cypher) {
        try (Table table = db.execOne(cypher)) {
            return singleRow(table).get(0).asObject();
        }
    }

    private static List<Object> firstColumn(Table table) {
        List<Object> out = new ArrayList<>();
        try (Rows rows = table.rows()) {
            List<Cell> row;
            while ((row = rows.next()) != null) {
                out.add(row.get(0).asObject());
            }
        }
        return out;
    }

    private static List<Object> values(Table table, String column) {
        List<Object> out = new ArrayList<>();
        for (Map<String, Object> row : table.toMaps()) {
            out.add(row.get(column));
        }
        return out;
    }

    private static List<String> labelValues(Velr db, String label) {
        List<String> out = new ArrayList<>();
        try (Table table = db.execOne("MATCH (n:" + label + ") RETURN n.k AS k ORDER BY k")) {
            for (Object value : firstColumn(table)) {
                out.add((String) value);
            }
        }
        return out;
    }

    private static void bindPeopleArrow(BufferAllocator allocator, String logical, ArrowBinder binder) {
        try (BigIntVector id = new BigIntVector("id", allocator);
                VarCharVector name = new VarCharVector("name", allocator);
                ArrowArray idArray = ArrowArray.allocateNew(allocator);
                ArrowSchema idSchema = ArrowSchema.allocateNew(allocator);
                ArrowArray nameArray = ArrowArray.allocateNew(allocator);
                ArrowSchema nameSchema = ArrowSchema.allocateNew(allocator)) {
            id.allocateNew(3);
            id.setSafe(0, 1L);
            id.setSafe(1, 2L);
            id.setSafe(2, 3L);
            id.setValueCount(3);

            name.allocateNew();
            setUtf8(name, 0, "Ada");
            setUtf8(name, 1, "Grace");
            setUtf8(name, 2, "Linus");
            name.setValueCount(3);

            Data.exportVector(allocator, id, null, idArray, idSchema);
            Data.exportVector(allocator, name, null, nameArray, nameSchema);
            try {
                binder.bind(
                        logical,
                        Arrays.asList(
                                ArrowColumn.cData(
                                        "id", idSchema.memoryAddress(), idArray.memoryAddress()),
                                ArrowColumn.cData(
                                        "name",
                                        nameSchema.memoryAddress(),
                                        nameArray.memoryAddress())));
            } finally {
                idSchema.release();
                nameSchema.release();
            }
        }
    }

    private static void bindPeopleArrowChunks(
            BufferAllocator allocator, String logical, ArrowBinder binder) {
        try (BigIntVector idA = new BigIntVector("id_a", allocator);
                BigIntVector idB = new BigIntVector("id_b", allocator);
                VarCharVector nameA = new VarCharVector("name_a", allocator);
                VarCharVector nameB = new VarCharVector("name_b", allocator);
                ArrowArray idArrayA = ArrowArray.allocateNew(allocator);
                ArrowSchema idSchemaA = ArrowSchema.allocateNew(allocator);
                ArrowArray idArrayB = ArrowArray.allocateNew(allocator);
                ArrowSchema idSchemaB = ArrowSchema.allocateNew(allocator);
                ArrowArray nameArrayA = ArrowArray.allocateNew(allocator);
                ArrowSchema nameSchemaA = ArrowSchema.allocateNew(allocator);
                ArrowArray nameArrayB = ArrowArray.allocateNew(allocator);
                ArrowSchema nameSchemaB = ArrowSchema.allocateNew(allocator)) {
            idA.allocateNew(2);
            idA.setSafe(0, 1L);
            idA.setSafe(1, 2L);
            idA.setValueCount(2);

            idB.allocateNew(1);
            idB.setSafe(0, 3L);
            idB.setValueCount(1);

            nameA.allocateNew();
            setUtf8(nameA, 0, "Ada");
            setUtf8(nameA, 1, "Grace");
            nameA.setValueCount(2);

            nameB.allocateNew();
            setUtf8(nameB, 0, "Linus");
            nameB.setValueCount(1);

            Data.exportVector(allocator, idA, null, idArrayA, idSchemaA);
            Data.exportVector(allocator, idB, null, idArrayB, idSchemaB);
            Data.exportVector(allocator, nameA, null, nameArrayA, nameSchemaA);
            Data.exportVector(allocator, nameB, null, nameArrayB, nameSchemaB);
            try {
                binder.bind(
                        logical,
                        Arrays.asList(
                                ArrowColumn.chunks(
                                        "id",
                                        ArrowColumn.chunk(
                                                idSchemaA.memoryAddress(),
                                                idArrayA.memoryAddress()),
                                        ArrowColumn.chunk(
                                                idSchemaB.memoryAddress(),
                                                idArrayB.memoryAddress())),
                                ArrowColumn.chunks(
                                        "name",
                                        ArrowColumn.chunk(
                                                nameSchemaA.memoryAddress(),
                                                nameArrayA.memoryAddress()),
                                        ArrowColumn.chunk(
                                                nameSchemaB.memoryAddress(),
                                                nameArrayB.memoryAddress()))));
            } finally {
                idSchemaA.release();
                idSchemaB.release();
                nameSchemaA.release();
                nameSchemaB.release();
            }
        }
    }

    private static void setUtf8(VarCharVector vector, int index, String value) {
        vector.setSafe(index, value.getBytes(StandardCharsets.UTF_8));
    }

    private interface ArrowBinder {
        void bind(String logical, List<ArrowColumn> columns);
    }

    private static List<Object> values(List<Map<String, Object>> rows, String column) {
        List<Object> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            out.add(row.get(column));
        }
        return out;
    }

    private static void assertVectorIndexInput(VectorEmbeddingInput input) {
        assertEquals("smokePaperEmbedding", input.indexName(), "vector index name");
        assertEquals(VectorEntityKind.NODE, input.entityKind(), "vector entity kind");
        assertNotNull(input.entityId(), "vector entity id");

        Map<String, VectorEmbeddingField> fields = new LinkedHashMap<>();
        for (VectorEmbeddingField field : input.fields()) {
            fields.put(field.name(), field);
        }
        assertEquals(
                Arrays.asList("title", "abstract", "pages", "active", "published", "tags"),
                new ArrayList<>(fields.keySet()),
                "vector field order");

        VectorEmbeddingField title = fields.get("title");
        assertEquals(VectorValueType.STRING, title.valueType(), "vector title type");
        assertEquals(VelrValueType.STRING, title.velrValue().type(), "VelrValue title type");
        assertEquals(
                "Alpha Graphs".equals(title.velrValue().asString())
                        || "Beta Notes".equals(title.velrValue().asString()),
                true,
                "vector title value");
        assertNotEmpty(title.display(), "vector title display");

        VectorEmbeddingField pages = fields.get("pages");
        assertEquals(VectorValueType.INT64, pages.valueType(), "vector pages type");
        assertEquals(VelrValueType.INT64, pages.velrValue().type(), "VelrValue pages type");
        assertTrue(pages.velrValue().asLong() > 0L, "vector pages value");

        VectorEmbeddingField active = fields.get("active");
        assertEquals(VectorValueType.BOOL, active.valueType(), "vector active type");
        assertEquals(VelrValueType.BOOL, active.velrValue().type(), "VelrValue active type");
        active.velrValue().asBoolean();

        VectorEmbeddingField published = fields.get("published");
        assertEquals(VectorValueType.DATE, published.valueType(), "vector published type");
        assertEquals(VelrValueType.DATE, published.velrValue().type(), "VelrValue published type");
        assertTrue(
                "2024-05-01".equals(published.velrValue().asDateText())
                        || "2024-05-02".equals(published.velrValue().asDateText()),
                "VelrValue published date");

        VectorEmbeddingField tags = fields.get("tags");
        assertEquals(VectorValueType.LIST, tags.valueType(), "vector tags type");
        assertEquals(VelrValueType.LIST, tags.velrValue().type(), "VelrValue tags type");
        assertTrue(tags.velrValue().asListJson().startsWith("["), "vector tags json");
        assertNotEmpty(tags.display(), "vector tags display");
    }

    private static void assertVectorQueryInput(VectorEmbeddingInput input) {
        assertEquals("smokePaperEmbedding", input.indexName(), "vector query index name");
        assertEquals(null, input.entityKind(), "vector query entity kind");
        assertEquals(null, input.entityId(), "vector query entity id");
        assertEquals(1, input.fields().size(), "vector query field count");
        VectorEmbeddingField field = input.fields().get(0);
        assertEquals(null, field.name(), "vector query field name");
        assertEquals(VectorValueType.STRING, field.valueType(), "vector query field type");
        assertEquals(VelrValueType.STRING, field.velrValue().type(), "VelrValue query field type");
        assertEquals("alpha query", field.velrValue().asString(), "VelrValue query text value");
        assertEquals("alpha query", field.value(), "vector query text value");
        assertEquals("alpha query", input.text(), "vector query text");
    }

    private static float[] toyVector(String text, int dimensions) {
        String lower = text.toLowerCase();
        float[] out = new float[dimensions];
        if (dimensions > 0) {
            out[0] = lower.contains("alpha") ? 1.0f : 0.0f;
        }
        if (dimensions > 1) {
            out[1] = lower.contains("beta") || lower.contains("planner") ? 1.0f : 0.0f;
        }
        if (dimensions > 2) {
            out[2] = lower.contains("graph") || lower.contains("query") ? 0.5f : 0.1f;
        }
        return out;
    }

    private static boolean sawVectorPurpose(
            List<VectorEmbeddingInput> inputs, VectorEmbeddingPurpose purpose) {
        for (VectorEmbeddingInput input : inputs) {
            if (input.purpose() == purpose) {
                return true;
            }
        }
        return false;
    }

    private static void deleteIfExists(Path path) throws IOException {
        Files.deleteIfExists(path);
    }

    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(path)) {
            java.util.Iterator<Path> iterator =
                    paths.sorted(Comparator.reverseOrder()).iterator();
            while (iterator.hasNext()) {
                Files.deleteIfExists(iterator.next());
            }
        }
    }

    private static void assertEquals(Object expected, Object actual, String what) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertDoubleEquals(double expected, double actual, String what) {
        if (Math.abs(expected - actual) > 0.000000000001d) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertTrue(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what + ": expected true");
        }
    }

    private static void assertNotNull(Object value, String what) {
        if (value == null) {
            throw new AssertionError(what + ": expected non-null");
        }
    }

    private static void assertNotEmpty(String value, String what) {
        if (value == null || value.isEmpty()) {
            throw new AssertionError(what + ": expected non-empty string");
        }
    }

    private static void assertThrows(
            Class<? extends Throwable> expected, ThrowingRunnable runnable, String what) {
        try {
            runnable.run();
        } catch (Throwable actual) {
            if (expected.isInstance(actual)) {
                return;
            }
            throw new AssertionError(
                    what + ": expected " + expected.getName() + ", got " + actual, actual);
        }
        throw new AssertionError(what + ": expected " + expected.getName());
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
