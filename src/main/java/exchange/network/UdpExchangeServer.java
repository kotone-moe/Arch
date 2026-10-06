package exchange.network;

import exchange.api.ClientListener;
import exchange.api.ExchangeApi;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * UDP-сервер биржи: слушает один DatagramSocket и обслуживает запросы клиентов.
 * <p>
 * Для каждого клиента сервер запоминает его UDP-сокет (InetSocketAddress),
 * обновляя адрес при каждом полученном сообщении: ответы и оповещения о сделках
 * (NOTICE) отправляются именно на этот адрес. Клиент, не приславший ни одного
 * сообщения, адреса не имеет — его оповещения копятся в бирже (почтовый ящик).
 * <p>
 * Обработка однопоточная: один поток разбирает датаграммы, вызывает ExchangeApi
 * и отправляет ответы, поэтому оповещения и ответы уходят в порядке вызовов.
 * Некорректные датаграммы без извлечённого номера запроса игнорируются.
 */
public final class UdpExchangeServer implements AutoCloseable {

    private static final int MAX_DATAGRAM_SIZE = 65_507;
    private static final int SOCKET_BUFFER_SIZE = 1 << 20;

    private final DatagramSocket socket;
    private final ExchangeApi exchange;
    private final Map<String, InetSocketAddress> clientSockets = new ConcurrentHashMap<>();
    private final Thread listenerThread;
    private volatile boolean running = true;

    public UdpExchangeServer(int port, ExchangeApi exchange) throws SocketException {
        this.exchange = exchange;
        this.socket = new DatagramSocket(null);
        this.socket.setReceiveBufferSize(SOCKET_BUFFER_SIZE);
        this.socket.setSendBufferSize(SOCKET_BUFFER_SIZE);
        this.socket.bind(new InetSocketAddress(port));
        this.listenerThread = new Thread(this::listen, "udp-exchange-server");
        this.listenerThread.setDaemon(true);
        this.listenerThread.start();
    }

    /** Фактический порт (полезен при тестовом запуске на порту 0). */
    public int port() {
        return socket.getLocalPort();
    }

    private void listen() {
        byte[] buffer = new byte[MAX_DATAGRAM_SIZE];
        while (running) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
            } catch (IOException e) {
                return;   // сокет закрыт — сервер остановлен
            }
            String text = new String(packet.getData(), packet.getOffset(),
                    packet.getLength(), StandardCharsets.UTF_8);
            InetSocketAddress from = (InetSocketAddress) packet.getSocketAddress();
            try {
                handle(text, from);
            } catch (RuntimeException e) {
                // Сбой обработки одной датаграммы не должен останавливать сервер.
            }
        }
    }

    private void handle(String text, InetSocketAddress from) {
        long requestId = -1;
        try {
            Request request = ProtocolCodec.decodeRequest(text);
            requestId = request.requestId();
            send(ProtocolCodec.encode(process(request, from)), from);
        } catch (ProtocolException e) {
            // Номер запроса не извлечён — ответить некому, просто игнорируем мусор.
            if (e.requestId() >= 0) {
                send(ProtocolCodec.encode(Response.error(e.requestId(), e.getMessage())), from);
            }
        } catch (RuntimeException e) {
            if (requestId >= 0) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                send(ProtocolCodec.encode(Response.error(requestId, message)), from);
            }
        }
    }

    private Response process(Request request, InetSocketAddress from) {
        String clientId = request.clientId();
        // Запоминаем/обновляем сокет клиента: на него пойдут ответы и оповещения.
        clientSockets.put(clientId, from);
        switch (request.command()) {
            case CONNECT -> {
                exchange.connect(clientId, noticeSender(clientId));
                return Response.ok(request.requestId(), "");
            }
            case DISCONNECT -> {
                exchange.disconnect(clientId);
                return Response.ok(request.requestId(), "");
            }
            case PLACE_ORDER -> {
                long orderId = exchange.placeOrder(request.order());
                return Response.ok(request.requestId(), Long.toString(orderId));
            }
            default -> throw new ProtocolException(request.requestId(),
                    "Неизвестная команда: " + request.command());
        }
    }

    /**
     * Оповещатель для клиента: шлёт NOTICE на запомненный сокет.
     * Если адреса нет (клиент отключался или не подключался), сообщение
     * остаётся в «почтовом ящике» биржи и доедет при следующем CONNECT.
     * Отправка оповещений «best-effort»: сбой отправки не ломает сделку.
     */
    private ClientListener noticeSender(String clientId) {
        return trade -> {
            InetSocketAddress address = clientSockets.get(clientId);
            if (address != null) {
                try {
                    send(ProtocolCodec.encode(new Notice(clientId, trade)), address);
                } catch (RuntimeException e) {
                    // UDP не гарантирует доставку — игнорируем.
                }
            }
        };
    }

    private void send(String text, InetSocketAddress to) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        try {
            socket.send(new DatagramPacket(data, data.length, to));
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось отправить датаграмму: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        running = false;
        socket.close();
        try {
            listenerThread.join(2_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
