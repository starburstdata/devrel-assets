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
