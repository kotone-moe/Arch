package exchange.network.protocol;

/** Тип датаграммы: по нему получатель понимает, что делать с сообщением. */
public enum MessageType {

    /** Запрос клиента к бирже (клиент → сервер). */
    REQ("REQ"),

    /** Ответ биржи на запрос (сервер → клиент). */
    RESP("RESP"),

    /** Оповещение о сделке без запроса (сервер → клиент). */
    NOTICE("NOTICE");

    private final String prefix;

    MessageType(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }

    /** Определяет тип датаграммы по её началу; мусор → ProtocolException. */
    public static MessageType typeOf(String text) {
        for (MessageType type : values()) {
            if (text.startsWith(type.prefix + "|")) {
                return type;
            }
        }
        throw new ProtocolException(-1, "Неизвестный тип сообщения: " + text);
    }
}
