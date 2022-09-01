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

import io.starburst.json.JsonDeserializer.TypedDeserializer;
import io.starburst.json.JsonToken.BeginArrayToken;
import io.starburst.json.JsonToken.BeginObjectToken;
import io.starburst.json.JsonToken.BooleanToken;
import io.starburst.json.JsonToken.EndArrayToken;
import io.starburst.json.JsonToken.EndObjectToken;
import io.starburst.json.JsonToken.NullToken;
import io.starburst.json.JsonToken.NumberToken;
import io.starburst.json.JsonToken.ObjectNameToken;
import io.starburst.json.JsonToken.StringToken;
import io.starburst.json.JsonToken.ValueSeparatorToken;
import io.starburst.json.util.StreamUtil;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public sealed interface JsonValue<T>
        permits NumberToken, StringToken, BooleanToken, NullToken, JsonValue.JsonObject, JsonValue.JsonArray
{
    T value();

    record JsonObject(Map<String, ? extends JsonValue<?>> value)
            implements JsonValue<Map<String, ? extends JsonValue<?>>> {}

    record JsonArray(List<? extends JsonValue<?>> value)
            implements JsonValue<List<? extends JsonValue<?>>> {}

    static JsonClass instance()
    {
        return new JsonClass() {
            @Override
            public String toString()
            {
                return "JsonClass-instance";
            }

            @Override
            public Optional<TypedDeserializer> maybeDeserialize(JsonDeserializer rootDeserializer, TypedDeserializer parentTypedDeserializer, Type type)
            {
                return switch (type) {
                    case Class<?> clazz when JsonValue.class.isAssignableFrom(clazz) -> Optional.of(deserializeJsonValue(rootDeserializer, parentTypedDeserializer));
                    default -> Optional.empty();
                };
            }

            @Override
            public Optional<Stream<JsonToken>> maybeSerialize(JsonSerializer rootSerializer, Object o)
            {
                return switch (o) {
                    case JsonValue<?> jsonValue -> Optional.of(StreamUtil.lazyStream(() -> serializeJsonValue(jsonValue)));
                    case null -> Optional.empty();
                    default -> Optional.empty();
                };
            }
        };
    }

    private static Stream<JsonToken> serializeJsonValue(JsonValue<?> jsonValue)
    {
        Stream.Builder<Stream<JsonToken>> builder = Stream.builder();
        switch (jsonValue) {
            case JsonObject(var value) -> {
                builder.accept(Stream.of(BeginObjectToken.INSTANCE));
                boolean first = true;
                for (Map.Entry<String, ? extends JsonValue<?>> entry : value.entrySet()) {
                    if (first) {
                        first = false;
                    }
                    else {
                        builder.accept(Stream.of(ValueSeparatorToken.INSTANCE));
                    }
                    builder.accept(Stream.of(new ObjectNameToken(entry.getKey())));
                    builder.accept(StreamUtil.lazyStream(() -> serializeJsonValue(entry.getValue())));
                }
                builder.accept(Stream.of(EndObjectToken.INSTANCE));
            }
            case JsonArray(var value) -> {
                builder.accept(Stream.of(BeginArrayToken.INSTANCE));
                boolean first = true;
                for (JsonValue<?> item : value) {
                    if (first) {
                        first = false;
                    }
                    else {
                        builder.accept(Stream.of(ValueSeparatorToken.INSTANCE));
                    }
                    builder.accept(StreamUtil.lazyStream(() -> serializeJsonValue(item)));
                }
                builder.accept(Stream.of(EndArrayToken.INSTANCE));
            }
            case NumberToken token -> builder.accept(Stream.of(token));
            case StringToken token -> builder.accept(Stream.of(token));
            case BooleanToken token -> builder.accept(Stream.of(token));
            case NullToken token -> builder.accept(Stream.of(token));
        }
        return builder.build().flatMap(Function.identity());
    }

    private static TypedDeserializer deserializeJsonValue(JsonDeserializer rootDeserializer, TypedDeserializer parentTypedDeserializer)
    {
        return new TypedDeserializer() {
            private Supplier<JsonValue<?>> rootValueSupplier;
            private JsonValue<?> rootValue;

            @Override
            public String toString()
            {
                return "deserializeJsonValue";
            }

            @SuppressWarnings({"unchecked", "rawtypes"})
            @Override
            public TypedDeserializer accept(JsonToken jsonToken)
            {
                TypedDeserializer nextTypedDeserializer = parentTypedDeserializer;
                switch (jsonToken) {
                    case StringToken stringToken -> rootValue = stringToken;
                    case NumberToken numberToken -> rootValue = numberToken;
                    case BooleanToken booleanToken -> rootValue = booleanToken;
                    case NullToken nullToken -> rootValue = nullToken;
                    case BeginArrayToken __ -> {
                        TypedDeserializer collectorTypedDeserializer = JsonDeserializer.collectionTypedDeserializer(rootDeserializer, parentTypedDeserializer, List.class, JsonValue.class);
                        nextTypedDeserializer = collectorTypedDeserializer.accept(jsonToken);
                        rootValueSupplier = () -> new JsonArray((List<? extends JsonValue<?>>) collectorTypedDeserializer.value());
                    }
                    case BeginObjectToken __ -> {
                        Function<Map<String, Object>, Object> builder = map -> new JsonObject((Map)map);
                        TypedDeserializer objectTypedDeserializer = JsonDeserializer.objectTypedDeserializer(rootDeserializer, parentTypedDeserializer, ___ -> JsonValue.class, builder);
                        nextTypedDeserializer = objectTypedDeserializer.accept(jsonToken);
                        rootValueSupplier = () -> (JsonObject) objectTypedDeserializer.value();
                    }
                    default -> throw new RuntimeException();    // TODO
                }
                return nextTypedDeserializer;
            }

            @Override
            public Object value()
            {
                if ((rootValueSupplier == null) && (rootValue == null)) {
                    throw new RuntimeException();   // TODO
                }
                if (rootValue != null) {
                    return rootValue;
                }
                return rootValueSupplier.get();
            }
        };
    }
}
