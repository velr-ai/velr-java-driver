package ai.velr.internal;

import ai.velr.ArrowColumn;
import ai.velr.QueryOptions;
import ai.velr.VelrException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class NativeSupport {
    private NativeSupport() {}

    public static byte[] utf8(String value, String field) {
        if (value == null) {
            throw new NullPointerException(field + " cannot be null");
        }
        if (value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(field + " contains NUL byte");
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] optionalUtf8(String value, String field) {
        if (value == null) {
            return null;
        }
        return utf8(value, field);
    }

    public static long maxRows(QueryOptions options) {
        if (options == null || options.maxResultRows() == null) {
            return -1;
        }
        return options.maxResultRows().longValue();
    }

    public static byte[] paramsJson(QueryOptions options) {
        if (options == null || options.params().isEmpty()) {
            return null;
        }
        String json = Json.stringify(options.params());
        return json.getBytes(StandardCharsets.UTF_8);
    }

    public static long requireHandle(long handle, String fallback) {
        if (handle == 0) {
            throw VelrException.fromNative(fallback);
        }
        return handle;
    }

    public static void checkStatus(int status, String fallback) {
        if (status != 0) {
            throw VelrException.fromNative(fallback);
        }
    }

    public static byte[] requireBytes(byte[] bytes, String fallback) {
        if (bytes == null) {
            throw VelrException.fromNative(fallback);
        }
        return bytes;
    }

    public static void validateParams(Map<String, ?> params) {
        if (params != null) {
            Json.stringify(params);
        }
    }

    public static ArrowBindings arrowBindings(List<ArrowColumn> columns) {
        List<ArrowColumn> checked = requireArrowColumns(columns);
        byte[][] names = new byte[checked.size()][];
        long[] schemas = new long[checked.size()];
        long[] arrays = new long[checked.size()];
        for (int i = 0; i < checked.size(); i++) {
            ArrowColumn column = checked.get(i);
            if (column.chunkCount() != 1) {
                throw new IllegalArgumentException(
                        "bindArrow requires one chunk per column; use bindArrowChunks for chunked columns");
            }
            names[i] = utf8(column.name(), "Arrow column name");
            schemas[i] = column.schemaAddress();
            arrays[i] = column.arrayAddress();
        }
        return new ArrowBindings(names, schemas, arrays);
    }

    public static ArrowChunkBindings arrowChunkBindings(List<ArrowColumn> columns) {
        List<ArrowColumn> checked = requireArrowColumns(columns);
        byte[][] names = new byte[checked.size()][];
        long[][] schemas = new long[checked.size()][];
        long[][] arrays = new long[checked.size()][];
        for (int i = 0; i < checked.size(); i++) {
            ArrowColumn column = checked.get(i);
            names[i] = utf8(column.name(), "Arrow column name");
            schemas[i] = new long[column.chunkCount()];
            arrays[i] = new long[column.chunkCount()];
            for (int chunk = 0; chunk < column.chunkCount(); chunk++) {
                schemas[i][chunk] = column.schemaAddress(chunk);
                arrays[i][chunk] = column.arrayAddress(chunk);
            }
        }
        return new ArrowChunkBindings(names, schemas, arrays);
    }

    private static List<ArrowColumn> requireArrowColumns(List<ArrowColumn> columns) {
        if (columns == null) {
            throw new NullPointerException("Arrow columns cannot be null");
        }
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("Arrow columns cannot be empty");
        }
        for (int i = 0; i < columns.size(); i++) {
            ArrowColumn column = columns.get(i);
            if (column == null) {
                throw new NullPointerException("Arrow column cannot be null");
            }
        }
        return columns;
    }

    public static final class ArrowBindings {
        public final byte[][] names;
        public final long[] schemas;
        public final long[] arrays;

        private ArrowBindings(byte[][] names, long[] schemas, long[] arrays) {
            this.names = names;
            this.schemas = schemas;
            this.arrays = arrays;
        }
    }

    public static final class ArrowChunkBindings {
        public final byte[][] names;
        public final long[][] schemas;
        public final long[][] arrays;

        private ArrowChunkBindings(byte[][] names, long[][] schemas, long[][] arrays) {
            this.names = names;
            this.schemas = schemas;
            this.arrays = arrays;
        }
    }
}
