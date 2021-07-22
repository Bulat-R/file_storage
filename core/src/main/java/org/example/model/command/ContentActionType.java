package org.example.model.command;

public enum ContentActionType {
    OPEN("open"), DELETE("delete"), RENAME("rename"), DOWNLOAD("download");

    private final String value;

    ContentActionType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
