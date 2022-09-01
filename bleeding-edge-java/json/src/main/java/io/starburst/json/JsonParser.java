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

import java.util.Iterator;
import java.util.PrimitiveIterator;
import java.util.Spliterators;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public interface JsonParser
{
    static JsonParser instance()
    {
        return JsonParser::parseStandard;
    }

    Stream<JsonToken> parse(IntStream stream);

    default JsonParser withNaming(JsonNaming naming)
    {
        return stream -> parse(stream)
                .map(token -> switch (token) {
                    case ObjectNameToken(var name) -> new ObjectNameToken(naming.apply(name));
                    default -> token;
                });
    }

    static Stream<JsonToken> parseStandard(IntStream stream)
    {
        Iterator<JsonToken> tokenIterator = new Iterator<>() {
            private final PrimitiveIterator.OfInt iterator = stream.iterator();
            private final StringBuilder builder = new StringBuilder();
            private StringToken previousStringToken;
            private int pushedBack = -1;
            private JsonToken nextToken;

            {
                advanceToNextToken();
            }

            @Override
            public boolean hasNext()
            {
                return (previousStringToken != null) || (nextToken != null);
            }

            @Override
            public JsonToken next()
            {
                JsonToken result;
                if (previousStringToken != null) {
                    result = previousStringToken;
                    previousStringToken = null;
                }
                else {
                    result = nextToken;
                    nextToken = null;
                }
                if (result == null) {
                    throw new RuntimeException();
                }
                advanceToNextToken();
                return result;
            }

            private void advanceToNextToken()
            {
                if (pushedBack < 0) {
                    pushedBack = advanceWhitespace(iterator);
                }

                String possibleObjectName = null;
                while ((nextToken == null) && ((pushedBack >= 0) || iterator.hasNext())) {
                    char c = (char) (((pushedBack >= 0) ? pushedBack : iterator.nextInt()) & 0xffff);
                    pushedBack = -1;
                    nextToken = switch (c) {
                        case '{' -> new BeginObjectToken();
                        case '}' -> new EndObjectToken();
                        case '[' -> new BeginArrayToken();
                        case ']' -> new EndArrayToken();
                        case ',' -> new ValueSeparatorToken();
                        case '"' -> {
                            if (possibleObjectName != null) {
                                throw new RuntimeException();   // can't have more than one string
                            }
                            possibleObjectName = StringUtils.parseString(builder, iterator);    // it may be an object name so we have to keep parsing
                            yield null;
                        }
                        case 't' -> parseLiteral(iterator, "rue", new BooleanToken(true));
                        case 'f' -> parseLiteral(iterator, "alse", new BooleanToken(false));
                        case 'n' -> parseLiteral(iterator, "ull", new NullToken());
                        case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '+', '.', 'e', 'E' -> new NumberToken(StringUtils.parseNumber(builder, c, iterator, i -> pushedBack = i));
                        case ':' -> {
                            if (possibleObjectName == null) {
                                throw new RuntimeException();
                            }
                            ObjectNameToken objectNameToken = new ObjectNameToken(possibleObjectName);
                            possibleObjectName = null;
                            yield objectNameToken;
                        }
                        default -> {
                            if (StringUtils.isWhitespace(c)) {
                                yield null; // ignore whitespace between tokens
                            }
                            throw new RuntimeException();   // unexpected character
                        }
                    };
                }
                if (possibleObjectName != null) {
                    // wasn't an object name - we now have to emit this string token first
                    previousStringToken = new StringToken(possibleObjectName);
                }
            }
        };
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(tokenIterator, 0), false);
    }

    private static JsonToken parseLiteral(PrimitiveIterator.OfInt iterator, String remaining, JsonToken token)
    {
        for (int i = 0; i < remaining.length(); ++i) {
            char c = (char) (iterator.nextInt() & 0xffff);
            if (c != remaining.charAt(i)) {
                throw new RuntimeException();
            }
        }
        return token;
    }

    private static int advanceWhitespace(PrimitiveIterator.OfInt iterator)
    {
        while (iterator.hasNext()) {
            int i = iterator.nextInt();
            char c = (char) (i & 0xffff);
            if (!StringUtils.isWhitespace(c)) {
                return i;
            }
        }
        return -1;
    }
}
