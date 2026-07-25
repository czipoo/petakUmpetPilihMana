package com.czipo.petakUmpetPilihMana;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.Component;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.FireworkEffect;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;

public class PilihManaManager {
    private final PetakUmpetPilihMana plugin;
    private final Random random = new Random();

    // Active WYR cycle state
    private boolean choiceActive = false;
    private Question currentQuestion = null;
    private final Map<UUID, Integer> playerChoices = new HashMap<>();
    private final Set<UUID> frozenPlayers = new HashSet<>();
    private GameLoopTask activeGameLoopTask = null;
    private Integer nextForcedQuestion = null;

    // Active movement and ticker effects (Player UUID -> expiration time in ms)
    private final Map<UUID, Long> activeADSwaps = new HashMap<>();
    private final Map<UUID, Long> activeWSSwaps = new HashMap<>();
    private final Map<UUID, Long> activeFootprints = new HashMap<>();
    private final Map<UUID, Long> activeCompassTrackers = new HashMap<>();
    private final Map<UUID, Long> activeHighlightRadius = new HashMap<>();
    private final Map<UUID, Long> activeRevealRadius = new HashMap<>();
    private final Map<UUID, Long> activeFlameTrails = new HashMap<>();
    private final Map<UUID, Long> activeSnowflakeTrails = new HashMap<>();
    private final Map<UUID, Long> activeSculkReveal = new HashMap<>();
    private final Map<UUID, Long> activeMutes = new HashMap<>();
    private final Map<UUID, Long> activeStepAmplifiers = new HashMap<>();
    private final Map<UUID, Long> activeAmbientAmplifiers = new HashMap<>();
    private final Map<UUID, Long> activeAmbientParticles = new HashMap<>();
    private final Map<UUID, Long> activeNoSprints = new HashMap<>();
    private final Map<UUID, Long> activeThorns = new HashMap<>();
    private final Map<UUID, Long> activeFogCloud = new HashMap<>();
    private final Map<UUID, Long> activeRadar = new HashMap<>();
    private final List<Player> roulettePool = new ArrayList<>();

    private final List<Question> questionRegistry = new ArrayList<>();

    public PilihManaManager(PetakUmpetPilihMana plugin) {
        this.plugin = plugin;
        registerAllQuestions();
        startPeriodicTicker();
    }

    public static class Question {
        int id;
        String optionA;
        String optionB;
        String[] cmdA;
        String[] cmdB;
        int durationA;
        int durationB;
        CustomEffect customA;
        CustomEffect customB;

        public Question(int id, String optionA, String optionB, String[] cmdA, String[] cmdB, int durationA, int durationB) {
            this.id = id;
            this.optionA = optionA;
            this.optionB = optionB;
            this.cmdA = cmdA;
            this.cmdB = cmdB;
            this.durationA = durationA;
            this.durationB = durationB;
        }

        public Question(int id, String optionA, String optionB, String[] cmdA, CustomEffect customA, int durationA, String[] cmdB, CustomEffect customB, int durationB) {
            this.id = id;
            this.optionA = optionA;
            this.optionB = optionB;
            this.cmdA = cmdA;
            this.cmdB = cmdB;
            this.durationA = durationA;
            this.durationB = durationB;
            this.customA = customA;
            this.customB = customB;
        }

        public Question setCustomA(CustomEffect custom) { this.customA = custom; return this; }
        public Question setCustomB(CustomEffect custom) { this.customB = custom; return this; }
    }

    public interface CustomEffect {
        void execute(Player p, PilihManaManager mgr);
    }

    public boolean isChoiceActive() { return choiceActive; }
    public Set<UUID> getFrozenPlayers() { return frozenPlayers; }
    public boolean isPlayerFrozen(Player p) { return choiceActive && frozenPlayers.contains(p.getUniqueId()); }

    public void registerChoice(Player p, int choice) {
        if (!choiceActive || currentQuestion == null) return;
        if (!plugin.getGameManager().isParticipant(p)) return;
        if (playerChoices.containsKey(p.getUniqueId())) return;

        playerChoices.put(p.getUniqueId(), choice);
        String selectionText = (choice == 1) ? currentQuestion.optionA : currentQuestion.optionB;
        p.sendMessage("§a[PILIHAN] §fKamu memilih: §e" + selectionText);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.5f);

        showWaitingDialog(p, activeGameLoopTask != null ? activeGameLoopTask.getWyrCountdown() : 15);
        checkAllSelected();
    }

    public void onPlayerDisconnect(Player p) {
        if (!choiceActive) {
            return;
        }
        frozenPlayers.remove(p.getUniqueId());
        p.closeDialog();
        checkAllSelected();
    }

    private void checkAllSelected() {
        List<Player> online = plugin.getGameManager().getOnlineParticipants();
        if (online.isEmpty()) return;

        for (Player p : online) {
            if (!playerChoices.containsKey(p.getUniqueId())) {
                return;
            }
        }

        if (activeGameLoopTask != null) {
            activeGameLoopTask.endWyrEarly();
        }
    }

    public void refreshWyrDialogs(int countdown) {
        if (!choiceActive || currentQuestion == null) {
            return;
        }

        for (Player p : plugin.getGameManager().getOnlineParticipants()) {
            if (playerChoices.containsKey(p.getUniqueId())) {
                showWaitingDialog(p, countdown);
            } else {
                showChoiceDialog(p, countdown);
            }
        }
    }

    private void showChoiceDialog(Player p, int countdown) {
        if (!choiceActive || currentQuestion == null || !p.isOnline()) return;
        if (playerChoices.containsKey(p.getUniqueId())) return;

        Question q = currentQuestion;
        Component title = Component.text("Pilih Mana | Waktu Menjawab " + countdown + "s");
        Component body = Component.text("\n\n\n\n\n\n\n")
                .append(Component.text(q.optionA, NamedTextColor.RED))
                .append(Component.text(" atau ", NamedTextColor.GOLD))
                .append(Component.text(q.optionB, NamedTextColor.AQUA));

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title)
                        .body(java.util.List.of(DialogBody.plainMessage(body)))
                        .canCloseWithEscape(false)
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.builder(Component.text("Opsi Kiri", NamedTextColor.RED))
                                .action(DialogAction.customClick((response, audience) -> {
                                    if (audience instanceof Player player) {
                                        registerChoice(player, 1);
                                    }
                                }, ClickCallback.Options.builder().build()))
                                .build(),
                        ActionButton.builder(Component.text("Opsi Kanan", NamedTextColor.AQUA))
                                .action(DialogAction.customClick((response, audience) -> {
                                    if (audience instanceof Player player) {
                                        registerChoice(player, 2);
                                    }
                                }, ClickCallback.Options.builder().build()))
                                .build()
                ))
        );

        p.showDialog(dialog);
    }

    private void showWaitingDialog(Player p, int countdown) {
        Component title = Component.text("Waktu Menjawab " + countdown + "s");
        Component body = Component.text("Tunggu pemain lain", NamedTextColor.WHITE);

        ActionButton placeholder = ActionButton.builder(Component.empty())
                .width(1)
                .action(DialogAction.customClick((response, audience) -> {
                }, ClickCallback.Options.builder().build()))
                .build();

        Dialog waitingDialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title)
                        .body(java.util.List.of(DialogBody.plainMessage(body)))
                        .canCloseWithEscape(false)
                        .build())
                .type(DialogType.multiAction(java.util.List.of(placeholder), null, 1))
        );
        p.showDialog(waitingDialog);
    }

    public boolean setNextQuestion(int questionId) {
        for (Question q : questionRegistry) {
            if (q.id == questionId) {
                this.nextForcedQuestion = questionId;
                return true;
            }
        }
        return false;
    }

    public boolean hasPlayerAnswered(UUID uuid) {
        return playerChoices.containsKey(uuid);
    }

    public void triggerStandaloneQuestion(int questionId, org.bukkit.command.CommandSender sender) {
        Question found = null;
        for (Question q : questionRegistry) {
            if (q.id == questionId) {
                found = q;
                break;
            }
        }

        if (found == null) {
            sender.sendMessage("§cPertanyaan nomor " + questionId + " tidak ditemukan (pilih 1-50).");
            return;
        }

        this.currentQuestion = found;
        playerChoices.clear();
        frozenPlayers.clear();
        choiceActive = true;
        this.activeGameLoopTask = null; // null means standalone

        for (Player p : Bukkit.getOnlinePlayers()) {
            frozenPlayers.add(p.getUniqueId());
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1f, 1f);
            showChoiceDialog(p, 15);
        }

        new org.bukkit.scheduler.BukkitRunnable() {
            int time = 15;
            @Override
            public void run() {
                if (!choiceActive || activeGameLoopTask != null) {
                    this.cancel();
                    return;
                }
                if (time <= 0) {
                    endStandaloneWyr();
                    this.cancel();
                    return;
                }
                refreshWyrDialogs(time);
                time--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void endStandaloneWyr() {
        if (!choiceActive || activeGameLoopTask != null) return;

        Question question = currentQuestion;
        Map<UUID, Integer> finalChoices = new HashMap<>(playerChoices);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.closeDialog();
        }

        if (question != null) {
            for (Map.Entry<UUID, Integer> entry : finalChoices.entrySet()) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null && p.isOnline()) {
                    applyEffect(p, question, entry.getValue() == 1);
                }
            }
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (frozenPlayers.contains(p.getUniqueId()) && !finalChoices.containsKey(p.getUniqueId())) {
                p.sendMessage("§c§l[PENALTI] §fKamu tidak memilih! Efek glowing 2 detik sekarang, dan setiap 10 detik.");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.GLOWING, 40, 0, false, false));
                for (int i = 1; i <= 5; i++) {
                    new org.bukkit.scheduler.BukkitRunnable() {
                        @Override
                        public void run() {
                            if (p.isOnline()) {
                                p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.GLOWING, 20, 0, false, false));
                            }
                        }
                    }.runTaskLater(plugin, i * 200L);
                }
            }
        }

        frozenPlayers.clear();
        playerChoices.clear();
        choiceActive = false;
        currentQuestion = null;
    }

    public void triggerPilihMana(GameLoopTask task) {
        if (questionRegistry.isEmpty()) return;

        this.activeGameLoopTask = task;
        if (nextForcedQuestion != null) {
            currentQuestion = null;
            for (Question q : questionRegistry) {
                if (q.id == nextForcedQuestion) {
                    currentQuestion = q;
                    break;
                }
            }
            if (currentQuestion == null) {
                currentQuestion = questionRegistry.get(random.nextInt(questionRegistry.size()));
            }
            nextForcedQuestion = null;
        } else {
            currentQuestion = questionRegistry.get(random.nextInt(questionRegistry.size()));
        }

        playerChoices.clear();
        choiceActive = true;

        frozenPlayers.clear();
        for (Player p : plugin.getGameManager().getOnlineParticipants()) {
            frozenPlayers.add(p.getUniqueId());
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1f, 1f);
            showChoiceDialog(p, task.getWyrCountdown());
        }
    }

    public void endWyrPhase() {
        if (!choiceActive) return;

        GameManager gm = plugin.getGameManager();
        Question question = currentQuestion;
        Map<UUID, Integer> finalChoices = new HashMap<>(playerChoices);

        for (Player p : gm.getOnlineParticipants()) {
            p.closeDialog();
        }

        if (question != null) {
            for (Map.Entry<UUID, Integer> entry : finalChoices.entrySet()) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null && p.isOnline() && gm.isParticipant(p)) {
                    applyEffect(p, question, entry.getValue() == 1);
                }
            }
        }

        for (Player p : gm.getOnlineParticipants()) {
            if (!finalChoices.containsKey(p.getUniqueId())) {
                p.sendMessage("§c§l[PENALTI] §fKamu tidak memilih! Efek glowing 2 detik sekarang, dan setiap 10 detik.");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.GLOWING, 40, 0, false, false));
                for (int i = 1; i <= 5; i++) {
                    new org.bukkit.scheduler.BukkitRunnable() {
                        @Override
                        public void run() {
                            if (p.isOnline() && gm.isParticipant(p)) {
                                p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.GLOWING, 20, 0, false, false));
                            }
                        }
                    }.runTaskLater(plugin, i * 200L);
                }
            }
        }

        frozenPlayers.clear();
        playerChoices.clear();
        choiceActive = false;
        currentQuestion = null;
        activeGameLoopTask = null;
    }

    private void applyEffect(Player p, Question q, boolean isOptionA) {
        String[] cmds = isOptionA ? q.cmdA : q.cmdB;
        int duration = isOptionA ? q.durationA : q.durationB;
        CustomEffect custom = isOptionA ? q.customA : q.customB;

        if (duration > 0) {
            p.sendMessage("§a[EFEK] §fMenerapkan efek pilihanmu selama §e" + duration + "s§f!");
        } else {
            p.sendMessage("§a[EFEK] §fEfek instan diterapkan!");
        }
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);

        if (cmds != null) {
            for (String cmd : cmds) {
                String cmdProcessed = cmd.replace("@s", p.getName()).trim();
                if (cmdProcessed.startsWith("/")) {
                    cmdProcessed = cmdProcessed.substring(1);
                }
                if (cmdProcessed.startsWith("disguise ")) {
                    String disguiseArg = cmdProcessed.substring("disguise ".length()).trim();
                    if (disguiseArg.startsWith("as ")) {
                        disguiseArg = disguiseArg.substring(3).trim();
                    }
                    executeDisguise(p, disguiseArg, duration);
                } else {
                    SilentCommands.run(cmdProcessed);
                }
            }
        }

        if (custom != null) {
            custom.execute(p, this);
        }

        if (duration > 0) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (p.isOnline()) {
                        resetPlayerEffects(p);
                        p.sendMessage("§e[INFO] §fEfek pilihanmu telah habis.");
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                    }
                }
            }.runTaskLater(plugin, duration * 20L);
        }
    }

    private void executeDisguise(Player p, String disguiseType, int duration) {
        if (disguiseType.contains(":")) {
            disguiseType = disguiseType.split(":")[1];
        }
        String finalDisguise = disguiseType;

        SilentCommands.run("disguiseplayer " + p.getName() + " " + finalDisguise);

        if (duration > 0) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (p.isOnline()) {
                        SilentCommands.run("undisguiseplayer " + p.getName());
                    }
                }
            }.runTaskLater(plugin, duration * 20L);
        }
    }

    public void resetPlayerEffects(Player p) {
        UUID uuid = p.getUniqueId();
        activeADSwaps.remove(uuid);
        activeWSSwaps.remove(uuid);
        activeFootprints.remove(uuid);
        activeCompassTrackers.remove(uuid);
        activeHighlightRadius.remove(uuid);
        activeRevealRadius.remove(uuid);
        activeFlameTrails.remove(uuid);
        activeSnowflakeTrails.remove(uuid);
        activeSculkReveal.remove(uuid);
        activeMutes.remove(uuid);
        activeStepAmplifiers.remove(uuid);
        activeAmbientAmplifiers.remove(uuid);
        activeAmbientParticles.remove(uuid);
        activeNoSprints.remove(uuid);
        activeThorns.remove(uuid);
        activeFogCloud.remove(uuid);
        activeRadar.remove(uuid);

        resetAttributesToDefault(p);
    }

    public void resetParticipantEffects() {
        activeADSwaps.clear();
        activeWSSwaps.clear();
        activeFootprints.clear();
        activeCompassTrackers.clear();
        activeHighlightRadius.clear();
        activeRevealRadius.clear();
        activeFlameTrails.clear();
        activeSnowflakeTrails.clear();
        activeSculkReveal.clear();
        activeMutes.clear();
        activeStepAmplifiers.clear();
        activeAmbientAmplifiers.clear();
        activeAmbientParticles.clear();
        activeNoSprints.clear();
        activeThorns.clear();
        activeFogCloud.clear();
        activeRadar.clear();
        roulettePool.clear();

        for (Player p : plugin.getGameManager().getParticipants()) {
            if (!p.isOnline()) continue;
            resetPlayerEffects(p);
            clearPotionEffects(p);
            p.setFoodLevel(20);
            p.setSaturation(20f);
            SilentCommands.run("undisguiseplayer " + p.getName());
        }
    }

    public void resetAllActiveEffects() {
        resetParticipantEffects();
    }

    private void clearPotionEffects(Player p) {
        p.removePotionEffect(PotionEffectType.SPEED);
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        p.removePotionEffect(PotionEffectType.JUMP_BOOST);
        p.removePotionEffect(PotionEffectType.SLOW_FALLING);
        p.removePotionEffect(PotionEffectType.HASTE);
        p.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        p.removePotionEffect(PotionEffectType.WATER_BREATHING);
        p.removePotionEffect(PotionEffectType.DOLPHINS_GRACE);
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
        p.removePotionEffect(PotionEffectType.GLOWING);
        p.removePotionEffect(PotionEffectType.NIGHT_VISION);
        p.removePotionEffect(PotionEffectType.DARKNESS);
        p.removePotionEffect(PotionEffectType.BLINDNESS);
        p.removePotionEffect(PotionEffectType.NAUSEA);
        p.removePotionEffect(PotionEffectType.LEVITATION);
        p.removePotionEffect(PotionEffectType.WEAKNESS);
        p.removePotionEffect(PotionEffectType.STRENGTH);
        p.removePotionEffect(PotionEffectType.REGENERATION);
        p.removePotionEffect(PotionEffectType.MINING_FATIGUE);
        p.removePotionEffect(PotionEffectType.RESISTANCE);
        p.removePotionEffect(PotionEffectType.LUCK);
        p.removePotionEffect(PotionEffectType.SATURATION);
        p.removePotionEffect(PotionEffectType.HUNGER);
    }

    private void resetAttributesToDefault(Player p) {
        String name = p.getName();
        String[] attributes = {
                "minecraft:sneaking_speed 0.3",
                "minecraft:movement_speed 0.1",
                "minecraft:gravity 0.08",
                "minecraft:jump_strength 0.42",
                "minecraft:step_height 0.6",
                "minecraft:fall_damage_multiplier 1.0",
                "minecraft:safe_fall_distance 3.0",
                "minecraft:attack_damage 1.0",
                "minecraft:max_health 20.0",
                "minecraft:entity_interaction_range 3.0",
                "minecraft:block_interaction_range 4.5",
                "minecraft:knockback_resistance 0.0",
                "minecraft:attack_knockback 0.0",
                "minecraft:attack_speed 4.0",
                "minecraft:scale 1.0",
                "minecraft:water_movement_efficiency 0.0",
                "minecraft:armor 0.0",
                "minecraft:armor_toughness 0.0"
        };
        for (String attr : attributes) {
            String[] split = attr.split(" ");
            SilentCommands.run("attribute " + name + " " + split[0] + " base set " + split[1]);
        }
    }

    public Set<UUID> getActiveADSwaps() { return activeADSwaps.keySet(); }
    public Set<UUID> getActiveWSSwaps() { return activeWSSwaps.keySet(); }
    public Set<UUID> getActiveNoSprints() { return activeNoSprints.keySet(); }
    public Set<UUID> getActiveSnowflakeTrails() { return activeSnowflakeTrails.keySet(); }
    public Set<UUID> getActiveFlameTrails() { return activeFlameTrails.keySet(); }
    public Map<UUID, Long> getActiveThorns() { return activeThorns; }

    private void startPeriodicTicker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.getGameManager().isGameRunning()) return;

                long now = System.currentTimeMillis();

                activeFootprints.forEach((uuid, expiry) -> {
                    if (now < expiry) {
                        Player target = Bukkit.getPlayer(uuid);
                        if (target != null && target.isOnline()) {
                            for (Player other : Bukkit.getOnlinePlayers()) {
                                if (other.equals(target) || !other.getWorld().equals(target.getWorld())) continue;
                                if (other.getLocation().distance(target.getLocation()) <= 20) {
                                    target.spawnParticle(Particle.SMOKE, other.getLocation(), 3, 0.1, 0.01, 0.1, 0.01);
                                }
                            }
                        }
                    }
                });

                activeHighlightRadius.forEach((uuid, expiry) -> {
                    if (now < expiry) {
                        Player target = Bukkit.getPlayer(uuid);
                        if (target != null && target.isOnline()) {
                            for (Player other : Bukkit.getOnlinePlayers()) {
                                if (other.equals(target) || !other.getWorld().equals(target.getWorld())) continue;
                                if (other.getLocation().distance(target.getLocation()) <= 12) {
                                    target.spawnParticle(Particle.GLOW, other.getLocation().add(0, 1, 0), 5, 0.3, 0.5, 0.3, 0.01);
                                }
                            }
                        }
                    }
                });

                activeRevealRadius.forEach((uuid, expiry) -> {
                    if (now < expiry) {
                        Player target = Bukkit.getPlayer(uuid);
                        if (target != null && target.isOnline()) {
                            for (Player other : Bukkit.getOnlinePlayers()) {
                                if (other.equals(target) || !other.getWorld().equals(target.getWorld())) continue;
                                if (other.getLocation().distance(target.getLocation()) <= 15) {
                                    target.spawnParticle(Particle.FLAME, other.getLocation().add(0, 1, 0), 4, 0.2, 0.4, 0.2, 0.01);
                                }
                            }
                        }
                    }
                });

                activeSculkReveal.forEach((uuid, expiry) -> {
                    if (now < expiry) {
                        Player target = Bukkit.getPlayer(uuid);
                        if (target != null && target.isOnline()) {
                            for (Player other : Bukkit.getOnlinePlayers()) {
                                if (other.equals(target) || !other.getWorld().equals(target.getWorld())) continue;
                                double dist = other.getLocation().distance(target.getLocation());
                                if (dist <= 10 && other.getVelocity().lengthSquared() > 0.001) {
                                    target.spawnParticle(Particle.SCULK_CHARGE_POP, other.getLocation(), 4, 0.2, 0.2, 0.2, 0.02);
                                }
                            }
                        }
                    }
                });

                activeAmbientAmplifiers.forEach((uuid, expiry) -> {
                    if (now < expiry) {
                        Player target = Bukkit.getPlayer(uuid);
                        if (target != null && target.isOnline()) {
                            if (random.nextInt(3) == 0) {
                                target.playSound(target.getLocation(), Sound.AMBIENT_CAVE, 1.5f, 0.5f);
                            }
                        }
                    }
                });

                activeAmbientParticles.forEach((uuid, expiry) -> {
                    if (now < expiry) {
                        Player target = Bukkit.getPlayer(uuid);
                        if (target != null && target.isOnline()) {
                            target.getWorld().spawnParticle(Particle.ENTITY_EFFECT, target.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 1.0);
                        }
                    }
                });

                activeMutes.forEach((uuid, expiry) -> {
                    // Placeholder for mute logic
                });

                activeStepAmplifiers.forEach((uuid, expiry) -> {
                    if (now < expiry) {
                        Player target = Bukkit.getPlayer(uuid);
                        if (target != null && target.isOnline() && target.getVelocity().lengthSquared() > 0.01) {
                            for (Player other : Bukkit.getOnlinePlayers()) {
                                if (!other.equals(target) && other.getWorld().equals(target.getWorld())) {
                                    if (other.getLocation().distance(target.getLocation()) <= 25) {
                                        other.playSound(target.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 0.8f, 1f);
                                    }
                                }
                            }
                        }
                    }
                });

                // Kabut / Selimut Asap — spawn smoke visible to others
                activeFogCloud.forEach((uuid, expiry) -> {
                    if (now < expiry) {
                        Player target = Bukkit.getPlayer(uuid);
                        if (target != null && target.isOnline()) {
                            for (Player other : Bukkit.getOnlinePlayers()) {
                                if (other.equals(target)) continue;
                                other.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,
                                    target.getLocation().add(0, 1, 0), 5, 0.8, 0.5, 0.8, 0.03);
                            }
                        }
                    }
                });

                // Radar Sesaat — update compass lodestone to nearest enemy
                activeRadar.forEach((uuid, expiry) -> {
                    if (now < expiry) {
                        Player tracer = Bukkit.getPlayer(uuid);
                        if (tracer != null && tracer.isOnline()) {
                            Player nearest = findNearestEnemy(tracer);
                            if (nearest != null) {
                                ItemStack[] contents = tracer.getInventory().getContents();
                                for (int i = 0; i < contents.length; i++) {
                                    ItemStack itm = contents[i];
                                    if (itm != null && itm.getType() == Material.COMPASS) {
                                        CompassMeta cmeta = (CompassMeta) itm.getItemMeta();
                                        if (cmeta != null) {
                                            cmeta.setLodestone(nearest.getLocation());
                                            cmeta.setLodestoneTracked(false);
                                            itm.setItemMeta(cmeta);
                                            tracer.getInventory().setItem(i, itm);
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    }
                });
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private Player findNearestEnemy(Player p) {
        GameManager gm = plugin.getGameManager();
        Player hunter = gm.getHunter();
        Set<UUID> ghosts = plugin.getGameListener().getGhostPlayers();
        boolean isHunterSide = p.equals(hunter) || (ghosts != null && ghosts.contains(p.getUniqueId()));
        Player nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Player other : gm.getOnlineParticipants()) {
            if (other.equals(p)) continue;
            boolean otherIsHunterSide = other.equals(hunter) || (ghosts != null && ghosts.contains(other.getUniqueId()));
            if (isHunterSide != otherIsHunterSide) {
                double dist = p.getLocation().distanceSquared(other.getLocation());
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = other;
                }
            }
        }
        return nearest;
    }

    private void registerAllQuestions() {
    // Q1
    questionRegistry.add(new Question(1, "Semut Pemberontak", "Monster Kampung",
        new String[]{"/attribute @s minecraft:scale base set 0.15"},
        new String[]{
            "/attribute @s minecraft:scale base set 2.5",
            "/attribute @s minecraft:attack_damage base set 14.0",
            "/attribute @s minecraft:movement_speed base set 0.07"
        }, 0, 0));

    // Q2
    questionRegistry.add(new Question(2, "Peri Kaca", "Tembok Hidup",
        new String[]{
            "/attribute @s minecraft:scale base set 0.4",
            "/attribute @s minecraft:max_health base set 8.0"
        },
        new String[]{
            "/attribute @s minecraft:scale base set 1.7",
            "/attribute @s minecraft:knockback_resistance base set 1.0"
        }, 0, 0));

    // Q3
    questionRegistry.add(new Question(3, "Badan Karet", "Baja Berduri",
        new String[]{
            "/attribute @s minecraft:jump_strength base set 1.6",
            "/attribute @s minecraft:scale base set 0.6"
        },
        new String[]{
            "/attribute @s minecraft:attack_knockback base set 3.5",
            "/attribute @s minecraft:scale base set 1.3"
        }, 0, 0));

    // Q4
    questionRegistry.add(new Question(4, "Sekali Sundut", "Maraton Santuy",
        new String[]{"/effect give @s speed 15 2"},
        new String[]{"/effect give @s speed 90 0"},
        15, 90));

    // Q5
    questionRegistry.add(new Question(5, "Kanguru Pusing", "Siput Zen",
        new String[]{
            "/attribute @s minecraft:jump_strength base set 2.2",
            "/effect give @s nausea 40 0"
        },
        new String[]{
            "/attribute @s minecraft:movement_speed base set 0.04",
            "/effect give @s resistance 40 0"
        }, 40, 40));

    // Q6
    questionRegistry.add(new Question(6, "Lantai Es", "Lem Super",
        new String[]{
            "/attribute @s minecraft:knockback_resistance base set 0.0",
            "/effect give @s speed 40 0"
        },
        new String[]{
            "/attribute @s minecraft:knockback_resistance base set 1.0",
            "/attribute @s minecraft:movement_speed base set 0.08"
        }, 40, 40));

    // Q7
    questionRegistry.add(new Question(7, "Parkour Hantu", "Peluru Sejajar",
        new String[]{
            "/attribute @s minecraft:jump_strength base set 1.8",
            "/effect give @s slow_falling 40 0"
        },
        new String[]{
            "/attribute @s minecraft:movement_speed base set 0.35",
            "/attribute @s minecraft:jump_strength base set 0.3"
        }, 40, 40));

    // Q8
    questionRegistry.add(new Question(8, "Kumis Kucing", "Baju Antipeluru",
        new String[]{}, (p, mgr) -> activeSculkReveal.put(p.getUniqueId(), System.currentTimeMillis() + 40000L), 40,
        new String[]{
            "/attribute @s minecraft:armor base set 12.0",
            "/attribute @s minecraft:armor_toughness base set 6.0"
        }, null, 0));

    // Q9
    questionRegistry.add(new Question(9, "Hidung Pelacak", "Telinga Tuli",
        new String[]{}, (p, mgr) -> activeFootprints.put(p.getUniqueId(), System.currentTimeMillis() + 45000L), 45,
        new String[]{"/effect give @s resistance 45 0"}, null, 45));

    // Q10
    questionRegistry.add(new Question(10, "Parfum Menyengat", "Bayangan Tanpa Suara",
        new String[]{"/effect give @s regeneration 45 0"}, (p, mgr) -> activeAmbientParticles.put(p.getUniqueId(), System.currentTimeMillis() + 45000L), 45,
        new String[]{
            "/effect give @s invisibility 45 0",
            "/attribute @s minecraft:movement_speed base set 0.08"
        }, null, 45));

    // Q11
    questionRegistry.add(new Question(11, "Sepatu Gemuruh", "Kaki Kapas",
        new String[]{"/effect give @s speed 45 0"}, (p, mgr) -> activeStepAmplifiers.put(p.getUniqueId(), System.currentTimeMillis() + 45000L), 45,
        new String[]{"/effect give @s night_vision 45 0"}, (p, mgr) -> activeNoSprints.put(p.getUniqueId(), System.currentTimeMillis() + 45000L), 45));

    // Q12
    questionRegistry.add(new Question(12, "Bisikan Setan", "Radio Mati",
        new String[]{"/effect give @s saturation 50 0"}, (p, mgr) -> activeAmbientAmplifiers.put(p.getUniqueId(), System.currentTimeMillis() + 50000L), 50,
        new String[]{"/effect give @s weakness 50 0"}, null, 50));

    // Q13
    questionRegistry.add(new Question(13, "Petinju Berat", "Petinju Kilat",
        new String[]{"/attribute @s minecraft:attack_damage base set 22.0"},
        new String[]{"/attribute @s minecraft:attack_speed base set 14.0"}, 0, 0));

    // Q14
    questionRegistry.add(new Question(14, "Kulit Naga", "Darah Panas",
        new String[]{
            "/attribute @s minecraft:armor base set 22.0",
            "/attribute @s minecraft:armor_toughness base set 12.0"
        },
        new String[]{
            "/effect give @s strength 40 0",
            "/effect give @s weakness 40 0"
        }, 0, 40));

    // Q15
    questionRegistry.add(new Question(15, "Nyawa Kucing", "Jimat Terakhir",
        new String[]{"/effect give @s regeneration 45 1"},
        new String[]{"/give @s totem_of_undying 1"}, 45, 0));

    // Q16
    questionRegistry.add(new Question(16, "Perisai Hidup", "Pedang Tanpa Sarung",
        new String[]{
            "/effect give @s resistance 40 0",
            "/attribute @s minecraft:knockback_resistance base set 1.0"
        },
        new String[]{
            "/attribute @s minecraft:attack_damage base set 24.0",
            "/attribute @s minecraft:armor base set 0.0"
        }, 40, 0));

    // Q17
    questionRegistry.add(new Question(17, "Kaki Lava", "Insang Ikan",
        new String[]{"/effect give @s fire_resistance 45 0"},
        new String[]{
            "/effect give @s water_breathing 45 0",
            "/effect give @s dolphins_grace 45 0"
        }, 45, 45));

    // Q18
    questionRegistry.add(new Question(18, "Kebal Bisa", "Kebal Guncangan",
        new String[]{"/effect give @s resistance 45 1"},
        new String[]{"/attribute @s minecraft:knockback_resistance base set 0.5"}, 45, 0));

    // Q19
    questionRegistry.add(new Question(19, "Mata Malam", "Lampu Sorot",
        new String[]{"/effect give @s night_vision 60 0"},
        new String[]{
            "/effect give @s glowing 60 0",
            "/effect give @s speed 60 0"
        }, 60, 60));

    // Q20
    questionRegistry.add(new Question(20, "Roulette Posisi", "Portal Darurat",
        new String[]{}, (p, mgr) -> {
            if (!roulettePool.contains(p)) {
                roulettePool.add(p);
            }
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (roulettePool.size() > 1 && roulettePool.contains(p)) {
                    org.bukkit.entity.Player target = null;
                    for (org.bukkit.entity.Player other : roulettePool) {
                        if (!other.equals(p)) {
                            target = other;
                            break;
                        }
                    }
                    if (target != null) {
                        org.bukkit.Location temp = p.getLocation().clone();
                        p.teleport(target.getLocation());
                        target.teleport(temp);
                        roulettePool.remove(p);
                        roulettePool.remove(target);
                        p.sendMessage("§d[SWAP] §fBertukar posisi dengan " + target.getName() + "!");
                        target.sendMessage("§d[SWAP] §fBertukar posisi dengan " + p.getName() + "!");
                    }
                } else if (roulettePool.size() == 1 && roulettePool.contains(p)) {
                    p.sendMessage("§c[INFO] §fHanya kamu yang pilih opsi ini. Tetap di posisi sekarang!");
                    roulettePool.remove(p);
                }
            }, 5L);
        }, 0,
        new String[]{"/give @s ender_pearl 1"}, null, 0));

    // Q22 (21 removed)
    questionRegistry.add(new Question(21, "Kembang Api Party", "Kabut Pelindung",
        new String[]{}, (p, mgr) -> {
            org.bukkit.entity.Firework fw = p.getWorld().spawn(p.getLocation(), org.bukkit.entity.Firework.class);
            org.bukkit.inventory.meta.FireworkMeta fwm = fw.getFireworkMeta();
            fwm.addEffect(org.bukkit.FireworkEffect.builder().withColor(org.bukkit.Color.RED, org.bukkit.Color.YELLOW).with(org.bukkit.FireworkEffect.Type.BALL_LARGE).build());
            fwm.setPower(0);
            fw.setFireworkMeta(fwm);
            fw.detonate();
        }, 0,
        new String[]{}, (p, mgr) -> activeFogCloud.put(p.getUniqueId(), System.currentTimeMillis() + 15000L), 15));

    // Q23
    questionRegistry.add(new Question(22, "Hujan Panah", "Zirah Kilat",
        new String[]{
            "/give @s bow 1",
            "/give @s arrow 10"
        },
        new String[]{
            "/give @s iron_helmet 1",
            "/give @s iron_chestplate 1",
            "/give @s iron_leggings 1",
            "/give @s iron_boots 1"
        }, 0, 0));

    // Q24
    questionRegistry.add(new Question(23, "Umpan Cantik", "Tameng Squad",
        new String[]{"/effect give @s speed 30 1"}, (p, mgr) -> {
            org.bukkit.entity.Player randomOther = null;
            java.util.List<org.bukkit.entity.Player> others = new java.util.ArrayList<>();
            for (org.bukkit.entity.Player part : plugin.getGameManager().getOnlineParticipants()) {
                if (!part.equals(p) && part.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                    others.add(part);
                }
            }
            if (!others.isEmpty()) {
                randomOther = others.get(new java.util.Random().nextInt(others.size()));
                SilentCommands.run("effect give " + randomOther.getName() + " glowing 30 0");
                p.sendMessage("§e[UMPAN] §f" + randomOther.getName() + " menjadi umpan glowing!");
            }
        }, 30,
        new String[]{}, (p, mgr) -> {
            for (org.bukkit.entity.Player part : plugin.getGameManager().getOnlineParticipants()) {
                if (part.getLocation().distanceSquared(p.getLocation()) <= 100) {
                    part.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.RESISTANCE, 30*20, 0));
                }
            }
        }, 30));

    // Q25
    questionRegistry.add(new Question(24, "Sinyal Nekat", "Kode Diam",
        new String[]{}, (p, mgr) -> {
            for (org.bukkit.entity.Player other : org.bukkit.Bukkit.getOnlinePlayers()) {
                other.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SPAWN, 1f, 1f);
                other.spawnParticle(org.bukkit.Particle.EXPLOSION, p.getLocation().add(0, 1, 0), 1);
            }
        }, 0,
        new String[]{"/effect give @s luck 45 0"}, null, 45));

    // Q26
    questionRegistry.add(new Question(25, "Kompas Nasib", "Mata Elang",
        new String[]{}, (p, mgr) -> {
            org.bukkit.inventory.ItemStack compass = new org.bukkit.inventory.ItemStack(org.bukkit.Material.COMPASS);
            org.bukkit.entity.Player nearest = findNearestEnemy(p);
            if (nearest != null) {
                org.bukkit.inventory.meta.CompassMeta cm = (org.bukkit.inventory.meta.CompassMeta) compass.getItemMeta();
                if (cm != null) {
                    cm.setLodestone(nearest.getLocation());
                    cm.setLodestoneTracked(false);
                    compass.setItemMeta(cm);
                }
            }
            p.getInventory().addItem(compass);
        }, 0,
        new String[]{
            "/effect give @s night_vision 45 0",
            "/effect give @s speed 45 0"
        }, null, 45));

    // Q27
    questionRegistry.add(new Question(26, "Duri Balas", "Kulit Ular",
        new String[]{}, (p, mgr) -> activeThorns.put(p.getUniqueId(), System.currentTimeMillis() + 40000L), 40,
        new String[]{
            "/attribute @s minecraft:knockback_resistance base set 1.0",
            "/effect give @s resistance 40 0"
        }, null, 40));

    // Q28
    questionRegistry.add(new Question(27, "Kutukan Acak", "Berkah Acak",
        new String[]{}, (p, mgr) -> {
            String[] curses = {"blindness", "slowness", "weakness", "nausea", "mining_fatigue"};
            String curse = curses[new java.util.Random().nextInt(curses.length)];
            SilentCommands.run("effect give " + p.getName() + " " + curse + " 40 0");
        }, 40,
        new String[]{}, (p, mgr) -> {
            String[] blessings = {"speed", "jump_boost", "haste", "regeneration", "resistance"};
            String bless = blessings[new java.util.Random().nextInt(blessings.length)];
            SilentCommands.run("effect give " + p.getName() + " " + bless + " 40 0");
        }, 40));

    // Q29
    questionRegistry.add(new Question(28, "Modal Nekat", "Rezeki Nomplok",
        new String[]{
            "/give @s golden_apple 1",
            "/effect give @s speed 30 0"
        },
        new String[]{
            "/give @s ender_pearl 2",
            "/give @s golden_apple 1"
        }, 30, 0));

    // Q30
    questionRegistry.add(new Question(29, "Panggung Sendiri", "Penonton Diam",
        new String[]{
            "/effect give @s glowing 50 0",
            "/attribute @s minecraft:max_health base set 30.0",
            "/attribute @s minecraft:armor base set 10.0"
        },
        new String[]{
            "/effect give @s invisibility 50 0",
            "/attribute @s minecraft:max_health base set 14.0"
        }, 50, 50));

    // Q31
    questionRegistry.add(new Question(30, "Kabur Berkali-kali", "Nyawa Cadangan",
        new String[]{"/give @s ender_pearl 3"},
        new String[]{"/give @s totem_of_undying 1"}, 0, 0));

    // Q32
    questionRegistry.add(new Question(31, "Selimut Asap", "Tembus Pandang",
        new String[]{"/give @s firework_star{display:{Name:'{\"text\":\"Smoke Bomb\"}'}} 3"}, null, 0,
        new String[]{}, (p, mgr) -> {
            p.setGameMode(org.bukkit.GameMode.SPECTATOR);
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                    p.setGameMode(org.bukkit.GameMode.ADVENTURE);
                }
            }, 60L);
        }, 3));

    // Q33
    questionRegistry.add(new Question(32, "Baju Zirah Lengkap", "Sekali Tembak Meledak",
        new String[]{
            "/give @s diamond_helmet 1",
            "/give @s diamond_chestplate 1",
            "/give @s diamond_leggings 1",
            "/give @s diamond_boots 1"
        }, null, 0,
        new String[]{}, (p, mgr) -> {
            org.bukkit.inventory.ItemStack crossbow = new org.bukkit.inventory.ItemStack(org.bukkit.Material.CROSSBOW);
            org.bukkit.inventory.meta.CrossbowMeta cbm = (org.bukkit.inventory.meta.CrossbowMeta) crossbow.getItemMeta();
            if (cbm != null) {
                org.bukkit.inventory.ItemStack firework = new org.bukkit.inventory.ItemStack(org.bukkit.Material.FIREWORK_ROCKET);
                org.bukkit.inventory.meta.FireworkMeta fm = (org.bukkit.inventory.meta.FireworkMeta) firework.getItemMeta();
                fm.addEffect(org.bukkit.FireworkEffect.builder().withColor(org.bukkit.Color.RED).with(org.bukkit.FireworkEffect.Type.BALL_LARGE).build());
                fm.setPower(2);
                firework.setItemMeta(fm);
                cbm.addChargedProjectile(firework);
                crossbow.setItemMeta(cbm);
            }
            p.getInventory().addItem(crossbow);
        }, 0));

    // Q34
    questionRegistry.add(new Question(33, "Dorongan Angin", "Busur Jatah",
        new String[]{"/give @s wind_charge 5"}, null, 0,
        new String[]{}, (p, mgr) -> {
            org.bukkit.inventory.ItemStack bow = new org.bukkit.inventory.ItemStack(org.bukkit.Material.BOW);
            org.bukkit.inventory.meta.Damageable dmg = (org.bukkit.inventory.meta.Damageable) bow.getItemMeta();
            if (dmg != null) {
                dmg.setDamage(384 - 3);
                bow.setItemMeta((org.bukkit.inventory.meta.ItemMeta) dmg);
            }
            p.getInventory().addItem(bow);
            p.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.ARROW, 3));
        }, 0));

    // Q35
    questionRegistry.add(new Question(34, "Radar Sesaat", "Nebeng Paksa",
        new String[]{}, (p, mgr) -> {
            activeRadar.put(p.getUniqueId(), System.currentTimeMillis() + 10000L);
            p.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.COMPASS));
        }, 10,
        new String[]{}, (p, mgr) -> {
            java.util.List<org.bukkit.entity.Player> others = new java.util.ArrayList<>();
            for (org.bukkit.entity.Player part : plugin.getGameManager().getOnlineParticipants()) {
                if (!part.equals(p) && part.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                    others.add(part);
                }
            }
            if (!others.isEmpty()) {
                org.bukkit.entity.Player target = others.get(new java.util.Random().nextInt(others.size()));
                p.teleport(target.getLocation());
                p.sendMessage("§e[TELEPORT] §fKamu nebeng paksa ke " + target.getName() + "!");
            }
        }, 0));

    // Q36
    questionRegistry.add(new Question(35, "Laper", "Sekarat",
        new String[]{}, (p, mgr) -> p.setFoodLevel(3), 0,
        new String[]{}, (p, mgr) -> p.setHealth(1.5), 0));

    }
}
