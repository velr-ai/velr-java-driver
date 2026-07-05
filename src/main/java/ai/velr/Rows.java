package ai.velr;

import ai.velr.internal.Binary;
import ai.velr.internal.Native;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Streaming row cursor for a result table.
 *
 * <p>A rows cursor is single-pass. Each row returned from {@link #next()} contains JVM-owned cell
 * values and remains valid after the next fetch. Closing the owning table also closes open row
 * cursors.
 */
public final class Rows implements AutoCloseable, ChildHandle, Iterable<List<Cell>> {
    private long handle;
    private final int columnCount;
    private final ParentHandle parent;

    Rows(long handle, int columnCount, ParentHandle parent) {
        this.handle = handle;
        this.columnCount = columnCount;
        this.parent = parent;
    }

    /**
     * Fetch the next row.
     *
     * @return next row, or {@code null} when the cursor is exhausted
     */
    public List<Cell> next() {
        byte[] bytes = Native.rowsNext(ptr(), columnCount);
        if (bytes == null) {
            byte[] error = Native.takeLastError();
            if (error != null) {
                throw new VelrException(new String(error, StandardCharsets.UTF_8));
            }
            return null;
        }
        return Binary.decodeRow(bytes);
    }

    /**
     * Return an iterator over remaining rows.
     *
     * @return row iterator
     */
    @Override
    public Iterator<List<Cell>> iterator() {
        return new Iterator<List<Cell>>() {
            private List<Cell> next;
            private boolean fetched;

            @Override
            public boolean hasNext() {
                if (!fetched) {
                    next = Rows.this.next();
                    fetched = true;
                }
                return next != null;
            }

            @Override
            public List<Cell> next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                List<Cell> value = next;
                next = null;
                fetched = false;
                return value;
            }
        };
    }

    /**
     * Close this cursor.
     */
    @Override
    public void close() {
        long ptr = handle;
        if (ptr == 0) {
            return;
        }
        handle = 0;
        Native.rowsClose(ptr);
        parent.unregisterChild(this);
    }

    private long ptr() {
        if (handle == 0) {
            throw new VelrException("Velr rows cursor is closed");
        }
        return handle;
    }
}
