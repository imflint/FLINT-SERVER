package kr.flint.batch.sync;

public enum TmdbSyncRunStatus {
    QUEUED,
    RUNNING,
    STOPPING,
    STOPPED,
    COMPLETED,
    FAILED
}
