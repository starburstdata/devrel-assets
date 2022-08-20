# Bleeding Edge Java

This is the start of a series of blog posts meant to show how to use the latest _bleeding edge_ features available in the Java programming language. Here at
[Starburst](https://www.starburst.io/careers/) Java is our primary language. [Trino](https://trino.io/) is written in Java as are most of our back end services. Unlike many companies, 
Starburst is not stuck on an older version of Java. Trino, in fact, was recently updated to support Java 17. The Java language has evolved so dramatically since its inception that code written 20 years ago hardly 
resembles modern code written today.

Many of the Java libraries we use today were written a long time ago and must also support old versions of Java lest they wreak havoc with their user communities.
_**Imagine if you could rewrite some of your favorite libraries with a fresh usage of modern Java?**_
In this series we will do exactly that. The goal is not to replace existing libraries - longstanding libraries shouldn't be replaced merely for aesthetic reasons. The purpose 
here is take a familiar use-case and see how it might be implemented using the very latest, even experimental, features in Java.

Let's take a common use-case that nearly all Java developers have to deal with: [JSON](https://www.json.org/json-en.html). The standard library for JSON in Java is [Jackson](https://github.com/FasterXML/jackson). It would be futile to attempt to emulate the performance and features of Jackson. Instead, let's have a simple goal of being able to write a Java object into valid JSON text and then read valid JSON text and map it to a Java object: serializing and deserializing.

# Roadmap

In this series you will learn...

### Modern Java Features

- **Records** - how to effectively use Java records for concise code that eliminates boilerplate as well as enabling pattern matching and de-structuring
- **Sealed interfaces** - how to close a hierarchy of related classes to express a pre-defined set of related types
- **Interfaces as pseudo companion classes** - a Java `class` definition is a low level concept and is usually not necessary at the top level
- **Record Patterns** - records are not just data carriers. They enable functional idioms that in the past were only available in languages such as Scala and Haskell.
- **Pattern Matching for switch** - the union of record patterns and switch makes your code much easier to reason about and to write

### Additional Goals

- **No magic** - no hacks or tricks should be used. Only standard, modern Java.
- **No annotations** - serialization libraries have tended to rely on Java's annotation processing but this can be error prone and difficult to maintain and debug
- **No special libraries or hacks for type information** - surprisingly, standard Java retains enough type information to write a serialization library that supports generics
- **No dependencies** - the library should be self-contained and not rely on any third party libraries

# Target audience

This series is targeted at Java developers who are comfortable with Java 8 but have not used or
are not comfortable with newer features in Java. The code samples in this series are not meant
for production but we would appreciate any bug reports. A more full featured version of the JSON library [is available here](https://github.com/starburstdata/developer-blog-assets/tree/main/bleeding-edge-java/json).

# Posts in the series

| Post                                                 | Description                                                       |
|------------------------------------------------------|-------------------------------------------------------------------|
| [Part 1 - Architecture](01-design.md)                | Designing an architecture for serializing/deserializing JSON      |
| [Part 2 - Serialization](02-serialization.md)        | Serializing is simpler than deserializing. Let's start with that. |
| [Part 3 - Printing](03-printing.md)                  | Converting a stream of JSON tokens into JSON text.                |
| [Part 4 - Parsing](04-parsing.md)                    | Parsing JSON text into tokens                                     |
| [Part 5 - Deserializing](05-deserialization-01.md)   | The harder part - deserialize JSON into Java objects              |
| [Part 6 - Putting it all together](06-conclusion.md) | Finalizing the system into a usable, extensible library           |

# We're hiring

Want to be able to use the latest features of Java? [We're hiring!](https://www.starburst.io/careers/)

------------

_About the author:_

_Jordan Zimmerman is a Software Engineer working on [Starburst Galaxy](https://www.starburst.io/platform/starburst-galaxy/)_ 
