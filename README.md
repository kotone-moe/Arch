# Биржа валют: лабораторная работа №2

Клиенты работают через интерфейс `ExchangeApi`, каждый в своём потоке.
Состояние биржи хранится во встроенной СУБД **H2** (файл): ждущие заявки, история сделок,
номера заявок и сделок, очередь оповещений для клиентов не в сети.

При перезапуске программа восстанавливает то же состояние, что было до завершения:
* штатное завершение — `close()` (в `Main` на него настроен shutdown hook);
* нештатное завершение — данные не теряются: каждая заявка фиксируется
  в БД в собственной транзакции (заявки + сделки + оповещения атомарно).

## Как запустить

Нужны Java 17 или новее и Maven.

Тесты (включая сценарии штатного и нештатного завершения):

    mvn test

Запуск биржи (файл БД по умолчанию `./data/exchange`, свой адрес — первым аргументом):

    mvn -q compile exec:java -Dexec.mainClass=exchange.Main

или класс `exchange.Main` через IntelliJ IDEA. Повторный запуск на том же файле
показывает восстановление состояния. Ctrl+C — штатное завершение.

## Метрики (Prometheus)

Биржа считает метрики через библиотеку `io.prometheus:prometheus-metrics-core` (1.x):

* `exchange_orders_processed_total` — обработанные заявки клиентов
  (инкремент в `Exchange.placeOrder` после успешной обработки: после `commit()`
  в персистентном режиме; неудачные/отклонённые заявки не считаются);
* `exchange_notifications_delivered_total` — доставленные клиентам оповещения
  (инкремент в `MailboxNotificationService` в момент реального вызова `onTrade`,
  в том числе при доставке из «почтового ящика» офлайн-клиенту при его `connect`).

Счётчики создаются в `PrometheusExchangeMetrics` (пакет `exchange.metrics`) со своим
`PrometheusRegistry` — поэтому каждый экземпляр биржи независим, тесты не конфликтуют.
Счётчики процессные: после перезапуска программы они начинаются с нуля (семантика
Prometheus); восстановленное состояние биржи при этом не зависит от метрик.

Метрики читаются программно (`ordersProcessed()`, `notificationsDelivered()`, `registry()`).
HTTP-эндпоинт `/metrics` сейчас не выставляется; при необходимости его добавляет
`io.prometheus:prometheus-metrics-exporter-httpserver` на базе `PrometheusExchangeMetrics.registry()`.

Проверка метрик — тесты `exchange.metrics.PrometheusMetricsTest` (6 сценариев,
включая штатное и нештатное завершение).

## Структура

    exchange/
    ├── api/            то, что видит клиент (ExchangeApi, OrderRequest, ClientListener)
    ├── domain/         предметные объекты (Side, CurrencyPair, Order, Trade)
    ├── matching/       книги ордеров и поиск пар покупатель/продавец
    ├── notification/   оповещения клиентов, в том числе для тех, кто не в сети
    ├── common/         счётчики уникальных номеров
    ├── persistence/    хранилище на H2 (ExchangeStore, JdbcExchangeStore)
    ├── metrics/        метрики Prometheus (ExchangeMetrics, PrometheusExchangeMetrics)
    ├── core/           Exchange (главный класс) и ExchangeFactory (сборка частей)
    └── Main.java       точка входа с shutdown hook
