package ai.velr.internal;

public final class Native {
    static {
        NativeLibrary.load();
    }

    private Native() {}

    public static native byte[] takeLastError();

    public static native long open(byte[] pathOrNull);

    public static native long openReadonly(byte[] path);

    public static native void close(long db);

    public static native int schemaVersion(long db);

    public static native int currentSchemaVersion(long db);

    public static native int needsMigration(long db);

    public static native byte[] migrate(long db);

    public static native long exec(long db, byte[] cypher, long maxResultRows, byte[] paramsJson);

    public static native long execOne(
            long db, byte[] cypher, long maxResultRows, byte[] paramsJson);

    public static native long streamNext(long stream);

    public static native void streamClose(long stream);

    public static native void tableClose(long table);

    public static native int tableColumnCount(long table);

    public static native byte[] tableColumnName(long table, int index);

    public static native long rowsOpen(long table);

    public static native byte[] rowsNext(long rows, int columnCount);

    public static native void rowsClose(long rows);

    public static native long beginTx(long db);

    public static native int txCommit(long tx);

    public static native int txRollback(long tx);

    public static native void txClose(long tx);

    public static native long txExec(long tx, byte[] cypher, long maxResultRows, byte[] paramsJson);

    public static native long streamTxNext(long stream);

    public static native void streamTxClose(long stream);

    public static native long savepoint(long tx);

    public static native long savepointNamed(long tx, byte[] name);

    public static native int rollbackTo(long tx, byte[] name);

    public static native int spRelease(long savepoint);

    public static native int spRollback(long savepoint);

    public static native void spClose(long savepoint);

    public static native long explain(long db, byte[] cypher);

    public static native long explainAnalyze(long db, byte[] cypher);

    public static native byte[] explainCompact(long trace);

    public static native void explainClose(long trace);

    public static native int bindArrowIpc(long db, byte[] logical, byte[] ipc);

    public static native int txBindArrowIpc(long tx, byte[] logical, byte[] ipc);

    public static native int bindArrow(
            long db, byte[] logical, byte[][] names, long[] schemas, long[] arrays);

    public static native int txBindArrow(
            long tx, byte[] logical, byte[][] names, long[] schemas, long[] arrays);

    public static native int bindArrowChunks(
            long db, byte[] logical, byte[][] names, long[][] schemas, long[][] arrays);

    public static native int txBindArrowChunks(
            long tx, byte[] logical, byte[][] names, long[][] schemas, long[][] arrays);

    public static native byte[] tableArrowIpc(long table);

    public static native int registerVectorEmbedder(long db, byte[] name, Object embedder);
}
