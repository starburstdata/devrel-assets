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
            case ObjectNameToken objectNameToken -> JsonPrinter.this.print(new ObjectNameToken(naming.apply(objectNameToken.name())));
            default -> JsonPrinter.this.print(jsonToken);
        };
    }

    static CharSequence printStandard(JsonToken jsonToken)
    {
        return switch (jsonToken) {
            case NumberToken numberToken -> numberToken.value().toString();
            case StringToken stringToken -> StringUtils.quoteAndEscape(stringToken.value(), false);
            case BooleanToken booleanToken -> booleanToken.value() ? "true" : "false";
            case NullToken ignore -> "null";
            case BeginArrayToken ignore -> "[";
            case EndArrayToken ignore -> "]";
            case BeginObjectToken ignore -> "{";
            case EndObjectToken ignore -> "}";
            case ObjectNameToken objectNameToken -> StringUtils.quoteAndEscape(objectNameToken.name(), true);
            case ValueSeparatorToken ignore -> ",";
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
                case ObjectNameToken __ -> addSpace = true;
                case BeginObjectToken __ -> {
                    ++context.currentLevel;
                    addNewLineAndIndent = true;
                }
                case BeginArrayToken __ -> {
                    ++context.currentLevel;
                    addNewLineAndIndent = true;
                }
                case ValueSeparatorToken __ -> addNewLineAndIndent = true;
                case null -> {}
                default -> {}
            }
            switch (jsonToken) {
                case EndArrayToken __ -> {
                    --context.currentLevel;
                    addNewLineAndIndent = true;
                }
                case EndObjectToken __ -> {
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
