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

import org.junit.jupiter.api.Test;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestDateTime
{
    @Test
    public void testInstant()
    {
        Instant now = Instant.now();
        Json instance = Json.instance();
        assertEquals(now, instance.deserialize(Instant.class, instance.serializeToString(now)));
    }

    @Test
    public void testDatesTimes()
    {
        LocalDate localDate = LocalDate.now();
        LocalDateTime localDateTime = LocalDateTime.now();
        LocalTime localTime = LocalTime.now();
        ZonedDateTime zonedDateTime = ZonedDateTime.now();
        OffsetDateTime offsetDateTime = OffsetDateTime.now();
        OffsetTime offsetTime = OffsetTime.now();

        Json instance = Json.instance();
        assertEquals(localDate, instance.deserialize(LocalDate.class, instance.serializeToString(localDate)));
        assertEquals(localDateTime, instance.deserialize(LocalDateTime.class, instance.serializeToString(localDateTime)));
        assertEquals(localTime, instance.deserialize(LocalTime.class, instance.serializeToString(localTime)));
        assertEquals(zonedDateTime, instance.deserialize(ZonedDateTime.class, instance.serializeToString(zonedDateTime)));
        assertEquals(offsetDateTime, instance.deserialize(OffsetDateTime.class, instance.serializeToString(offsetDateTime)));
        assertEquals(offsetTime, instance.deserialize(OffsetTime.class, instance.serializeToString(offsetTime)));
    }

    @Test
    public void testMonthsYears()
    {
        Year year = Year.now();
        YearMonth yearMonth = YearMonth.now();
        Month month = Month.DECEMBER;

        Json instance = Json.instance();
        assertEquals(year, instance.deserialize(Year.class, instance.serializeToString(year)));
        assertEquals(yearMonth, instance.deserialize(YearMonth.class, instance.serializeToString(yearMonth)));
        assertEquals(month, instance.deserialize(Month.class, instance.serializeToString(month)));
    }

    @Test
    public void testDuration()
    {
        Duration duration = Duration.between(Instant.now(), Instant.now().plusSeconds(10101));
        Json instance = Json.instance();
        assertEquals(duration, instance.deserialize(Duration.class, instance.serializeToString(duration)));
    }
}
