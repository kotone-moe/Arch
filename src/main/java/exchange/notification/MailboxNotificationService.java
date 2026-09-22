package exchange.notification;

import exchange.api.ClientListener;
import exchange.domain.Trade;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;

/**
 * Доставляет оповещения онлайн-клиентам сразу, а для тех, кто не в сети,
 * складывает их в «почтовый ящик» и отдаёт при подключении.
 * Методы синхронизированы, чтобы оповещение не потерялось в момент подключения клиента.
 */
public final class MailboxNotificationService implements TradeNotifier, ClientConnections {

    private final Map<String, ClientListener> onlineClients = new HashMap<>();
    private final Map<String, Queue<Trade>> mailboxes = new HashMap<>();

    @Override
    public synchronized void connect(String clientId, ClientListener listener) {
        Objects.requireNonNull(clientId, "Не задан клиент");
        Objects.requireNonNull(listener, "Не задан слушатель");
        onlineClients.put(clientId, listener);
        deliverStoredTrades(clientId, listener);
    }

    @Override
    public synchronized void disconnect(String clientId) {
        onlineClients.remove(clientId);
    }

    @Override
    public synchronized void notifyClient(String clientId, Trade trade) {
        ClientListener listener = onlineClients.get(clientId);
        if (listener == null) {
            storeInMailbox(clientId, trade);
        } else {
            listener.onTrade(trade);
        }
    }

    private void deliverStoredTrades(String clientId, ClientListener listener) {
        Queue<Trade> stored = mailboxes.remove(clientId);
        if (stored != null) {
            stored.forEach(listener::onTrade);
        }
    }

    private void storeInMailbox(String clientId, Trade trade) {
        mailboxes.computeIfAbsent(clientId, id -> new ArrayDeque<>()).add(trade);
    }
}
