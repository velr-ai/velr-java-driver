package ai.velr;

/** Outcome of an explicit Velr schema migration. */
public enum MigrationStatus {
    /** The database schema already matches the runtime. */
    ALREADY_CURRENT,
    /** One or more migration steps were applied. */
    MIGRATED
}
