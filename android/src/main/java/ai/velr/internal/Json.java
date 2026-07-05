package ai.velr.internal;

import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.Map;

public final class Json {
    private Json() {}

    public static String stringify(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof Boolean) {
            out.append(((Boolean) value).booleanValue() ? "true" : "false");
        } else if (value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long) {
            out.append(((Number) value).longValue());
        } else if (value instanceof Float || value instanceof Double) {
            double number = ((Number) value).doubleValue();
            if (!Double.isFinite(number)) {
                throw new IllegalArgumentException("floating point query parameter must be finite");
            }
            out.append(Double.toString(number));
        } else if (value instanceof CharSequence || value instanceof Character) {
            writeString(value.toString(), out);
        } else if (value instanceof Map<?, ?>) {
            writeMap((Map<?, ?>) value, out);
        } else if (value instanceof Iterable<?>) {
            writeIterable(((Iterable<?>) value).iterator(), out);
        } else if (value.getClass().isArray()) {
            writeArray(value, out);
        } else {
            throw new IllegalArgumentException(
                    "unsupported query parameter type: " + value.getClass().getName());
        }
    }

    private static void writeMap(Map<?, ?> map, StringBuilder out) {
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            if (!(key instanceof String)) {
                throw new IllegalArgumentException("query parameter map keys must be strings");
            }
            if (!first) {
                out.append(',');
            }
            first = false;
            writeString((String) key, out);
            out.append(':');
            write(entry.getValue(), out);
        }
        out.append('}');
    }

    private static void writeIterable(Iterator<?> iterator, StringBuilder out) {
        out.append('[');
        boolean first = true;
        while (iterator.hasNext()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            write(iterator.next(), out);
        }
        out.append(']');
    }

    private static void writeArray(Object array, StringBuilder out) {
        out.append('[');
        int len = Array.getLength(array);
        for (int i = 0; i < len; i++) {
            if (i > 0) {
                out.append(',');
            }
            write(Array.get(array, i), out);
        }
        out.append(']');
    }

    private static void writeString(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }
}
