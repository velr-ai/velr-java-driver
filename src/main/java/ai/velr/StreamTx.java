package ai.velr;

import ai.velr.internal.Native;
import ai.velr.internal.NativeSupport;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Streaming result-table handle returned by {@link VelrTx#exec(String)}.
 *
 * <p>A query may produce zero or more result tables. Pull tables one at a time with
 * {@link #nextTable()} or iterate over the stream. Closing the stream closes any still-open tables
 * produced by it.
 */
public final class StreamTx implements AutoCloseable, ChildHandle, ParentHandle, Iterable<Table> {
    private long handle;
    private final ParentHandle parent;
    private final HandleSet children = new HandleSet();

    StreamTx(long handle, ParentHandle parent) {
        this.handle = handle;
        this.parent = parent;
    }

    /**
     * Pull the next result table.
     *
     * @return next table, or {@code null} when the stream is exhausted
     */
    public Table nextTable() {
        long table = Native.streamTxNext(ptr());
        if (table == 0) {
            byte[] error = Native.takeLastError();
            if (error != null) {
                throw new VelrException(new String(error, StandardCharsets.UTF_8));
            }
            return null;
        }
        Table out =
                new Table(
                        NativeSupport.requireHandle(table, "transaction stream returned null table"),
                        this);
        registerChild(out);
        return out;
    }

    /**
     * Return an iterator over remaining result tables.
     *
     * @return table iterator
     */
    @Override
    public Iterator<Table> iterator() {
        return new Iterator<Table>() {
            private Table next;
            private boolean fetched;

            @Override
            public boolean hasNext() {
                if (!fetched) {
                    next = nextTable();
                    fetched = true;
                }
                return next != null;
            }

            @Override
            public Table next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                Table value = next;
                next = null;
                fetched = false;
                return value;
            }
        };
    }

    /**
     * Close this stream and any open tables produced by it.
     */
    @Override
    public void close() {
        long ptr = handle;
        if (ptr == 0) {
            return;
        }
        children.closeAll();
        handle = 0;
        Native.streamTxClose(ptr);
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

    private long ptr() {
        if (handle == 0) {
            throw new VelrException("Velr transaction stream is closed");
        }
        return handle;
    }
}
