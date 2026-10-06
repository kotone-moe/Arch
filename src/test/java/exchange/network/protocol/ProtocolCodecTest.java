package exchange.network.protocol;

import exchange.api.OrderRequest;
import exchange.domain.CurrencyPair;
import exchange.domain.Side;
import exchange.domain.Trade;
import exchange.support.Decimals;
import org.junit.jupiter.api.Test;

import static exchange.support.Decimals.d;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Кодирование/декодирование датаграмм протокола и реакция на некорректный ввод. */
class ProtocolCodecTest {

    private static final CurrencyPair EUR_USD = new CurrencyPair("EUR", "USD");

    // --- Round-trip: закодировали → раскодировали → то же самое -----------------------------

    @Test
    void connectRequestRoundTrip() {
        Request original = Request.connect(1, "Vasya");

        Request decoded = ProtocolCodec.decodeRequest(ProtocolCodec.encode(original));

        assertEquals(original, decoded);
        assertEquals(Request.CommandType.CONNECT, decoded.command());
        assertEquals("REQ|1|CONNECT|Vasya", ProtocolCodec.encode(original));
    }

    @Test
    void disconnectRequestRoundTrip() {
        Request original = Request.disconnect(2, "Petya");

        assertEquals(original, ProtocolCodec.decodeRequest(ProtocolCodec.encode(original)));
        assertEquals("REQ|2|DISCONNECT|Petya", ProtocolCodec.encode(original));
    }

    @Test
    void placeOrderRequestRoundTrip() {
        OrderRequest order = new OrderRequest("Vasya", EUR_USD, Side.BUY, d("1.10"), d("100"));
        Request original = Request.placeOrder(3, order);

        Request decoded = ProtocolCodec.decodeRequest(ProtocolCodec.encode(original));

        assertEquals(original, decoded);
        assertEquals("REQ|3|PLACE_ORDER|Vasya|EUR|USD|BUY|1.10|100", ProtocolCodec.encode(original));
    }

    @Test
    void okResponseRoundTrip() {
        Response original = Response.ok(3, "42");

        assertEquals(original, ProtocolCodec.decodeResponse(ProtocolCodec.encode(original)));
        assertEquals("RESP|3|OK|42", ProtocolCodec.encode(original));
    }

    @Test
    void errorResponseRoundTrip() {
        Response original = Response.error(5, "Цена должна быть больше нуля");

        assertEquals(original, ProtocolCodec.decodeResponse(ProtocolCodec.encode(original)));
    }

    @Test
    void emptyPayloadResponseRoundTrip() {
        Response original = Response.ok(1, "");

        assertEquals(original, ProtocolCodec.decodeResponse(ProtocolCodec.encode(original)));
        assertEquals("RESP|1|OK|", ProtocolCodec.encode(original));
    }

    @Test
    void noticeRoundTrip() {
        Trade trade = new Trade(7, EUR_USD, d("1.10"), d("100"), "Petya", "Vasya");
        Notice original = new Notice("Vasya", trade);

        Notice decoded = ProtocolCodec.decodeNotice(ProtocolCodec.encode(original));

        assertEquals(original, decoded);
        assertEquals("NOTICE|Vasya|7|EUR|USD|1.10|100|Petya|Vasya", ProtocolCodec.encode(original));
    }

    // --- Некорректный ввод ------------------------------------------------------------------

    @Test
    void garbageIsRejectedWithUnknownRequestId() {
        ProtocolException error = assertThrows(ProtocolException.class,
                () -> ProtocolCodec.decodeRequest("просто текст"));

        assertEquals(-1, error.requestId());
    }

    @Test
    void unknownMessageTypeIsRejected() {
        assertThrows(ProtocolException.class, () -> MessageType.typeOf("HELLO|1|2"));
    }

    @Test
    void unparseableRequestIdIsRejected() {
        ProtocolException error = assertThrows(ProtocolException.class,
                () -> ProtocolCodec.decodeRequest("REQ|x|CONNECT|Vasya"));

        assertEquals(-1, error.requestId());
    }

    @Test
    void unknownCommandKeepsRequestId() {
        ProtocolException error = assertThrows(ProtocolException.class,
                () -> ProtocolCodec.decodeRequest("REQ|5|FOO|Vasya"));

        assertEquals(5, error.requestId());
    }

    @Test
    void placeOrderWithMissingFieldsKeepsRequestId() {
        ProtocolException error = assertThrows(ProtocolException.class,
                () -> ProtocolCodec.decodeRequest("REQ|4|PLACE_ORDER|Vasya"));

        assertEquals(4, error.requestId());
    }

    @Test
    void placeOrderWithUnparseablePriceKeepsRequestId() {
        ProtocolException error = assertThrows(ProtocolException.class,
                () -> ProtocolCodec.decodeRequest("REQ|6|PLACE_ORDER|Vasya|EUR|USD|BUY|abc|10"));

        assertEquals(6, error.requestId());
    }

    @Test
    void placeOrderWithNonPositivePriceIsRejected() {
        ProtocolException error = assertThrows(ProtocolException.class,
                () -> ProtocolCodec.decodeRequest("REQ|9|PLACE_ORDER|Vasya|EUR|USD|BUY|0|10"));

        assertEquals(9, error.requestId());
        assertTrue(error.getMessage().contains("больше нуля"));
    }

    @Test
    void noticeWithWrongFieldCountIsRejected() {
        assertThrows(ProtocolException.class,
                () -> ProtocolCodec.decodeNotice("NOTICE|7|EUR|USD"));
    }

    @Test
    void clientIdWithSeparatorIsRejectedOnEncode() {
        assertThrows(IllegalArgumentException.class,
                () -> ProtocolCodec.encode(Request.connect(1, "Va|sya")));
    }

    @Test
    void numbersKeepTheirValueAfterRoundTrip() {
        OrderRequest order = new OrderRequest("Masha", EUR_USD, Side.SELL, d("0.000001"), d("999999999"));
        Request decoded = ProtocolCodec.decodeRequest(
                ProtocolCodec.encode(Request.placeOrder(10, order)));

        assertEquals(order, decoded.order());
        assertEquals(0, order.limitPrice().compareTo(decoded.order().limitPrice()));
        assertEquals(0, order.amount().compareTo(decoded.order().amount()));
        assertEquals(Decimals.d("0.000001"), decoded.order().limitPrice());
    }
}
