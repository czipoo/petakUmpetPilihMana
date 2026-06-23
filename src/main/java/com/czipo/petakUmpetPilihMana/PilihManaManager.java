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

public class PilihManaManager {
    private final PetakUmpetPilihMana plugin;
    private final Random random = new Random();

    // Active WYR cycle state
    private boolean choiceActive = false;
    private Question currentQuestion = null;
    private final Map<UUID, Integer> playerChoices = new HashMap<>();
    private final Map<UUID, Question> activeTestQuestions = new HashMap<>();
    private final Set<UUID> frozenPlayers = new HashSet<>();
    private GameLoopTask activeGameLoopTask = null;

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

        public Question setCustomA(CustomEffect custom) { this.customA = custom; return this; }
        public Question setCustomB(CustomEffect custom) { this.customB = custom; return this; }
    }

    public interface CustomEffect {
        void execute(Player p, PilihManaManager mgr);
    }

    public boolean isChoiceActive() { return choiceActive; }
    public Map<UUID, Integer> getPlayerChoices() { return playerChoices; }
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

        showWaitingDialog(p);
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

    public void refreshOpenChoiceDialogs(int countdown) {
        if (!choiceActive || currentQuestion == null) return;

        for (Player p : plugin.getGameManager().getOnlineParticipants()) {
            if (!playerChoices.containsKey(p.getUniqueId())) {
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

    private void showWaitingDialog(Player p) {
        Dialog waitingDialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Tunggu player lain memilih"))
                        .canCloseWithEscape(false)
                        .build())
                .type(DialogType.notice())
        );
        p.showDialog(waitingDialog);
    }

    public Map<UUID, Question> getActiveTestQuestions() { return activeTestQuestions; }

    public void triggerTestQuestion(Player p, int questionId) {
        Question found = null;
        for (Question q : questionRegistry) {
            if (q.id == questionId) {
                found = q;
                break;
            }
        }

        if (found == null) {
            p.sendMessage("§cPertanyaan nomor " + questionId + " tidak ditemukan (pilih 1-25).");
            return;
        }

        final Question finalFound = found;

        Dialog dialog = Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(Component.text("Pilih Mana"))
                .canCloseWithEscape(false)
                .build()
            )
            .type(DialogType.confirmation(
                ActionButton.builder(Component.text("§cPilih Opsi Kiri"))
                    .action(DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player player) {
                            registerTestChoice(player, 1, finalFound);
                        }
                    }, ClickCallback.Options.builder().build()))
                    .build(),
                ActionButton.builder(Component.text("§bPilih Opsi Kanan"))
                    .action(DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player player) {
                            registerTestChoice(player, 2, finalFound);
                        }
                    }, ClickCallback.Options.builder().build()))
                    .build()
            ))
        );

        p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1f, 1f);
        p.sendTitle("§6§lPILIH MANA!", "§7Pilih salah satu opsi di dialog", 5, 60, 10);
        p.showDialog(dialog);
    }

    public void registerTestChoice(Player p, int choice, Question q) {
        String selectionText = (choice == 1) ? q.optionA : q.optionB;
        p.sendMessage("§a[TEST PILIHAN] §fKamu memilih: §e" + selectionText);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);

        applyEffect(p, q, choice == 1);
    }

    public void triggerPilihMana(GameLoopTask task) {
        if (questionRegistry.isEmpty()) return;

        this.activeGameLoopTask = task;
        currentQuestion = questionRegistry.get(random.nextInt(questionRegistry.size()));
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
                p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, false, false));
                p.sendMessage("§c§l[PENALTI] §fKamu tidak memilih! Efek glowing diberikan selama 5 detik.");
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1f);
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

        p.sendMessage("§a[EFEK] §fMenerapkan efek pilihanmu selama §e" + duration + "s§f!");

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
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmdProcessed);
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
                        p.sendMessage("§e[INFO] §fEfek pilihan Anda telah habis.");
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

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "disguiseplayer " + p.getName() + " " + finalDisguise);
        p.performCommand("disguise " + finalDisguise);

        if (duration > 0) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (p.isOnline()) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "undisguiseplayer " + p.getName());
                        p.performCommand("undisguise");
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

        for (Player p : plugin.getGameManager().getParticipants()) {
            if (!p.isOnline()) continue;
            resetPlayerEffects(p);
            clearPotionEffects(p);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "undisguiseplayer " + p.getName());
            p.performCommand("undisguise");
        }
    }

    public void resetAllActiveEffects() {
        resetParticipantEffects();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.getGameManager().isParticipant(p)) continue;
            resetAttributesToDefault(p);
            clearPotionEffects(p);
        }
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
                "minecraft:water_movement_efficiency 0.0"
        };
        for (String attr : attributes) {
            String[] split = attr.split(" ");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "attribute " + name + " " + split[0] + " base set " + split[1]);
        }
    }

    public Set<UUID> getActiveADSwaps() { return activeADSwaps.keySet(); }
    public Set<UUID> getActiveWSSwaps() { return activeWSSwaps.keySet(); }
    public Set<UUID> getActiveNoSprints() { return activeNoSprints.keySet(); }
    public Set<UUID> getActiveSnowflakeTrails() { return activeSnowflakeTrails.keySet(); }
    public Set<UUID> getActiveFlameTrails() { return activeFlameTrails.keySet(); }

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
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    public void handleStepEvents(Player p, Location to) {
        // Placeholder for step event handling
    }

    private void registerAllQuestions() {
        // 25 QUESTIONS - Core effects yang paling penting

        // Disguise-based questions (Block, Mob, Special) - 7 questions
        Question q1 = new Question(1, "Menyatu dengan tanah", "Menyatu dengan dinding bata",
                new String[]{"disguise as minecraft:dirt"},
                new String[]{"disguise as minecraft:stone_bricks"}, 45, 45);
        questionRegistry.add(q1);

        Question q2 = new Question(2, "Wujud kucing kecil", "Wujud kelinci melompat",
                new String[]{"disguise as cat"},
                new String[]{"disguise as rabbit"}, 45, 45);
        questionRegistry.add(q2);

        Question q3 = new Question(3, "Wujud kotak harta karun", "Wujud rak buku",
                new String[]{"disguise as chest"},
                new String[]{"disguise as bookshelf"}, 45, 45);
        questionRegistry.add(q3);

        Question q4 = new Question(4, "Wujud creeper berbahaya", "Wujud villager damai",
                new String[]{"disguise as creeper"},
                new String[]{"disguise as villager"}, 45, 45);
        questionRegistry.add(q4);

        Question q5 = new Question(5, "Wujud phantom terbang", "Wujud kelelawar mungil",
                new String[]{"disguise as phantom"},
                new String[]{"disguise as bat"}, 45, 45);
        questionRegistry.add(q5);

        Question q6 = new Question(6, "Wujud penyihir", "Wujud squid bercahaya",
                new String[]{"disguise as witch"},
                new String[]{"disguise as glow_squid"}, 45, 45);
        questionRegistry.add(q6);

        Question q7 = new Question(7, "Wujud zombi hijau", "Wujud skeleton putih",
                new String[]{"disguise as zombie"},
                new String[]{"disguise as skeleton"}, 45, 45);
        questionRegistry.add(q7);

        // Size change questions - 3 questions
        Question q8 = new Question(8, "Tubuh membesar 2x", "Tubuh mengecil 50%",
                new String[]{"/attribute @s minecraft:scale base set 2.0"},
                new String[]{"/attribute @s minecraft:scale base set 0.5"}, 40, 40);
        questionRegistry.add(q8);

        Question q9 = new Question(9, "Tubuh mengecil drastis", "Tubuh membesar besar",
                new String[]{"/attribute @s minecraft:scale base set 0.25"},
                new String[]{"/attribute @s minecraft:scale base set 3.0"}, 40, 40);
        questionRegistry.add(q9);

        Question q10 = new Question(10, "Tetap normal tapi terlihat aneh", "Tetap normal tapi bergerak aneh",
                new String[]{"/attribute @s minecraft:scale base set 0.9"},
                new String[]{"/attribute @s minecraft:movement_speed base set 0.08"}, 40, 40);
        questionRegistry.add(q10);

        // Jump and gravity questions - 3 questions
        Question q11 = new Question(11, "Lompat sangat tinggi", "Jatuh perlahan seperti balon",
                new String[]{"/attribute @s minecraft:jump_strength base set 2.0"},
                new String[]{"/attribute @s minecraft:gravity base set 0.01"}, 40, 40);
        questionRegistry.add(q11);

        Question q12 = new Question(12, "Gravitasi terbalik", "Gravitasi ke samping",
                new String[]{"/attribute @s minecraft:gravity base set -0.08"},
                new String[]{"/attribute @s minecraft:gravity base set 0.15"}, 35, 35);
        questionRegistry.add(q12);

        Question q13 = new Question(13, "Tidak pernah terluka jatuh", "Gugur seperti batu bata",
                new String[]{"/attribute @s minecraft:safe_fall_distance base set 100.0"},
                new String[]{"/attribute @s minecraft:fall_damage_multiplier base set 5.0"}, 40, 40);
        questionRegistry.add(q13);

        // Speed and movement questions - 2 questions
        Question q14 = new Question(14, "Berlari super cepat", "Bergerak seperti siput",
                new String[]{"/attribute @s minecraft:movement_speed base set 0.5"},
                new String[]{"/attribute @s minecraft:movement_speed base set 0.05"}, 40, 40);
        questionRegistry.add(q14);

        Question q15 = new Question(15, "Bisa naik blok tinggi", "Bisa jangkau jauh",
                new String[]{"/attribute @s minecraft:step_height base set 3.0"},
                new String[]{"/attribute @s minecraft:entity_interaction_range base set 8.0"}, 40, 40);
        questionRegistry.add(q15);

        // Combat and strength questions - 2 questions
        Question q16 = new Question(16, "Pukulan super kuat", "Pukulan sangat cepat",
                new String[]{"/attribute @s minecraft:attack_damage base set 25.0"},
                new String[]{"/attribute @s minecraft:attack_speed base set 16.0"}, 40, 40);
        questionRegistry.add(q16);

        Question q17 = new Question(17, "Tahan dari smackdown", "Tahan dari api",
                new String[]{"/attribute @s minecraft:knockback_resistance base set 1.0"},
                new String[]{"/effect give @s fire_resistance 45 0"}, 45, 45);
        questionRegistry.add(q17);

        // Perception and visual questions - 2 questions
        Question q18 = new Question(18, "Menjadi tak terlihat", "Bersinar terang",
                new String[]{"/effect give @s invisibility 30 0"},
                new String[]{"/effect give @s glowing 35 0"}, 30, 35);
        questionRegistry.add(q18);

        Question q19 = new Question(19, "Menjadi buta total", "Mabuk kepala keras",
                new String[]{"/effect give @s blindness 45 0"},
                new String[]{"/effect give @s nausea 45 0"}, 45, 45);
        questionRegistry.add(q19);

        // Utility questions - 1 question
        Question q20 = new Question(20, "Melihat dengan jelas", "Hidup di dalam air",
                new String[]{"/effect give @s night_vision 40 0"},
                new String[]{"/effect give @s water_breathing 45 0", "/effect give @s dolphins_grace 45 0"}, 40, 45);
        questionRegistry.add(q20);

        // Combination effects (21-25)
        Question q21 = new Question(21, "Lompat tinggi + jatuh aman", "Cepat + tanpa gravitasi",
                new String[]{"/attribute @s minecraft:jump_strength base set 2.5", "/attribute @s minecraft:safe_fall_distance base set 50.0"},
                new String[]{"/attribute @s minecraft:movement_speed base set 0.4", "/attribute @s minecraft:gravity base set 0.02"}, 40, 40);
        questionRegistry.add(q21);

        Question q22 = new Question(22, "Tak terlihat + lambat", "Bersinar + cepat",
                new String[]{"/effect give @s invisibility 40 0", "/effect give @s slowness 40 1"},
                new String[]{"/effect give @s glowing 40 0", "/effect give @s speed 40 2"}, 40, 40);
        questionRegistry.add(q22);

        Question q23 = new Question(23, "Kuat + tahan banting", "Cepat + lompat tinggi",
                new String[]{"/attribute @s minecraft:attack_damage base set 20.0", "/attribute @s minecraft:knockback_resistance base set 1.0"},
                new String[]{"/attribute @s minecraft:movement_speed base set 0.35", "/attribute @s minecraft:jump_strength base set 1.8"}, 40, 40);
        questionRegistry.add(q23);

        Question q24 = new Question(24, "Besar + lambat", "Kecil + cepat",
                new String[]{"/attribute @s minecraft:scale base set 2.5", "/effect give @s slowness 40 2"},
                new String[]{"/attribute @s minecraft:scale base set 0.3", "/effect give @s speed 40 2"}, 40, 40);
        questionRegistry.add(q24);

        Question q25 = new Question(25, "Kekuatan maksimal", "Regenerasi diri sendiri",
                new String[]{"/effect give @s strength 40 2"},
                new String[]{"/effect give @s regeneration 40 1"}, 40, 40);
        questionRegistry.add(q25);
    }
}
















