package ai.velr;

import ai.velr.internal.Native;
import ai.velr.internal.NativeSupport;

/**
 * Transaction savepoint handle.
 *
 * <p>Scoped savepoints are owned by this handle. Closing a scoped savepoint rolls back to it.
 * Named savepoint handles refer to transaction-owned savepoints and can release or roll back that
 * named savepoint explicitly.
 */
public final class Savepoint implements AutoCloseable, ChildHandle {
    private long handle;
    private final VelrTx parent;
    private final VelrTx.NamedSavepoint namedEntry;

    private Savepoint(long handle, VelrTx parent, VelrTx.NamedSavepoint namedEntry) {
        this.handle = handle;
        this.parent = parent;
        this.namedEntry = namedEntry;
    }

    static Savepoint scoped(long handle, VelrTx parent) {
        return new Savepoint(handle, parent, null);
    }

    static Savepoint named(VelrTx parent, VelrTx.NamedSavepoint entry) {
        return new Savepoint(0, parent, entry);
    }

    /**
     * Return whether this savepoint handle is closed or inactive.
     *
     * @return {@code true} when the savepoint can no longer be used
     */
    public boolean isClosed() {
        return namedEntry == null ? handle == 0 : !parent.isNamedActive(namedEntry);
    }

    /**
     * Release this savepoint and keep changes made after it.
     */
    public void release() {
        if (namedEntry != null) {
            parent.releaseNamed(namedEntry);
            parent.unregisterChild(this);
            return;
        }
        long ptr = ptr();
        handle = 0;
        try {
            NativeSupport.checkStatus(Native.spRelease(ptr), "velr_sp_release failed");
        } finally {
            parent.unregisterChild(this);
        }
    }

    /**
     * Roll back to this savepoint and release it.
     */
    public void rollback() {
        if (namedEntry != null) {
            parent.rollbackNamed(namedEntry, true);
            parent.unregisterChild(this);
            return;
        }
        long ptr = ptr();
        handle = 0;
        try {
            NativeSupport.checkStatus(Native.spRollback(ptr), "velr_sp_rollback failed");
        } finally {
            parent.unregisterChild(this);
        }
    }

    /**
     * Close this savepoint handle.
     *
     * <p>Closing a scoped savepoint closes the underlying savepoint. Closing a named savepoint
     * handle does not remove the named savepoint from the transaction.
     */
    @Override
    public void close() {
        if (namedEntry != null) {
            parent.unregisterChild(this);
            return;
        }
        long ptr = handle;
        if (ptr == 0) {
            return;
        }
        handle = 0;
        Native.spClose(ptr);
        parent.unregisterChild(this);
    }

    private long ptr() {
        if (handle == 0) {
            throw new VelrException("Velr savepoint is closed");
        }
        return handle;
    }
}
