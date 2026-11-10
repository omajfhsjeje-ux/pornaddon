package com.example.addon;

import com.example.addon.modules.RelapseChunkFinder;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Relapse Debug");
    public static final HudGroup HUD_GROUP = new HudGroup("Relapse Debug");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Relapse Debug");
        Modules.get().add(new RelapseChunkFinder());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }
}
