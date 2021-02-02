/*
 * Copyright 2017 - 2021 tsc4j project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.tsc4j.core.impl;

import com.github.tsc4j.core.ByClassRegistry;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException.BadValue;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigMemorySize;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URL;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Currency;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Various value deserialization functions. Each deserializer function should accept any object type including
 * lightbend's {@link Config}, {@link ConfigValue}, {@link ConfigList}.
 */
@Slf4j
@UtilityClass
public class Deserializers {
    private static final String KEY = "k";
    private static final Pattern IPV4_PATTERN = Pattern.compile("^(?:::ffff:)?\\d+\\.\\d+\\.\\d+\\.\\d+$");
    private static final Pattern IPV6_PATTERN = Pattern.compile("^[a-f0-9:]+:+[a-f0-9]+$");

    /**
     * Converts config value to {@link Boolean}.
     *
     * @param value config value
     * @return boolean
     */
    public boolean toBoolean(ConfigValue value) {
        if (value == null) {
            return false;
        } else if (value.valueType() == ConfigValueType.BOOLEAN) {
            return (Boolean) value.unwrapped();
        } else if (value.valueType() == ConfigValueType.NUMBER) {
            return ((Number) value.unwrapped()).longValue() > 0;
        }
        return Boolean.parseBoolean(trimmedStr(value));
    }

    /**
     * Converts config value to {@link String}.
     *
     * @param value config value
     * @return string
     */
    public String toString(ConfigValue value) {
        if (value == null) {
            return null;
        }
        val unwrapped = value.unwrapped();
        return (unwrapped == null) ? null : unwrapped.toString();
    }

    /**
     * Converts config value to {@link Short}.
     *
     * @param value config value
     * @return short
     */
    public short toShort(ConfigValue value) {
        val n = toNumber(value);
        return (n == null) ? Short.parseShort(reqNonEmptyS(value, short.class)) : n.shortValue();
    }

    /**
     * Converts config value to {@link Long}.
     *
     * @param value config value
     * @return long
     */
    public int toInteger(ConfigValue value) {
        val n = toNumber(value);
        return (n == null) ? Integer.parseInt(reqNonEmptyS(value, int.class)) : n.intValue();
    }

    /**
     * Converts config value to {@link Long}.
     *
     * @param value config value
     * @return long
     */
    public long toLong(ConfigValue value) {
        val n = toNumber(value);
        return (n == null) ? Long.parseLong(reqNonEmptyS(value, long.class)) : n.longValue();
    }

    /**
     * Converts config value to {@link Float}.
     *
     * @param value config value
     * @return float
     */
    public float toFloat(ConfigValue value) {
        val number = toNumber(value);
        return (number == null) ? Float.parseFloat(reqNonEmptyS(value, float.class)) : number.floatValue();
    }

    /**
     * Converts config value to {@link Double}.
     *
     * @param value config value
     * @return double
     */
    public double toDouble(ConfigValue value) {
        val n = toNumber(value);
        return (n == null) ? Double.parseDouble(reqNonEmptyS(value, double.class)) : n.doubleValue();
    }

    /**
     * Converts config value to {@link Number}.
     *
     * @param value config value
     * @return number
     */
    private Number toNumber(ConfigValue value) {
        if (value.valueType() == ConfigValueType.NUMBER) {
            val unwrapped = value.unwrapped();
            if (unwrapped instanceof Number) {
                return (Number) unwrapped;
            }
        }
        return null;
    }

    /**
     * Converts config value to {@link BigInteger}.
     *
     * @param value config value
     * @return big int
     */
    public BigInteger toBigInteger(ConfigValue value) {
        return new BigInteger(reqNonEmptyS(value, BigInteger.class));
    }

    /**
     * Converts config value to {@link BigDecimal}.
     *
     * @param value config value
     * @return big decimal
     */
    public BigDecimal toBigDecimal(ConfigValue value) {
        return new BigDecimal(reqNonEmptyS(value, BigDecimal.class));
    }

    /**
     * Converts config value to {@link Charset}.
     *
     * @param value config value
     * @return charset
     */
    public Charset toCharset(ConfigValue value) {
        return Charset.forName(reqNonEmptyS(value, Charset.class));
    }

    /**
     * Converts config value to {@link File}.
     *
     * @param value config value
     * @return file
     */
    public File toFile(ConfigValue value) {
        return new File(reqNonEmptyS(value, File.class));
    }

    /**
     * Converts config value to {@link Path}.
     *
     * @param value config value
     * @return path
     */
    public Path toPath(ConfigValue value) {
        return Paths.get(reqNonEmptyS(value, Path.class));
    }

    /**
     * Converts config value to {@link InetAddress}.
     *
     * @param value config value
     * @return inet address
     */
    @SneakyThrows
    public InetAddress toInetAddress(ConfigValue value) {
        val s = reqNonEmptyS(value, InetAddress.class).toLowerCase();
        if (IPV4_PATTERN.matcher(s).find() || IPV6_PATTERN.matcher(s).find()) {
            return InetAddress.getByName(s);
        }
        throw new IllegalArgumentException("Invalid IP address string: '" + value + "'.");
    }

    /**
     * Converts config value to {@link NetworkInterface}.
     *
     * @param value config value
     * @return network interface
     */
    @SneakyThrows
    public NetworkInterface toNetworkInterface(ConfigValue value) {
        return Optional.ofNullable(NetworkInterface.getByName(reqNonEmptyS(value, NetworkInterface.class)))
            .orElseThrow(() -> new IllegalArgumentException("Unknown network interface: '" + value + "'"));
    }

    /**
     * Converts config value to {@link URI}.
     *
     * @param value config value
     * @return uri
     */
    public URI toUri(ConfigValue value) {
        return URI.create(reqNonEmptyS(value, URI.class));
    }

    /**
     * Converts config value to {@link URL}.
     *
     * @param value config value
     * @return URL
     */
    @SneakyThrows
    public URL toUrl(ConfigValue value) {
        return new URL(reqNonEmptyS(value, URL.class));
    }

    /**
     * Converts config value to {@link Class}.
     *
     * @param value config value
     * @return class
     */
    @SneakyThrows
    public Class<?> toClass(ConfigValue value) {
        return Class.forName(reqNonEmptyS(value, Class.class));
    }

    /**
     * Converts config value to {@link ByteOrder}.
     *
     * @param value config value
     * @return byte order
     */
    public ByteOrder toByteOrder(ConfigValue value) {
        val s = trimmedStr(value);
        if ("big_endian".equalsIgnoreCase(s)) {
            return ByteOrder.BIG_ENDIAN;
        } else if ("little_endian".equalsIgnoreCase(s)) {
            return ByteOrder.LITTLE_ENDIAN;
        }
        throw new IllegalArgumentException("Invalid byte order: " + value);
    }

    /**
     * Converts config value to {@link Currency}.
     *
     * @param value config value
     * @return currency
     */
    public Currency toCurrency(ConfigValue value) {
        return Currency.getInstance(reqNonEmptyS(value, Currency.class));
    }

    /**
     * Converts config value to {@link Locale}.
     *
     * @param value config value
     * @return locale
     */
    public Locale toLocale(ConfigValue value) {
        return Locale.forLanguageTag(reqNonEmptyS(value, Locale.class));
    }

    /**
     * Converts config value to by array by decoding base64 (mime or urlencoded) string.
     *
     * @param value config value
     * @return byte array
     */
    public byte[] toBytesFromBase64(ConfigValue value) {
        return decodeBase64(trimmedStr(value));
    }

    /**
     * Decodes base64 string to bytes.
     *
     * @param s base64 string to decode; may be url-safe or mime encoded base64 string
     * @return decoded bytes
     * @throws IllegalArgumentException if string can't be base64 decoded
     */
    byte[] decodeBase64(String s) {
        try {
            // try first with url-safe decoder
            return Base64.getUrlDecoder().decode(s);
        } catch (IllegalArgumentException e) {
            // otherwise try with mime decoder
            return Base64.getMimeDecoder().decode(s);
        }
    }

    /**
     * Converts config value to {@link Pattern}.
     *
     * @param value config value
     * @return pattern
     */
    public Pattern toPattern(ConfigValue value) {
        return Pattern.compile(reqNonEmptyS(value, Pattern.class));
    }

    /**
     * Converts config value to {@link ConfigMemorySize}.
     *
     * @param value config value
     * @return ConfigMemorySize
     */
    public ConfigMemorySize toConfigMemorySize(ConfigValue value) {
        return getViaConfig(trimmedStr(value), Config::getMemorySize);
    }

    /**
     * Converts given value to {@link Duration}
     *
     * @param value value
     * @return parsed duration
     * @throws RuntimeException in case of bad input
     */
    public Duration toDuration(ConfigValue value) {
        val s = reqNonEmptyS(value, Duration.class);
        try {
            return Duration.parse(s);
        } catch (DateTimeParseException e) {
            return getViaConfig(s, Config::getDuration);
        }
    }

    /**
     * Converts given value to {@link Instant}
     *
     * @param value value
     * @return instant
     */
    public Instant toInstant(ConfigValue value) {
        return Instant.parse(reqNonEmptyS(value, Instant.class));
    }

    /**
     * Converts given value to {@link Period}.
     *
     * @param value value
     * @return parsed period
     * @throws RuntimeException in case of bad input
     */
    public Period toPeriod(ConfigValue value) {
        val s = reqNonEmptyS(value, Period.class);
        try {
            return Period.parse(s);
        } catch (Exception e) {
            return getViaConfig(s, Config::getPeriod);
        }
    }

    /**
     * Converts <a href=>ISO 8601</a> string to a {@link ZonedDateTime}.
     *
     * @param value value to convert
     * @return zoned date time
     * @throws RuntimeException in case of parsing errors
     */
    public ZonedDateTime toZonedDateTime(ConfigValue value) {
        return ZonedDateTime.parse(reqNonEmptyS(value, ZonedDateTime.class));
    }

    /**
     * Converts <a href=>ISO 8601</a> string to a {@link LocalDateTime}.
     *
     * @param value value to convert
     * @return local date time
     * @throws RuntimeException in case of parsing errors
     */
    public LocalDateTime toLocalDateTime(ConfigValue value) {
        try {
            return LocalDateTime.parse(reqNonEmptyS(value, LocalDateTime.class));
        } catch (DateTimeParseException e) {
            return toZonedDateTime(value).toLocalDateTime();
        }
    }

    /**
     * Converts <a href=>ISO 8601</a> string to a {@link LocalDate}.
     *
     * @param value value to convert
     * @return local date
     * @throws RuntimeException in case of parsing errors
     */
    public LocalDate toLocalDate(ConfigValue value) {
        try {
            return LocalDate.parse(reqNonEmptyS(value, LocalDate.class));
        } catch (DateTimeParseException e) {
            return toZonedDateTime(value).toLocalDate();
        }
    }

    /**
     * Converts <a href=>ISO 8601</a> string to a {@link LocalTime}.
     *
     * @param value value to convert
     * @return local time
     */
    public LocalTime toLocalTime(ConfigValue value) {
        return LocalTime.parse(reqNonEmptyS(value, LocalTime.class));
    }

    /**
     * Converts <a href=>ISO 8601</a> string to a {@link MonthDay}.
     *
     * @param value value to convert
     * @return month day
     */
    public MonthDay toMonthDay(ConfigValue value) {
        return MonthDay.parse(reqNonEmptyS(value, MonthDay.class));
    }

    /**
     * Converts <a href=>ISO 8601</a> string to a {@link OffsetDateTime}.
     *
     * @param value value to convert
     * @return offset day time
     */
    public OffsetDateTime toOffsetDateTime(ConfigValue value) {
        return OffsetDateTime.parse(reqNonEmptyS(value, OffsetDateTime.class));
    }

    /**
     * Converts <a href=>ISO 8601</a> string to a {@link OffsetTime}.
     *
     * @param value value to convert
     * @return offset time
     */
    public OffsetTime toOffsetTime(ConfigValue value) {
        return OffsetTime.parse(reqNonEmptyS(value, OffsetDateTime.class));
    }

    /**
     * Converts <a href=>ISO 8601</a> string to a {@link Year}.
     *
     * @param value value to convert
     * @return year
     */
    public Year toYear(ConfigValue value) {
        return Year.parse(reqNonEmptyS(value, Year.class));
    }

    /**
     * Converts <a href=>ISO 8601</a> string to a {@link YearMonth}.
     *
     * @param value value to convert
     * @return year/month
     */
    public YearMonth toYearMonth(ConfigValue value) {
        return YearMonth.parse(reqNonEmptyS(value, YearMonth.class));
    }

    /**
     * Converts <a href=>ISO 8601</a> string to a {@link ZoneId}.
     *
     * @param value value to convert
     * @return zone id
     */
    public ZoneId toZoneId(ConfigValue value) {
        return ZoneId.of(reqNonEmptyS(value, ZoneId.class));
    }

    /**
     * Converts <a href=>ISO 8601</a> string to a {@link ZoneOffset}.
     *
     * @param value value to convert
     * @return zone offset
     */
    public ZoneOffset toZoneOffset(ConfigValue value) {
        return ZoneOffset.of(reqNonEmptyS(value, ZoneOffset.class));
    }

    /**
     * Converts <a href=>ISO 8601</a> string to a {@link Date}. If value doesn't
     * contain time zone information, system's timezone is used by default.
     *
     * @param value value to convert
     * @return date
     * @throws RuntimeException in case of parsing errors
     */
    public Date toDate(ConfigValue value) {
        try {
            return Date.from(toZonedDateTime(value).toInstant());
        } catch (Exception e) {
            val instant = toLocalDateTime(value)
                .atZone(ZoneId.systemDefault())
                .toInstant();
            return Date.from(instant);
        }
    }

    /**
     * Converts config value to a {@link TimeZone}.
     *
     * @param value value to convert
     * @return time zone
     */
    public TimeZone toTimeZone(ConfigValue value) {
        return TimeZone.getTimeZone(reqNonEmptyS(value, TimeZone.class));
    }

    /**
     * Converts config value to a {@link java.sql.Time}.
     *
     * @param value value to convert
     * @return time
     */
    public java.sql.Time toSqlTime(ConfigValue value) {
        return java.sql.Time.valueOf(reqNonEmptyS(value, java.sql.Time.class));
    }

    /**
     * Converts config value to a {@link java.sql.Timestamp}.
     *
     * @param value value to convert
     * @return timestamp
     */
    public java.sql.Timestamp toSqlTimestamp(ConfigValue value) {
        return java.sql.Timestamp.valueOf(reqNonEmptyS(value, java.sql.Timestamp.class));
    }

    /**
     * Converts config value to {@link UUID}.
     *
     * @param value value to convert
     * @return UUID
     * @throws RuntimeException in case of bad input
     */
    public UUID toUuid(ConfigValue value) {
        return UUID.fromString(reqNonEmptyS(value, UUID.class));
    }

    public Config toConfig(ConfigValue value) {
        if (value.valueType() == ConfigValueType.OBJECT) {
            return ((ConfigObject) value).toConfig();
        }
        return ConfigFactory.parseString(toString(value));
    }

    /**
     * Converts config value to a {@link ConfigObject}.
     *
     * @param value value to convert
     * @return config object
     */
    public ConfigObject toConfigObject(ConfigValue value) {
        if (value instanceof ConfigObject) {
            return (ConfigObject) value;
        }
        throw new IllegalArgumentException("Can't convert config value " + value.valueType() + " to ConfigObject.");
    }

    /**
     * Converts config value to a {@link ConfigList}.
     *
     * @param value value to convert
     * @return config list
     */
    public ConfigList toConfigList(ConfigValue value) {
        if (value instanceof ConfigList) {
            return (ConfigList) value;
        }
        throw new IllegalArgumentException("Can't convert config value " + value.valueType() + " to ConfigList.");
    }

    /**
     * Tries to deserialize object via {@link Config} accessor methods.
     *
     * @param o         object
     * @param converter config converter bifunc
     * @param <T>       required type
     * @return deserialized instance
     */
    private <T> T getViaConfig(Object o, BiFunction<Config, String, T> converter) {
        val config = ConfigFactory.parseString(KEY + ": " + o);
        return converter.apply(config, KEY);
    }

    /**
     * Converts given config value to trimmed string.
     *
     * @param value config value
     * @return value as trimmed string, empty string in case of nulls
     * @see #toString(ConfigValue)
     */
    private String trimmedStr(ConfigValue value) {
        val str = toString(value);
        return (str == null) ? "" : str.trim();
    }

    /**
     * Converts given config value to trimmed string via {@link #trimmedStr(ConfigValue)} and checks whether it's empty.
     *
     * @param value config value
     * @param clazz clazz description for the purpose of formatting an exception message in case of errors
     * @return config value as trimmed, non-empty string
     * @throws BadValue if config value stringification results in empty string.
     */
    private String reqNonEmptyS(ConfigValue value, @NonNull Class<?> clazz) {
        val s = trimmedStr(value);
        if (s.isEmpty()) {
            throw new IllegalArgumentException("Can't parse " + clazz.getName() + " from an empty string.");
        }
        return s;
    }

    /**
     * Creates registry of converter functions that are able to deserialize Lightbend's
     * config types from the {@link Config} instance directly.
     * <p/>
     * Function must be called with {@code config, path} arguments.
     *
     * @return {@link Config} converter function registry
     * @see #convertersLightbendConfigValue()
     * @see Config
     * @see ConfigList
     * @see ConfigObject
     * @see ConfigValue
     */
    public ByClassRegistry<Function<ConfigValue, ?>> convertersLightbendConfigValue() {
        return ByClassRegistry.<Function<ConfigValue, ?>>empty()
            .add(Duration.class, Deserializers::toDuration)
            .add(Period.class, Deserializers::toPeriod)
            .add(Config.class, Deserializers::toConfig)
            .add(ConfigList.class, Deserializers::toConfigList)
            .add(ConfigValue.class, Function.identity())
            .add(ConfigObject.class, Deserializers::toConfigObject)
            .add(ConfigMemorySize.class, Deserializers::toConfigMemorySize);
    }

    /**
     * Creates registry of {@link Config} converter functions that are able to deserialize Lightbend's
     * config types from the {@link Config} instance directly.
     * <p/>
     * Function must be called with {@code config, path} arguments.
     *
     * @return {@link Config} converter function registry
     * @see #convertersLightbendConfigValue()
     */
    public ByClassRegistry<BiFunction<Config, String, ?>> convertersLightbendConfig() {
        return ByClassRegistry.<BiFunction<Config, String, ?>>empty()
            .add(Config.class, (config, path) -> path.isEmpty() ? config : config.getConfig(path))
            .add(ConfigValue.class, Config::getValue)
            .add(ConfigObject.class, Config::getObject)
            .add(ConfigList.class, Config::getList)
            .add(boolean.class, Config::getBoolean)
            .add(Boolean.class, Config::getBoolean)
            .add(int.class, Config::getInt)
            .add(Integer.class, Config::getInt)
            .add(long.class, Config::getLong)
            .add(Long.class, Config::getLong)
            .add(double.class, Config::getDouble)
            .add(Double.class, Config::getDouble)
            .add(Number.class, Config::getNumber)
            .add(String.class, Config::getString)
            .add(Duration.class, Config::getDuration)
            .add(Period.class, Config::getPeriod)
            .add(TemporalAmount.class, Config::getTemporal);
    }

    /**
     * Creates {@link ConfigValue} converter function registry for basic java types.
     *
     * @return converter function registry.
     */
    public ByClassRegistry<Function<ConfigValue, ?>> convertersJavaPrimitives() {
        return ByClassRegistry.<Function<ConfigValue, ?>>empty()
            .add(boolean.class, Deserializers::toBoolean)
            .add(Boolean.class, Deserializers::toBoolean)
            .add(CharSequence.class, Deserializers::toString)
            .add(String.class, Deserializers::toString)
            .add(short.class, Deserializers::toShort)
            .add(Short.class, Deserializers::toShort)
            .add(int.class, Deserializers::toInteger)
            .add(Integer.class, Deserializers::toInteger)
            .add(long.class, Deserializers::toLong)
            .add(Long.class, Deserializers::toLong)
            .add(float.class, Deserializers::toFloat)
            .add(Float.class, Deserializers::toFloat)
            .add(double.class, Deserializers::toDouble)
            .add(Double.class, Deserializers::toDouble)
            .add(Number.class, Deserializers::toDouble)
            .add(byte[].class, Deserializers::toBytesFromBase64)
            ;
    }

    /**
     * Creates converter function registry that contains functions to deserialize commonly used java types.
     *
     * @return converter registry.
     */
    public ByClassRegistry<Function<ConfigValue, ?>> convertersJava() {
        return ByClassRegistry.<Function<ConfigValue, ?>>empty()
            .add(Class.class, Deserializers::toClass)
            .add(UUID.class, Deserializers::toUuid)
            .add(Pattern.class, Deserializers::toPattern)
            .add(Date.class, Deserializers::toDate)
            .add(TimeZone.class, Deserializers::toTimeZone)
            .add(Charset.class, Deserializers::toCharset)
            .add(File.class, Deserializers::toFile)
            .add(Path.class, Deserializers::toPath)
            .add(InetAddress.class, Deserializers::toInetAddress)
            .add(NetworkInterface.class, Deserializers::toNetworkInterface)
            .add(URI.class, Deserializers::toUri)
            .add(URL.class, Deserializers::toUrl)
            .add(ByteOrder.class, Deserializers::toByteOrder)
            .add(Currency.class, Deserializers::toCurrency)
            .add(Locale.class, Deserializers::toLocale)
            .add(BigInteger.class, Deserializers::toBigInteger)
            .add(BigDecimal.class, Deserializers::toBigDecimal)
            ;
    }

    /**
     * Creates converter function registry that contains functions to deserialize
     * <a href="https://www.threeten.org/">JSR-310</a> types.
     *
     * @return converter registry.
     */
    public ByClassRegistry<Function<ConfigValue, ?>> convertersJavaTime() {
        return ByClassRegistry.<Function<ConfigValue, ?>>empty()
            .add(Duration.class, Deserializers::toDuration)
            .add(Instant.class, Deserializers::toInstant)
            .add(Period.class, Deserializers::toPeriod)
            .add(ZonedDateTime.class, Deserializers::toZonedDateTime)
            .add(LocalDateTime.class, Deserializers::toLocalDateTime)
            .add(LocalDate.class, Deserializers::toLocalDate)
            .add(LocalTime.class, Deserializers::toLocalTime)
            .add(MonthDay.class, Deserializers::toMonthDay)
            .add(OffsetDateTime.class, Deserializers::toOffsetDateTime)
            .add(OffsetTime.class, Deserializers::toOffsetTime)
            .add(Year.class, Deserializers::toYear)
            .add(YearMonth.class, Deserializers::toYearMonth)
            .add(ZoneId.class, Deserializers::toZoneId)
            .add(ZoneOffset.class, Deserializers::toZoneOffset)
            ;
    }

    /**
     * Creates converter function registry that contains functions to deserialize
     * <a href="https://www.threeten.org/">JSR-310</a> types.
     *
     * @return converter registry.
     */
    public ByClassRegistry<Function<ConfigValue, ?>> convertersJdbc() {
        return ByClassRegistry.<Function<ConfigValue, ?>>empty()
            .add(Time.class, Deserializers::toSqlTime)
            .add(Timestamp.class, Deserializers::toSqlTimestamp)
            ;
    }

    /**
     * Creates converter function registry that contains functions to deserialize commonly used java types.
     *
     * @return converter registry.
     */
    public ByClassRegistry<Function<ConfigValue, ?>> convertersJavaCrypto() {
        return ByClassRegistry.<Function<ConfigValue, ?>>empty()
            .add(X509Certificate.class, Deserializers::toX509Certificate)
            ;
    }

    /**
     * Converts config value to {@link X509Certificate}.
     *
     * @param value config value
     * @return certificate
     */
    @SneakyThrows
    public X509Certificate toX509Certificate(ConfigValue value) {
        val encodedValues = parsePem(value);
        if (encodedValues.isEmpty()) {
            throw new IllegalArgumentException("Didn't parse any openssl pem encoded values from input.");
        }

        val encodedVal = encodedValues.get(0);
        if (!encodedVal.type.equals("CERTIFICATE")) {
            throw new IllegalArgumentException("Can't parse X509Certificate from PEM type: '" + encodedVal.type + '"');
        }

        val bytes = decodeBase64(encodedVal.encoded);
        return (X509Certificate) CertificateFactory.getInstance("X.509")
            .generateCertificate(new ByteArrayInputStream(bytes));
    }

    private static final Pattern PEM_BEGIN_PATTERN = Pattern
        .compile("-----\\s*BEGIN\\s+([A-Z\\s]{2,})\\s*-+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PEM_END_PATTERN = Pattern.compile("^-----END ", Pattern.CASE_INSENSITIVE);

    /**
     * Parses OpenSSL pem-like string for entries.
     *
     * @param value config value
     * @return list of parsed encoded entries.
     */
    @SneakyThrows
    List<EncodedValue> parsePem(ConfigValue value) {
        val res = new ArrayList<EncodedValue>(1);
        val br = new BufferedReader(new StringReader(trimmedStr(value)));

        String line;
        boolean inContent = false;
        EncodedValue encodedVal = new EncodedValue();
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            // do we have ----begin ?
            val m1 = PEM_BEGIN_PATTERN.matcher(line);
            if (m1.find()) {
                encodedVal = new EncodedValue();
                encodedVal.type = m1.group(1).toUpperCase().trim();
                inContent = true;
                continue;
            }

            // looks like content
            if (inContent) {
                // do we have ----end ?
                val m2 = PEM_END_PATTERN.matcher(line);
                if (m2.find()) {
                    res.add(encodedVal);
                    inContent = false;
                    continue;
                }

                // maybe it's a header...
                if (line.contains(":")) {
                    val chunks = line.split(":", 2);
                    encodedVal.headers.put(chunks[0].toLowerCase().trim(), chunks[1].trim());
                } else {
                    // nope, it's just a content
                    encodedVal.encoded += line + "\n";
                }
            }
        }

        return res;
    }

    @ToString(doNotUseGetters = true)
    static class EncodedValue {
        String type = "";
        String encoded = "";
        Map<String, String> headers = new LinkedHashMap<>(0);
    }
}
