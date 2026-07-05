package ai.velr;

import java.util.List;

public final class LifecycleSmokeTest {
    private LifecycleSmokeTest() {}

    public static void main(String[] args) {
        execTableIsStreamScopedAndClosedWithStream();
        execOneTableSurvivesMethodReturn();
        execOneTableIsClosedWhenConnectionCloses();
        connectionCloseClosesOpenStreamsAndTables();
        tableCloseClosesOpenRows();
        rowsCloseRejectsFurtherUse();
        txExecTableIsStreamScopedAndClosedWithStream();
        txExecOneTableSurvivesMethodReturn();
        txCloseCommitRollbackCloseChildren();
        explainTraceClosesWithConnection();
        multipleCloseCallsAreHarmless();
    }

    private static void execTableIsStreamScopedAndClosedWithStream() {
        try (Velr db = Velr.open()) {
            Stream stream = db.exec("RETURN 1 AS x");
            Table table = stream.nextTable();
            assertScalar(table, 1L, "stream table scalar");
            stream.close();
            assertClosedTable(table, "stream close closes table");
        }
    }

    private static void execOneTableSurvivesMethodReturn() {
        try (Velr db = Velr.open()) {
            Table table = db.execOne("RETURN 1 AS x");
            try {
                assertScalar(table, 1L, "execOne table scalar");
            } finally {
                table.close();
            }
        }
    }

    private static void execOneTableIsClosedWhenConnectionCloses() {
        Velr db = Velr.open();
        Table table = db.execOne("RETURN 1 AS x");
        assertScalar(table, 1L, "execOne table before connection close");
        db.close();
        assertClosedTable(table, "connection close closes execOne table");
    }

    private static void connectionCloseClosesOpenStreamsAndTables() {
        Velr db = Velr.open();
        Stream stream = db.exec("RETURN 1 AS x");
        Table table = stream.nextTable();
        assertScalar(table, 1L, "stream table before connection close");
        db.close();
        assertClosedTable(table, "connection close closes stream table");
        assertThrows(
                VelrException.class,
                new ThrowingRunnable() {
                    @Override
                    public void run() {
                        stream.nextTable();
                    }
                },
                "connection close closes stream");
    }

    private static void tableCloseClosesOpenRows() {
        try (Velr db = Velr.open()) {
            Table table = db.execOne("RETURN 1 AS x");
            Rows rows = table.rows();
            List<Cell> row = rows.next();
            assertEquals(1L, row.get(0).asLong(), "row before table close");
            table.close();
            assertThrows(
                    VelrException.class,
                    new ThrowingRunnable() {
                        @Override
                        public void run() {
                            rows.next();
                        }
                    },
                    "table close closes rows");
        }
    }

    private static void rowsCloseRejectsFurtherUse() {
        try (Velr db = Velr.open()) {
            try (Table table = db.execOne("RETURN 1 AS x")) {
                Rows rows = table.rows();
                rows.close();
                assertThrows(
                        VelrException.class,
                        new ThrowingRunnable() {
                            @Override
                            public void run() {
                                rows.next();
                            }
                        },
                        "closed rows reject use");
            }
        }
    }

    private static void txExecTableIsStreamScopedAndClosedWithStream() {
        try (Velr db = Velr.open()) {
            try (VelrTx tx = db.beginTx()) {
                StreamTx stream = tx.exec("RETURN 1 AS x");
                Table table = stream.nextTable();
                assertScalar(table, 1L, "tx stream table scalar");
                stream.close();
                assertClosedTable(table, "tx stream close closes table");
            }
        }
    }

    private static void txExecOneTableSurvivesMethodReturn() {
        try (Velr db = Velr.open()) {
            try (VelrTx tx = db.beginTx()) {
                Table table = tx.execOne("RETURN 1 AS x");
                try {
                    assertScalar(table, 1L, "tx execOne table scalar");
                } finally {
                    table.close();
                }
            }
        }
    }

    private static void txCloseCommitRollbackCloseChildren() {
        try (Velr db = Velr.open()) {
            VelrTx closeTx = db.beginTx();
            Table closeTable = closeTx.execOne("RETURN 1 AS x");
            assertScalar(closeTable, 1L, "tx close child before close");
            closeTx.close();
            assertClosedTable(closeTable, "tx close closes table");

            VelrTx commitTx = db.beginTx();
            Table commitTable = commitTx.execOne("RETURN 2 AS x");
            assertScalar(commitTable, 2L, "tx commit child before commit");
            commitTx.commit();
            assertClosedTable(commitTable, "tx commit closes table");

            VelrTx rollbackTx = db.beginTx();
            Table rollbackTable = rollbackTx.execOne("RETURN 3 AS x");
            assertScalar(rollbackTable, 3L, "tx rollback child before rollback");
            rollbackTx.rollback();
            assertClosedTable(rollbackTable, "tx rollback closes table");
        }
    }

    private static void explainTraceClosesWithConnection() {
        Velr db = Velr.open();
        ExplainTrace trace = db.explain("RETURN 1 AS x");
        assertNotEmpty(trace.compact(), "explain before connection close");
        db.close();
        assertThrows(
                VelrException.class,
                new ThrowingRunnable() {
                    @Override
                    public void run() {
                        trace.compact();
                    }
                },
                "connection close closes explain trace");
    }

    private static void multipleCloseCallsAreHarmless() {
        try (Velr db = Velr.open()) {
            Table table = db.execOne("RETURN 1 AS x");
            table.close();
            table.close();
            assertClosedTable(table, "table double close");

            ExplainTrace trace = db.explain("RETURN 2 AS y");
            trace.close();
            trace.close();
            assertThrows(
                    VelrException.class,
                    new ThrowingRunnable() {
                        @Override
                        public void run() {
                            trace.compact();
                        }
                    },
                    "trace double close");
        }
    }

    private static void assertScalar(Table table, long expected, String what) {
        try (Rows rows = table.rows()) {
            List<Cell> row = rows.next();
            assertEquals(expected, row.get(0).asLong(), what);
            assertEquals(null, rows.next(), what + " end");
        }
    }

    private static void assertClosedTable(final Table table, String what) {
        assertThrows(
                VelrException.class,
                new ThrowingRunnable() {
                    @Override
                    public void run() {
                        table.columnCount();
                    }
                },
                what);
    }

    private static void assertEquals(Object expected, Object actual, String what) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
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
        void run();
    }
}
