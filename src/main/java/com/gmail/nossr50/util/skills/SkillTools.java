package com.gmail.nossr50.util.skills;

import com.gmail.nossr50.api.exceptions.InvalidSkillException;
import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.datatypes.skills.ToolType;
import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.util.Misc;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.StringUtils;
import com.google.common.collect.ImmutableList;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * This class handles misc operations involving skills
 * It also keeps track of the hard-coded relationship between skills
 * This class will be removed once the new skill system is in place
 */
public class SkillTools {

    private final mcMMO pluginRef;
    private final int ENCHANT_SPEED_VAR;

    public final List<String> LOCALIZED_SKILL_NAMES;
    public final List<String> SUBSKILL_NAMES;
    public final List<PrimarySkillType> CHILD_SKILLS;
    public final List<PrimarySkillType> NON_CHILD_SKILLS;
    public final List<PrimarySkillType> COMBAT_SKILLS;
    public final List<PrimarySkillType> GATHERING_SKILLS;
    public final List<PrimarySkillType> MISC_SKILLS;

    private HashMap<SubSkillType, PrimarySkillType> subSkillParentRelationshipMap; //TODO: This disgusts me, but it will have to do until the new skill system is in place
    private HashMap<SuperAbilityType, PrimarySkillType> superAbilityParentRelationshipMap; //TODO: This disgusts me, but it will have to do until the new skill system is in place
    private HashMap<PrimarySkillType, HashSet<SubSkillType>> primarySkillChildrenMap; //TODO: This disgusts me, but it will have to do until the new skill system is in place

    // The map below is for the super abilities which require readying a tool, its everything except blast mining
    private HashMap<PrimarySkillType, SuperAbilityType> mainActivatedAbilityChildMap; //TODO: This disgusts me, but it will have to do until the new skill system is in place
    private HashMap<PrimarySkillType, ToolType> primarySkillToolMap; //TODO: Christ..

    public SkillTools(mcMMO pluginRef) {
        this.pluginRef = pluginRef;

        this.ENCHANT_SPEED_VAR = 5;

        initSubSkillRelationshipMap();
        initPrimaryChildMap();
        initPrimaryToolMap();
        initSuperAbilityParentRelationships();
        buildSubSkillNameList();

        List<PrimarySkillType> childSkills = new ArrayList<>();
        List<PrimarySkillType> nonChildSkills = new ArrayList<>();

        for (PrimarySkillType primarySkillType : PrimarySkillType.values()) {
            if (isChildSkill(primarySkillType)) {
                childSkills.add(primarySkillType);
            } else {
                nonChildSkills.add(primarySkillType);
            }
        }

        COMBAT_SKILLS = ImmutableList.of(PrimarySkillType.ARCHERY, PrimarySkillType.AXES, PrimarySkillType.SWORDS, PrimarySkillType.TAMING, PrimarySkillType.UNARMED);
        GATHERING_SKILLS = ImmutableList.of(PrimarySkillType.EXCAVATION, PrimarySkillType.FISHING, PrimarySkillType.HERBALISM, PrimarySkillType.MINING, PrimarySkillType.WOODCUTTING);
        MISC_SKILLS = ImmutableList.of(PrimarySkillType.ACROBATICS, PrimarySkillType.ALCHEMY, PrimarySkillType.REPAIR, PrimarySkillType.SALVAGE, PrimarySkillType.SMELTING);

        LOCALIZED_SKILL_NAMES = ImmutableList.copyOf(buildLocalizedPrimarySkillNames());
        SUBSKILL_NAMES = ImmutableList.copyOf(buildSubSkillNameList());

        CHILD_SKILLS = ImmutableList.copyOf(childSkills);
        NON_CHILD_SKILLS = ImmutableList.copyOf(nonChildSkills);
    }

    //TODO: What is with this design?
    private void initPrimaryToolMap() {
        primarySkillToolMap = new HashMap<>();

        primarySkillToolMap.put(PrimarySkillType.AXES, ToolType.AXE);
        primarySkillToolMap.put(PrimarySkillType.WOODCUTTING, ToolType.AXE);
        primarySkillToolMap.put(PrimarySkillType.UNARMED, ToolType.FISTS);
        primarySkillToolMap.put(PrimarySkillType.SWORDS, ToolType.SWORD);
        primarySkillToolMap.put(PrimarySkillType.EXCAVATION, ToolType.SHOVEL);
        primarySkillToolMap.put(PrimarySkillType.HERBALISM, ToolType.HOE);
        primarySkillToolMap.put(PrimarySkillType.MINING, ToolType.PICKAXE);
    }

    private void initSuperAbilityParentRelationships() {
        superAbilityParentRelationshipMap = new HashMap<>();
        mainActivatedAbilityChildMap = new HashMap<>();

        for(SuperAbilityType superAbilityType : SuperAbilityType.values()) {
            try {
                PrimarySkillType parent = getSuperAbilityParent(superAbilityType);
                superAbilityParentRelationshipMap.put(superAbilityType, parent);

                if(superAbilityType != SuperAbilityType.BLAST_MINING) {
                    //This map is used only for abilities that have a tool readying phase, so blast mining is ignored
                    mainActivatedAbilityChildMap.put(parent, superAbilityType);
                }
            } catch (InvalidSkillException e) {
                e.printStackTrace();
            }
        }
    }

    private PrimarySkillType getSuperAbilityParent(SuperAbilityType superAbilityType) throws InvalidSkillException {
        switch(superAbilityType) {
            case BERSERK:
                return PrimarySkillType.UNARMED;
            case GREEN_TERRA:
                return PrimarySkillType.HERBALISM;
            case TREE_FELLER:
                return PrimarySkillType.WOODCUTTING;
            case SUPER_BREAKER:
            case BLAST_MINING:
                return PrimarySkillType.MINING;
            case SKULL_SPLITTER:
                return PrimarySkillType.AXES;
            case SERRATED_STRIKES:
                return PrimarySkillType.SWORDS;
            case GIGA_DRILL_BREAKER:
                return PrimarySkillType.EXCAVATION;
            default:
                throw new InvalidSkillException("No parent defined for super ability! "+superAbilityType.toString());
        }
    }

    /**
     * Builds a list of localized {@link PrimarySkillType} names
     * @return list of localized {@link PrimarySkillType} names
     */
    private ArrayList<String> buildLocalizedPrimarySkillNames() {
        ArrayList<String> localizedSkillNameList = new ArrayList<>();

        for(PrimarySkillType primarySkillType : PrimarySkillType.values()) {
            localizedSkillNameList.add(getLocalizedSkillName(primarySkillType));
        }

        Collections.sort(localizedSkillNameList);

        return localizedSkillNameList;
    }

    /**
     * Builds a map containing a HashSet of SubSkillTypes considered Children of PrimarySkillType
     * Disgusting Hacky Fix until the new skill system is in place
     */
    private void initPrimaryChildMap() {
        primarySkillChildrenMap = new HashMap<>();

        //Init the empty Hash Sets
        for(PrimarySkillType primarySkillType : PrimarySkillType.values()) {
            primarySkillChildrenMap.put(primarySkillType, new HashSet<SubSkillType>());
        }

        //Fill in the hash sets
        for(SubSkillType subSkillType : SubSkillType.values()) {
            PrimarySkillType parentSkill = subSkillParentRelationshipMap.get(subSkillType);

            //Add this subskill as a child
            primarySkillChildrenMap.get(parentSkill).add(subSkillType);
        }
    }

    /**
     * Makes a list of the "nice" version of sub skill names
     * Used in tab completion mostly
     * @return a list of formatted sub skill names
     */
    private ArrayList<String> buildSubSkillNameList() {
        ArrayList<String> subSkillNameList = new ArrayList<>();

        for(SubSkillType subSkillType : SubSkillType.values()) {
            subSkillNameList.add(subSkillType.getNiceNameNoSpaces(subSkillType));
        }

        return subSkillNameList;
    }

    /**
     * Builds a map containing the relationships of SubSkillTypes to PrimarySkillTypes
     * Disgusting Hacky Fix until the new skill system is in place
     */
    private void initSubSkillRelationshipMap() {
        subSkillParentRelationshipMap = new HashMap<>();

        //Super hacky and disgusting
        for(PrimarySkillType primarySkillType : PrimarySkillType.values()) {
            for(SubSkillType subSkillType : SubSkillType.values()) {
                String[] splitSubSkillName = subSkillType.toString().split("_");

                if(primarySkillType.toString().equalsIgnoreCase(splitSubSkillName[0])) {
                    //Parent Skill Found
                    subSkillParentRelationshipMap.put(subSkillType, primarySkillType);
                }
            }
        }
    }

    public void applyXpGain(McMMOPlayer mcMMOPlayer, PrimarySkillType skill, float xp, XPGainReason xpGainReason) {
        mcMMOPlayer.beginXpGain(skill, xp, xpGainReason, XPGainSource.SELF);
    }

    public void applyXpGain(McMMOPlayer mcMMOPlayer, PrimarySkillType skill, float xp, XPGainReason xpGainReason, XPGainSource xpGainSource) {
        mcMMOPlayer.beginXpGain(skill, xp, xpGainReason, xpGainSource);
    }

    /**
     * Calculates how long a given ability should last in seconds
     * Does not factor in perks
     * @param mcMMOPlayer target mcMMO Player
     * @param skill target skill
     * @param superAbilityType target Super Ability
     * @return how long an ability should last in seconds
     */
    public int calculateAbilityLength(McMMOPlayer mcMMOPlayer, PrimarySkillType skill, SuperAbilityType superAbilityType) {
        //These values change depending on whether or not the server is in retro mode
        int abilityLengthVar = pluginRef.getConfigManager().getConfigSuperAbilities().getSuperAbilityStartingSeconds();

        int maxLength = pluginRef.getConfigManager().getConfigSuperAbilities().getMaxLengthForSuper(superAbilityType);

        int skillLevel = mcMMOPlayer.getSkillLevel(skill);

        int ticks;

        //Ability cap of 0 or below means no cap
        if (maxLength > 0) {
            ticks = Math.min(2 + (Math.min(maxLength, skillLevel) / abilityLengthVar), maxLength);
        } else {
            ticks = Math.min(2 + (Math.min(maxLength, skillLevel) / abilityLengthVar), maxLength);
        }

        return ticks;
    }

    /**
     * Calculates how long a given ability should last in seconds
     * Adds in perks if the player has any
     * @param mcMMOPlayer target mcMMO Player
     * @param skill target skill
     * @param superAbilityType target Super Ability
     * @return how long an ability should last in seconds
     */
    public int calculateAbilityLengthPerks(McMMOPlayer mcMMOPlayer, PrimarySkillType skill, SuperAbilityType superAbilityType) {
        return getEnduranceLength(mcMMOPlayer.getPlayer()) + calculateAbilityLength(mcMMOPlayer, skill, superAbilityType);
    }

    public int getEnduranceLength(Player player) {
        if (Permissions.twelveSecondActivationBoost(player)) {
            return 12;
        } else if (Permissions.eightSecondActivationBoost(player)) {
            return  8;
        } else if (Permissions.fourSecondActivationBoost(player)) {
            return  4;
        } else {
            return 0;
        }
    }

    public int handleFoodSkills(Player player, int eventFoodLevel, SubSkillType subSkillType) {
        int curRank = pluginRef.getRankTools().getRank(player, subSkillType);

        int currentFoodLevel = player.getFoodLevel();
        int foodChange = eventFoodLevel - currentFoodLevel;

        foodChange += curRank;

        return currentFoodLevel + foodChange;
    }

    /**
     * Calculate the time remaining until the cooldown expires.
     *
     * @param deactivatedTimeStamp Time of deactivation
     * @param cooldown             The length of the cooldown
     * @param player               The Player to check for cooldown perks
     * @return the number of seconds remaining before the cooldown expires
     */
    public int calculateTimeLeft(long deactivatedTimeStamp, int cooldown, Player player) {
        return (int) (((deactivatedTimeStamp + (PerksUtils.handleCooldownPerks(player, cooldown) * Misc.TIME_CONVERSION_FACTOR)) - System.currentTimeMillis()) / Misc.TIME_CONVERSION_FACTOR);
    }

    /**
     * Check if the cooldown has expired.
     * This does NOT account for cooldown perks!
     *
     * @param deactivatedTimeStamp Time of deactivation in seconds
     * @param cooldown             The length of the cooldown in seconds
     * @return true if the cooldown is expired
     */
    public boolean cooldownExpired(long deactivatedTimeStamp, int cooldown) {
        return System.currentTimeMillis() >= (deactivatedTimeStamp + cooldown) * Misc.TIME_CONVERSION_FACTOR;
    }

    /**
     * Checks if the given string represents a valid skill
     *
     * @param skillName The name of the skill to check
     * @return true if this is a valid skill, false otherwise
     */
    public boolean isSkill(String skillName) {
        return pluginRef.getConfigManager().getConfigLanguage().getTargetLanguage().equalsIgnoreCase("en_US") ? matchSkill(skillName) != null : isLocalizedSkill(skillName);
    }

    public void sendSkillMessage(Player player, NotificationType notificationType, String key) {
        Location location = player.getLocation();

        for (Player otherPlayer : player.getWorld().getPlayers()) {
            if (otherPlayer != player && Misc.isNear(location, otherPlayer.getLocation(), Misc.SKILL_MESSAGE_MAX_SENDING_DISTANCE)) {
                pluginRef.getNotificationManager().sendNearbyPlayersInformation(otherPlayer, notificationType, key, player.getName());
            }
        }
    }

    public void handleAbilitySpeedIncrease(Player player) {
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        if (heldItem == null || heldItem.getType() == Material.AIR) {
            return;
        }

        int efficiencyLevel = heldItem.getEnchantmentLevel(Enchantment.DIG_SPEED);
        ItemMeta itemMeta = heldItem.getItemMeta();
        List<String> itemLore = new ArrayList<>();

        if (itemMeta.hasLore()) {
            itemLore = itemMeta.getLore();
        }

        itemLore.add("mcMMO Ability Tool");
        itemMeta.addEnchant(Enchantment.DIG_SPEED, efficiencyLevel + ENCHANT_SPEED_VAR, true);

        itemMeta.setLore(itemLore);
        heldItem.setItemMeta(itemMeta);
        player.updateInventory();

        /*else {
            int duration = 0;
            int amplifier = 0;

            if (player.hasPotionEffect(PotionEffectType.FAST_DIGGING)) {
                for (PotionEffect effect : player.getActivePotionEffects()) {
                    if (effect.getType() == PotionEffectType.FAST_DIGGING) {
                        duration = effect.getDuration();
                        amplifier = effect.getAmplifier();
                        break;
                    }
                }
            }

            McMMOPlayer mcMMOPlayer = pluginRef.getUserManager().getPlayer(player);

            //Not Loaded
            if(mcMMOPlayer == null)
                return;

            PrimarySkillType skill = mcMMOPlayer.getAbilityMode(SuperAbilityType.SUPER_BREAKER) ? PrimarySkillType.MINING : PrimarySkillType.EXCAVATION;

            int abilityLengthVar = AdvancedConfig.getInstance().getAbilityLength();
            int abilityLengthCap = AdvancedConfig.getInstance().getAbilityLengthCap();

            int ticks;

            if(abilityLengthCap > 0)
            {
                ticks = PerksUtils.calculateAbilityLength(player,  Math.min(abilityLengthCap, 2 + (mcMMOPlayer.getSkillLevel(skill) / abilityLengthVar)),
                        skill.getSuperAbility().getMaxLength()) * Misc.TICK_CONVERSION_FACTOR;
            } else {
                ticks = PerksUtils.calculateAbilityLength(player, 2 + ((mcMMOPlayer.getSkillLevel(skill)) / abilityLengthVar),
                        skill.getSuperAbility().getMaxLength()) * Misc.TICK_CONVERSION_FACTOR;
            }

            PotionEffect abilityBuff = new PotionEffect(PotionEffectType.FAST_DIGGING, duration + ticks, amplifier + 10);
            player.addPotionEffect(abilityBuff, true);
        }*/
    }

    public void handleAbilitySpeedDecrease(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            removeAbilityBuff(item);
        }
    }

    public void removeAbilityBuff(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || (!pluginRef.getItemTools().isPickaxe(item) && !pluginRef.getItemTools().isShovel(item)) || !item.containsEnchantment(Enchantment.DIG_SPEED)) {
            return;
        }

        ItemMeta itemMeta = item.getItemMeta();

        if (itemMeta.hasLore()) {
            List<String> itemLore = itemMeta.getLore();

            if (itemLore.remove("mcMMO Ability Tool")) {
                int efficiencyLevel = item.getEnchantmentLevel(Enchantment.DIG_SPEED);

                if (efficiencyLevel <= ENCHANT_SPEED_VAR) {
                    itemMeta.removeEnchant(Enchantment.DIG_SPEED);
                } else {
                    itemMeta.addEnchant(Enchantment.DIG_SPEED, efficiencyLevel - ENCHANT_SPEED_VAR, true);
                }

                itemMeta.setLore(itemLore);
                item.setItemMeta(itemMeta);
            }
        }
    }

    public void handleDurabilityChange(ItemStack itemStack, int durabilityModifier) {
        handleDurabilityChange(itemStack, durabilityModifier, 1.0);
    }

    /**
     * Modify the durability of an ItemStack.
     *
     * @param itemStack          The ItemStack which durability should be modified
     * @param durabilityModifier the amount to modify the durability by
     * @param maxDamageModifier  the amount to adjust the max damage by
     */
    public void handleDurabilityChange(ItemStack itemStack, double durabilityModifier, double maxDamageModifier) {
        if(itemStack.getItemMeta() != null && itemStack.getItemMeta().isUnbreakable()) {
            return;
        }

        Material type = itemStack.getType();
        short maxDurability = pluginRef.getRepairableManager().isRepairable(type) ? pluginRef.getRepairableManager().getRepairable(type).getMaximumDurability() : type.getMaxDurability();
        durabilityModifier = (int) Math.min(durabilityModifier / (itemStack.getEnchantmentLevel(Enchantment.DURABILITY) + 1), maxDurability * maxDamageModifier);

        itemStack.setDurability((short) Math.min(itemStack.getDurability() + durabilityModifier, maxDurability));
    }

    private boolean isLocalizedSkill(String skillName) {
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            if (skillName.equalsIgnoreCase(pluginRef.getLocaleManager().getString(StringUtils.getCapitalized(skill.toString()) + ".SkillName"))) {
                return true;
            }
        }

        return false;
    }

    protected Material getRepairAndSalvageItem(ItemStack inHand) {
        if (pluginRef.getItemTools().isDiamondTool(inHand) || pluginRef.getItemTools().isDiamondArmor(inHand)) {
            return Material.DIAMOND;
        } else if (pluginRef.getItemTools().isGoldTool(inHand) || pluginRef.getItemTools().isGoldArmor(inHand)) {
            return Material.GOLD_INGOT;
        } else if (pluginRef.getItemTools().isIronTool(inHand) || pluginRef.getItemTools().isIronArmor(inHand)) {
            return Material.IRON_INGOT;
        } else if (pluginRef.getItemTools().isStoneTool(inHand)) {
            return Material.COBBLESTONE;
        } else if (pluginRef.getItemTools().isWoodTool(inHand)) {
            return Material.OAK_WOOD;
        } else if (pluginRef.getItemTools().isLeatherArmor(inHand)) {
            return Material.LEATHER;
        } else if (pluginRef.getItemTools().isStringTool(inHand)) {
            return Material.STRING;
        } else {
            return null;
        }
    }

    public int getRepairAndSalvageQuantities(ItemStack item) {
        return getRepairAndSalvageQuantities(item.getType(), getRepairAndSalvageItem(item));
    }

    public int getRepairAndSalvageQuantities(Material itemMaterial, Material recipeMaterial) {
        int quantity = 0;

        for(Iterator<? extends Recipe> recipeIterator = Bukkit.getServer().recipeIterator(); recipeIterator.hasNext();) {
            Recipe bukkitRecipe = recipeIterator.next();

            if(bukkitRecipe.getResult().getType() != itemMaterial)
                continue;

            if(bukkitRecipe instanceof ShapelessRecipe) {
                for (ItemStack ingredient : ((ShapelessRecipe) bukkitRecipe).getIngredientList()) {
                    if (ingredient != null
                            && (recipeMaterial == null || ingredient.getType() == recipeMaterial)
                            && (ingredient.getType() == recipeMaterial)) {
                        quantity += ingredient.getAmount();
                    }
                }
            } else if(bukkitRecipe instanceof ShapedRecipe) {
                for (ItemStack ingredient : ((ShapedRecipe) bukkitRecipe).getIngredientMap().values()) {
                    if (ingredient != null
                            && (recipeMaterial == null || ingredient.getType() == recipeMaterial)
                            && (ingredient.getType() == recipeMaterial)) {
                        quantity += ingredient.getAmount();
                    }
                }
            }
        }

        return quantity;
    }

    public int getRepairAndSalvageQuantities(Material itemMaterial, List<Material> recipeMaterials) {
        int quantity = 0;

        for(Iterator<? extends Recipe> recipeIterator = Bukkit.getServer().recipeIterator(); recipeIterator.hasNext();) {
            Recipe bukkitRecipe = recipeIterator.next();

            if(bukkitRecipe.getResult().getType() != itemMaterial)
                continue;

            boolean matchedIngredient = false;

            for(Material recipeMaterial : recipeMaterials) {
                if(matchedIngredient)
                    break;

                if(bukkitRecipe instanceof ShapelessRecipe) {
                    for (ItemStack ingredient : ((ShapelessRecipe) bukkitRecipe).getIngredientList()) {
                        if (ingredient != null
                                && (recipeMaterial == null || ingredient.getType() == recipeMaterial)
                                && (ingredient.getType() == recipeMaterial)) {
                            quantity += ingredient.getAmount();
                            matchedIngredient = true;
                        }
                    }
                } else if(bukkitRecipe instanceof ShapedRecipe) {
                    for (ItemStack ingredient : ((ShapedRecipe) bukkitRecipe).getIngredientMap().values()) {
                        if (ingredient != null
                                && (recipeMaterial == null || ingredient.getType() == recipeMaterial)
                                && (ingredient.getType() == recipeMaterial)) {
                            quantity += ingredient.getAmount();
                            matchedIngredient = true;
                        }
                    }
                }
            }
        }

        return quantity;
    }

    /**
     * Determine if a recipe has already been registered
     * @param recipe target recipe
     * @return true if the recipe has already been registered
     */
    //TODO: Why is this in this class?
    //TODO: Why is this in this class?
    //TODO: Why is this in this class?
    //TODO: Why is this in this class?
    //TODO: Why is this in this class?
    //TODO: Why is this in this class?
    //TODO: Why is this in this class?
    //TODO: Why is this in this class?
    //TODO: Why is this in this class?
    //TODO: Why is this in this class?
    //TODO: Why is this in this class?
    //TODO: Why is this in this class?
    //TODO: Why is this in this class?
    //TODO: Why is this in this class?
    //TODO: Why is this in this class?
    //TODO: Why is this in this class?
    public boolean hasRecipeBeenRegistered(Recipe recipe) {
        for(Iterator<? extends Recipe> recipeIterator = Bukkit.getServer().recipeIterator(); recipeIterator.hasNext();) {
            Recipe bukkitRecipe = recipeIterator.next();

            if(bukkitRecipe.getResult().isSimilar(recipe.getResult())) {
                return true;
            }

        }
        return false;
    }

    /**
     * Matches a string of a skill to a skill
     * This is NOT case sensitive
     * First it checks the locale file and tries to match by the localized name of the skill
     * Then if nothing is found it checks against the hard coded "name" of the skill, which is just its name in English
     * @param skillName target skill name
     * @return the matching PrimarySkillType if one is found, otherwise null
     */
    public PrimarySkillType matchSkill(String skillName) {
        if (!pluginRef.getConfigManager().getConfigLanguage().getTargetLanguage().equalsIgnoreCase("en_US")) {
            for (PrimarySkillType type : PrimarySkillType.values()) {
                if (skillName.equalsIgnoreCase(pluginRef.getLocaleManager().getString(StringUtils.getCapitalized(type.name()) + ".SkillName"))) {
                    return type;
                }
            }
        }

        for (PrimarySkillType type : PrimarySkillType.values()) {
            if (type.name().equalsIgnoreCase(skillName)) {
                return type;
            }
        }

        if (!skillName.equalsIgnoreCase("all")) {
            pluginRef.getLogger().warning("Invalid mcMMO skill (" + skillName + ")"); //TODO: Localize
        }

        return null;
    }

    /**
     * Gets the PrimarySkillStype to which a SubSkillType belongs
     * Return null if it does not belong to one.. which should be impossible in most circumstances
     * @param subSkillType target subskill
     * @return the PrimarySkillType of this SubSkill, null if it doesn't exist
     */
    public PrimarySkillType getPrimarySkillBySubSkill(SubSkillType subSkillType) {
        return subSkillParentRelationshipMap.get(subSkillType);
    }

    /**
     * Gets the PrimarySkillStype to which a SuperAbilityType belongs
     * Return null if it does not belong to one.. which should be impossible in most circumstances
     * @param superAbilityType target super ability
     * @return the PrimarySkillType of this SuperAbilityType, null if it doesn't exist
     */
    public PrimarySkillType getPrimarySkillBySuperAbility(SuperAbilityType superAbilityType) {
        return superAbilityParentRelationshipMap.get(superAbilityType);
    }

    public SuperAbilityType getSuperAbility(PrimarySkillType primarySkillType) {
        if(mainActivatedAbilityChildMap.get(primarySkillType) == null)
            return null;

        return mainActivatedAbilityChildMap.get(primarySkillType);
    }

    public boolean isSuperAbilityUnlocked(PrimarySkillType primarySkillType, Player player) {
        return pluginRef.getRankTools().getRank(player, getSuperAbility(primarySkillType).getSubSkillTypeDefinition()) >= 1;
    }

    public boolean getPVPEnabled(PrimarySkillType primarySkillType) {
        return pluginRef.getConfigManager().getConfigCoreSkills().isPVPEnabled(primarySkillType);
    }

    public boolean getPVEEnabled(PrimarySkillType primarySkillType) {
        return pluginRef.getConfigManager().getConfigCoreSkills().isPVEEnabled(primarySkillType);
    }

    public boolean getHardcoreStatLossEnabled() {
        return pluginRef.getConfigManager().getConfigHardcore().getDeathPenalty().getSkillToggleMap().get(this);
    }

    public boolean getHardcoreVampirismEnabled() {
        return pluginRef.getConfigManager().getConfigHardcore().getVampirism().getSkillToggleMap().get(this);
    }

    public ToolType getPrimarySkillToolType(PrimarySkillType primarySkillType) {
        return primarySkillToolMap.get(primarySkillType);
    }

    public List<SubSkillType> getSkillAbilities(PrimarySkillType primarySkillType) {
        //TODO: Cache this!
        //TODO: Cache this!
        //TODO: Cache this!
        //TODO: Cache this!
        //TODO: Cache this!
        //TODO: Cache this!
        //TODO: Cache this!
        //TODO: Cache this!
        //TODO: Cache this!
        //TODO: Cache this!
        //TODO: Cache this!
        //TODO: Cache this!
        //TODO: Cache this!
        //TODO: Cache this!
        //TODO: Cache this!
        //TODO: Cache this!
        //TODO: Cache this!
        //TODO: Cache this!
        //TODO: Cache this!
        //TODO: Cache this!
        //TODO: Cache this!
        //TODO: Cache this!
        //TODO: Cache this!
        //TODO: Cache this!
        //TODO: Cache this!
        //TODO: Cache this!
        //TODO: Cache this!
        //TODO: Cache this!
        //TODO: Cache this!
        //TODO: Cache this!
        //TODO: Cache this!
        //TODO: Cache this!
        //TODO: Cache this!
        return new ArrayList<>(primarySkillChildrenMap.get(primarySkillType));
    }

    public double getXpModifier(PrimarySkillType primarySkillType) {
        return pluginRef.getConfigManager().getConfigLeveling().getSkillXpFormulaModifier(primarySkillType);
    }

    // TODO: This is a little "hacky", we probably need to add something to distinguish child skills in the enum, or to use another enum for them
    public boolean isChildSkill(PrimarySkillType primarySkillType) {
        switch (primarySkillType) {
            case SALVAGE:
            case SMELTING:
                return true;

            default:
                return false;
        }
    }

    /**
     * Get the localized name for a {@link PrimarySkillType}
     * @param primarySkillType target {@link PrimarySkillType}
     * @return the localized name for a {@link PrimarySkillType}
     */
    public String getLocalizedSkillName(PrimarySkillType primarySkillType) {
        return StringUtils.getCapitalized(pluginRef.getLocaleManager().getString(StringUtils.getCapitalized(primarySkillType.toString()) + ".SkillName"));
    }

    public boolean doesPlayerHaveSkillPermission(PrimarySkillType primarySkillType, Player player) {
        return Permissions.skillEnabled(player, primarySkillType);
    }

    public boolean canCombatSkillsTrigger(PrimarySkillType primarySkillType, Entity target) {
        return (target instanceof Player || (target instanceof Tameable && ((Tameable) target).isTamed())) ? getPVPEnabled(primarySkillType) : getPVEEnabled(primarySkillType);
    }

    public String getCapitalizedPrimarySkillName(PrimarySkillType primarySkillType) {
        return StringUtils.getCapitalized(primarySkillType.toString());
    }
}
