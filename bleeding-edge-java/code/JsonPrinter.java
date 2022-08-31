public interface JsonPrinter
{
    static JsonPrinter instance()
    {
        return new JsonPrinter() {};
    }

    default CharSequence print(JsonToken jsonToken)
    {
        return switch (jsonToken) {
            case JsonToken.NumberToken numberToken -> numberToken.value().toString();
            case JsonToken.StringToken stringToken -> StringUtils.quoteAndEscape(stringToken.value());
            case JsonToken.BooleanToken booleanToken -> booleanToken.value() ? "true" : "false";
            case JsonToken.NullToken ignore -> "null";
            case JsonToken.BeginArrayToken ignore -> "[";
            case JsonToken.EndArrayToken ignore -> "]";
            case JsonToken.BeginObjectToken ignore -> "{";
            case JsonToken.EndObjectToken ignore -> "}";
            case JsonToken.ObjectNameToken objectNameToken -> StringUtils.quoteAndEscape(objectNameToken.name()) + ":";
            case JsonToken.ValueSeparatorToken ignore -> ",";
        };
    }
}
