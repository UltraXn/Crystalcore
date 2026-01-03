package com.crystaltides.core.managers;

import com.crystaltides.core.CrystalCore;
import com.crystaltides.core.api.CrystalModule;
import java.util.HashMap;
import java.util.Map;

public class ModuleManager {

    private final CrystalCore plugin;
    private final Map<String, CrystalModule> modules = new HashMap<>();

    public ModuleManager(CrystalCore plugin) {
        this.plugin = plugin;
    }

    public void registerModule(CrystalModule module) {
        modules.put(module.getName(), module);
    }

    public void enableModules() {
        for (CrystalModule module : modules.values()) {
            // Check config if module is enabled (defaulting to true for core modules)
            if (plugin.getConfig().getBoolean("modules." + module.getName(), true)) {
                try {
                    module.onEnable();
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to enable module " + module.getName() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    public void disableModules() {
        for (CrystalModule module : modules.values()) {
            if (module.isEnabled()) {
                try {
                    module.onDisable();
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to disable module " + module.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends CrystalModule> T getModule(Class<T> moduleClass) {
        for (CrystalModule module : modules.values()) {
            if (moduleClass.isInstance(module)) {
                return (T) module;
            }
        }
        return null;
    }
}
