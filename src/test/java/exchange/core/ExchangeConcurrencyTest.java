package exchange.core;

import exchange.api.ExchangeApi;
import exchange.api.OrderRequest;
import exchange.domain.CurrencyPair;
import exchange.domain.Side;
import exchange.domain.Trade;
import exchange.support.RecordingListener;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static exchange.support.Decimals.d;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Проверяет работу биржи, когда много клиентов действуют одновременно, каждый в своём потоке. */
class ExchangeConcurrencyTest {

    private static final CurrencyPair EUR_USD = new CurrencyPair("EUR", "USD");
    private static final int CLIENTS_PER_SIDE = 50;
    private static final int ORDERS_PER_CLIENT = 100;
    private static final int TIMEOUT_SECONDS = 30;

    @Test
    void everyOrderIsMatchedExactlyOnceWhenManyClientsTradeAtTheSameTime() throws Exception {
        ExchangeApi exchange = ExchangeFactory.createInMemory();
        List<RecordingListener> buyers = connectClients(exchange, "buyer-");
        List<RecordingListener> sellers = connectClients(exchange, "seller-");

        runConcurrently(exchange);

        int expectedTrades = CLIENTS_PER_SIDE * ORDERS_PER_CLIENT;
        assertEquals(expectedTrades, countTrades(buyers));
        assertEquals(expectedTrades, countTrades(sellers));
        assertEquals(expectedTrades, uniqueTradeIds(buyers).size(), "Номера сделок не должны повторяться");
    }

    private List<RecordingListener> connectClients(ExchangeApi exchange, String namePrefix) {
        List<RecordingListener> listeners = new ArrayList<>();
        for (int i = 0; i < CLIENTS_PER_SIDE; i++) {
            RecordingListener listener = new RecordingListener();
            exchange.connect(namePrefix + i, listener);
            listeners.add(listener);
        }
        return listeners;
    }

    private void runConcurrently(ExchangeApi exchange) throws Exception {
        int threadCount = CLIENTS_PER_SIDE * 2;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch everyoneIsReady = new CountDownLatch(threadCount);
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<?>> clients = new ArrayList<>();

        try {
            for (int i = 0; i < CLIENTS_PER_SIDE; i++) {
                clients.add(pool.submit(client(exchange, "buyer-" + i, Side.BUY, everyoneIsReady, startSignal)));
                clients.add(pool.submit(client(exchange, "seller-" + i, Side.SELL, everyoneIsReady, startSignal)));
            }
            everyoneIsReady.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            startSignal.countDown();   // «Пистолет»: все клиенты стартуют одновременно
            for (Future<?> client : clients) {
                client.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);   // Если клиент упал, тест увидит ошибку
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private Runnable client(ExchangeApi exchange, String clientId, Side side,
                            CountDownLatch everyoneIsReady, CountDownLatch startSignal) {
        return () -> {
            everyoneIsReady.countDown();
            awaitStart(startSignal);
            for (int i = 0; i < ORDERS_PER_CLIENT; i++) {
                exchange.placeOrder(new OrderRequest(clientId, EUR_USD, side, d("1.10"), BigDecimal.ONE));
            }
        };
    }

    private void awaitStart(CountDownLatch startSignal) {
        try {
            startSignal.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Клиента прервали до старта", e);
        }
    }

    private int countTrades(List<RecordingListener> listeners) {
        return listeners.stream().mapToInt(listener -> listener.receivedTrades().size()).sum();
    }

    private Set<Long> uniqueTradeIds(List<RecordingListener> listeners) {
        Set<Long> ids = new HashSet<>();
        for (RecordingListener listener : listeners) {
            for (Trade trade : listener.receivedTrades()) {
                ids.add(trade.id());
            }
        }
        return ids;
    }
}
