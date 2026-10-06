package exchange.network;

import exchange.api.ExchangeApi;
import exchange.api.OrderRequest;
import exchange.core.ExchangeFactory;
import exchange.domain.CurrencyPair;
import exchange.domain.Side;
import exchange.support.RecordingListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;

import static exchange.support.Decimals.d;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Свойства UDP-сервера: память о сокетах клиентов (как требует вариант),
 * устойчивость к мусорным датаграммам и ответы об ошибках по сети.
 */
class UdpServerTest {

    private static final CurrencyPair EUR_USD = new CurrencyPair("EUR", "USD");

    private ExchangeApi backingExchange;
    private UdpExchangeServer server;

    @BeforeEach
    void setUp() throws SocketException {
        backingExchange = ExchangeFactory.createInMemory();
        server = new UdpExchangeServer(0, backingExchange);
    }

    @AfterEach
    void tearDown() {
        server.close();
        backingExchange.close();
    }

    /**
     * Сервер запоминает сокет клиента: оповещение уходит на адрес последнего
     * сообщения, а не на первый. Старый сокет оповещений не получает.
     */
    @Test
    void noticeGoesToTheSocketOfTheLatestRequest() throws Exception {
        try (UdpExchangeClient oldSocket = client();
             UdpExchangeClient newSocket = client();
             UdpExchangeClient petya = client()) {

            RecordingListener oldListener = new RecordingListener();
            RecordingListener newListener = new RecordingListener();

            oldSocket.connect("Vasya", oldListener);
            newSocket.connect("Vasya", newListener);      // сокет Васи переехал
            petya.connect("Petya", new RecordingListener());

            newSocket.placeOrder(request("Vasya", Side.SELL, "1.10", "100"));
            petya.placeOrder(request("Petya", Side.BUY, "1.10", "100"));

            await(() -> newListener.receivedTrades().size() == 1);
            Thread.sleep(100);   // окно, за которое оповещение успело бы прийти на старый сокет
            assertEquals(0, oldListener.receivedTrades().size(),
                    "Оповещение ушло не на тот сокет");
        }
    }

    /** Мусорная датаграмма без номера запроса игнорируется, сервер продолжает работу. */
    @Test
    void serverSurvivesMalformedDatagrams() throws Exception {
        String garbageReply = sendRaw("просто мусор вместо протокола");
        assertNull(garbageReply, "На мусор без номера запроса ответа быть не должно");

        // Сервер обязан обслуживать клиентов как прежде.
        try (UdpExchangeClient client = client()) {
            client.connect("Vasya", new RecordingListener());
            long orderId = client.placeOrder(request("Vasya", Side.SELL, "1.10", "100"));
            assertEquals(1, orderId);
        }

        // Мусор с разобранным номером запроса получает ответ ERROR.
        String errorReply = sendRaw("REQ|5|FOO|Vasya");
        assertEquals("RESP|5|ERROR|Неизвестная команда: FOO", errorReply);
    }

    /** Некорректная заявка по сети (цена 0) получает понятный ответ ERROR. */
    @Test
    void invalidOrderOverWireReturnsErrorResponse() throws Exception {
        String reply = sendRaw("REQ|9|PLACE_ORDER|Vasya|EUR|USD|BUY|0|10");

        assertTrue(reply.startsWith("RESP|9|ERROR|"), "Ожидали ответ ERROR, получили: " + reply);
        assertTrue(reply.contains("больше нуля"), reply);
    }

    /** Ответ на запрос приходит именно тому сокету, с которого пришёл запрос. */
    @Test
    void responseGoesBackToTheCaller() throws Exception {
        try (DatagramSocket caller = new DatagramSocket()) {
            caller.setSoTimeout(2_000);
            byte[] data = "REQ|42|CONNECT|Masha".getBytes(StandardCharsets.UTF_8);
            caller.send(new DatagramPacket(data, data.length,
                    new InetSocketAddress("127.0.0.1", server.port())));

            assertEquals("RESP|42|OK|", receive(caller));
        }
    }

    // --- Хелперы ----------------------------------------------------------------------------

    private UdpExchangeClient client() throws SocketException {
        return new UdpExchangeClient("127.0.0.1", server.port());
    }

    private static OrderRequest request(String clientId, Side side, String price, String amount) {
        return new OrderRequest(clientId, EUR_USD, side, d(price), d(amount));
    }

    /** Сырая датаграмма на порт сервера; null — ответ не пришёл за 2 с. */
    private String sendRaw(String text) throws IOException {
        try (DatagramSocket raw = new DatagramSocket()) {
            raw.setSoTimeout(2_000);
            byte[] data = text.getBytes(StandardCharsets.UTF_8);
            raw.send(new DatagramPacket(data, data.length,
                    new InetSocketAddress("127.0.0.1", server.port())));
            try {
                return receive(raw);
            } catch (SocketTimeoutException e) {
                return null;
            }
        }
    }

    private static String receive(DatagramSocket socket) throws IOException {
        byte[] buffer = new byte[65_507];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        return new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                fail("Событие не наступило за 2 с");
            }
            Thread.sleep(10);
        }
    }
}
