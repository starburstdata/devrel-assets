# JSON Library

An evolving JSON serialization library using modern Java techniques. 

## Standard usage

```java
Json json = Json.instance();

// serialize to JSON text
String jsonText = json.serializeToString(object);

// serialize to a Writer
json.serializeToWriter(object, writer);

// deserialize to non-generic classes
MyType myType = json.deserialize(MyType.class, jsonText);

// deserialize to generic classes
TypeToken<List<MyType>> new TypeToken<>() {};
List<MyType> myType = json.deserialize(typeToken, jsonText);
```

## Custom formatting

```java
// instance that "pretty" prints the JSON text
Json json = Json.builder()
        .withPrettyPrinting()
        .build();

// instance that prints JSON text with snake-case names and reads JSON text with camel-case names
Json json = Json.builder()
        .withDeserializationNaming(JsonNaming.SNAKE_CASE)
        .withSerializationNaming(JsonNaming.CAMEL_CASE)
        .build();
```

## Custom type serialization

Use `JsonClass` to create a specification for simple types such as dates, etc. For example, for `Instant`:

```java
JsonClass jsonClass = JsonClass.forSimple(
        Instant.class, 
        StringToken.class, 
        token -> Instant.parse(token.value()),
        (rootDeserializer, instant) -> new StringToken(instant.toString()));

Json json = Json.builder()
    .add(jsonClass)
    .build();

Instant i = json.deserialize(Instant.class, "\"2022-08-21T07:08:54.989685Z\"");
```

## Custom class serialization

Use `JsonClass` to create a specification for your class. For example, given this class:

```java
public class MyObject<T>
{
    private final T type;
    private final int qty;

    public MyObject(T type, int qty)
    {
        this.type = type;
        this.qty = qty;
    }

    public int qty()
    {
        return qty;
    }

    public T type()
    {
        return type;
    }

    // ... etc ..
}
```

Build the specification and use it:

```java
TypeToken<MyObject<String>> typeToken = new TypeToken<>() {};

JsonClass jsonClass = JsonClass.builder(typeToken)
        .addTypeVariableField("type", MyObject::type, String.class)
        .addField("qty", MyObject::qty, int.class)
        .build();

Json json = Json.builder()
    .add(jsonClass)
    .build();

String jsonText = json.serializeToString(new MyObject<String>("test", 101));
MyObject<String> myObject = json.deserialize(typeToken, jsonText);
```
