package ai.velr;

import ai.velr.internal.Native;
import ai.velr.internal.NativeSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Explicit Velr transaction.
 *
 * <p>A transaction owns result streams, result tables, rows, and savepoints opened from it. Commit,
 * roll back, or close the transaction when it is no longer needed. Closing a transaction closes any
 * still-open child handles first.
 */
public final class VelrTx implements AutoCloseable, ChildHandle, ParentHandle {
    private long handle;
    private final ParentHandle parent;
    private final HandleSet children = new HandleSet();
    private final List<NamedSavepoint> namedSavepoints = new ArrayList<>();

    VelrTx(long handle, ParentHandle parent) {
        this.handle = handle;
        this.parent = parent;
    }

    /**
     * Return whether this transaction has been closed, committed, or rolled back.
     *
     * @return {@code true} after the transaction handle is no longer open
     */
    public boolean isClosed() {
        return handle == 0;
    }

    /**
     * Execute an openCypher statement inside this transaction and stream every result table it
     * produces.
     *
     * @param cypher openCypher statement
     * @return a stream of result tables
     */
    public StreamTx exec(String cypher) {
        return exec(cypher, QueryOptions.defaults());
    }

    /**
     * Execute an openCypher statement with query options inside this transaction and stream every
     * result table it produces.
     *
     * @param cypher openCypher statement
     * @param options query options such as parameters and row limits
     * @return a stream of result tables
     */
    public StreamTx exec(String cypher, QueryOptions options) {
        long stream =
                Native.txExec(
                        ptr(),
                        NativeSupport.utf8(cypher, "openCypher"),
                        NativeSupport.maxRows(options),
                        NativeSupport.paramsJson(options));
        StreamTx out =
                new StreamTx(
                        NativeSupport.requireHandle(stream, "velr_tx_exec_start failed"), this);
        registerChild(out);
        return out;
    }

    /**
     * Execute an openCypher statement inside this transaction that is expected to produce exactly
     * one result table.
     *
     * @param cypher openCypher statement
     * @return the single result table
     */
    public Table execOne(String cypher) {
        return execOne(cypher, QueryOptions.defaults());
    }

    /**
     * Execute an openCypher statement with query options inside this transaction that is expected
     * to produce exactly one result table.
     *
     * @param cypher openCypher statement
     * @param options query options such as parameters and row limits
     * @return the single result table
     */
    public Table execOne(String cypher, QueryOptions options) {
        StreamTx stream = exec(cypher, options);
        Table table = null;
        try {
            table = stream.nextTable();
            if (table == null) {
                throw new VelrException("query produced no result tables");
            }
            table.reparent(this);
            Table extra = stream.nextTable();
            if (extra != null) {
                try {
                    extra.close();
                } finally {
                    table.close();
                }
                throw new VelrException("query produced multiple tables; use exec() to stream them");
            }
            stream.close();
            return table;
        } catch (RuntimeException e) {
            try {
                stream.close();
            } catch (RuntimeException ignored) {
            }
            if (table != null) {
                try {
                    table.close();
                } catch (RuntimeException ignored) {
                }
            }
            throw e;
        }
    }

    /**
     * Execute an openCypher statement inside this transaction and discard any result tables.
     *
     * @param cypher openCypher statement
     */
    public void run(String cypher) {
        run(cypher, QueryOptions.defaults());
    }

    /**
     * Execute an openCypher statement with query options inside this transaction and discard any
     * result tables.
     *
     * @param cypher openCypher statement
     * @param options query options such as parameters and row limits
     */
    public void run(String cypher, QueryOptions options) {
        try (StreamTx stream = exec(cypher, options)) {
            Table table;
            while ((table = stream.nextTable()) != null) {
                table.close();
            }
        }
    }

    /**
     * Execute an openCypher statement inside this transaction and collect the single result table
     * as object maps.
     *
     * @param cypher openCypher statement
     * @return collected rows
     */
    public List<Map<String, Object>> query(String cypher) {
        return query(cypher, QueryOptions.defaults());
    }

    /**
     * Execute an openCypher statement with query options inside this transaction and collect the
     * single result table as object maps.
     *
     * @param cypher openCypher statement
     * @param options query options such as parameters and row limits
     * @return collected rows
     */
    public List<Map<String, Object>> query(String cypher, QueryOptions options) {
        try (Table table = execOne(cypher, options)) {
            return table.toMaps();
        }
    }

    /**
     * Bind Arrow IPC file / Feather v2 bytes as a logical table within this transaction.
     *
     * <p>The byte array is borrowed only for the duration of the call. Velr decodes the IPC file
     * and owns the resulting Arrow arrays before this method returns. Arrow IPC stream bytes are not
     * accepted by this method.
     *
     * @param logical logical table name to expose to queries
     * @param ipcBytes Arrow IPC file / Feather v2 bytes
     */
    public void bindArrowIpc(String logical, byte[] ipcBytes) {
        if (ipcBytes == null || ipcBytes.length == 0) {
            throw new IllegalArgumentException("Arrow IPC bytes cannot be empty");
        }
        NativeSupport.checkStatus(
                Native.txBindArrowIpc(ptr(), NativeSupport.utf8(logical, "logical table name"), ipcBytes),
                "velr_tx_bind_arrow_ipc failed");
    }

    /**
     * Bind single-chunk Arrow C Data Interface columns as a logical table within this transaction.
     *
     * <p>For each column, the {@code ArrowArray} address is transferred to Velr by this call. Do
     * not use or release transferred arrays after calling this method. The {@code ArrowSchema}
     * address is not consumed and may be released after this method returns.
     *
     * @param logical logical table name to expose to queries
     * @param columns one or more Arrow columns
     */
    public void bindArrow(String logical, List<ArrowColumn> columns) {
        NativeSupport.ArrowBindings bindings = NativeSupport.arrowBindings(columns);
        NativeSupport.checkStatus(
                Native.txBindArrow(
                        ptr(),
                        NativeSupport.utf8(logical, "logical table name"),
                        bindings.names,
                        bindings.schemas,
                        bindings.arrays),
                "velr_tx_bind_arrow failed");
    }

    /**
     * Bind single-chunk Arrow C Data Interface columns as a logical table within this transaction.
     *
     * @param logical logical table name to expose to queries
     * @param columns one or more Arrow columns
     */
    public void bindArrow(String logical, ArrowColumn... columns) {
        bindArrow(logical, Arrays.asList(columns));
    }

    /**
     * Bind chunked Arrow C Data Interface columns as a logical table within this transaction.
     *
     * <p>Each column may contain one or more chunks. For every chunk, the {@code ArrowArray}
     * address is transferred to Velr by this call. Do not use or release transferred arrays after
     * calling this method. The {@code ArrowSchema} address is not consumed and may be released after
     * this method returns.
     *
     * @param logical logical table name to expose to queries
     * @param columns one or more Arrow columns
     */
    public void bindArrowChunks(String logical, List<ArrowColumn> columns) {
        NativeSupport.ArrowChunkBindings bindings = NativeSupport.arrowChunkBindings(columns);
        NativeSupport.checkStatus(
                Native.txBindArrowChunks(
                        ptr(),
                        NativeSupport.utf8(logical, "logical table name"),
                        bindings.names,
                        bindings.schemas,
                        bindings.arrays),
                "velr_tx_bind_arrow_chunks failed");
    }

    /**
     * Bind chunked Arrow C Data Interface columns as a logical table within this transaction.
     *
     * @param logical logical table name to expose to queries
     * @param columns one or more Arrow columns
     */
    public void bindArrowChunks(String logical, ArrowColumn... columns) {
        bindArrowChunks(logical, Arrays.asList(columns));
    }

    /**
     * Create a scoped savepoint.
     *
     * <p>The returned savepoint can be released or rolled back directly. Close the savepoint if it
     * is no longer needed and no action should be taken.
     *
     * @return an open savepoint handle
     */
    public Savepoint savepoint() {
        long sp = Native.savepoint(ptr());
        Savepoint out =
                Savepoint.scoped(
                        NativeSupport.requireHandle(sp, "velr_tx_savepoint failed"), this);
        registerChild(out);
        return out;
    }

    /**
     * Create a named savepoint.
     *
     * <p>Named savepoints can be addressed later with {@link #rollbackTo(String)} and {@link
     * #releaseSavepoint(String)} while they remain active.
     *
     * @param name savepoint name
     * @return an open savepoint handle
     */
    public Savepoint savepointNamed(String name) {
        long sp = Native.savepointNamed(ptr(), NativeSupport.utf8(name, "savepoint name"));
        NamedSavepoint entry =
                new NamedSavepoint(
                        name, NativeSupport.requireHandle(sp, "velr_tx_savepoint_named failed"));
        namedSavepoints.add(entry);
        Savepoint out = Savepoint.named(this, entry);
        registerChild(out);
        return out;
    }

    /**
     * Roll back this transaction to an active named savepoint.
     *
     * <p>The target savepoint remains active after rollback; savepoints created after it are no
     * longer active.
     *
     * @param name savepoint name
     */
    public void rollbackTo(String name) {
        int idx = findNamedIndex(name);
        if (idx < 0) {
            throw new VelrException("no such savepoint '" + name + "'");
        }
        rollbackNamed(namedSavepoints.get(idx), false);
    }

    /**
     * Release an active named savepoint.
     *
     * <p>Velr releases named savepoints from the top of the savepoint stack.
     *
     * @param name savepoint name
     */
    public void releaseSavepoint(String name) {
        int idx = findNamedIndex(name);
        if (idx < 0) {
            throw new VelrException("no such savepoint '" + name + "'");
        }
        releaseNamed(namedSavepoints.get(idx));
    }

    /**
     * Commit this transaction.
     *
     * <p>After commit, this transaction handle is closed and cannot be reused.
     */
    public void commit() {
        long ptr = ptr();
        children.closeAll();
        releaseNamedForCommit();
        handle = 0;
        try {
            NativeSupport.checkStatus(Native.txCommit(ptr), "velr_tx_commit failed");
        } finally {
            invalidateNamed();
            parent.unregisterChild(this);
        }
    }

    /**
     * Roll back this transaction.
     *
     * <p>After rollback, this transaction handle is closed and cannot be reused.
     */
    public void rollback() {
        long ptr = ptr();
        children.closeAll();
        handle = 0;
        try {
            NativeSupport.checkStatus(Native.txRollback(ptr), "velr_tx_rollback failed");
        } finally {
            invalidateNamed();
            parent.unregisterChild(this);
        }
    }

    /**
     * Close this transaction without committing it.
     */
    @Override
    public void close() {
        long ptr = handle;
        if (ptr == 0) {
            return;
        }
        children.closeAll();
        handle = 0;
        try {
            Native.txClose(ptr);
        } finally {
            invalidateNamed();
            parent.unregisterChild(this);
        }
    }

    /** Driver-internal child-handle registration hook. */
    @Override
    public void registerChild(ChildHandle child) {
        children.register(child);
    }

    /** Driver-internal child-handle registration hook. */
    @Override
    public void unregisterChild(ChildHandle child) {
        children.unregister(child);
    }

    long ptr() {
        if (handle == 0) {
            throw new VelrException("Velr transaction is closed");
        }
        return handle;
    }

    boolean isNamedActive(NamedSavepoint entry) {
        return entry.active && entry.handle != 0;
    }

    void releaseNamed(NamedSavepoint entry) {
        ptr();
        int idx = findNamedEntryIndex(entry);
        if (idx < 0 || !entry.active || entry.handle == 0) {
            throw new VelrException("no such savepoint '" + entry.name + "'");
        }
        if (idx != namedSavepoints.size() - 1) {
            throw new VelrException("named savepoint can only be released from the top of the stack");
        }
        long sp = entry.handle;
        entry.handle = 0;
        entry.active = false;
        namedSavepoints.remove(namedSavepoints.size() - 1);
        NativeSupport.checkStatus(Native.spRelease(sp), "velr_sp_release failed");
    }

    void rollbackNamed(NamedSavepoint entry, boolean releaseTarget) {
        ptr();
        int idx = findNamedEntryIndex(entry);
        if (idx < 0 || !entry.active || entry.handle == 0) {
            throw new VelrException("no such savepoint '" + entry.name + "'");
        }
        if (releaseTarget) {
            long sp = entry.handle;
            for (int i = idx; i < namedSavepoints.size(); i++) {
                namedSavepoints.get(i).active = false;
                namedSavepoints.get(i).handle = 0;
            }
            while (namedSavepoints.size() > idx) {
                namedSavepoints.remove(namedSavepoints.size() - 1);
            }
            NativeSupport.checkStatus(Native.spRollback(sp), "velr_sp_rollback failed");
            return;
        }

        NativeSupport.checkStatus(
                Native.rollbackTo(ptr(), NativeSupport.utf8(entry.name, "savepoint name")),
                "velr_tx_rollback_to failed");
        retainNamedTargetAfterRollback(idx);
    }

    private void retainNamedTargetAfterRollback(int idx) {
        NamedSavepoint entry = namedSavepoints.get(idx);
        long recreated =
                Native.savepointNamed(ptr(), NativeSupport.utf8(entry.name, "savepoint name"));
        entry.handle =
                NativeSupport.requireHandle(
                        recreated, "failed to recreate named savepoint after rollback_to");
        entry.active = true;
        for (int i = idx + 1; i < namedSavepoints.size(); i++) {
            namedSavepoints.get(i).active = false;
            namedSavepoints.get(i).handle = 0;
        }
        while (namedSavepoints.size() > idx + 1) {
            namedSavepoints.remove(namedSavepoints.size() - 1);
        }
    }

    private int findNamedIndex(String name) {
        for (int i = namedSavepoints.size() - 1; i >= 0; i--) {
            NamedSavepoint entry = namedSavepoints.get(i);
            if (entry.active && entry.name.equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private int findNamedEntryIndex(NamedSavepoint wanted) {
        for (int i = namedSavepoints.size() - 1; i >= 0; i--) {
            NamedSavepoint entry = namedSavepoints.get(i);
            if (entry == wanted && wanted.active) {
                return i;
            }
        }
        return -1;
    }

    private void releaseNamedForCommit() {
        while (!namedSavepoints.isEmpty()) {
            NamedSavepoint entry = namedSavepoints.get(namedSavepoints.size() - 1);
            if (!entry.active || entry.handle == 0) {
                namedSavepoints.remove(namedSavepoints.size() - 1);
                continue;
            }
            releaseNamed(entry);
        }
    }

    private void invalidateNamed() {
        for (NamedSavepoint entry : namedSavepoints) {
            entry.active = false;
            entry.handle = 0;
        }
        namedSavepoints.clear();
    }

    static final class NamedSavepoint {
        final String name;
        long handle;
        boolean active = true;

        NamedSavepoint(String name, long handle) {
            this.name = name;
            this.handle = handle;
        }
    }
}
