package ai.velr;

import ai.velr.internal.Native;
import ai.velr.internal.NativeSupport;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * One result table returned by a query.
 *
 * <p>A table owns row cursors opened from it. Close the table when it is no longer needed. Tables
 * returned from {@link Velr#execOne(String)} are parented to the connection; tables pulled from a
 * {@link Stream} are stream-scoped and are closed when their stream is closed.
 */
public final class Table implements AutoCloseable, ChildHandle, ParentHandle {
    private long handle;
    private ParentHandle parent;
    private final HandleSet children = new HandleSet();
    private List<String> columnNames;

    Table(long handle, ParentHandle parent) {
        this.handle = handle;
        this.parent = parent;
    }

    /**
     * Return the number of columns in this result table.
     *
     * @return column count
     */
    public int columnCount() {
        int count = Native.tableColumnCount(ptr());
        if (count < 0) {
            throw VelrException.fromNative("velr_table_column_count failed");
        }
        return count;
    }

    /**
     * Return result column names in order.
     *
     * @return immutable column-name list
     */
    public List<String> columnNames() {
        if (columnNames != null) {
            return columnNames;
        }
        int count = columnCount();
        List<String> names = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            byte[] bytes =
                    NativeSupport.requireBytes(
                            Native.tableColumnName(ptr(), i), "velr_table_column_name failed");
            names.add(new String(bytes, StandardCharsets.UTF_8));
        }
        columnNames = Collections.unmodifiableList(names);
        return columnNames;
    }

    /**
     * Open a streaming row cursor for this table.
     *
     * @return row cursor; close it when finished
     */
    public Rows rows() {
        long rows = Native.rowsOpen(ptr());
        Rows out =
                new Rows(
                        NativeSupport.requireHandle(rows, "velr_table_rows_open failed"),
                        columnCount(),
                        this);
        registerChild(out);
        return out;
    }

    /**
     * Collect all rows into JVM-owned memory.
     *
     * @return table rows as lists of cells
     */
    public List<List<Cell>> collect() {
        List<List<Cell>> out = new ArrayList<>();
        try (Rows rows = rows()) {
            List<Cell> row;
            while ((row = rows.next()) != null) {
                out.add(row);
            }
        }
        return out;
    }

    /**
     * Collect all rows as maps keyed by column name.
     *
     * @return rows as maps from column name to Java value
     */
    public List<Map<String, Object>> toMaps() {
        return Velr.rowsToMaps(this);
    }

    /**
     * Export this result table as Arrow IPC file bytes.
     *
     * @return Arrow IPC file / Feather v2 bytes
     */
    public byte[] toArrowIpc() {
        return NativeSupport.requireBytes(Native.tableArrowIpc(ptr()), "velr_table_ipc_file failed");
    }

    /**
     * Close this table and any open row cursors owned by it.
     */
    @Override
    public void close() {
        long ptr = handle;
        if (ptr == 0) {
            return;
        }
        children.closeAll();
        handle = 0;
        Native.tableClose(ptr);
        parent.unregisterChild(this);
    }

    /** Driver-internal child-handle registration method. */
    @Override
    public void registerChild(ChildHandle child) {
        children.register(child);
    }

    /** Driver-internal child-handle unregistration method. */
    @Override
    public void unregisterChild(ChildHandle child) {
        children.unregister(child);
    }

    void reparent(ParentHandle newParent) {
        parent.unregisterChild(this);
        parent = newParent;
        newParent.registerChild(this);
    }

    private long ptr() {
        if (handle == 0) {
            throw new VelrException("Velr table is closed");
        }
        return handle;
    }
}
