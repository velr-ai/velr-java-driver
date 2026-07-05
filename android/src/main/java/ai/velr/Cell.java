package ai.velr;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * One value returned by a Velr result table.
 *
 * <p>Cells expose the storage class used by tabular query results. Primitive values can be read
 * through type-specific accessors. JSON cells contain a UTF-8 JSON rendering of richer Cypher
 * values such as lists, maps, nodes, relationships, and paths.
 */
public final class Cell {
    private static final byte[] EMPTY = new byte[0];

    private final CellType type;
    private final long int64;
    private final double doubleValue;
    private final byte[] bytes;

    private Cell(CellType type, long int64, double doubleValue, byte[] bytes) {
        this.type = type;
        this.int64 = int64;
        this.doubleValue = doubleValue;
        this.bytes = bytes == null ? EMPTY : bytes;
    }

    /**
     * Create a null cell.
     *
     * @return null cell
     */
    public static Cell nullValue() {
        return new Cell(CellType.NULL, 0, 0.0, EMPTY);
    }

    /**
     * Create a boolean cell.
     *
     * @param value boolean value
     * @return boolean cell
     */
    public static Cell bool(boolean value) {
        return new Cell(CellType.BOOL, value ? 1 : 0, 0.0, EMPTY);
    }

    /**
     * Create a signed 64-bit integer cell.
     *
     * @param value integer value
     * @return integer cell
     */
    public static Cell int64(long value) {
        return new Cell(CellType.INT64, value, 0.0, EMPTY);
    }

    /**
     * Create a floating-point cell.
     *
     * @param value double value
     * @return double cell
     */
    public static Cell doubleValue(double value) {
        return new Cell(CellType.DOUBLE, 0, value, EMPTY);
    }

    /**
     * Create a text cell from UTF-8 bytes.
     *
     * @param bytes UTF-8 text bytes
     * @return text cell
     */
    public static Cell text(byte[] bytes) {
        return new Cell(CellType.TEXT, 0, 0.0, bytes);
    }

    /**
     * Create a JSON cell from UTF-8 bytes.
     *
     * @param bytes UTF-8 JSON bytes
     * @return JSON cell
     */
    public static Cell json(byte[] bytes) {
        return new Cell(CellType.JSON, 0, 0.0, bytes);
    }

    /**
     * Return the cell kind.
     *
     * @return cell type
     */
    public CellType type() {
        return type;
    }

    /**
     * Return this cell as a boolean.
     *
     * @return boolean value
     * @throws VelrException if this cell is not {@link CellType#BOOL}
     */
    public boolean asBoolean() {
        require(CellType.BOOL);
        return int64 != 0;
    }

    /**
     * Return this cell as a signed 64-bit integer.
     *
     * @return integer value
     * @throws VelrException if this cell is not {@link CellType#INT64}
     */
    public long asLong() {
        require(CellType.INT64);
        return int64;
    }

    /**
     * Return this cell as a double.
     *
     * @return double value
     * @throws VelrException if this cell is not {@link CellType#DOUBLE}
     */
    public double asDouble() {
        require(CellType.DOUBLE);
        return doubleValue;
    }

    /**
     * Return this text or JSON cell as a string.
     *
     * @return UTF-8 decoded text or JSON
     * @throws VelrException if this cell is not {@link CellType#TEXT} or {@link CellType#JSON}
     */
    public String asString() {
        if (type != CellType.TEXT && type != CellType.JSON) {
            throw new VelrException("cannot convert " + type + " cell to string");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Return the raw byte payload for text and JSON cells.
     *
     * <p>For non-byte-backed cell types this returns an empty array.
     *
     * @return copied byte payload
     */
    public byte[] asBytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    /**
     * Convert this cell to a simple Java value.
     *
     * <p>Returns {@code null}, {@link Boolean}, {@link Long}, {@link Double}, or {@link String}.
     * JSON cells are returned as their canonical JSON string.
     *
     * @return Java value
     */
    public Object asObject() {
        switch (type) {
            case NULL:
                return null;
            case BOOL:
                return int64 != 0;
            case INT64:
                return int64;
            case DOUBLE:
                return doubleValue;
            case TEXT:
            case JSON:
                return asString();
            default:
                throw new AssertionError("unknown cell type " + type);
        }
    }

    private void require(CellType expected) {
        if (type != expected) {
            throw new VelrException("cannot convert " + type + " cell to " + expected);
        }
    }

    /**
     * Return a string representation of the Java value.
     *
     * @return display string
     */
    @Override
    public String toString() {
        Object value = asObject();
        return value == null ? "null" : value.toString();
    }
}
