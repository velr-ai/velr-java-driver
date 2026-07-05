package ai.velr.internal;

import ai.velr.Cell;
import ai.velr.MigrationReport;
import ai.velr.MigrationStatus;
import ai.velr.VelrException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Binary {
    private Binary() {}

    public static List<Cell> decodeRow(byte[] bytes) {
        ByteBuffer in = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int count = checkedSize(in.getLong(), "row cell count");
        List<Cell> row = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int type = in.get() & 0xff;
            switch (type) {
                case 0:
                    row.add(Cell.nullValue());
                    break;
                case 1:
                    row.add(Cell.bool(in.get() != 0));
                    break;
                case 2:
                    row.add(Cell.int64(in.getLong()));
                    break;
                case 3:
                    row.add(Cell.doubleValue(in.getDouble()));
                    break;
                case 4:
                    row.add(Cell.text(readBytes(in)));
                    break;
                case 5:
                    row.add(Cell.json(readBytes(in)));
                    break;
                default:
                    throw new VelrException("unknown native cell type " + type);
            }
        }
        return Collections.unmodifiableList(row);
    }

    public static MigrationReport decodeMigrationReport(byte[] bytes) {
        ByteBuffer in = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int from = in.getInt();
        int to = in.getInt();
        int statusRaw = in.getInt();
        in.getLong(); // native step_count, retained for ABI symmetry
        String stepText = new String(readBytes(in), StandardCharsets.UTF_8);
        List<String> steps = new ArrayList<>();
        if (!stepText.isEmpty()) {
            for (String step : stepText.split(",")) {
                if (!step.isEmpty()) {
                    steps.add(step);
                }
            }
        }
        MigrationStatus status =
                statusRaw == 1 ? MigrationStatus.MIGRATED : MigrationStatus.ALREADY_CURRENT;
        return new MigrationReport(from, to, status, steps);
    }

    private static byte[] readBytes(ByteBuffer in) {
        int len = checkedSize(in.getLong(), "byte payload length");
        byte[] out = new byte[len];
        in.get(out);
        return out;
    }

    private static int checkedSize(long value, String what) {
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new VelrException(what + " does not fit Java int: " + value);
        }
        return (int) value;
    }
}
