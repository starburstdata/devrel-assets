package io.starburst.json;

import io.starburst.json.util.StringUtils;

public interface JsonPrinter
{
    static JsonPrinter instance()
    {
        return JsonPrinter::printStandard;
    }

    CharSequence print(JsonToken jsonToken);

    default JsonPrinter withNaming(JsonNaming naming)
    {
        return jsonToken -> switch (jsonToken) {
            case JsonToken.ObjectNameToken objectNameToken -> JsonPrinter.this.print(new JsonToken.ObjectNameToken(naming.apply(objectNameToken.name())));
            default -> JsonPrinter.this.print(jsonToken);
        };
    }

    static CharSequence printStandard(JsonToken jsonToken)
    {
        return switch (jsonToken) {
            case JsonToken.NumberToken numberToken -> numberToken.value().toString();
            case JsonToken.StringToken stringToken -> StringUtils.quoteAndEscape(stringToken.value(), false);
            case JsonToken.BooleanToken booleanToken -> booleanToken.value() ? "true" : "false";
            case JsonToken.NullToken ignore -> "null";
            case JsonToken.BeginArrayToken ignore -> "[";
            case JsonToken.EndArrayToken ignore -> "]";
            case JsonToken.BeginObjectToken ignore -> "{";
            case JsonToken.EndObjectToken ignore -> "}";
            case JsonToken.ObjectNameToken objectNameToken -> StringUtils.quoteAndEscape(objectNameToken.name(), true);
            case JsonToken.ValueSeparatorToken ignore -> ",";
        };
    }
    default JsonPrinter pretty()
    {
        return pretty(2);
    }

    default JsonPrinter pretty(int indent)
    {
        var context = new Object() {
            int currentLevel;
            JsonToken previousJsonToken;
        };
        return jsonToken -> {
            boolean addNewLineAndIndent = false;
            boolean addSpace = false;
            switch (context.previousJsonToken) {
                case JsonToken.ObjectNameToken __ -> addSpace = true;
                case JsonToken.BeginObjectToken __ -> {
                    ++context.currentLevel;
                    addNewLineAndIndent = true;
                }
                case JsonToken.BeginArrayToken __ -> {
                    ++context.currentLevel;
                    addNewLineAndIndent = true;
                }
                case JsonToken.ValueSeparatorToken __ -> addNewLineAndIndent = true;
                case null -> {}
                default -> {}
            }
            switch (jsonToken) {
                case JsonToken.EndArrayToken __ -> {
                    --context.currentLevel;
                    addNewLineAndIndent = true;
                }
                case JsonToken.EndObjectToken __ -> {
                    --context.currentLevel;
                    addNewLineAndIndent = true;
                }
                default -> {}
            }
            JsonToken previousJsonToken = context.previousJsonToken;
            context.previousJsonToken = jsonToken;
            CharSequence value = print(jsonToken);
            if (addNewLineAndIndent) {
                return "\n" + " ".repeat(context.currentLevel * indent) + value;
            }
            if (addSpace) {
                return " " + value;
            }
            return value;
        };
    }
}
