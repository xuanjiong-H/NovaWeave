package cn.bugstack.ai.domain.agent.service.sse;

/**
 * Signals that the HTTP client can no longer receive SSE events.
 */
public class SseConnectionClosedException extends RuntimeException {

    public SseConnectionClosedException(String message, Throwable cause) {
        super(message, cause);
    }

    public static boolean isCausedBy(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SseConnectionClosedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

}
