package ai.velr;

import java.util.Collections;
import java.util.List;

/** Result of an explicit Velr schema migration check or migration run. */
public final class MigrationReport {
    private final int fromVersion;
    private final int toVersion;
    private final MigrationStatus status;
    private final List<String> steps;

    /**
     * Create a migration report.
     *
     * @param fromVersion schema version before the migration attempt
     * @param toVersion current schema version targeted by the runtime
     * @param status migration outcome
     * @param steps migration steps applied, or an empty list when already current
     */
    public MigrationReport(
            int fromVersion, int toVersion, MigrationStatus status, List<String> steps) {
        this.fromVersion = fromVersion;
        this.toVersion = toVersion;
        this.status = status;
        this.steps = Collections.unmodifiableList(steps);
    }

    /**
     * Return the schema version observed before migration.
     *
     * @return starting schema version
     */
    public int fromVersion() {
        return fromVersion;
    }

    /**
     * Return the schema version expected by this runtime.
     *
     * @return target schema version
     */
    public int toVersion() {
        return toVersion;
    }

    /**
     * Return the migration outcome.
     *
     * @return migration status
     */
    public MigrationStatus status() {
        return status;
    }

    /**
     * Return the migration steps applied by Velr.
     *
     * @return immutable step list
     */
    public List<String> steps() {
        return steps;
    }
}
