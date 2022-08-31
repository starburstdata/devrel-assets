package io.starburst.json;

import io.starburst.json.util.Cache;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface JsonDeserializer
{
    static JsonDeserializer instance()
    {
        return builder().addStandard().build();
    }

    TypedDeserializer deserializerFor(TypedDeserializer parentTypedDeserializer, Type type);

    interface TypedDeserializer
    {
        TypedDeserializer accept(JsonToken jsonToken);

        Object value();
    }

    interface PredicateDeserializer
    {
        Optional<TypedDeserializer> maybeDeserialize(JsonDeserializer rootDeserializer, TypedDeserializer parentTypedDeserializer, Type type);
    }

    interface Builder
    {
        Builder add(PredicateDeserializer predicateSerializer);

        Builder addStandard();

        Builder withAlternateRecordCache(Cache<Class<?>, RecordComponent[]> recordCache);

        JsonDeserializer build();
    }

    static Builder builder()
    {
        return new Builder() {
            private final List<PredicateDeserializer> predicateDeserializers = new ArrayList<>();
            private boolean addStandard;
            private Cache<Class<?>, RecordComponent[]> recordCache = Cache.simple();

            @Override
            public Builder add(PredicateDeserializer predicateSerializer)
            {
                predicateDeserializers.add(predicateSerializer);
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
            public JsonDeserializer build()
            {
                List<PredicateDeserializer> work = new ArrayList<>(predicateDeserializers);
                if (addStandard) {
                    work.add(standardTypedDeserializer(recordCache));
                }
                return buildDeserializer(Collections.unmodifiableList(work));
            }
        };
    }

    static <T extends JsonToken> TypedDeserializer simpleTypedDeserializer(TypedDeserializer parentTypedDeserializer, Class<T> tokenClass, Function<T, Object> valueProvider)
    {
        return new TypedDeserializer()
        {
            private Object value;
            private boolean valueIsSet;

            @Override
            public String toString()
            {
                return "simpleTypedDeserializer";
            }

            @Override
            public TypedDeserializer accept(JsonToken jsonToken)
            {
                if (valueIsSet) {
                    throw new RuntimeException();
                }
                switch (jsonToken) {
                    case JsonToken.NullToken ignore -> {
                        value = null;
                        valueIsSet = true;
                    }
                    case JsonToken ignore when tokenClass.isAssignableFrom(jsonToken.getClass()) -> {
                        value = valueProvider.apply(tokenClass.cast(jsonToken));
                        valueIsSet = true;
                    }
                    default -> throw new RuntimeException();
                }
                return parentTypedDeserializer;
            }

            @Override
            public Object value()
            {
                if (!valueIsSet) {
                    throw new RuntimeException();
                }
                return value;
            }
        };
    }

    static TypedDeserializer optionalTypedDeserializer(JsonDeserializer rootDeserializer, TypedDeserializer parentTypedDeserializer, Type componentType)
    {
        return new TypedDeserializer()
        {
            private final TypedDeserializer valueDeserializer = rootDeserializer.deserializerFor(parentTypedDeserializer, componentType);

            @Override
            public String toString()
            {
                return "optionalTypedDeserializer";
            }

            @Override
            public TypedDeserializer accept(JsonToken jsonToken)
            {
                return valueDeserializer.accept(jsonToken);
            }

            @Override
            public Object value()
            {
                return Optional.ofNullable(valueDeserializer.value());
            }
        };
    }

    static TypedDeserializer enumTypedDeserializer(TypedDeserializer parentTypedDeserializer, Class<?> enumClass)
    {
        return simpleTypedDeserializer(parentTypedDeserializer, JsonToken.StringToken.class, stringToken ->
                Stream.of(enumClass.getEnumConstants()).filter(c -> c.toString().equals(stringToken.value())).findFirst().orElseThrow(RuntimeException::new));
    }

    static TypedDeserializer collectionTypedDeserializer(JsonDeserializer rootDeserializer, TypedDeserializer parentTypedDeserializer, Class<?> collectionClass, Type componentType)
    {
        return new TypedDeserializer()
        {
            private final List<TypedDeserializer> values = new ArrayList<>();
            private boolean started;
            private boolean isDone;
            private boolean expectingValue;

            @Override
            public String toString()
            {
                return "collectionTypedDeserializer";
            }

            @Override
            public TypedDeserializer accept(JsonToken jsonToken)
            {
                TypedDeserializer nextTypedDeserializer = this;
                if (expectingValue) {
                    expectingValue = false;
                    switch (jsonToken) {
                        case JsonToken.EndArrayToken ignore -> {
                            nextTypedDeserializer = accept(jsonToken);    // it's an empty array
                        }
                        default -> {
                            TypedDeserializer valueTypedDeserializer = rootDeserializer.deserializerFor(this, componentType);
                            values.add(valueTypedDeserializer);
                            nextTypedDeserializer = valueTypedDeserializer.accept(jsonToken);
                        }
                    }
                }
                else {
                    switch (jsonToken) {
                        case JsonToken.BeginArrayToken ignore -> {
                            if (started) {
                                throw new RuntimeException();
                            }
                            started = true;
                            expectingValue = true;
                        }
                        case JsonToken.EndArrayToken ignore -> {
                            if (!started || isDone) {
                                throw new RuntimeException();
                            }
                            nextTypedDeserializer = parentTypedDeserializer;
                            isDone = true;
                        }
                        case JsonToken.ValueSeparatorToken ignore -> {
                            if (!started) {
                                throw new RuntimeException();
                            }
                            expectingValue = true;
                        }
                        default -> throw new RuntimeException();
                    }
                }
                return nextTypedDeserializer;
            }

            @Override
            public Object value()
            {
                if (!isDone) {
                    throw new RuntimeException();
                }
/*  javac crashes on this code
                if (collectionClass.isArray()) {
                    Class<?> componentClass = TypeToken.getRawType(componentType);
                    Object tab = Array.newInstance(componentClass, values.size());
                    for (int i = 0; i < values.size(); ++i) {
                        Object value = values.get(i).value();
                        switch (value) {
                            case null -> Array.set(tab, i, value);
                            case Byte b when componentClass.equals(byte.class) -> Array.setByte(tab, i, b);
                            case Short s when componentClass.equals(short.class) -> Array.setShort(tab, i, s);
                            case Character c when componentClass.equals(char.class) -> Array.setChar(tab, i, c);
                            case Integer v when componentClass.equals(int.class) -> Array.setInt(tab, i, v);
                            case Long l when componentClass.equals(long.class) -> Array.setLong(tab, i, l);
                            case Float f when componentClass.equals(float.class) -> Array.setFloat(tab, i, f);
                            case Double d when componentClass.equals(double.class) -> Array.setDouble(tab, i, d);
                            default -> Array.set(tab, i, value);
                        }
                    }
                    return tab;
                }
*/
                // get the final value from each of the stored deserializers
                Stream<Object> valueStream = values.stream().map(TypedDeserializer::value);
                // create either a set or a list depending on the collection class
                return Set.class.isAssignableFrom(collectionClass) ? valueStream.collect(Collectors.toSet()) : valueStream.toList();
            }
        };
    }


    static TypedDeserializer objectTypedDeserializer(JsonDeserializer rootDeserializer, TypedDeserializer parentTypedDeserializer, Function<String, Type> nameToType, Function<Map<String, Object>, Object> builder)
    {
        record NameAndValue(String name, TypedDeserializer value) {}
        return new TypedDeserializer() {
            private final List<NameAndValue> values = new ArrayList<>();
            private String currentName;
            private boolean started;
            private boolean isDone;

            @Override
            public String toString()
            {
                return "objectTypedDeserializer";
            }

            @Override
            public TypedDeserializer accept(JsonToken jsonToken)
            {
                TypedDeserializer nextTypedDeserializer = this;
                switch (jsonToken) {
                    case JsonToken.BeginObjectToken __ -> {
                        if (started) {
                            throw new RuntimeException();    // TODO
                        }
                        else {
                            started = true;
                        }
                    }
                    case JsonToken.ObjectNameToken objectNameToken -> {
                        if (!started || (currentName != null)) {
                            throw new RuntimeException();    // TODO
                        }
                        currentName = objectNameToken.name();
                        Type type = nameToType.apply(currentName);
                        TypedDeserializer typedDeserializer = rootDeserializer.deserializerFor(this, type);
                        values.add(new NameAndValue(currentName, typedDeserializer));
                        nextTypedDeserializer = typedDeserializer;
                    }
                    case JsonToken.EndObjectToken __ -> {
                        if (!started || isDone) {
                            throw new RuntimeException();   // TODO
                        }
                        nextTypedDeserializer = parentTypedDeserializer;
                        isDone = true;
                    }
                    case JsonToken.ValueSeparatorToken __ -> {
                        if (!started || (currentName == null)) {
                            throw new RuntimeException();   // TODO
                        }
                        currentName = null;
                    }
                    default -> throw new RuntimeException();    // TODO
                }
                return nextTypedDeserializer;
            }

            @Override
            public Object value()
            {
                if (!isDone) {
                    throw new RuntimeException();   // TODO
                }
                Map<String, Object> mappedValues = new HashMap<>(values.size());
                values.forEach(nameAndValue -> mappedValues.put(nameAndValue.name, (nameAndValue.value != null) ? nameAndValue.value.value() : null));
                return builder.apply(mappedValues);
            }
        };
    }

    static TypedDeserializer recordTypedDeserializer(JsonDeserializer rootDeserializer, TypedDeserializer parentTypedDeserializer, Class<?> recordClass, Cache<Class<?>, RecordComponent[]> recordCache)
    {
        RecordComponent[] recordComponents = recordCache.computeIfAbsent(recordClass, __ -> recordClass.getRecordComponents());
        Map<String, RecordComponent> recordComponentMap = Stream.of(recordComponents).collect(Collectors.toMap(RecordComponent::getName, Function.identity()));
        Function<String, Type> nameToType = name -> {
            RecordComponent recordComponent = recordComponentMap.get(name);
            if (recordComponent == null) {
                throw new RuntimeException();   // TODO
            }
            return recordComponent.getGenericType();
        };
        Function<Map<String, Object>, Object> builder = valuesMap -> {
            Class<?>[] argumentTypes = new Class[recordComponents.length];
            Object[] arguments = new Object[recordComponents.length];
            for (int i = 0; i < recordComponents.length; ++i) {
                RecordComponent recordComponent = recordComponents[i];
                argumentTypes[i] = recordComponent.getType();
                arguments[i] = valuesMap.get(recordComponent.getName());
            }
            try {
                return recordClass.getConstructor(argumentTypes).newInstance(arguments);
            }
            catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException | IllegalArgumentException e) {
                throw new RuntimeException(e);  // TODO
            }
        };
        return objectTypedDeserializer(rootDeserializer, parentTypedDeserializer, nameToType, builder);
    }

    private static PredicateDeserializer standardTypedDeserializer(Cache<Class<?>, RecordComponent[]> recordCache)
    {
        return (rootDeserializer, parentTypedDeserializer, type) -> {
            TypedDeserializer typedDeserializer = switch (type) {
                case Class<?> clazz when clazz.equals(byte.class) || clazz.equals(Byte.class) -> simpleTypedDeserializer(parentTypedDeserializer, JsonToken.NumberToken.class, numberToken -> numberToken.value().byteValue());
                case Class<?> clazz when clazz.equals(short.class) || clazz.equals(Short.class) -> simpleTypedDeserializer(parentTypedDeserializer, JsonToken.NumberToken.class, numberToken -> numberToken.value().shortValue());
                case Class<?> clazz when clazz.equals(int.class) || clazz.equals(Integer.class) -> simpleTypedDeserializer(parentTypedDeserializer, JsonToken.NumberToken.class, numberToken -> numberToken.value().intValue());
                case Class<?> clazz when clazz.equals(long.class) || clazz.equals(Long.class) -> simpleTypedDeserializer(parentTypedDeserializer, JsonToken.NumberToken.class, numberToken -> numberToken.value().longValue());
                case Class<?> clazz when clazz.equals(float.class) || clazz.equals(Float.class) -> simpleTypedDeserializer(parentTypedDeserializer, JsonToken.NumberToken.class, numberToken -> numberToken.value().floatValue());
                case Class<?> clazz when clazz.equals(double.class) || clazz.equals(Double.class) -> simpleTypedDeserializer(parentTypedDeserializer, JsonToken.NumberToken.class, numberToken -> numberToken.value().doubleValue());
                case Class<?> clazz when clazz.equals(boolean.class) || clazz.equals(Boolean.class) -> simpleTypedDeserializer(parentTypedDeserializer, JsonToken.BooleanToken.class, JsonToken.BooleanToken::value);
                case Class<?> clazz when Number.class.isAssignableFrom(clazz) -> simpleTypedDeserializer(parentTypedDeserializer, JsonToken.NumberToken.class, JsonToken.NumberToken::value);
                case Class<?> clazz when clazz.equals(String.class) -> simpleTypedDeserializer(parentTypedDeserializer, JsonToken.StringToken.class, JsonToken.StringToken::value);
                case ParameterizedType parameterizedType when(parameterizedType.getRawType() instanceof Class<?> clazz) && Collection.class.isAssignableFrom(clazz) -> collectionTypedDeserializer(rootDeserializer, parentTypedDeserializer, clazz, parameterizedType.getActualTypeArguments()[0]);
                case ParameterizedType parameterizedType when(parameterizedType.getRawType() instanceof Class<?> clazz) && Optional.class.isAssignableFrom(clazz) -> optionalTypedDeserializer(rootDeserializer, parentTypedDeserializer, parameterizedType.getActualTypeArguments()[0]);
                case Class<?> clazz when clazz.isRecord() -> recordTypedDeserializer(rootDeserializer, parentTypedDeserializer, clazz, recordCache);
                case Class<?> clazz when clazz.isEnum() -> enumTypedDeserializer(parentTypedDeserializer, clazz);
                case GenericArrayType genericArrayType -> collectionTypedDeserializer(rootDeserializer, parentTypedDeserializer, Object[].class, genericArrayType.getGenericComponentType());
                case Class<?> clazz when clazz.isArray() -> collectionTypedDeserializer(rootDeserializer, parentTypedDeserializer, clazz, clazz.getComponentType());
                default -> null;
            };
            return Optional.of(typedDeserializer);
        };
    }

    private static JsonDeserializer buildDeserializer(List<PredicateDeserializer> predicateDeserializers)
    {
        return new JsonDeserializer()
        {
            @Override
            public TypedDeserializer deserializerFor(TypedDeserializer parentTypedDeserializer, Type type)
            {
                return predicateDeserializers.stream()
                        .flatMap(predicateDeserializer -> predicateDeserializer.maybeDeserialize(this, parentTypedDeserializer, type).stream())
                        .findFirst()
                        .orElseThrow(RuntimeException::new); // TODO
            }
        };
    }
}
