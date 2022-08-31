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

import java.util.function.UnaryOperator;

public interface JsonNaming
        extends UnaryOperator<String>
{
    JsonNaming SNAKE_CASE = s -> {
        StringBuilder result = new StringBuilder();
        boolean previousWasUnderscore = true;
        for (char c : s.toCharArray()) {
            if (Character.isUpperCase(c)) {
                if (!previousWasUnderscore) {
                    result.append('_');
                }
                previousWasUnderscore = true;
                result.append(Character.toLowerCase(c));
            }
            else {
                previousWasUnderscore = (c == '_');
                result.append(c);
            }
        }
        return result.toString();
    };

    JsonNaming CAMEL_CASE = s -> {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        boolean previousWasUnderscore = false;
        for (char c : s.toCharArray()) {
            if (first) {
                result.append(Character.toLowerCase(c));
                first = false;
            }
            else if (c == '_') {
                previousWasUnderscore = true;
            }
            else if (previousWasUnderscore) {
                previousWasUnderscore = false;
                result.append(Character.toUpperCase(c));
            }
            else {
                result.append(c);
            }
        }
        return result.toString();
    };

    static JsonNaming build(UnaryOperator<String> naming)
    {
        return naming::apply;
    }
}