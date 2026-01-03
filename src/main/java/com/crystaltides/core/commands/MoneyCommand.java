package com.crystaltides.core.commands;

import com.crystaltides.core.CrystalCore;
import com.crystaltides.core.modules.ProfileModule;
import com.crystaltides.core.profile.CrystalProfile;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.NumberFormat;
import java.util.Locale;

public class MoneyCommand implements CommandExecutor {

    private final CrystalCore plugin;

    public MoneyCommand(CrystalCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cSolo jugadores.");
            return true;
        }

        Player player = (Player) sender;
        ProfileModule profileModule = plugin.getModuleManager().getModule(ProfileModule.class);

        if (profileModule != null) {
            CrystalProfile profile = profileModule.getProfile(player.getUniqueId());
            if (profile != null) {
                long balance = profile.getKillucoins();
                String formatted = NumberFormat.getInstance(Locale.US).format(balance);

                // You can add logic here to determine currency rank (Gold, Diamond, etc.)
                // For now, simple output:
                player.sendMessage("§b[Banco] §7Tienes: §e" + formatted + " Killucoins!");
            } else {
                player.sendMessage("§cError cargando tu perfil.");
            }
        }
        return true;
    }
}
