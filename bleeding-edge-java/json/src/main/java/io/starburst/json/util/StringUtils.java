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
package io.starburst.json.util;

import java.util.PrimitiveIterator;
import java.util.function.IntConsumer;

public interface StringUtils
{
    static Number parseNumber(StringBuilder builder, char firstChar, PrimitiveIterator.OfInt iterator, IntConsumer pushbackProc)
    {
        builder.setLength(0);
        builder.append(firstChar);
        boolean isComplete = false;
        while (!isComplete) {
            if (!iterator.hasNext()) {
                break;
            }
            int next = iterator.nextInt();
            char c = (char) (next & 0xffff);
            switch (c) {
                case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '+', '.', 'e', 'E' -> builder.append(c);
                case '}', ',', ']' -> {
                    isComplete = true;
                    pushbackProc.accept(next);
                }
                default -> {
                    if (!isWhitespace(c)) {
                        throw new RuntimeException("Unexpected character while parsing number: " + c);
                    }
                    isComplete = true;
                }
            }
        }
        String value = builder.toString();
        return (value.indexOf('.') >= 0) ? (Number)Double.parseDouble(value) : (Number)Long.parseLong(value);
    }

    static String parseString(StringBuilder builder, PrimitiveIterator.OfInt iterator)
    {
        builder.setLength(0);
        boolean previousWasEscape = false;
        boolean isComplete = false;
        int unicodeRemaining = 0;
        int unicodeValue = 0;
        while (!isComplete) {
            if (!iterator.hasNext()) {
                throw new RuntimeException("Unexpected end of stream while parsing string");
            }
            char c = (char) (iterator.nextInt() & 0xffff);
            if (unicodeRemaining > 0) {
                --unicodeRemaining;
                int value = switch (c) {
                    case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> (c - '0');
                    case 'a', 'b', 'c', 'd', 'e', 'f' -> (10 + (c - 'a'));
                    case 'A', 'B', 'C', 'D', 'E', 'F' -> (10 + (c - 'A'));
                    default -> throw new RuntimeException("Unexpected unicode escape character while parsing string: " + c);
                };
                unicodeValue = (unicodeValue << 4) | value;
                if (unicodeRemaining == 0) {
                    builder.append((char) (unicodeValue & 0xffff));
                }
            }
            else if (previousWasEscape) {
                previousWasEscape = false;  // handle \t, \r, etc.
                switch (c) {
                    case 'u' -> {
                        unicodeRemaining = 4;
                        unicodeValue = 0;
                    }
                    case 'b' -> builder.append('\b');
                    case 'f' -> builder.append('\f');
                    case 'n' -> builder.append('\n');
                    case 'r' -> builder.append('\r');
                    case 't' -> builder.append('\t');
                    default -> builder.append(c);
                }
            }
            else if (c != '\\') {
                isComplete = (c == '"');
                if (!isComplete) {
                    builder.append(c);
                }
            }
            else {
                previousWasEscape = true;
            }
        }
        return builder.toString();
    }

    static boolean isWhitespace(char c)
    {
        return switch (c) {
            case 0x20, 0x09, 0x0a, 0x0d -> true;
            default -> false;
        };
    }

    static CharSequence quoteAndEscape(String str, boolean addObjectNameSeparator)
    {
        StringBuilder result = new StringBuilder();
        result.append('"');
        for (int i = 0; i < str.length(); ++i) {
            char c = str.charAt(i);
            switch (c) {
                case '"' -> result.append("\\\"");
                case '\t' -> result.append("\\t");
                case '\r' -> result.append("\\r");
                case '\n' -> result.append("\\n");
                default -> result.append(c);
            }
        }
        result.append('"');
        if (addObjectNameSeparator) {
            result.append(':');
        }
        return result;
    }
}
