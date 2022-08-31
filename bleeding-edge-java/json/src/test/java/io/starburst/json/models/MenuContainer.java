package io.starburst.json.models;

import java.util.List;

public record MenuContainer(Menu menu)
{
    public record Menu(String id, String value, Popup popup) {}

    public record Popup(List<MenuItem> menuitem) {}

    public record MenuItem(String value, String onclick) {}
}
