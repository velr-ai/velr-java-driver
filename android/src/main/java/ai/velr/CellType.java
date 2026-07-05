package ai.velr;

/** Runtime kind of a value returned in a Velr result row. */
public enum CellType {
    /** Null value. */
    NULL,
    /** Boolean value. */
    BOOL,
    /** Signed 64-bit integer value. */
    INT64,
    /** Double-precision floating-point value. */
    DOUBLE,
    /** UTF-8 text value. */
    TEXT,
    /** UTF-8 canonical JSON rendering of a richer Cypher value. */
    JSON
}
