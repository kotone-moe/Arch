package exchange.notification;

import exchange.api.ClientListener;
import exchange.domain.Trade;
import exchange.persistence.ExchangeStore;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;

/**
 * Доставляет оповещения онлайн-клиентам сразу, а для тех, кто не в сети,
 * складывает их в «почтовый ящик» и отдаёт при подключении.
 * Методы синхронизированы, чтобы оповещение не потерялось в момент подключения клиента.
 * <p>
 * С хранилищем (H2) очередь живёт в БД: строки добавляются в одной транзакции со сделкой
 * (см. Exchange.placeOrder), а здесь только доставляются и удаляются — ничего не теряется
 * при нештатном завершении.
 */
public final class MailboxNotificationService implements TradeNotifier, ClientConnections {

    private final Map<String, ClientListener> onlineClients = new HashMap<>();
    private final Map<String, Queue<Trade>> mailboxes = new HashMap<>();
    private final ExchangeStore store;

    public MailboxNotificationService() {
        this(null);
    }

    public MailboxNotificationService(ExchangeStore store) {
        this.store = store;
    }

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
            if (store == null) {
                storeInMailbox(clientId, trade);
            }
            // С хранилищем строка уже сохранена в транзакции сделки — делать нечего.
        } else if (store != null) {
            deliverStoredTrades(clientId, listener);
        } else {
            listener.onTrade(trade);
        }
    }

    private void deliverStoredTrades(String clientId, ClientListener listener) {
        if (store != null) {
            // Сначала доставляем, потом удаляем: при сбое оповещения можно повторить,
            // но не потерять.
            for (Trade trade : store.loadNotifications(clientId)) {
                listener.onTrade(trade);
            }
            store.deleteNotifications(clientId);
            return;
        }
        Queue<Trade> stored = mailboxes.remove(clientId);
        if (stored != null) {
            stored.forEach(listener::onTrade);
        }
    }

    private void storeInMailbox(String clientId, Trade trade) {
        mailboxes.computeIfAbsent(clientId, id -> new ArrayDeque<>()).add(trade);
    }
}
