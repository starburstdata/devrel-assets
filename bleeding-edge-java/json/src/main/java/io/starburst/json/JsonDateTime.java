/*
 * Copyright 2022 Starburst Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.starburst.json;

import io.starburst.json.JsonDeserializer.PredicateDeserializer;
import io.starburst.json.JsonDeserializer.TypedDeserializer;
import io.starburst.json.JsonSerializer.PredicateSerializer;
import io.starburst.json.JsonToken.StringToken;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalQuery;
import java.util.Optional;
import java.util.stream.Stream;

import static io.starburst.json.JsonDeserializer.simpleTypedDeserializer;

public interface JsonDateTime
        extends PredicateSerializer, PredicateDeserializer
{
    record Formatters(
            DateTimeFormatter fullFormatter,
            DateTimeFormatter dateTimeFormatter,
            DateTimeFormatter dateFormatter,
            DateTimeFormatter timeFormatter,
            DateTimeFormatter zonedFormatter,
            DateTimeFormatter offsetDateTimeFormatter,
            DateTimeFormatter offsetTimeFormatter,
            DateTimeFormatter monthFormatter,
            DateTimeFormatter yearFormatter,
            DateTimeFormatter yearMonthFormatter
    )
    {
        public Formatters()
        {
            this(
                    DateTimeFormatter.ISO_INSTANT,
                    DateTimeFormatter.ISO_DATE_TIME,
                    DateTimeFormatter.ISO_DATE,
                    DateTimeFormatter.ISO_TIME,
                    DateTimeFormatter.ISO_ZONED_DATE_TIME,
                    DateTimeFormatter.ISO_OFFSET_DATE_TIME,
                    DateTimeFormatter.ISO_OFFSET_TIME,
                    DateTimeFormatter.ofPattern("MM"),
                    DateTimeFormatter.ofPattern("yyyy"),
                    DateTimeFormatter.ofPattern("MMyyyy"));
        }
    }

    static JsonDateTime instance()
    {
        return instance(new Formatters());
    }

    static JsonDateTime instance(Formatters formatters)
    {
        return build(formatters);
    }

    private static JsonDateTime build(Formatters formatters)
    {
        return new JsonDateTime()
        {
            @Override
            public Optional<TypedDeserializer> maybeDeserialize(JsonDeserializer rootDeserializer, TypedDeserializer parentTypedDeserializer, Type type)
            {
                return switch (type) {
                    case Class<?> clazz when clazz.equals(Instant.class) -> deserialize(formatters.fullFormatter, parentTypedDeserializer, Instant::from);
                    case Class<?> clazz when clazz.equals(LocalDate.class) -> deserialize(formatters.dateFormatter, parentTypedDeserializer, LocalDate::from);
                    case Class<?> clazz when clazz.equals(LocalTime.class) -> deserialize(formatters.timeFormatter, parentTypedDeserializer, LocalTime::from);
                    case Class<?> clazz when clazz.equals(LocalDateTime.class) -> deserialize(formatters.dateTimeFormatter, parentTypedDeserializer, LocalDateTime::from);
                    case Class<?> clazz when clazz.equals(ZonedDateTime.class) -> deserialize(formatters.zonedFormatter, parentTypedDeserializer, ZonedDateTime::from);
                    case Class<?> clazz when clazz.equals(OffsetTime.class) -> deserialize(formatters.offsetTimeFormatter, parentTypedDeserializer, OffsetTime::from);
                    case Class<?> clazz when clazz.equals(OffsetDateTime.class) -> deserialize(formatters.offsetDateTimeFormatter, parentTypedDeserializer, OffsetDateTime::from);
                    case Class<?> clazz when clazz.equals(Month.class) -> deserialize(formatters.monthFormatter, parentTypedDeserializer, Month::from);
                    case Class<?> clazz when clazz.equals(YearMonth.class) -> deserialize(formatters.yearMonthFormatter, parentTypedDeserializer, YearMonth::from);
                    case Class<?> clazz when clazz.equals(Year.class) -> deserialize(formatters.yearFormatter, parentTypedDeserializer, Year::from);
                    case Class<?> clazz when clazz.equals(Duration.class) -> Optional.of(simpleTypedDeserializer(parentTypedDeserializer, StringToken.class, stringToken -> Duration.parse(stringToken.value())));
                    default -> Optional.empty();
                };
            }

            @Override
            public Optional<Stream<JsonToken>> maybeSerialize(JsonSerializer rootSerializer, Object o)
            {
                return switch (o) {
                    case Instant instant -> Optional.of(Stream.of(new StringToken(formatters.fullFormatter.format(instant))));
                    case LocalDate localDate -> Optional.of(Stream.of(new StringToken(formatters.dateFormatter.format(localDate))));
                    case LocalTime localTime -> Optional.of(Stream.of(new StringToken(formatters.timeFormatter.format(localTime))));
                    case LocalDateTime localDateTime -> Optional.of(Stream.of(new StringToken(formatters.dateTimeFormatter.format(localDateTime))));
                    case ZonedDateTime zonedDateTime -> Optional.of(Stream.of(new StringToken(formatters.zonedFormatter.format(zonedDateTime))));
                    case OffsetTime offsetTime -> Optional.of(Stream.of(new StringToken(formatters.offsetTimeFormatter.format(offsetTime))));
                    case OffsetDateTime offsetDateTime -> Optional.of(Stream.of(new StringToken(formatters.offsetDateTimeFormatter.format(offsetDateTime))));
                    case Month month -> Optional.of(Stream.of(new StringToken(formatters.monthFormatter.format(month))));
                    case YearMonth yearMonth -> Optional.of(Stream.of(new StringToken(formatters.yearMonthFormatter.format(yearMonth))));
                    case Year year -> Optional.of(Stream.of(new StringToken(formatters.yearFormatter.format(year))));
                    case Duration duration -> Optional.of(Stream.of(new StringToken(duration.toString())));
                    default -> Optional.empty();
                };
            }
        };
    }

    private static <T> Optional<TypedDeserializer> deserialize(DateTimeFormatter formatter, TypedDeserializer parentTypedDeserializer, TemporalQuery<T> query)
    {
        return Optional.of(simpleTypedDeserializer(parentTypedDeserializer, StringToken.class, stringToken -> formatter.parse(stringToken.value(), query)));
    }
}
