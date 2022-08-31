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
import io.starburst.json.util.Cache;
import io.starburst.json.util.StreamUtil;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

public interface JsonSerializer
{
    static JsonSerializer instance()
    {
        return builder().addStandard().build();
    }

    Stream<JsonToken> serialize(Object o);

    @FunctionalInterface
    interface PredicateSerializer
    {
        Optional<Stream<JsonToken>> maybeSerialize(JsonSerializer rootSerializer, Object o);
    }

    interface Builder
    {
        Builder add(PredicateSerializer predicateSerializer);

        Builder addStandard();

        Builder withAlternateRecordCache(Cache<Class<?>, RecordComponent[]> recordCache);

        JsonSerializer build();
    }

    static Builder builder()
    {
        return new Builder()
        {
            private final List<PredicateSerializer> predicateSerializers = new ArrayList<>();
            private boolean addStandard = false;
            private Cache<Class<?>, RecordComponent[]> recordCache = Cache.simple();

            @Override
            public Builder add(PredicateSerializer predicateSerializer)
            {
                predicateSerializers.add(predicateSerializer);
                return this;
            }

            @Override
            public Builder addStandard()
            {
                addStandard = true;
                return this;
            }

            @Override
            public Builder withAlternateRecordCache(Cache<Class<?>, RecordComponent[]> recordCache)
            {
                this.recordCache = recordCache;
                return this;
            }

            @Override
            public JsonSerializer build()
            {
                List<PredicateSerializer> work = new ArrayList<>(predicateSerializers);
                if (addStandard) {
                    work.add((rootSerializer, o) -> serializeStandard(rootSerializer, o, recordCache));
                }
                return buildSerializer(Collections.unmodifiableList(work));
            }
        };
    }

    static Optional<Stream<JsonToken>> serializeStandard(JsonSerializer rootSerializer, Object o, Cache<Class<?>, RecordComponent[]> recordCache)
    {
        Stream<JsonToken> tokenStream = switch (o) {
            case null -> Stream.of(new NullToken());
            case String str -> Stream.of(new StringToken(str));
            case Number n -> Stream.of(new NumberToken(n));
            case Boolean b -> Stream.of(new BooleanToken(b));
            case Enum<?> e -> Stream.of(new StringToken(e.name()));
            case UUID uuid -> Stream.of(new StringToken(uuid.toString()));
            case Optional<?> optional -> StreamUtil.lazyStream(() -> rootSerializer.serialize(optional.orElse(null)));
            case OptionalInt optional -> Stream.of(optional.isPresent() ? new NumberToken(optional.getAsInt()) : new NullToken());
            case OptionalLong optional -> Stream.of(optional.isPresent() ? new NumberToken(optional.getAsLong()) : new NullToken());
            case OptionalDouble optional -> Stream.of(optional.isPresent() ? new NumberToken(optional.getAsDouble()) : new NullToken());
            case Collection<?> collection -> serializeCollection(rootSerializer, collection);
            case Object ignore when o.getClass().isArray() -> serializeArray(rootSerializer, o);
            case Object ignore when o.getClass().isRecord() -> serializeRecord(rootSerializer, o, recordCache);
            default -> null;
        };
        return Optional.ofNullable(tokenStream);
    }

    static Stream<JsonToken> serializeArray(JsonSerializer rootSerializer, Object array)
    {
        Stream.Builder<Stream<JsonToken>> builder = Stream.builder();
        builder.accept(Stream.of(BeginArrayToken.INSTANCE));
        boolean first = true;
        int length = Array.getLength(array);
        for (int i = 0; i < length; ++i) {
            if (i > 0) {
                builder.accept(Stream.of(ValueSeparatorToken.INSTANCE));
            }
            int index = i;
            builder.accept(StreamUtil.lazyStream(() -> rootSerializer.serialize(Array.get(array, index))));
        }
        builder.accept(Stream.of(EndArrayToken.INSTANCE));
        return builder.build().flatMap(Function.identity());
    }

    static Stream<JsonToken> serializeCollection(JsonSerializer rootSerializer, Collection<?> collection)
    {
        Stream.Builder<Stream<JsonToken>> builder = Stream.builder();
        builder.accept(Stream.of(new BeginArrayToken())); // we have to wrap the token in a stream as we want a stream of streams
        boolean first = true;
        for (Object value : collection) {
            if (first) {
                first = false;
            }
            else {
                builder.accept(Stream.of(new ValueSeparatorToken()));    // again, wrap the token in a stream
            }
            builder.accept(StreamUtil.lazyStream(() -> rootSerializer.serialize(value)));   // recursively serialize each value
        }
        builder.accept(Stream.of(new EndArrayToken()));   // again, wrap the token in a stream
        return builder.build().flatMap(Function.identity());    // flatten stream of streams into stream of tokens
    }

    record ObjectField(String name, Object value) {}

    static Stream<JsonToken> serializeObject(JsonSerializer rootSerializer, Collection<ObjectField> objectFields)
    {
        Stream.Builder<Stream<JsonToken>> builder = Stream.builder();
        builder.accept(Stream.of(BeginObjectToken.INSTANCE));
        boolean first = true;
        for (ObjectField objectField : objectFields) {
            if (first) {
                first = false;
            }
            else {
                builder.accept(Stream.of(ValueSeparatorToken.INSTANCE));
            }
            builder.accept(Stream.of(new ObjectNameToken(objectField.name())));
            builder.accept(rootSerializer.serialize(objectField.value()));
        }
        builder.accept(Stream.of(EndObjectToken.INSTANCE));
        return builder.build().flatMap(Function.identity());
    }

    static Stream<JsonToken> serializeRecord(JsonSerializer rootSerializer, Object record, Cache<Class<?>, RecordComponent[]> recordCache)
    {
        RecordComponent[] recordComponents = recordCache.computeIfAbsent(record.getClass(), Class::getRecordComponents);   // Java records include a complete specification of the record's components
        List<ObjectField> objectFields = Arrays.stream(recordComponents)
                .map(recordComponent -> {
                    try {
                        Object o = recordComponent.getAccessor().invoke(record);
                        return new ObjectField(recordComponent.getName(), o);
                    }
                    catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);  // TODO
                    }
                })
                .toList();
        return serializeObject(rootSerializer, objectFields);
    }

    private static JsonSerializer buildSerializer(List<PredicateSerializer> predicateSerializers)
    {
        return new JsonSerializer()
        {
            @Override
            public Stream<JsonToken> serialize(Object o)
            {
                return predicateSerializers.stream()
                        .flatMap(predicateSerializer -> predicateSerializer.maybeSerialize(this, o).stream())
                        .findFirst()
                        .orElseThrow(RuntimeException::new); // TODO
            }
        };
    }
}
