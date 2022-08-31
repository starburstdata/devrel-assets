package io.starburst.json;

import io.starburst.json.util.TypeToken;
import io.starburst.json.JsonSerializer.ObjectField;
import io.starburst.json.JsonSerializer.PredicateSerializer;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.starburst.json.JsonSerializer.serializeObject;

public interface JsonClass
        extends PredicateSerializer, JsonDeserializer.PredicateDeserializer
{
    interface Builder<T>
    {
        <U> Builder<T> addField(String name, Function<T, U> accessor, TypeToken<U> type);

        <U> Builder<T> addField(String name, Function<T, U> accessor, Class<U> type);

        <U> Builder<T> addTypeVariableField(String name, Function<T, U> accessor, TypeToken<U> instanceType);

        <U> Builder<T> addTypeVariableField(String name, Function<T, U> accessor, Class<U> instanceType);

        Builder<T> withAlternateBuilder(Function<Map<String, Object>, Object> builder);

        JsonClass build();
    }

    static <T extends JsonToken, U> JsonClass forSimple(Type type, Class<T> tokenClass, Function<T, U> deserializer, BiFunction<JsonSerializer, U, T> serializer)
    {
        Class<?> rawType = TypeToken.getRawType(type);
        return new JsonClass() {
            @Override
            public Optional<JsonDeserializer.TypedDeserializer> maybeDeserialize(JsonDeserializer rootDeserializer, JsonDeserializer.TypedDeserializer parentTypedDeserializer, Type maybeType)
            {
                if (type.equals(maybeType)) {
                    return Optional.of(JsonDeserializer.simpleTypedDeserializer(parentTypedDeserializer, tokenClass, deserializer::apply));
                }
                return Optional.empty();
            }

            @SuppressWarnings("unchecked")
            @Override
            public Optional<Stream<JsonToken>> maybeSerialize(JsonSerializer rootSerializer, Object o)
            {
                if ((o != null) && rawType.equals(TypeToken.getRawType(o.getClass()))) {
                    return Optional.of(Stream.of(serializer.apply(rootSerializer, (U)o)));
                }
                return Optional.empty();
            }
        };
    }

    static <T> Builder<T> builder(TypeToken<T> type)
    {
        return internalBuilder(type.type());
    }

    static <T> Builder<T> builder(Class<T> type)
    {
        return internalBuilder(type);
    }

    static <T> Builder<T> internalBuilder(Type type)
    {
        record Field<T>(Function<T, Object> accessor, Type type, Class<?> rawType, int ordinal) {}

        Class<?> rawType = TypeToken.getRawType(type);
        return new Builder<>() {
            private final Map<String, Field<T>> fields = new HashMap<>();
            private Function<Map<String, Object>, Object> builder;

            @Override
            public <U> Builder<T> addField(String name, Function<T, U> accessor, TypeToken<U> type)
            {
                fields.put(name, new Field<>(accessor.andThen(o -> o), type.type(), TypeToken.getRawType(type.type()), fields.size()));
                return this;
            }

            @Override
            public <U> Builder<T> addField(String name, Function<T, U> accessor, Class<U> type)
            {
                fields.put(name, new Field<>(accessor.andThen(o -> o), type, TypeToken.getRawType(type), fields.size()));
                return this;
            }

            @Override
            public <U> Builder<T> addTypeVariableField(String name, Function<T, U> accessor, TypeToken<U> instanceType)
            {
                fields.put(name, new Field<>(accessor.andThen(o -> o), instanceType.type(), Object.class, fields.size()));
                return this;
            }

            @Override
            public <U> Builder<T> addTypeVariableField(String name, Function<T, U> accessor, Class<U> instanceType)
            {
                fields.put(name, new Field<>(accessor.andThen(o -> o), instanceType, Object.class, fields.size()));
                return this;
            }

            @Override
            public Builder<T> withAlternateBuilder(Function<Map<String, Object>, Object> builder)
            {
                this.builder = builder;
                return this;
            }

            @Override
            public JsonClass build()
            {
                Constructor<?> constructor = findConstructor();
                return new JsonClass() {
                    @Override
                    public String toString()
                    {
                        return "JsonClass-builder";
                    }

                    @Override
                    public Optional<Stream<JsonToken>> maybeSerialize(JsonSerializer rootSerializer, Object o)
                    {
                        if ((o != null) && rawType.equals(TypeToken.getRawType(o.getClass()))) {
                            return Optional.of(serializeObject(rootSerializer, buildObjectFields(o)));
                        }
                        return Optional.empty();
                    }

                    @Override
                    public Optional<JsonDeserializer.TypedDeserializer> maybeDeserialize(JsonDeserializer rootDeserializer, JsonDeserializer.TypedDeserializer parentTypedDeserializer, Type maybeType)
                    {
                        if (type.equals(maybeType)) {
                            Function<String, Type> nameToType = name -> {
                                Field<T> field = fields.get(name);
                                if (field == null) {
                                    throw new RuntimeException();   // TODO
                                }
                                return field.type;
                            };
                            Function<Map<String, Object>, Object> useBuilder = (builder != null) ? builder : makeBuilder();
                            return Optional.of(JsonDeserializer.objectTypedDeserializer(rootDeserializer, parentTypedDeserializer, nameToType, useBuilder));
                        }
                        return Optional.empty();
                    }
                };
            }

            private Collection<ObjectField> buildObjectFields(Object o)
            {
                //noinspection unchecked
                return fields.entrySet().stream()
                        .sorted(Comparator.comparingInt(entry -> entry.getValue().ordinal))
                        .map(entry -> new ObjectField(entry.getKey(), entry.getValue().accessor.apply((T)o)))
                        .toList();
            }

            private Function<Map<String, Object>, Object> makeBuilder()
            {
                Constructor<?> constructor = findConstructor();
                return valuesMap -> {
                    Object[] arguments = new Object[fields.size()];
                    valuesMap.forEach((name, value) -> {
                        Field<T> field = fields.get(name);
                        if (field == null) {
                            throw new RuntimeException();   // TODO
                        }
                        arguments[field.ordinal] = value;
                    });
                    try {
                        return constructor.newInstance(arguments);
                    }
                    catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException();   // TODO
                    }
                };
            }

            private Constructor<?> findConstructor()
            {
                Class<?>[] argumentTypes = new Class[fields.size()];
                fields.forEach((__, field) -> argumentTypes[field.ordinal] = field.rawType);
                try {
                    return rawType.getConstructor(argumentTypes);
                }
                catch (NoSuchMethodException e) {
                    throw new RuntimeException();   // TODO
                }
            }
        };
    }
}