package exchange.network.protocol;

import exchange.domain.Trade;

/**
 * Оповещение клиенту о сделке: сервер шлёт его без предварительного запроса.
 * recipient — clientId, которому адресовано оповещение (у одного сокета
 * может быть несколько клиентов, слушатель выбирается по этому полю).
 */
public record Notice(String recipient, Trade trade) {
}
