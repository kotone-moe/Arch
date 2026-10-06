package exchange.network;

import exchange.api.ClientListener;
import exchange.api.ExchangeApi;
import exchange.api.OrderRequest;
import exchange.common.AtomicIdSequence;
import exchange.network.protocol.MessageType;
import exchange.network.protocol.Notice;
import exchange.network.protocol.ProtocolCodec;
import exchange.network.protocol.ProtocolException;
import exchange.network.protocol.Request;
import exchange.network.protocol.Response;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * UDP-клиент биржи: реализует ExchangeApi поверх DatagramSocket.
 * <p>
 * Вызов метода = отправленная датаграмма REQ с уникальным номером и ожидание
 * ответа RESP с тем же номером (ответы UDP могут приходить в любом порядке).
 * Отдельный поток принимает датаграммы: RESP завершает ожидающий вызов,
 * NOTICE вызывает onTrade у слушателя, привязанного к clientId.
 * <p>
 * Один клиент может обслуживать несколько clientId (как и in-process биржа).
 * Ожидание ответа ограничено таймаутом: по условию UDP не гарантирует
 * доставку, поэтому при его истечении бросается IllegalStateException.
 */
public final class UdpExchangeClient implements ExchangeApi {

    private static final int MAX_DATAGRAM_SIZE = 65_507;
    private static final int SOCKET_BUFFER_SIZE = 1 << 20;
    private static final long RESPONSE_TIMEOUT_SECONDS = 5;

    private final DatagramSocket socket;
    private final InetSocketAddress serverAddress;
    private final AtomicIdSequence requestIds = new AtomicIdSequence();
    private final Map<Long, CompletableFuture<Response>> pendingRequests = new ConcurrentHashMap<>();
    private final Map<String, ClientListener> listeners = new ConcurrentHashMap<>();
    private final Thread receiverThread;
    private volatile boolean running = true;

    public UdpExchangeClient(String host, int port) throws SocketException {
        this.socket = new DatagramSocket(null);
        this.socket.setReceiveBufferSize(SOCKET_BUFFER_SIZE);
        this.socket.setSendBufferSize(SOCKET_BUFFER_SIZE);
        this.socket.bind(new InetSocketAddress(0));
        this.serverAddress = new InetSocketAddress(host, port);
        this.receiverThread = new Thread(this::receive, "udp-exchange-client");
        this.receiverThread.setDaemon(true);
        this.receiverThread.start();
    }

    @Override
    public void connect(String clientId, ClientListener listener) {
        // Слушателя регистрируем до запроса: NOTICE может прийти раньше RESP.
        listeners.put(clientId, listener);
        Response response = call(Request.connect(requestIds.next(), clientId));
        if (!response.ok()) {
            throw new IllegalStateException(response.payload());
        }
    }

    @Override
    public void disconnect(String clientId) {
        Response response = call(Request.disconnect(requestIds.next(), clientId));
        if (!response.ok()) {
            throw new IllegalStateException(response.payload());
        }
    }

    @Override
    public long placeOrder(OrderRequest request) {
        Response response = call(Request.placeOrder(requestIds.next(), request));
        if (!response.ok()) {
            throw new IllegalStateException(response.payload());
        }
        try {
            return Long.parseLong(response.payload());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Некорректный номер ордера: " + response.payload(), e);
        }
    }

    @Override
    public void close() {
        running = false;
        socket.close();
        // Будим ожидающие вызовы, чтобы они не висели до таймаута.
        pendingRequests.values().forEach(future ->
                future.completeExceptionally(new IllegalStateException("Клиент закрыт")));
        pendingRequests.clear();
        try {
            receiverThread.join(2_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Отправляет запрос и ждёт ответ с тем же номером (с таймаутом). */
    private Response call(Request request) {
        CompletableFuture<Response> future = new CompletableFuture<>();
        pendingRequests.put(request.requestId(), future);
        try {
            send(ProtocolCodec.encode(request));
            return future.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException(
                    "Сервер не ответил на запрос " + request.requestId()
                            + " за " + RESPONSE_TIMEOUT_SECONDS + " с", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ожидание ответа прервано", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Ошибка ожидания ответа", e.getCause());
        } finally {
            pendingRequests.remove(request.requestId());
        }
    }

    private void send(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        try {
            socket.send(new DatagramPacket(data, data.length, serverAddress));
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось отправить запрос: " + e.getMessage(), e);
        }
    }

    private void receive() {
        byte[] buffer = new byte[MAX_DATAGRAM_SIZE];
        while (running) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
            } catch (IOException e) {
                return;   // сокет закрыт — клиент остановлен
            }
            String text = new String(packet.getData(), packet.getOffset(),
                    packet.getLength(), StandardCharsets.UTF_8);
            try {
                dispatch(text);
            } catch (ProtocolException e) {
                // Чужая или повреждённая датаграмма — игнорируем, поток продолжает.
            }
        }
    }

    private void dispatch(String text) {
        MessageType type = MessageType.typeOf(text);
        switch (type) {
            case RESP -> {
                Response response = ProtocolCodec.decodeResponse(text);
                CompletableFuture<Response> future = pendingRequests.get(response.requestId());
                if (future != null) {
                    future.complete(response);
                }
            }
            case NOTICE -> notifyListeners(ProtocolCodec.decodeNotice(text));
            default -> {
                // Сервер не должен слать REQ — игнорируем.
            }
        }
    }

    private void notifyListeners(Notice notice) {
        ClientListener listener = listeners.get(notice.recipient());
        if (listener != null) {
            listener.onTrade(notice.trade());
        }
    }
}
