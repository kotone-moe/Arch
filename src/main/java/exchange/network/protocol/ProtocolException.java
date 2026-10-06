package exchange.network.protocol;

/**
 * Ошибка разбора датаграммы протокола.
 * requestId = -1, если номер запроса ещё не удалось извлечь (сервер в этом
 * случае не может отправить ответ и просто игнорирует датаграмму).
 */
public final class ProtocolException extends RuntimeException {

    private final long requestId;

    public ProtocolException(long requestId, String message) {
        super(message);
        this.requestId = requestId;
    }

    public long requestId() {
        return requestId;
    }
}
