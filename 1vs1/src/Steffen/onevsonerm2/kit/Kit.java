package com.sero583.onevsonerm.kit;

import cn.nukkit.Player;
import cn.nukkit.form.element.ElementButton;
import cn.nukkit.form.element.ElementButtonImageData;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.ConfigSection;

import java.lang.reflect.Field;
import java.util.*;

/**
 * @author Serhat G. (sero583)
 */
public class Kit {
    protected static Map<String, Kit> kits = new LinkedHashMap<>();
    protected static FormWindowSimple kitPickForm;

    public static void load(Config config) {
        load(config, false);
    }

    /**
     * Loads from a valid kit config the values inside.
     * @param config
     * @param emptyOld
     */
    public static void load(Config config, boolean emptyOld) {
        if(emptyOld==true) {
            kits.clear();
            kitPickForm = null;
        }

        ConfigSection kits = config.getSection("kits");

        if(kits.isEmpty()==false) {
            for(Map.Entry<String, Object> entry : kits.entrySet()) {
                String kitName = entry.getKey();
                Object rawData = entry.getValue();

                String displayName = null;
                String imageLink = null;
                Item helmet = null;
                Item chestplate = null;
                Item leggings = null;
                Item boots = null;
                Item[] content = null;

                // Kit(String name, String displayName, String imageLink, Item helmet, Item chestplate, Item leggings, Item boots, Item[] content) {
                if(rawData instanceof ConfigSection) {
                    ConfigSection section = (ConfigSection) rawData;

                    displayName = section.getString("displayName", null);
                    imageLink = section.getString("imageLink", null);

                    if(imageLink.isEmpty()) imageLink = null;

                    helmet = loadItemByArray((String[]) ((List)section.get("helmet")).toArray(new String[] {}));
                    chestplate = loadItemByArray((String[]) ((List)section.get("chestplate")).toArray(new String[] {}));
                    leggings = loadItemByArray((String[]) ((List)section.get("leggings")).toArray(new String[] {}));
                    boots = loadItemByArray((String[]) ((List)section.get("boots")).toArray(new String[] {}));

                    List<List<String>> list = section.getList("items");
                    content = new Item[list.size()];

                    int i = 0;
                    for(List<String> itemData : list) {
                        content[i] = loadItemByArray(itemData.toArray(new String[] {}));
                        i++;
                    }
                }/* else if(rawData instanceof LinkedHashMap) {
                    do not implement, nukkit works for the first time reliable!! :)

                    LinkedHashMap map = (LinkedHashMap) rawData;
                } */else {
                    System.err.println("Couldn't load kit \"" + kitName + "\" due to unknown data-type called \"" + rawData.getClass().getSimpleName() + "\".");
                    continue;
                }

                String missing = "";
                if(displayName==null) {
                    missing += "displayName, ";
                }

                if(helmet==null) {
                    missing += "helmet, ";
                }

                if(chestplate==null) {
                    missing += "chestplate, ";
                }

                if(leggings==null) {
                    missing += "leggings, ";
                }

                if(boots==null) {
                    missing += "boots, ";
                }

                if(content==null) {
                    missing += "content, ";
                }

                if(missing.isEmpty()==false) {
                    missing = missing.substring(0, missing.length()-2);

                    System.err.println("Couldn't load kit \"" + kitName + "\" due to unloaded values=\"" + missing + "\".");
                } else registerKit(kitName, new Kit(kitName, displayName, imageLink, helmet, chestplate, leggings, boots, content));
            }
        }

        loadKitPickForm(config.getString("title", ""), config.getString("content", ""));
    }

    /**
     * Loads item with help of a string-array, format [0] -> id, [1] -> meta, [2] -> amount, [3] -> customName(optional), [4] -> "enchantment:level(optional, default=1)
     * If enchantment are wished, you have to leave customName blank and skip that index
     * Minimum array MUST contain id, otherwise IndexOutOfBoundsException will occur
     * @param array
     * @throws IndexOutOfBoundsException
     * @return Item
     */
    public static Item loadItemByArray(String[] array) {
        //System.out.println("Parsing item data: " + String.join(", ", array));

        int id = Integer.parseInt(array[0]);
        int damage = array.length >= 2 ? Integer.parseInt(array[1]) : 0;
        int amount = array.length >= 3 ? Integer.parseInt(array[2]) : 1;

        Item item = Item.get(id, damage, amount);

        String customName = array.length >= 4 ? (array[3].isEmpty() == false ? array[3] : null) : null;

        if(customName!=null) {
            item.setCustomName(customName);
        }

        if(array.length>=5) {
            for(int i = 4; i < array.length; i++) {
                String value = array[i];
                //System.out.println("Got enchantment data " + value);

                String[] split = value.split(":");

                Enchantment enchantment = getEnchantmentByName(split[0]);

                if(enchantment!=null) {
                    int level = 1;

                    if(split.length>1) {
                        level = Integer.parseInt(split[1]);

                        if(level<=0) {
                            throw new RuntimeException("Level for enchantment \""+split[0]+"\" cannot be 0 or negative. Data=("+String.join(" > ", array));
                        }
                    }
                    item.addEnchantment(enchantment.setLevel(level));
                } else throw new RuntimeException("Couldn't find enchantment " + split[0]);
            }
        }
        return item;
    }

    protected static void loadKitPickForm(String title, String content) {
        kitPickForm = new FormWindowSimple(title, content);

        for(Kit kit : kits.values()) {
            if(kit.hasImageLink()==true) {
                String imageLink = kit.getImageLink();
                String[] linkData = imageLink.split(":", 2);

                kitPickForm.addButton(new ElementButton(kit.getDisplayName(), new ElementButtonImageData(linkData[0], linkData[1])));
            } else kitPickForm.addButton(new ElementButton(kit.getDisplayName()));
        }
    }

    public static FormWindowSimple getKitPickForm() {
        return cloneForm(kitPickForm);
    }

    private static FormWindowSimple cloneForm(FormWindowSimple origin) {
        return new FormWindowSimple(origin.getTitle(), origin.getContent(), origin.getButtons());
    }

    /**
     * Used to register a kit
     * @param name
     * @param kit
     */
    public static void registerKit(String name, Kit kit) {
        registerKit(name, kit, false);
    }

    /**
     * Used to register a kit
     * @param name
     * @param kit
     * @param force
     * @return
     */
    public static boolean registerKit(String name, Kit kit, boolean force) {
        if(kits.containsKey(name)==true && force==false) {
            System.err.println("Tried to override kit " + name + " without applying force.");
            return false;
        }
        kits.put(name, kit);
        return true;
    }

    /**
     * Get kit by using its internal name
     * @param name
     * @return
     */
    public static Kit getKit(String name) {
        return kits.get(name);
    }

    /**
     * Get kit by its display name
     * @param displayName
     * @return
     */
    public static Kit getKitByDisplayName(String displayName) {
        for(Kit kit : kits.values()) {
            if(kit.getDisplayName().equals(displayName)==true) {
                return kit;
            }
        }
        return null;
    }

    @Deprecated
    public static void equipKit(String name, Player player) {
        equipKit(name, player, true);
    }

    /**
     * Deprecated, use Kit.getKitByName(String name) and call its equip function.
     * @param name
     * @param player
     * @param clearInv
     */
    @Deprecated
    public static void equipKit(String name, Player player, boolean clearInv) {
        Kit kit = kits.get(name);

        kit.equip(player, clearInv);
    }

    private String name;
    private String displayName;
    private String imageLink;
    private Item helmet;
    private Item chestplate;
    private Item leggings;
    private Item boots;
    private Item[] content;

    protected Kit(String name, String displayName, String imageLink, Item helmet, Item chestplate, Item leggings, Item boots, Item[] content) {
        this.name = name;
        this.displayName = displayName;

        this.imageLink = imageLink;

        this.helmet = helmet;
        this.chestplate = chestplate;
        this.leggings = leggings;
        this.boots = boots;

        this.content = content;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getImageLink() {
        return imageLink;
    }

    public boolean hasImageLink() {
        return this.imageLink != null ? (this.imageLink.isEmpty()==false && this.imageLink.equals("null") == false) : false;
    }

    public Item getHelmet() {
        return helmet;
    }

    public Item getChestplate() {
        return chestplate;
    }

    public Item getLeggings() {
        return leggings;
    }

    public Item getBoots() {
        return boots;
    }

    public Item[] getContent() {
        return content;
    }

    public void equip(Player player) {
        this.equip(player, true);
    }

    public void equip(Player player, boolean clearInv) {
        PlayerInventory inventory = player.getInventory();

        if(clearInv==true) {
            inventory.clearAll();
        }

        inventory.setHelmet(this.helmet.clone());
        inventory.setChestplate(this.chestplate.clone());
        inventory.setLeggings(this.leggings.clone());
        inventory.setBoots(this.boots.clone());

        for(Item each : this.content) {
            inventory.addItem(each.clone());
        }
    }



    // constants because nukkit API is trash (:
    public static final int PROTECTION = 0;
    public static final int FIRE_PROTECTION = 1;
    public static final int FEATHER_FALLING = 2;
    public static final int BLAST_PROTECTION = 3;
    public static final int PROJECTILE_PROTECTION = 4;
    public static final int THORNS = 5;
    public static final int RESPIRATION = 6;
    public static final int DEPTH_STRIDER = 7;
    public static final int AQUA_AFFINITY = 8;
    public static final int SHARPNESS = 9;
    public static final int SMITE = 10;
    public static final int BANE_OF_ARTHROPODS = 11;
    public static final int KNOCKBACK = 12;
    public static final int FIRE_ASPECT = 13;
    public static final int LOOTING = 14;
    public static final int EFFICIENCY = 15;
    public static final int SILK_TOUCH = 16;
    public static final int UNBREAKING = 17;
    public static final int FORTUNE = 18;
    public static final int POWER = 19;
    public static final int PUNCH = 20;
    public static final int FLAME = 21;
    public static final int INFINITY = 22;
    public static final int LUCK_OF_THE_SEA = 23;
    public static final int LURE = 24;
    public static final int FROST_WALKER = 25;
    public static final int MENDING = 26;
    public static final int BINDING = 27;
    public static final int VANISHING = 28;
    public static final int IMPALING = 29;
    public static final int RIPTIDE = 30;
    public static final int LOYALTY = 31;
    public static final int CHANNELING = 32;
    public static final int MULTISHOT = 33;
    public static final int PIERCING = 34;
    public static final int QUICK_CHARGE = 35;
    public static final int SOUL_SPEED = 36;

    /**
     * Finds enchantment by its name
     * @param name
     * @return Enchantment
     */
    public static Enchantment getEnchantmentByName(String name) {
        String mc = "minecraft:";
        if(name.startsWith(mc)==true) name = name.substring(mc.length());

        try {
            Field field =  Kit.class.getDeclaredField(name.toUpperCase());
            int id = (int) field.get(null);
            return Enchantment.getEnchantment(id);
        } catch(Exception e) {}
        return null;
    }
}
