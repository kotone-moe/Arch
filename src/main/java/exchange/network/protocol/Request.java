package exchange.network.protocol;

import exchange.api.OrderRequest;

/**
 * Запрос клиента: один из трёх вызовов ExchangeApi.
 * Для PLACE_ORDER поля заказа лежат в order (clientId дублируется там же),
 * для CONNECT/DISCONNECT order == null.
 */
public record Request(long requestId, CommandType command, String clientId, OrderRequest order) {

    /** Команды протокола — по одной на метод ExchangeApi. */
    public enum CommandType {
        CONNECT,
        DISCONNECT,
        PLACE_ORDER
    }

    public static Request connect(long requestId, String clientId) {
        return new Request(requestId, CommandType.CONNECT, clientId, null);
    }

    public static Request disconnect(long requestId, String clientId) {
        return new Request(requestId, CommandType.DISCONNECT, clientId, null);
    }

    public static Request placeOrder(long requestId, OrderRequest order) {
        return new Request(requestId, CommandType.PLACE_ORDER, order.clientId(), order);
    }
}
