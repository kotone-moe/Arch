# Биржа валют: лабораторная работа №1

Программа хранит все данные в оперативной памяти. Клиенты работают через интерфейс `ExchangeApi`,
каждый в своём потоке.

## Как запустить тесты

Нужны Java 17 или новее и Maven:

    mvn test

В IntelliJ IDEA: File -> Open -> выбрать папку проекта (или pom.xml), затем правой кнопкой по
`src/test/java` -> Run 'All Tests'.

## Структура

    exchange/
    ├── api/            то, что видит клиент (ExchangeApi, OrderRequest, ClientListener)
    ├── domain/         предметные объекты (Side, CurrencyPair, Order, Trade)
    ├── matching/       книги ордеров и поиск пар покупатель/продавец
    ├── notification/   оповещения клиентов, в том числе для тех, кто не в сети
    ├── common/         счётчики уникальных номеров
    └── core/           Exchange (главный класс) и ExchangeFactory (сборка частей)
