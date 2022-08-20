[⬆️ Top](00-introduction.md) • [⬅️ Previous](04-parsing.md) • [Next ➡️](05-deserialization-02.md)

# Bleeding Edge Java

_Note: this is Part 5 of this series. Please [start at the introduction](00-introduction.md) if you haven't already_.

## Part 5-A - Deserialization - Introduction

Deserialization is the process of taking a JSON token stream and coalescing it into a Java object. Deserialization is the hardest part
of this process. Serialization is straightforward because the JSON spec is very small and simple. Conversely, Java objects are complex
and mapping from simple JSON objects into Java objects will require much more thought. Consequently, the deserialization portion
of this series is broken into two parts: this introduction that will introduce the design and a follow-up that shows the 
implementations for collection types and object types.

## The Design

For our library we will implement something that loosely resembles a recursive descent parser.
We define a deserializer for all Java types that we will support. Each of these deserializers is responsible
for accepting and coalescing each of the fields that it needs. When a deserializer encounters a type it doesn't
handle, it obtains a new deserializer for that type and calls this new deserializer passing in itself as the parent. When a deserializer
is complete, it prepares its value and returns to the parent to continue processing until all tokens have been processed. 
This process is broken into two methods:

```java
public interface JsonDeserializer
{
    interface TypedDeserializer
    {
        TypedDeserializer accept(JsonToken t);  // accept token and return next deserializer to use

        Object value();
    }

    TypedDeserializer deserializerFor(TypedDeserializer parentTypedDeserializer, Type type);
}
```

`deserializerFor()` is called to get a `TypedDeserializer` for a given type. JSON tokens from the 
stream are passed to that `TypedDeserializer`. Each time a token is passed to `accept()` it returns the next 
deserializer to call when a new token is received. When all the tokens have
been processed the final value can be received from the first deserializer created. 
Here is pseudocode for this:

```java
TypedDeserializer first;
TypedDeserializer current;
for-each-token -> {
    if (first == null) {
        current = first = deserializer.deserializerFor(rootTypedDeserializer, type);
    }
    current = current.accept(token);
}
Object value = first.value();
```

## Details

Simple types like numbers, strings, etc. are straightforward to process. Collections and records are
more complicated and will be the subject of the follow-up deserialization article. We can create a general-purpose
deserializer for these simple types. It can be used to map number tokens, string tokens, boolean tokens
and null tokens to Java primitives and Strings. Let's define a method that returns this deserializer:

### simpleTypedDeserializer

```java
<T extends JsonToken> TypedDeserializer simpleTypedDeserializer(TypedDeserializer parentTypedDeserializer, Class<T> tokenClass, Function<T, Object> valueProvider)
{
    return new TypedDeserializer()
    {
        ...
    };
}
```

We declare a generic method with a generic parameter that must extend `JsonToken`. The method receives the
parent deserializer, the type of token to expect, and a mapper method that creates a Java object from the
token.

Here's the complete definition of the deserializer:

```java
new TypedDeserializer()
{
    private Object value;
    private boolean valueIsSet;

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

```

All the work is in the `accept()` method. This deserializer handles nulls in addition to values. Only
one token is expected by this deserializer as simple values can be specified by one JSON token. It checks
if the token received is the correct type and, if so, extracts the value and saves it. Otherwise, it is
an error.

### Simple type mapping

We must also implement the `deserializerFor()` method that maps a Java type to a deserializer. As usual,
we will use enhanced switch and pattern matching:

```java
TypedDeserializer deserializerFor(TypedDeserializer parentTypedDeserializer, Type type)
{
    return switch (type) {
        case Class<?> clazz when clazz.equals(byte.class) || clazz.equals(Byte.class) -> simpleTypedDeserializer(parentTypedDeserializer, JsonToken.NumberToken.class, numberToken -> numberToken.value().byteValue());
        case Class<?> clazz when clazz.equals(short.class) || clazz.equals(Short.class) -> simpleTypedDeserializer(parentTypedDeserializer, JsonToken.NumberToken.class, numberToken -> numberToken.value().shortValue());
        case Class<?> clazz when clazz.equals(int.class) || clazz.equals(Integer.class) -> simpleTypedDeserializer(parentTypedDeserializer, JsonToken.NumberToken.class, numberToken -> numberToken.value().intValue());
        case Class<?> clazz when clazz.equals(long.class) || clazz.equals(Long.class) -> simpleTypedDeserializer(parentTypedDeserializer, JsonToken.NumberToken.class, numberToken -> numberToken.value().longValue());
        case Class<?> clazz when clazz.equals(float.class) || clazz.equals(Float.class) -> simpleTypedDeserializer(parentTypedDeserializer, JsonToken.NumberToken.class, numberToken -> numberToken.value().floatValue());
        case Class<?> clazz when clazz.equals(double.class) || clazz.equals(Double.class) -> simpleTypedDeserializer(parentTypedDeserializer, JsonToken.NumberToken.class, numberToken -> numberToken.value().doubleValue());
        case Class<?> clazz when clazz.equals(boolean.class) || clazz.equals(Boolean.class) -> simpleTypedDeserializer(parentTypedDeserializer, JsonToken.BooleanToken.class, JsonToken.BooleanToken::value);
        case Class<?> clazz when Number.class.isAssignableFrom(clazz) -> simpleTypedDeserializer(parentTypedDeserializer, JsonToken.NumberToken.class, JsonToken.NumberToken::value);
        case Class<?> clazz when clazz.equals(String.class) -> simpleTypedDeserializer(parentTypedDeserializer, JsonToken.StringToken.class, JsonToken.StringToken::value);
        default -> throw new RuntimeException();
    };
}
```

It's an eye-full but, hopefully, by now it's easy to read. For example, the first case statement equates to
this pseudocode:

```java
if (type instanceof Class<?>) {
    Class<?> clazz = (Class<?>) type;
    if (clazz.equals(byte.class) || clazz.equals(Byte.class)) {
        // the mapper calls byteValue() on the Number token
        return simpleTypedDeserializer(parentTypedDeserializer, JsonToken.NumberToken.class, numberToken -> numberToken.value().byteValue());
    }
}
```

## Collector

Java streams use the Collector mechanism to reduce stream elements into a single object. 
[JsonDeserializerCollector.java](https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/JsonDeserializerCollector.java) is an implementation of a stream Collector.

It defines an Accumulator class to manage the first and current serializers:

```java
class Accumulator
{
    JsonDeserializer.TypedDeserializer first;
    JsonDeserializer.TypedDeserializer current;
}

```

The `accumulator()` method of the Collector then applies the token and manages the first and current deserializer:

```java
public BiConsumer<Accumulator, JsonToken> accumulator()
{
    return (accumulator, jsonToken) -> {
        if (accumulator.first == null) {
            accumulator.current = accumulator.first = deserializer.deserializerFor(rootTypedDeserializer, type);
        }
        accumulator.current = accumulator.current.accept(jsonToken);
    };
}
```

Finally, the `finisher()` returns the value from the first deserializer and casts it to the desired type:

```java
public Function<Accumulator, T> finisher()
{
    return accumulator -> {
        if (accumulator.first == null) {
            throw new RuntimeException();
        }
        return (T) accumulator.first.value();
    };
}
```

## Next steps

In the [follow-up article](05-deserialization-02.md) we will continue developing deserializers for more complex
types. 

# We're hiring

Want to be able to use the latest features of Java? [We're hiring!](https://www.starburst.io/careers/)

------------

_About the author:_

_Jordan Zimmerman is a Software Engineer working on [Starburst Galaxy](https://www.starburst.io/platform/starburst-galaxy/)_ 
