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
package io.starburst.json.models;

import java.util.List;

public record GlossaryContainer(Glossary glossary)
{
    public enum ID {
        GML,
        XML,
        SGML
    }

    public record Glossary(String title, GlossDiv GlossDiv) {}

    public record GlossDiv(String title, GlossList GlossList) {}

    public record GlossList(GlossEntry GlossEntry) {}

    public record GlossEntry(ID ID, ID SortAs, String GlossTerm, ID Acronym, String Abbrev, GlossDef GlossDef, String GlossSee) {}

    public record GlossDef(String para, List<ID> GlossSeeAlso) {}
}
