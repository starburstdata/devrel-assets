[⬆️ Top](00-introduction.md) • [⬅️ Previous](05-deserialization-01.md) • [Next ➡️](06-conclusion.md)

# Bleeding Edge Java

_Note: this is Part 5 of this series. Please [start at the introduction](00-introduction.md) if you haven't already_.

## Part 5-B - Deserialization - Complex types

In the [first part](05-deserialization-01.md) of the deserialization article we defined a framework for
deserializing simple Java types. Now we will add support for complex types.

First we update the implementation of `deserializerFor` to include the complex types we will handle:

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
        case ParameterizedType parameterizedType when (parameterizedType.getRawType() instanceof Class<?> clazz) && Collection.class.isAssignableFrom(clazz) -> collectionTypedDeserializer(parentTypedDeserializer, clazz, parameterizedType.getActualTypeArguments()[0]);
        case ParameterizedType parameterizedType when (parameterizedType.getRawType() instanceof Class<?> clazz) && Optional.class.isAssignableFrom(clazz) -> optionalTypedDeserializer(parentTypedDeserializer, parameterizedType.getActualTypeArguments()[0]);
        case Class<?> clazz when clazz.isRecord() -> recordTypedDeserializer(parentTypedDeserializer, clazz);
        case Class<?> clazz when clazz.isEnum() -> enumTypedDeserializer(parentTypedDeserializer, clazz);
        default -> throw new RuntimeException();
    };
}
```

### Enumerations

Use [simpleTypedDeserializer](05-deserialization-01.md#simpletypeddeserializer) to accept a string and use the string 
to build the enum.

```java
TypedDeserializer enumTypedDeserializer(TypedDeserializer parentTypedDeserializer, Class<?> enumClass)
{
    return simpleTypedDeserializer(parentTypedDeserializer, JsonToken.StringToken.class, stringToken ->
            Stream.of(enumClass.getEnumConstants()).filter(c -> c.toString().equals(stringToken.value())).findFirst().orElseThrow(RuntimeException::new));
}
```

### Optional

The pattern matching in the `deserializerFor()` switch will have extracted the Optional's component type.
We get the deserializer for that component type, get its value and then pass that 
value through `Optional.ofNullable()`.

```java
TypedDeserializer optionalTypedDeserializer(TypedDeserializer parentTypedDeserializer, Type componentType)
{
    return new TypedDeserializer()
    {
        private final TypedDeserializer valueDeserializer = deserializerFor(parentTypedDeserializer, componentType);

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
```

### Collections

For collections we will keep a list of a deserializers for each value in the collection. When the parent
deserializer asks for the collection value, we can then process each deserializer to get its final value.
We must perform this delayed value resolution because all the tokens that apply to the collection must
be processed before the value can be determined.

The deserializer does the following:

- Wait for the BeginArrayToken to mark that the collection has started
- Once the collection has started set a flag to indicate that a value is expected
- If the value-expected flag is set, the next token is checked to see if it's a EndArrayToken
  - if so the collection is complete
  - if not, get a new deserializer for the list's component type and add it to values and set it as the next deserializer
- If a ValueSeparatorToken is accepted, reset for a new value
- Otherwise wait for EndArrayToken

```java
TypedDeserializer collectionTypedDeserializer(TypedDeserializer parentTypedDeserializer, Class<?> collectionClass, Type componentType)
{
    return new TypedDeserializer()
    {
        private final List<TypedDeserializer> values = new ArrayList<>();
        private boolean started;
        private boolean isDone;
        private boolean expectingValue;

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
                        TypedDeserializer valueTypedDeserializer = deserializerFor(this, componentType);
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
            // get the final value from each of the stored deserializers
            Stream<Object> valueStream = values.stream().map(TypedDeserializer::value);
            // create either a set or a list depending on the collection class
            return Set.class.isAssignableFrom(collectionClass) ? valueStream.collect(Collectors.toSet()) : valueStream.toList();
        }
    };
}
``` 

### Records

For records we will use the record's array of RecordComponents to know the names and types of the fields to
expect in the object. Similar to the collection deserializer we'll keep a map whose key is the name of the
field and the value is the deserializer for the component's type. Just like for collections, when the parent
deserializer asks for the record value, we can then process each deserializer to get its final value.
We must perform this delayed value resolution because all the tokens that apply to the record must
be processed before the value can be determined.

The deserializer does the following:

- Waits for BeginObjectToken and marks that the object has started
- Until the object ends the next token must be an ObjectNameToken
  - when accepted get a new deserializer for the record component's type and add it to values map
  - set it as the next deserializer
- If a ValueSeparatorToken is accepted reset for another BeginObjectToken
- Otherwise wait for EndObjectToken
- To build the final value use the RecordComponents to find the canonical Constructor, create an array of values and create the record via reflection

```java
TypedDeserializer recordTypedDeserializer(TypedDeserializer parentTypedDeserializer, Class<?> recordClass)
{
    RecordComponent[] recordComponents = recordClass.getRecordComponents();
    Map<String, RecordComponent> recordComponentMap = Stream.of(recordComponents).collect(Collectors.toMap(RecordComponent::getName, Function.identity()));

    return new TypedDeserializer() {
        private final Map<String, TypedDeserializer> valuesMap = new HashMap<>();
        private String currentName;
        private boolean started;
        private boolean isDone;

        @Override
        public TypedDeserializer accept(JsonToken jsonToken)
        {
            TypedDeserializer nextTypedDeserializer = this;
            switch (jsonToken) {
                case JsonToken.BeginObjectToken ignore -> {
                    if (started) {
                        throw new RuntimeException();
                    }
                    else {
                        started = true;
                    }
                }
                case JsonToken.ObjectNameToken objectNameToken -> {
                    if (!started || (currentName != null)) {
                        throw new RuntimeException();
                    }
                    currentName = objectNameToken.name();
                    RecordComponent recordComponent = recordComponentMap.get(currentName);
                    if (recordComponent == null) {
                        throw new RuntimeException();
                    }
                    TypedDeserializer typedDeserializer = deserializerFor(this, recordComponent.getGenericType());
                    valuesMap.put(currentName, typedDeserializer);
                    nextTypedDeserializer = typedDeserializer;
                }
                case JsonToken.EndObjectToken ignore -> {
                    if (!started || isDone) {
                        throw new RuntimeException();
                    }
                    nextTypedDeserializer = parentTypedDeserializer;
                    isDone = true;
                }
                case JsonToken.ValueSeparatorToken ignore -> {
                    if (!started || (currentName == null)) {
                        throw new RuntimeException();
                    }
                    currentName = null;
                }
                default -> throw new RuntimeException();
            }
            return nextTypedDeserializer;
        }

        @Override
        public Object value()
        {
            if (!isDone) {
                throw new RuntimeException();
            }
            Class<?>[] argumentTypes = new Class[recordComponents.length];
            Object[] arguments = new Object[recordComponents.length];
            for (int i = 0; i < recordComponents.length; ++i) {
                RecordComponent recordComponent = recordComponents[i];
                argumentTypes[i] = recordComponent.getType();
                TypedDeserializer valueTypedDeserializer = valuesMap.get(recordComponent.getName());
                if (valueTypedDeserializer == null) {
                    if (Optional.class.isAssignableFrom(recordComponent.getType())) {
                        arguments[i] = Optional.empty();
                    }
                    // otherwise leave it null
                }
                else {
                    arguments[i] = valueTypedDeserializer.value();
                }
            }
            try {
                return recordClass.getConstructor(argumentTypes).newInstance(arguments);
            }
            catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException | IllegalArgumentException e) {
                throw new RuntimeException(e);
            }
        }
    };
}
```

## Generics

Because of [erasure](https://docs.oracle.com/javase/tutorial/java/generics/erasure.html) there is no way in Java
to specify many object types that we might want to deserialize such as generic lists (e.g. `List<String>`). 
However generic type information is not completely lost at compile-time in Java.
The Java runtime retains a surprising amount of generic type information. In fact, we can create a real generic
type for any generic definition we need by using a very simple utility called a "type token". Sadly, the JDK
does not include a utility to create these type tokens. Fortunately, the code to create them is just a few Java
lines. The included [TypeToken.java](https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/TypeToken.java) utility provides this. To create a type token for a list of strings
write:

```java
// create a type token that represents a list of string
TypeToken<List<String>> typeToken = new TypeToken<>(){};
```

We can use this type token to deserialize a list of records, strings, optionals, etc. E.g.

```java
var parser = JsonParser.instance();
var deserializer = JsonDeserializer.instance();

String jsonText = "[1, 2, 3]";
TypeToken<List<Integer>> typeToken = new TypeToken<>() {};
List<Integer> ints = parser.parse(jsonText.chars()).collect(JsonDeserializerCollector.deserializing(deserializer, typeToken));
```

# Test it out for yourself!

In the previous articles we developed a serializer, a printer and a parser. Now we can do a complete round
trip from a Java object, to JSON tokens, to JSON text, back to JSON tokens and back to the original Java object.

Let's put this together in jshell. This example will use these files:

- [TypeToken.java](https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/TypeToken.java)
- [JsonToken.java](https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/JsonToken.java)
- [JsonSerializer.java](https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/JsonSerializer.java)
- [StringUtils.java](https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/StringUtils.java)
- [JsonPrinter.java](https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/JsonPrinter.java)
- [JsonParser.java](https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/JsonParser.java)
- [JsonDeserializer.java](https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/JsonDeserializer.java)
- [JsonDeserializerCollector.java](https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/JsonDeserializerCollector.java)

From a terminal with Java 19 installed, run:

```shell
wget -nc https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/TypeToken.java
wget -nc https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/JsonToken.java
wget -nc https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/JsonSerializer.java
wget -nc https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/StringUtils.java
wget -nc https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/JsonPrinter.java
wget -nc https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/JsonParser.java
wget -nc https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/JsonDeserializer.java
wget -nc https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/JsonDeserializerCollector.java
jshell --enable-preview TypeToken.java JsonToken.java JsonSerializer.java StringUtils.java JsonPrinter.java JsonParser.java JsonDeserializer.java JsonDeserializerCollector.java
```

Inside jshell let's serialize a Java record into JSON tokens, print those tokens into JSON text, parse that text 
back into a stream of JSON tokens and finally deserialize those tokens back into a Java record. We can 
compare the two records to ensure it worked properly.

```text
var serializer = JsonSerializer.instance();
var printer = JsonPrinter.instance();
var parser = JsonParser.instance();
var deserializer = JsonDeserializer.instance();

record Person(String name, int age) {}
var person = new Person("someone", 28);

var deserializedPerson = serializer.serialize(person)        // serialize to stream of JsonToken
    .map(printer::print)            // map each JsonToken to a String (as a CharSequence)
    .map(CharSequence::chars)       // map each CharSequence to an IntStream
    .flatMap(parser::parse)         // pass each IntStream to the parser and flatten the resulting stream of tokens
    .collect(JsonDeserializerCollector.deserializing(deserializer, Person.class));  // use the collector to reduce the tokens into a Person via the deserializer
    
if (person.equals(deserializedPerson)) {
    System.out.println("It worked!");    
}
System.out.println("Done.");    
```

# We're hiring

Want to be able to use the latest features of Java? [We're hiring!](https://www.starburst.io/careers/)

------------

_About the author:_

_Jordan Zimmerman is a Software Engineer working on [Starburst Galaxy](https://www.starburst.io/platform/starburst-galaxy/)_ 
