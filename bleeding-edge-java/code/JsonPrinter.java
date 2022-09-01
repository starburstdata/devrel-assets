public interface JsonPrinter
{
    static JsonPrinter instance()
    {
        return new JsonPrinter() {};
    }

    default CharSequence print(JsonToken jsonToken)
    {
        return switch (jsonToken) {
            case JsonToken.NumberToken(var number) -> number.toString();
            case JsonToken.StringToken(var string) -> StringUtils.quoteAndEscape(string);
            case JsonToken.BooleanToken(var value) -> value ? "true" : "false";
            case JsonToken.NullToken __ -> "null";
            case JsonToken.BeginArrayToken __ -> "[";
            case JsonToken.EndArrayToken __ -> "]";
            case JsonToken.BeginObjectToken __ -> "{";
            case JsonToken.EndObjectToken __ -> "}";
            case JsonToken.ObjectNameToken(var name) -> StringUtils.quoteAndEscape(name) + ":";
            case JsonToken.ValueSeparatorToken __ -> ",";
        };
    }
}
