package br.com.gabrielmartins.itemzider;

import net.minecraft.server.v1_7_R4.*;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_7_R4.inventory.CraftItemStack;
import org.bukkit.entity.*;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ItemizerPlugin extends JavaPlugin implements Listener {
    private final Set<UUID> activeDamageEvents = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("ItemizerPlugin fork ativado!");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;

        Player attacker = (Player) event.getDamager();
        ItemStack item = attacker.getItemInHand();
        if (item == null || item.getType() == Material.AIR) return;

        net.minecraft.server.v1_7_R4.ItemStack nmsItem = CraftItemStack.asNMSCopy(item);
        if (nmsItem == null || !nmsItem.hasTag() || !nmsItem.getTag().hasKey("AttributeModifiers")) return;

        Entity target = event.getEntity();
        if (!(target instanceof LivingEntity) || activeDamageEvents.contains(target.getUniqueId())) return;

        try {
            activeDamageEvents.add(target.getUniqueId());

            double customDamage = calculateCustomDamage(nmsItem, target);

            if (customDamage > 0) {
                event.setDamage(0);

                ((LivingEntity) target).damage(customDamage, attacker);

                event.setCancelled(true);
            }
        } finally {
            activeDamageEvents.remove(target.getUniqueId());
        }
    }
    private double calculateCustomDamage(net.minecraft.server.v1_7_R4.ItemStack nmsItem, Entity target) {
        String damageType = target instanceof Player ? "generic.attackDamage.player"
                : "generic.attackDamage.npc";

        double damage = getAttributeValue(nmsItem, damageType);
        return damage > 0 ? damage : getAttributeValue(nmsItem, "generic.attackDamage");
    }

    private double getAttributeValue(net.minecraft.server.v1_7_R4.ItemStack nmsItem, String attributeName) {
        if (!nmsItem.tag.hasKey("AttributeModifiers")) return 0;

        NBTTagList attributes = nmsItem.tag.getList("AttributeModifiers", 10);
        double total = 0;

        for (int i = 0; i < attributes.size(); i++) {
            NBTTagCompound attr = attributes.get(i);
            if (attr.getString("AttributeName").equals(attributeName)) {
                total += attr.getDouble("Amount");
            }
        }
        return total;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "Itemizer - Use /itemizer help para ajuda");
            return true;
        }

        if (args[0].equalsIgnoreCase("attr")) {
            return handleAttributesCommand(sender, Arrays.copyOfRange(args, 1, args.length));
        }

        sender.sendMessage(ChatColor.RED + "Comando não reconhecido. Use /itemizer help");
        return true;
    }

    private boolean handleAttributesCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Apenas jogadores podem usar este comando!");
            return true;
        }

        Player player = (Player) sender;
        if (args.length == 0) {
            sendAttributeHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "add":
                return addAttribute(player, args);
            case "remove":
                return removeAttribute(player, args);
            case "list":
                return listAttributes(player);
            default:
                sendAttributeHelp(player);
                return true;
        }
    }

    private boolean addAttribute(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(ChatColor.RED + "Uso: /itemizer attr add <nome> <tipo> <valor> [operação]");
            return true;
        }

        try {
            String name = args[1];
            String type = args[2].toLowerCase();
            double value = Double.parseDouble(args[3]);
            int operation = args.length > 4 ? parseOperation(args[4]) : 0;

            net.minecraft.server.v1_7_R4.ItemStack nmsItem = CraftItemStack.asNMSCopy(player.getItemInHand());
            if (nmsItem == null) {
                player.sendMessage(ChatColor.RED + "Segure um item na mão!");
                return true;
            }

            if (nmsItem.tag == null) {
                nmsItem.tag = new NBTTagCompound();
            }

            NBTTagList attributes = nmsItem.tag.getList("AttributeModifiers", 10);
            NBTTagCompound attribute = new NBTTagCompound();

            attribute.set("Name", new NBTTagString(name));
            attribute.set("AttributeName", new NBTTagString(getAttributeType(type)));
            attribute.set("Amount", new NBTTagDouble(value));
            attribute.set("Operation", new NBTTagInt(operation));

            UUID uuid = UUID.randomUUID();
            attribute.set("UUIDMost", new NBTTagLong(uuid.getMostSignificantBits()));
            attribute.set("UUIDLeast", new NBTTagLong(uuid.getLeastSignificantBits()));

            attributes.add(attribute);
            nmsItem.tag.set("AttributeModifiers", attributes);

            player.setItemInHand(CraftItemStack.asCraftMirror(nmsItem));
            player.sendMessage(ChatColor.GREEN + "Atributo adicionado com sucesso!");

        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Valor inválido!");
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + e.getMessage());
        }

        return true;
    }

    private String getAttributeType(String type) throws IllegalArgumentException {
        switch (type) {
            case "damage":
                return "generic.attackDamage";
            case "damage_player":
                return "generic.attackDamage.player";
            case "damage_npc":
                return "generic.attackDamage.npc";
            case "speed":
                return "generic.movementSpeed";
            case "health":
                return "generic.maxHealth";
            case "knockback":
                return "generic.knockbackResistance";
            default:
                throw new IllegalArgumentException("Tipo de atributo inválido!");
        }
    }

    private int parseOperation(String op) throws IllegalArgumentException {
        switch (op.toLowerCase()) {
            case "add":
                return 0;
            case "multiply":
                return 1;
            case "multiply_base":
                return 2;
            default:
                throw new IllegalArgumentException("Operação inválida! Use: add, multiply ou multiply_base");
        }
    }

    private boolean removeAttribute(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Uso: /itemizer attr remove <nome>");
            return true;
        }

        net.minecraft.server.v1_7_R4.ItemStack nmsItem = CraftItemStack.asNMSCopy(player.getItemInHand());
        if (nmsItem == null || nmsItem.tag == null) {
            player.sendMessage(ChatColor.RED + "Segure um item com atributos na mão!");
            return true;
        }

        NBTTagList attributes = nmsItem.tag.getList("AttributeModifiers", 10);
        NBTTagList newAttributes = new NBTTagList();
        String nameToRemove = args[1];
        boolean removed = false;

        for (int i = 0; i < attributes.size(); i++) {
            NBTTagCompound attr = attributes.get(i);
            if (!attr.getString("Name").equals(nameToRemove)) {
                newAttributes.add(attr);
            } else {
                removed = true;
            }
        }

        if (!removed) {
            player.sendMessage(ChatColor.RED + "Atributo não encontrado!");
            return true;
        }

        nmsItem.tag.set("AttributeModifiers", newAttributes);
        player.setItemInHand(CraftItemStack.asCraftMirror(nmsItem));
        player.sendMessage(ChatColor.GREEN + "Atributo removido com sucesso!");
        return true;
    }

    private boolean listAttributes(Player player) {
        net.minecraft.server.v1_7_R4.ItemStack nmsItem = CraftItemStack.asNMSCopy(player.getItemInHand());
        if (nmsItem == null || nmsItem.tag == null) {
            player.sendMessage(ChatColor.YELLOW + "O item não possui atributos.");
            return true;
        }

        NBTTagList attributes = nmsItem.tag.getList("AttributeModifiers", 10);
        if (attributes.size() == 0) {
            player.sendMessage(ChatColor.YELLOW + "O item não possui atributos.");
            return true;
        }

        player.sendMessage(ChatColor.GOLD + "Atributos do item:");
        for (int i = 0; i < attributes.size(); i++) {
            NBTTagCompound attr = attributes.get(i);
            player.sendMessage(ChatColor.YELLOW + "- " + attr.getString("Name") + ": " +
                    attr.getString("AttributeName") + " (" + attr.getDouble("Amount") + ")");
        }
        return true;
    }

    private void sendAttributeHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "Ajuda de Atributos:");
        player.sendMessage(ChatColor.YELLOW + "/itemizer attr add <nome> <tipo> <valor> [operação]");
        player.sendMessage(ChatColor.YELLOW + "/itemizer attr remove <nome>");
        player.sendMessage(ChatColor.YELLOW + "/itemizer attr list");
        player.sendMessage(ChatColor.GOLD + "Tipos: damage, damage_player, damage_npc, speed, health, knockback");
        player.sendMessage(ChatColor.GOLD + "Operações: add (padrão), multiply, multiply_base");
    }
}