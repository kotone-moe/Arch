package exchange.support;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Мелкие помощники, чтобы в тестах писать числа коротко. */
public final class Decimals {

    private Decimals() {
    }

    public static BigDecimal d(String number) {
        return new BigDecimal(number);
    }

    /** Сравнивает числа по значению: 60 и 60.00 считаются равными. */
    public static void assertNumberEquals(String expected, BigDecimal actual) {
        assertEquals(0, d(expected).compareTo(actual),
                "Ожидали " + expected + ", а получили " + actual);
    }
}
