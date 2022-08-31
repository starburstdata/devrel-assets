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
import io.starburst.json.JsonDeserializerCollector.CollectingConsumer;
import io.starburst.json.JsonSerializer.PredicateSerializer;
import io.starburst.json.util.Cache;
import io.starburst.json.util.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.reflect.RecordComponent;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.starburst.json.JsonDeserializerCollector.collectingConsumer;

public interface Json
{
    static Json instance()
    {
        return builder().build();
    }

    String serializeToString(Object o);

    void serializeToWriter(Object o, Writer writer);

    <T> T deserialize(TypeToken<T> type, String json);

    <T> T deserialize(Class<T> type, String json);

    <T> T deserialize(TypeToken<T> type, Reader reader);

    <T> T deserialize(Class<T> type, Reader reader);


    interface Builder
    {
        Builder addSerializer(PredicateSerializer predicateSerializer);

        Builder addDeserializer(PredicateDeserializer predicateSerializer);

        Builder add(JsonClass jsonClass);

        Builder withAlternateJsonDateTime(Optional<JsonDateTime> jsonDateTime);

        Builder withoutStandard();

        Builder withAlternateRecordCache(Cache<Class<?>, RecordComponent[]> recordCache);

        Builder withSerializationNaming(JsonNaming naming);

        Builder withDeserializationNaming(JsonNaming naming);

        Builder withPrettyPrinting();

        Builder withPrettyPrinting(int indent);

        Json build();
    }

    static Builder builder()
    {
        return new Builder() {
            private final JsonSerializer.Builder serializerBuilder = JsonSerializer.builder();
            private final JsonDeserializer.Builder deserializerBuilder = JsonDeserializer.builder();
            private JsonParser parser = JsonParser.instance();
            private JsonPrinter printer = JsonPrinter.instance();
            private Optional<JsonDateTime> jsonDateTime = Optional.of(JsonDateTime.instance());
            private Cache<Class<?>, RecordComponent[]> recordCache = Cache.simple();
            private boolean addStandard = true;

            @Override
            public Builder addSerializer(PredicateSerializer predicateSerializer)
            {
                serializerBuilder.add(predicateSerializer);
                return this;
            }

            @Override
            public Builder addDeserializer(PredicateDeserializer predicateSerializer)
            {
                deserializerBuilder.add(predicateSerializer);
                return this;
            }

            @Override
            public Builder add(JsonClass jsonClass)
            {
                addDeserializer(jsonClass);
                addSerializer(jsonClass);
                return this;
            }

            @Override
            public Builder withAlternateJsonDateTime(Optional<JsonDateTime> jsonDateTime)
            {
                this.jsonDateTime = jsonDateTime;
                return this;
            }

            @Override
            public Builder withoutStandard()
            {
                addStandard = false;
                return this;
            }

            @Override
            public Builder withAlternateRecordCache(Cache<Class<?>, RecordComponent[]> recordCache)
            {
                this.recordCache = recordCache;
                return this;
            }

            @Override
            public Builder withSerializationNaming(JsonNaming naming)
            {
                printer = printer.withNaming(naming);
                return this;
            }

            @Override
            public Builder withDeserializationNaming(JsonNaming naming)
            {
                parser = parser.withNaming(naming);
                return this;
            }

            @Override
            public Builder withPrettyPrinting()
            {
                printer = printer.pretty();
                return this;
            }

            @Override
            public Builder withPrettyPrinting(int indent)
            {
                printer = printer.pretty(indent);
                return this;
            }

            @Override
            public Json build()
            {
                if (addStandard) {
                    serializerBuilder.addStandard();
                    deserializerBuilder.addStandard();
                }
                jsonDateTime.ifPresent(j -> {
                    serializerBuilder.add(j);
                    deserializerBuilder.add(j);
                });
                serializerBuilder.withAlternateRecordCache(recordCache);
                deserializerBuilder.withAlternateRecordCache(recordCache);
                return Json.build(serializerBuilder.build(), deserializerBuilder.build(), parser, printer);
            }
        };
    }

    private static Json build(JsonSerializer serializer, JsonDeserializer deserializer, JsonParser parser, JsonPrinter printer)
    {
        return new Json()
        {
            @Override
            public String serializeToString(Object o)
            {
                return serializer.serialize(o).map(printer::print).collect(Collectors.joining());
            }

            @Override
            public void serializeToWriter(Object o, Writer writer)
            {
                serializer.serialize(o).map(printer::print).forEach(charSequence -> {
                    try {
                        writer.append(charSequence);
                    }
                    catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }

            @Override
            public <T> T deserialize(TypeToken<T> typeToken, String json)
            {
                CollectingConsumer<T> collectingConsumer = collectingConsumer(deserializer, typeToken);
                return (T) deserialize(collectingConsumer, json);
            }

            @Override
            public <T> T deserialize(TypeToken<T> type, Reader reader)
            {
                CollectingConsumer<T> collectingConsumer = collectingConsumer(deserializer, type);
                return (T) deserialize(collectingConsumer, reader);
            }

            @Override
            public <T> T deserialize(Class<T> type, String json)
            {
                CollectingConsumer<T> collectingConsumer = collectingConsumer(deserializer, type);
                return type.cast(deserialize(collectingConsumer, json));
            }

            @Override
            public <T> T deserialize(Class<T> type, Reader reader)
            {
                CollectingConsumer<T> collectingConsumer = collectingConsumer(deserializer, type);
                return type.cast(deserialize(collectingConsumer, reader));
            }

            private <T> T deserialize(CollectingConsumer<T> collectingConsumer, String json)
            {
                parser.parse(json.chars()).forEachOrdered(collectingConsumer);
                return collectingConsumer.value();
            }

            private <T> T deserialize(CollectingConsumer<T> collectingConsumer, Reader reader)
            {
                BufferedReader bufferedReader = new BufferedReader(reader);
                parser.parse(bufferedReader.lines().flatMapToInt(String::chars)).forEachOrdered(collectingConsumer);
                return collectingConsumer.value();
            }
        };
    }
}
