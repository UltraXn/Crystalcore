package com.crystaltides.core.api;

public interface Module {
    void onEnable();

    void onDisable();

    void reload();

    boolean isEnabled();

    String getName();
}
