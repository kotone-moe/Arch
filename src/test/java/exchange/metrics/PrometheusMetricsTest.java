package exchange.metrics;

import exchange.api.ExchangeApi;
import exchange.api.OrderRequest;
import exchange.core.ExchangeFactory;
import exchange.domain.CurrencyPair;
import exchange.domain.Side;
import exchange.support.RecordingListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static exchange.support.Decimals.d;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Проверяет метрики биржи: количество обработанных заявок и доставленных оповещений,
 * а также их поведение при штатном и нештатном завершении работы программы.
 */
class PrometheusMetricsTest {

    private static final CurrencyPair EUR_USD = new CurrencyPair("EUR", "USD");

    @TempDir
    Path tempDir;

    private PrometheusExchangeMetrics metrics;
    private ExchangeApi exchange;

    @BeforeEach
    void setUp() {
        metrics = new PrometheusExchangeMetrics();
        exchange = ExchangeFactory.createInMemory(metrics);
    }

    // --- Количество обработанных заявок ----------------------------------------------------

    @Test
    void ordersCounterCountsEveryProcessedRequest() {
        exchange.placeOrder(request("Vasya", Side.SELL, "1.10", "100"));
        exchange.placeOrder(request("Petya", Side.BUY, "1.05", "50"));   // цены не пересекаются
        exchange.placeOrder(request("Masha", Side.BUY, "1.05", "50"));

        assertEquals(3, metrics.ordersProcessed());
        assertEquals(0, metrics.notificationsDelivered());
    }

    @Test
    void invalidOrderRequestIsNotCounted() {
        assertThrows(IllegalArgumentException.class,
                () -> request("Vasya", Side.BUY, "0", "10"));   // даже не дошёл до биржи
        assertEquals(0, metrics.ordersProcessed());

        exchange.placeOrder(request("Vasya", Side.BUY, "1.10", "10"));
        assertEquals(1, metrics.ordersProcessed());
    }

    // --- Количество доставленных оповещений ------------------------------------------------

    @Test
    void notificationsCounterCountsBothTradeSides() {
        exchange.connect("Vasya", new RecordingListener());
        exchange.connect("Petya", new RecordingListener());

        exchange.placeOrder(request("Vasya", Side.SELL, "1.10", "100"));
        assertEquals(1, metrics.ordersProcessed());
        assertEquals(0, metrics.notificationsDelivered());

        exchange.placeOrder(request("Petya", Side.BUY, "1.10", "100"));
        assertEquals(2, metrics.ordersProcessed());
        assertEquals(2, metrics.notificationsDelivered());   // по одному оповещению каждой стороне
    }

    @Test
    void offlineClientNotificationIsCountedOnlyWhenDelivered() {
        exchange.connect("Petya", new RecordingListener());

        exchange.placeOrder(request("Vasya", Side.SELL, "1.10", "100"));  // Вася офлайн
        exchange.placeOrder(request("Petya", Side.BUY, "1.10", "100"));

        assertEquals(1, metrics.notificationsDelivered());   // только Петя, Васе ещё не доставлено

        exchange.connect("Vasya", new RecordingListener());
        assertEquals(2, metrics.notificationsDelivered());   // доставка из «почтового ящика»
    }

    // --- Завершение работы программы -------------------------------------------------------

    @Test
    void countersAreProcessLocalAfterGracefulRestart() {
        String db = dbUrl();

        PrometheusExchangeMetrics firstRun = new PrometheusExchangeMetrics();
        try (ExchangeApi first = ExchangeFactory.createPersistent(db, firstRun)) {
            first.placeOrder(request("Vasya", Side.SELL, "1.10", "100"));
            first.placeOrder(request("Masha", Side.BUY, "1.05", "50"));   // ждущая заявка
        }   // штатное завершение: close()

        assertEquals(2, firstRun.ordersProcessed());

        PrometheusExchangeMetrics secondRun = new PrometheusExchangeMetrics();
        try (ExchangeApi second = ExchangeFactory.createPersistent(db, secondRun)) {
            assertEquals(0, secondRun.ordersProcessed(),
                    "После перезапуска счётчики начинаются заново");

            RecordingListener petya = new RecordingListener();
            second.connect("Petya", petya);
            second.placeOrder(request("Petya", Side.BUY, "1.10", "100"));

            assertEquals(1, secondRun.ordersProcessed());
            assertEquals(1, secondRun.notificationsDelivered());
            assertEquals(1, petya.receivedTrades().size(),
                    "Ждущая заявка из прошлой сессии должна восстановиться");
        }
    }

    @Test
    void ordersAreCountedBeforeAbnormalShutdown() {
        String db = dbUrl();

        PrometheusExchangeMetrics crashedRun = new PrometheusExchangeMetrics();
        ExchangeApi crashed = ExchangeFactory.createPersistent(db, crashedRun);
        crashed.placeOrder(request("Vasya", Side.SELL, "1.10", "100"));
        assertEquals(1, crashedRun.ordersProcessed());
        // close() не вызываем — процесс «упал», но заявка уже закоммичена в БД

        PrometheusExchangeMetrics restartedMetrics = new PrometheusExchangeMetrics();
        try (ExchangeApi restarted = ExchangeFactory.createPersistent(db, restartedMetrics)) {
            assertEquals(0, restartedMetrics.ordersProcessed(),
                    "После крэша счётчики начинаются заново");

            RecordingListener petya = new RecordingListener();
            restarted.connect("Petya", petya);
            restarted.placeOrder(request("Petya", Side.BUY, "1.10", "100"));

            assertEquals(1, restartedMetrics.ordersProcessed());
            assertEquals(1, restartedMetrics.notificationsDelivered());
            assertEquals(1, petya.receivedTrades().size(),
                    "Заявка из «упавшей» сессии должна восстановиться");
        }
    }

    private String dbUrl() {
        return "jdbc:h2:file:" + tempDir.resolve("exchange").toString().replace('\\', '/');
    }

    private static OrderRequest request(String clientId, Side side, String price, String amount) {
        return new OrderRequest(clientId, EUR_USD, side, d(price), d(amount));
    }
}
