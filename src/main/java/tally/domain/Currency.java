package tally.domain;

/**
 * A currency, carrying its ISO 4217 alphabetic code and the number of decimal
 * places its minor unit uses.
 *
 * <p>The scale is the part that matters. It is not presentation: it decides
 * what one unit of {@link Money} means. A {@code Money} of 100 is one US dollar
 * (scale 2), one hundred Japanese yen (scale 0), and a tenth of a Kuwaiti dinar
 * (scale 3). Storing amounts without their scale is how ledgers end up off by a
 * factor of a hundred.
 *
 * <p>All three real-world scales are represented deliberately, so that code and
 * tests cannot quietly assume every currency has two decimal places.
 *
 * <p>This is a closed set. Currencies are not user data: adding one is a change
 * to the domain, reviewed like any other, and a closed enum means a
 * {@code switch} over currencies is checked by the compiler.
 */
public enum Currency {
    /** United States dollar. */
    USD("USD", 2),
    /** Euro. */
    EUR("EUR", 2),
    /** Pound sterling. */
    GBP("GBP", 2),
    /** Brazilian real. */
    BRL("BRL", 2),
    /** Japanese yen — no minor unit. */
    JPY("JPY", 0),
    /** Kuwaiti dinar — three decimal places. */
    KWD("KWD", 3);

    private final String code;
    private final int scale;

    Currency(String code, int scale) {
        this.code = code;
        this.scale = scale;
    }

    /** The ISO 4217 alphabetic code, such as {@code "USD"}. */
    public String code() {
        return code;
    }

    /**
     * How many decimal places this currency's minor unit occupies: 2 for USD
     * (cents), 0 for JPY, 3 for KWD (fils).
     */
    public int scale() {
        return scale;
    }
}
