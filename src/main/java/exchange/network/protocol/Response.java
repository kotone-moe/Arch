package exchange.network.protocol;

/**
 * Ответ сервера на запрос: тот же requestId, что у запроса,
 * OK с полезной нагрузкой (например, номер ордера) или ERROR с текстом ошибки.
 */
public record Response(long requestId, boolean ok, String payload) {

    public static Response ok(long requestId, String payload) {
        return new Response(requestId, true, payload);
    }

    public static Response error(long requestId, String message) {
        return new Response(requestId, false, message);
    }
}
