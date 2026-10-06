package exchange.metrics;

import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.model.registry.PrometheusRegistry;

/**
 * Метрики на Prometheus (библиотека prometheus-metrics 1.x).
 * Счётчики регистрируются в собственном registry, поэтому несколько экземпляров
 * (например, в тестах) не конфликтуют по именам.
 * Счётчики процессные: после перезапуска программы они начинаются заново с нуля.
 */
public final class PrometheusExchangeMetrics implements ExchangeMetrics {

    private final PrometheusRegistry registry = new PrometheusRegistry();

    private final Counter ordersProcessed = Counter.builder()
            .name("exchange_orders_processed_total")
            .help("Number of processed placeOrder requests")
            .register(registry);

    private final Counter notificationsDelivered = Counter.builder()
            .name("exchange_notifications_delivered_total")
            .help("Number of notifications delivered to clients")
            .register(registry);

    @Override
    public void orderProcessed() {
        ordersProcessed.inc();
    }

    @Override
    public void notificationDelivered() {
        notificationsDelivered.inc();
    }

    /** Обработано заявок с момента старта процесса. */
    public long ordersProcessed() {
        return ordersProcessed.getLongValue();
    }

    /** Доставлено оповещений с момента старта процесса. */
    public long notificationsDelivered() {
        return notificationsDelivered.getLongValue();
    }

    /** Registry со всеми метриками: место, где будущий экспортёр возьмёт данные. */
    public PrometheusRegistry registry() {
        return registry;
    }

    @Override
    public String toString() {
        return "exchange_orders_processed_total=" + ordersProcessed()
                + ", exchange_notifications_delivered_total=" + notificationsDelivered();
    }
}
