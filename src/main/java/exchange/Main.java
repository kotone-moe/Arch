package exchange;

import exchange.api.ExchangeApi;
import exchange.core.ExchangeFactory;
import exchange.network.UdpExchangeServer;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Точка входа: запускает биржу с хранилищем в файле H2 и UDP-интерфейсом
 * для клиентов (аргументы: [jdbcUrl] [udp-порт], порт по умолчанию 9000).
 * При штатном завершении (Ctrl+C) срабатывает shutdown hook и закрывает
 * UDP-сервер и БД. При нештатном завершении (kill/сбой) данные не теряются:
 * каждая заявка фиксируется в БД в собственной транзакции сразу после выполнения.
 */
public final class Main {

    private static final int DEFAULT_UDP_PORT = 9000;

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        String jdbcUrl = args.length > 0 ? args[0] : "jdbc:h2:file:./data/exchange";
        int udpPort = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_UDP_PORT;

        ExchangeApi exchange = ExchangeFactory.createPersistent(jdbcUrl);
        UdpExchangeServer server = new UdpExchangeServer(udpPort, exchange);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            exchange.close();
        }));

        System.out.println("Биржа запущена, БД: " + jdbcUrl);
        System.out.println("UDP-интерфейс: порт " + server.port());
        System.out.println("Нажмите Enter для штатного завершения...");
        new BufferedReader(new InputStreamReader(System.in)).readLine();
        server.close();
        exchange.close();
    }
}
