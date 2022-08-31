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
package io.starburst.json.models;

import java.time.Instant;
import java.util.Objects;

public class CustomInstant
{
    private final long epochMilli;

    public CustomInstant()
    {
        this(Instant.now());
    }

    public CustomInstant(Instant instant)
    {
        epochMilli = instant.toEpochMilli();
    }

    @Override
    public String toString()
    {
        return Instant.ofEpochMilli(epochMilli).toString();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (o == null || getClass() != o.getClass()) {return false;}
        CustomInstant that = (CustomInstant) o;
        return epochMilli == that.epochMilli;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(epochMilli);
    }

    public static CustomInstant parse(String value)
    {
        return new CustomInstant(Instant.parse(value));
    }
}
