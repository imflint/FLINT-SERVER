package kr.flint.batch.service;

public class TmdbApiException extends RuntimeException {

    public enum Kind { NOT_FOUND, TRANSIENT, INVALID_RESPONSE, PERMANENT }

    private final Kind kind;

    private TmdbApiException(String operation, Kind kind, Integer status) {
        super("TMDB operation=" + operation + " kind=" + kind + " status=" + (status == null ? "unknown" : status));
        this.kind = kind;
    }

    public static TmdbApiException invalidResponse(String operation) {
        return new TmdbApiException(operation, Kind.INVALID_RESPONSE, null);
    }

    public static TmdbApiException httpFailure(String operation, int status) {
        Kind kind = status == 404 ? Kind.NOT_FOUND
            : status == 429 || status >= 500 || status < 0 ? Kind.TRANSIENT : Kind.PERMANENT;
        return new TmdbApiException(operation, kind, status);
    }

    public static TmdbApiException permanent(String operation) {
        return new TmdbApiException(operation, Kind.PERMANENT, null);
    }

    public boolean retryable() {
        return kind == Kind.TRANSIENT || kind == Kind.INVALID_RESPONSE;
    }

    public boolean notFound() {
        return kind == Kind.NOT_FOUND;
    }

    public static String safeMessage(Throwable error) {
        if (error == null) return null;
        if (error instanceof TmdbApiException failure) return failure.getMessage();
        return "type=" + error.getClass().getSimpleName();
    }
}
