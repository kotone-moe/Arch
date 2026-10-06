package exchange.network.protocol;

import exchange.api.OrderRequest;
import exchange.domain.CurrencyPair;
import exchange.domain.Side;
import exchange.domain.Trade;
import exchange.network.protocol.Request.CommandType;

import java.math.BigDecimal;

/**
 * Кодек протокола: строковая датаграмма ⇄ объекты сообщений.
 * Формат (поля разделены «|», кодировка UTF-8):
 *
 * <pre>
 * REQ|&lt;id&gt;|CONNECT|&lt;clientId&gt;
 * REQ|&lt;id&gt;|DISCONNECT|&lt;clientId&gt;
 * REQ|&lt;id&gt;|PLACE_ORDER|&lt;clientId&gt;|&lt;base&gt;|&lt;quote&gt;|&lt;BUY|SELL&gt;|&lt;price&gt;|&lt;amount&gt;
 * RESP|&lt;id&gt;|OK|&lt;payload&gt;
 * RESP|&lt;id&gt;|ERROR|&lt;message&gt;
 * NOTICE|&lt;recipient&gt;|&lt;tradeId&gt;|&lt;base&gt;|&lt;quote&gt;|&lt;price&gt;|&lt;amount&gt;|&lt;buyerId&gt;|&lt;sellerId&gt;
 * </pre>
 *
 * Символ «|» запрещён в идентификаторах клиентов и кодах валют.
 */
public final class ProtocolCodec {

    private static final String SEP = "\\|";
    private static final String FIELD_SEP = "|";

    private ProtocolCodec() {
    }

    // --- Кодирование -----------------------------------------------------------------------

    public static String encode(Request request) {
        requireNoSeparator(request.clientId(), "Идентификатор клиента");
        StringBuilder text = new StringBuilder()
                .append(MessageType.REQ.prefix()).append(FIELD_SEP)
                .append(request.requestId()).append(FIELD_SEP)
                .append(request.command());
        if (request.command() == CommandType.PLACE_ORDER) {
            OrderRequest order = request.order();
            text.append(FIELD_SEP).append(order.clientId())
                    .append(FIELD_SEP).append(order.pair().base())
                    .append(FIELD_SEP).append(order.pair().quote())
                    .append(FIELD_SEP).append(order.side())
                    .append(FIELD_SEP).append(order.limitPrice().toPlainString())
                    .append(FIELD_SEP).append(order.amount().toPlainString());
        } else {
            text.append(FIELD_SEP).append(request.clientId());
        }
        return text.toString();
    }

    public static String encode(Response response) {
        return MessageType.RESP.prefix() + FIELD_SEP
                + response.requestId() + FIELD_SEP
                + (response.ok() ? "OK" : "ERROR") + FIELD_SEP
                + response.payload();
    }

    public static String encode(Notice notice) {
        Trade trade = notice.trade();
        requireNoSeparator(notice.recipient(), "Получатель оповещения");
        return MessageType.NOTICE.prefix() + FIELD_SEP
                + notice.recipient() + FIELD_SEP
                + trade.id() + FIELD_SEP
                + trade.pair().base() + FIELD_SEP
                + trade.pair().quote() + FIELD_SEP
                + trade.price().toPlainString() + FIELD_SEP
                + trade.amount().toPlainString() + FIELD_SEP
                + trade.buyerId() + FIELD_SEP
                + trade.sellerId();
    }

    // --- Декодирование ---------------------------------------------------------------------

    public static Request decodeRequest(String text) {
        String[] fields = split(text, MessageType.REQ);
        long requestId = parseId(fields[1]);
        CommandType command = parseCommand(fields[2], requestId);
        switch (command) {
            case CONNECT, DISCONNECT -> {
                if (fields.length != 4 || fields[3].isEmpty()) {
                    throw new ProtocolException(requestId, "Не задан идентификатор клиента");
                }
                return new Request(requestId, command, fields[3], null);
            }
            case PLACE_ORDER -> {
                if (fields.length != 9) {
                    throw new ProtocolException(requestId,
                            "Заявка должна содержать 9 полей, получено " + fields.length);
                }
                return new Request(requestId, command, fields[3],
                        decodeOrder(fields, requestId));
            }
            default -> throw new ProtocolException(requestId, "Неизвестная команда: " + command);
        }
    }

    public static Response decodeResponse(String text) {
        String[] fields = split(text, MessageType.RESP);
        long requestId = parseId(fields[1]);
        if (fields.length < 3) {
            throw new ProtocolException(requestId, "В ответе нет статуса");
        }
        String payload = fields.length >= 4 ? fields[3] : "";
        switch (fields[2]) {
            case "OK":
                return Response.ok(requestId, payload);
            case "ERROR":
                return Response.error(requestId, payload);
            default:
                throw new ProtocolException(requestId, "Неизвестный статус ответа: " + fields[2]);
        }
    }

    public static Notice decodeNotice(String text) {
        String[] fields = split(text, MessageType.NOTICE);
        if (fields.length != 9) {
            throw new ProtocolException(-1,
                    "Оповещение должно содержать 9 полей, получено " + fields.length);
        }
        if (fields[1].isEmpty()) {
            throw new ProtocolException(-1, "Не задан получатель оповещения");
        }
        try {
            Trade trade = new Trade(
                    Long.parseLong(fields[2]),
                    currencyPair(fields[3], fields[4]),
                    new BigDecimal(fields[5]),
                    new BigDecimal(fields[6]),
                    fields[7],
                    fields[8]);
            return new Notice(fields[1], trade);
        } catch (RuntimeException e) {
            throw new ProtocolException(-1, "Некорректное оповещение: " + e.getMessage());
        }
    }

    // --- Вспомогательные -------------------------------------------------------------------

    private static String[] split(String text, MessageType expected) {
        String[] fields = text.split(SEP, -1);
        if (fields.length < 3 || !expected.prefix().equals(fields[0])) {
            throw new ProtocolException(-1,
                    "Ожидалось сообщение " + expected.prefix() + ", получено: " + text);
        }
        return fields;
    }

    private static long parseId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new ProtocolException(-1, "Некорректный номер запроса: " + value);
        }
    }

    private static CommandType parseCommand(String value, long requestId) {
        try {
            return CommandType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new ProtocolException(requestId, "Неизвестная команда: " + value);
        }
    }

    private static OrderRequest decodeOrder(String[] fields, long requestId) {
        try {
            Side side = Side.valueOf(fields[6]);
            return new OrderRequest(fields[3],
                    currencyPair(fields[4], fields[5]),
                    side,
                    new BigDecimal(fields[7]),
                    new BigDecimal(fields[8]));
        } catch (RuntimeException e) {
            throw new ProtocolException(requestId, e.getMessage());
        }
    }

    private static CurrencyPair currencyPair(String base, String quote) {
        return new CurrencyPair(base, quote);
    }

    private static void requireNoSeparator(String value, String name) {
        if (value.contains(FIELD_SEP)) {
            throw new IllegalArgumentException(name + " не должен содержать «" + FIELD_SEP + "»");
        }
    }
}
