package sn.senwallet.ledger.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import sn.senwallet.ledger.domain.model.account.Balances;
import sn.senwallet.shared.money.Currency;
import sn.senwallet.shared.money.Money;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Column conversions. Amounts are stored as {@code bigint} minor units,
 * enums as lower-case labels, instants as {@code timestamptz}.
 */
final class Sql {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private Sql() {
    }

    static String label(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    static <E extends Enum<E>> E fromLabel(Class<E> type, String label) {
        return Enum.valueOf(type, label.toUpperCase(Locale.ROOT));
    }

    static OffsetDateTime timestamp(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    static long minorUnits(Money money) {
        return money.toMinorUnits();
    }

    static Money money(long minorUnits, Currency currency) {
        return Money.of(BigDecimal.valueOf(minorUnits, currency.exponent()), currency);
    }

    static Currency currency(ResultSet rs, String column) throws SQLException {
        return Currency.valueOf(rs.getString(column).trim());
    }

    static Balances balances(ResultSet rs, Currency currency, long lockVersion) throws SQLException {
        return new Balances(
                money(rs.getLong("posted_dr"), currency),
                money(rs.getLong("posted_cr"), currency),
                money(rs.getLong("pending_dr"), currency),
                money(rs.getLong("pending_cr"), currency),
                lockVersion);
    }

    static String json(Map<String, String> metadata) {
        return JSON.writeValueAsString(metadata);
    }

    static Map<String, String> metadata(ResultSet rs, String column) throws SQLException {
        return JSON.readValue(rs.getString(column), STRING_MAP);
    }
}
