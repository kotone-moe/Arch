package exchange;

import exchange.api.ExchangeApi;
import exchange.core.ExchangeFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Точка входа: запускает биржу с хранилищем в файле H2.
 * При штатном завершении (Ctrl+C) срабатывает shutdown hook и закрывает БД.
 * При нештатном завершении (kill/сбой) данные не теряются: каждая заявка
 * фиксируется в БД в собственной транзакции сразу после выполнения.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        String jdbcUrl = args.length > 0 ? args[0] : "jdbc:h2:file:./data/exchange";
        ExchangeApi exchange = ExchangeFactory.createPersistent(jdbcUrl);
        Runtime.getRuntime().addShutdownHook(new Thread(exchange::close));

        System.out.println("Биржа запущена, БД: " + jdbcUrl);
        System.out.println("Нажмите Enter для штатного завершения...");
        new BufferedReader(new InputStreamReader(System.in)).readLine();
        exchange.close();
    }
}
