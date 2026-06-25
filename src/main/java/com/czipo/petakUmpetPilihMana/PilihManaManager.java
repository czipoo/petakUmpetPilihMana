package com.czipo.petakUmpetPilihMana;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

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
    private final Set<UUID> frozenPlayers = new HashSet<>();
    private GameLoopTask activeGameLoopTask = null;

    // Override question untuk ronde berikutnya (dari /setquestion saat permainan)
    private Integer overrideNextQuestionId = null;

    // Standalone WYR task (untuk /setquestion di luar permainan)
    private BukkitTask standaloneWyrTask = null;

    // Active movement and ticker effects (Player UUID -> expiration time in ms)
    private final Map<UUID, Long> activeADSwaps = new HashMap<>();
    private final Map<UUID, Long> activeWSSwaps = new HashMap<>();
    private final Map<UUID, Long> activeFootprints = new HashMap<>();
    private final Map<UUID, Long> activeHighlightRadius = new HashMap<>();
    private final Map<UUID, Long> activeRevealRadius = new HashMap<>();
    private final Map<UUID, Long> activeSculkReveal = new HashMap<>();
    private final Map<UUID, Long> activeStepAmplifiers = new HashMap<>();
    private final Map<UUID, Long> activeAmbientAmplifiers = new HashMap<>();
    private final Map<UUID, Long> activeAmbientParticles = new HashMap<>();
    private final Map<UUID, Long> activeNoSprints = new HashMap<>();
    private final Map<UUID, org.bukkit.entity.BlockDisplay> activeBlockDisguises = new HashMap<>();

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

    // Custom Effect: Random Teleport (TYPE 0 - Swap All Players)
    public static CustomEffect randomTeleportAll() {
        return (p, mgr) -> {
            GameManager gm = mgr.plugin.getGameManager();
            List<Player> allPlayers = new ArrayList<>(gm.getOnlineParticipants());
            if (gm.getHunter() != null && gm.getHunter().isOnline()) {
                allPlayers.add(gm.getHunter());
            }

            if (allPlayers.size() < 2) return;

            // Collect all locations
            List<org.bukkit.Location> locations = new ArrayList<>();
            for (Player player : allPlayers) {
                locations.add(player.getLocation().clone());
            }

            // Shuffle locations
            java.util.Collections.shuffle(locations, mgr.random);

            // Teleport each player to a random location
            for (int i = 0; i < allPlayers.size(); i++) {
                allPlayers.get(i).teleport(locations.get(i));
                allPlayers.get(i).playSound(allPlayers.get(i).getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            }

            Bukkit.broadcastMessage("§6§l[TELEPORT] §eSemua pemain bertukar posisi secara acak!");
        };
    }

    // Custom Effect: Random Teleport Hiders Only (TYPE 1)
    public static CustomEffect randomTeleportHiders() {
        return (p, mgr) -> {
            GameManager gm = mgr.plugin.getGameManager();
            List<Player> hiders = new ArrayList<>(gm.getOnlineParticipants());
            if (gm.getHunter() != null) {
                hiders.remove(gm.getHunter());
            }

            if (hiders.size() < 2) return;

            List<org.bukkit.Location> locations = new ArrayList<>();
            for (Player hider : hiders) {
                locations.add(hider.getLocation().clone());
            }

            java.util.Collections.shuffle(locations, mgr.random);

            for (int i = 0; i < hiders.size(); i++) {
                hiders.get(i).teleport(locations.get(i));
                hiders.get(i).playSound(hiders.get(i).getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            }

            Bukkit.broadcastMessage("§6§l[TELEPORT] §eHiders bertukar posisi satu sama lain!");
        };
    }

    // Custom Effect: Block Disguise (multiplayer compatible)
    public static CustomEffect blockDisguise(org.bukkit.Material blockType, int duration) {
        return (p, mgr) -> {
            org.bukkit.Location loc = p.getLocation().add(0, 1, 0);
            org.bukkit.entity.BlockDisplay display = p.getWorld().spawn(loc, org.bukkit.entity.BlockDisplay.class);
            display.setBlock(blockType.createBlockData());
            display.setTransformation(new org.bukkit.util.Transformation(
                new org.bukkit.joml.Vector3f(0, -1, 0),
                new org.bukkit.joml.Quaternionf(),
                new org.bukkit.joml.Vector3f(1, 1, 1),
                new org.bukkit.joml.Quaternionf()
            ));

            mgr.activeBlockDisguises.put(p.getUniqueId(), display);
            p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, duration * 20, 0, false, false));

            new BukkitRunnable() {
                @Override
                public void run() {
                    org.bukkit.entity.BlockDisplay d = mgr.activeBlockDisguises.remove(p.getUniqueId());
                    if (d != null && d.isValid()) {
                        d.remove();
                    }
                    if (p.isOnline()) {
                        p.removePotionEffect(PotionEffectType.INVISIBILITY);
                    }
                }
            }.runTaskLater(mgr.plugin, duration * 20L);

            p.sendMessage("§a[EFEK] §fKamu terlihat seperti " + blockType.name() + " selama §e" + duration + "s§f!");
        };
    }

    // Custom Effect: Give one-time use items
    public static CustomEffect giveItems(org.bukkit.Material... items) {
        return (p, mgr) -> {
            for (org.bukkit.Material item : items) {
                ItemStack stack = new ItemStack(item, 1);
                p.getInventory().addItem(stack);
            }
            p.sendMessage("§a[EFEK] §fKamu mendapatkan item sekali pakai!");
        };
    }

    // Custom Effect: Summon Firework
    public static CustomEffect summonFirework() {
        return (p, mgr) -> {
            org.bukkit.entity.Firework fw = p.getWorld().spawn(p.getLocation(), org.bukkit.entity.Firework.class);
            org.bukkit.inventory.meta.FireworkMeta meta = (org.bukkit.inventory.meta.FireworkMeta) fw.getFireworkMeta();
            meta.addEffect(org.bukkit.FireworkEffect.builder()
                .withColor(org.bukkit.Color.RED, org.bukkit.Color.GREEN, org.bukkit.Color.BLUE)
                .with(org.bukkit.FireworkEffect.Type.BALL)
                .build());
            meta.setPower(1);
            fw.setFireworkMeta(meta);
            p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
        };
    }

    public boolean isChoiceActive() { return choiceActive; }
    public Set<UUID> getFrozenPlayers() { return frozenPlayers; }
    public boolean isPlayerFrozen(Player p) { return choiceActive && frozenPlayers.contains(p.getUniqueId()); }

    // === Override Question Logic ===

    /**
     * Set pertanyaan override untuk ronde WYR berikutnya.
     * @return true jika question id valid
     */
    public boolean setNextQuestion(int questionId) {
        for (Question q : questionRegistry) {
            if (q.id == questionId) {
                this.overrideNextQuestionId = questionId;
                return true;
            }
        }
        return false;
    }

    // === Choice Registration ===

    public void registerChoice(Player p, int choice) {
        if (!choiceActive || currentQuestion == null) return;
        if (!plugin.getGameManager().isParticipant(p) && !isStandaloneMode()) return;
        if (playerChoices.containsKey(p.getUniqueId())) return;

        playerChoices.put(p.getUniqueId(), choice);
        String selectionText = (choice == 1) ? currentQuestion.optionA : currentQuestion.optionB;
        p.sendMessage("§a[PILIHAN] §fKamu memilih: §e" + selectionText);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.5f);

        int countdown = activeGameLoopTask != null ? activeGameLoopTask.getWyrCountdown() : 15;
        showWaitingDialog(p, countdown);
        checkAllSelected();
    }

    private boolean isStandaloneMode() {
        return standaloneWyrTask != null;
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
        List<Player> targets;
        if (isStandaloneMode()) {
            targets = new ArrayList<>(Bukkit.getOnlinePlayers().stream()
                    .filter(p -> frozenPlayers.contains(p.getUniqueId()) || playerChoices.containsKey(p.getUniqueId()))
                    .toList());
        } else {
            targets = plugin.getGameManager().getOnlineParticipants();
        }

        if (targets.isEmpty()) return;

        for (Player p : targets) {
            if (!playerChoices.containsKey(p.getUniqueId())) {
                return;
            }
        }

        // Semua sudah memilih
        if (activeGameLoopTask != null) {
            activeGameLoopTask.endWyrEarly();
        } else if (isStandaloneMode()) {
            endStandaloneWyr();
        }
    }

    // === Dialog Display ===

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

    // === Standalone WYR (di luar permainan, /setquestion) ===

    public void triggerStandaloneQuestion(int questionId, CommandSender sender) {
        Question found = null;
        for (Question q : questionRegistry) {
            if (q.id == questionId) {
                found = q;
                break;
            }
        }

        if (found == null) {
            sender.sendMessage("§cPertanyaan ke " + questionId + " tidak ditemukan (pilih 1-44).");
            return;
        }

        if (choiceActive) {
            sender.sendMessage("§cSedang ada pertanyaan aktif! Tunggu sampai selesai.");
            return;
        }

        List<Player> targets = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (targets.isEmpty()) {
            sender.sendMessage("§cTidak ada player online!");
            return;
        }

        currentQuestion = found;
        playerChoices.clear();
        choiceActive = true;
        frozenPlayers.clear();

        final int maxCountdown = 15;

        for (Player p : targets) {
            frozenPlayers.add(p.getUniqueId());
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1f, 1f);
            showChoiceDialog(p, maxCountdown);
        }

        sender.sendMessage("§a[SET] §fPertanyaan Q" + questionId + " ditampilkan ke " + targets.size() + " player!");

        // Standalone countdown timer
        standaloneWyrTask = new BukkitRunnable() {
            int countdown = maxCountdown;

            @Override
            public void run() {
                if (!choiceActive) {
                    this.cancel();
                    standaloneWyrTask = null;
                    return;
                }

                if (countdown <= 0) {
                    this.cancel();
                    standaloneWyrTask = null;
                    endStandaloneWyr();
                    return;
                }

                // Refresh dialog countdown untuk semua target
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!frozenPlayers.contains(p.getUniqueId()) && !playerChoices.containsKey(p.getUniqueId())) {
                        continue;
                    }
                    if (playerChoices.containsKey(p.getUniqueId())) {
                        showWaitingDialog(p, countdown);
                    } else {
                        showChoiceDialog(p, countdown);
                    }
                }

                if (countdown <= 5) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (frozenPlayers.contains(p.getUniqueId()) || playerChoices.containsKey(p.getUniqueId())) {
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f + (6 - countdown) * 0.1f);
                        }
                    }
                }

                countdown--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void endStandaloneWyr() {
        if (!choiceActive) return;

        Question question = currentQuestion;
        Map<UUID, Integer> finalChoices = new HashMap<>(playerChoices);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (frozenPlayers.contains(p.getUniqueId()) || finalChoices.containsKey(p.getUniqueId())) {
                p.closeDialog();
            }
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
                p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, false, false));
                p.sendMessage("§c§l[PENALTI] §fKamu tidak memilih! Efek glowing diberikan selama 5 detik.");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            }
        }

        frozenPlayers.clear();
        playerChoices.clear();
        choiceActive = false;
        currentQuestion = null;

        if (standaloneWyrTask != null) {
            standaloneWyrTask.cancel();
            standaloneWyrTask = null;
        }
    }

    // === In-game WYR ===

    public void triggerPilihMana(GameLoopTask task) {
        if (questionRegistry.isEmpty()) return;

        this.activeGameLoopTask = task;

        // Cek apakah ada override question dari mod
        if (overrideNextQuestionId != null) {
            Question overrideQ = null;
            for (Question q : questionRegistry) {
                if (q.id == overrideNextQuestionId) {
                    overrideQ = q;
                    break;
                }
            }
            if (overrideQ != null) {
                currentQuestion = overrideQ;
            } else {
                currentQuestion = questionRegistry.get(random.nextInt(questionRegistry.size()));
            }
            overrideNextQuestionId = null; // Reset setelah dipakai
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
                p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, false, false));
                p.sendMessage("§c§l[PENALTI] §fKamu tidak memilih! Efek glowing diberikan selama 5 detik.");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            }
        }

        frozenPlayers.clear();
        playerChoices.clear();
        choiceActive = false;
        currentQuestion = null;
        activeGameLoopTask = null;
    }

    // === Effect Application ===

    private void applyEffect(Player p, Question q, boolean isOptionA) {
        String[] cmds = isOptionA ? q.cmdA : q.cmdB;
        int duration = isOptionA ? q.durationA : q.durationB;
        CustomEffect custom = isOptionA ? q.customA : q.customB;

        p.sendMessage("§a[EFEK] §fMenerapkan efek pilihanmu selama §e" + duration + "s§f!");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);

        if (cmds != null) {
            for (String cmd : cmds) {
                String cmdProcessed = cmd.replace("@s", p.getName()).trim();
                if (cmdProcessed.startsWith("/")) {
                    cmdProcessed = cmdProcessed.substring(1);
                }
                SilentCommands.run(cmdProcessed);
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
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                    }
                }
            }.runTaskLater(plugin, duration * 20L);
        }
    }

    // === Effect Management ===

    public void resetPlayerEffects(Player p) {
        UUID uuid = p.getUniqueId();
        activeADSwaps.remove(uuid);
        activeWSSwaps.remove(uuid);
        activeFootprints.remove(uuid);
        activeHighlightRadius.remove(uuid);
        activeRevealRadius.remove(uuid);
        activeSculkReveal.remove(uuid);
        activeStepAmplifiers.remove(uuid);
        activeAmbientAmplifiers.remove(uuid);
        activeAmbientParticles.remove(uuid);
        activeNoSprints.remove(uuid);

        // Remove block disguise if exists
        org.bukkit.entity.BlockDisplay display = activeBlockDisguises.remove(uuid);
        if (display != null && display.isValid()) {
            display.remove();
        }

        resetAttributesToDefault(p);
    }

    public void resetParticipantEffects() {
        activeADSwaps.clear();
        activeWSSwaps.clear();
        activeFootprints.clear();
        activeHighlightRadius.clear();
        activeRevealRadius.clear();
        activeSculkReveal.clear();
        activeStepAmplifiers.clear();
        activeAmbientAmplifiers.clear();
        activeAmbientParticles.clear();
        activeNoSprints.clear();

        // Remove all block disguises
        for (org.bukkit.entity.BlockDisplay display : activeBlockDisguises.values()) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        activeBlockDisguises.clear();

        for (Player p : plugin.getGameManager().getParticipants()) {
            if (!p.isOnline()) continue;
            resetPlayerEffects(p);
            clearPotionEffects(p);
            SilentCommands.run("undisguiseplayer " + p.getName());
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
            SilentCommands.run("attribute " + name + " " + split[0] + " base set " + split[1]);
        }
    }

    public Set<UUID> getActiveADSwaps() { return activeADSwaps.keySet(); }
    public Set<UUID> getActiveWSSwaps() { return activeWSSwaps.keySet(); }
    public Set<UUID> getActiveNoSprints() { return activeNoSprints.keySet(); }

    // === Periodic Ticker ===

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

    // === Question Registry ===

    private void registerAllQuestions() {

    // ═══════════════════════════════════════════════════════════
    // Q1–Q8 : SIZE CHANGE
    // ═══════════════════════════════════════════════════════════

    // Q1 - Besar vs Kecil (standar)
    Question q1 = new Question(1, "Tubuh membesar 2x", "Tubuh mengecil 50%",
        new String[]{"/attribute @s minecraft:scale base set 2.0"},
        new String[]{"/attribute @s minecraft:scale base set 0.5"}, 40, 40);
    questionRegistry.add(q1);

    // Q2 - Ekstrem kecil vs Ekstrem besar
    Question q2 = new Question(2, "Tubuh mengecil drastis", "Tubuh membesar besar",
        new String[]{"/attribute @s minecraft:scale base set 0.25"},
        new String[]{"/attribute @s minecraft:scale base set 3.0"}, 40, 40);
    questionRegistry.add(q2);

    // Q3 - Tipis vs Besar + damage
    Question q3 = new Question(3, "Tubuh setipis kertas", "Tubuh selebar pintu",
        new String[]{"/attribute @s minecraft:scale base set 0.5",
                     "/attribute @s minecraft:sneaking_speed base set 0.3"},
        new String[]{"/attribute @s minecraft:scale base set 1.8",
                     "/attribute @s minecraft:attack_damage base set 12.0"}, 40, 40);
    questionRegistry.add(q3);

    // Q4 - Raksasa lambat vs Kurcaci lincah
    Question q4 = new Question(4, "Raksasa yang menakutkan", "Si kecil yang lincah",
        new String[]{"/attribute @s minecraft:scale base set 3.0",
                     "/attribute @s minecraft:attack_damage base set 15.0",
                     "/attribute @s minecraft:movement_speed base set 0.15"},
        new String[]{"/attribute @s minecraft:scale base set 0.2",
                     "/attribute @s minecraft:movement_speed base set 0.45",
                     "/attribute @s minecraft:jump_strength base set 1.5"}, 40, 40);
    questionRegistry.add(q4);

    // Q5 - Besar lambat vs Kecil + invisible
    Question q5 = new Question(5, "Besar + lambat", "Kecil + cepat",
        new String[]{"/attribute @s minecraft:scale base set 2.5",
                     "/effect give @s slowness 40 2"},
        new String[]{"/attribute @s minecraft:scale base set 0.3",
                     "/effect give @s speed 40 2"}, 40, 40);
    questionRegistry.add(q5);

    // Q6 - Bisa lewat celah sempit vs Tidak bisa lewat pintu
    Question q6 = new Question(6, "Bisa menembus celah sempit", "Tidak bisa lewat pintu apapun",
        new String[]{"/attribute @s minecraft:scale base set 0.15"},
        new String[]{"/attribute @s minecraft:scale base set 1.9",
                     "/attribute @s minecraft:movement_speed base set 0.4"}, 40, 40);
    questionRegistry.add(q6);


    // Q7 - Ukuran micro + jump vs Scale besar + step height
    Question q7 = new Question(7, "Semut raksasa", "Tikus yang cepat",
        new String[]{"/attribute @s minecraft:scale base set 0.1",
                     "/attribute @s minecraft:jump_strength base set 2.0",
                     "/attribute @s minecraft:movement_speed base set 0.5"},
        new String[]{"/attribute @s minecraft:scale base set 0.35",
                     "/attribute @s minecraft:movement_speed base set 0.55",
                     "/attribute @s minecraft:sneaking_speed base set 0.25"}, 40, 40);
    questionRegistry.add(q7);

    // ═══════════════════════════════════════════════════════════
    // Q9–Q15 : JUMP, GRAVITY & MOVEMENT
    // ═══════════════════════════════════════════════════════════

    // Q9 - Lompat tinggi vs Jatuh aman
    Question q9 = new Question(9, "Lompat sangat tinggi", "Jatuh perlahan seperti balon",
        new String[]{"/attribute @s minecraft:jump_strength base set 2.0"},
        new String[]{"/attribute @s minecraft:gravity base set 0.01"}, 40, 40);
    questionRegistry.add(q9);

    // Q10 - Gravitasi terbalik vs Gravitasi ekstrem
    Question q10 = new Question(10, "Gravitasi terbalik", "Gravitasi ke bawah sangat kuat",
        new String[]{"/attribute @s minecraft:gravity base set -0.08"},
        new String[]{"/attribute @s minecraft:gravity base set 0.25",
                     "/attribute @s minecraft:movement_speed base set 0.3"}, 35, 35);
    questionRegistry.add(q10);

    // Q11 - Safe fall vs Fall damage x5
    Question q11 = new Question(11, "Tidak pernah terluka jatuh", "Gugur seperti batu bata",
        new String[]{"/attribute @s minecraft:safe_fall_distance base set 100.0"},
        new String[]{"/attribute @s minecraft:fall_damage_multiplier base set 5.0"}, 40, 40);
    questionRegistry.add(q11);

    // Q12 - Lompat + aman vs Sprint tapi no jump
    Question q12 = new Question(12, "Melompat berkali-kali di udara", "Secepat roket tapi tak bisa lompat",
        new String[]{"/attribute @s minecraft:jump_strength base set 1.8",
                     "/attribute @s minecraft:safe_fall_distance base set 30.0"},
        new String[]{"/attribute @s minecraft:movement_speed base set 0.55",
                     "/attribute @s minecraft:jump_strength base set 0.0"}, 40, 40);
    questionRegistry.add(q12);

    // Q13 - Levitation vs Slow falling
    Question q13 = new Question(13, "Terangkat ke atas tanpa kendali", "Turun perlahan tak berujung",
        new String[]{"/effect give @s levitation 20 2"},
        new String[]{"/effect give @s slow_falling 45 0",
                     "/attribute @s minecraft:jump_strength base set 0.1"}, 20, 45);
    questionRegistry.add(q13);

    // Q14 - Super speed vs Super slow
    Question q14 = new Question(14, "Berlari super cepat", "Bergerak seperti siput",
        new String[]{"/attribute @s minecraft:movement_speed base set 0.5"},
        new String[]{"/attribute @s minecraft:movement_speed base set 0.05"}, 40, 40);
    questionRegistry.add(q14);

    // Q15 - Jalan jongkok cepat vs Sprint + slow fall
    Question q15 = new Question(15, "Jalan jongkok secepat sprint", "Sprint tapi melayang sedikit",
        new String[]{"/attribute @s minecraft:sneaking_speed base set 0.15"},
        new String[]{"/attribute @s minecraft:movement_speed base set 0.35",
                     "/effect give @s slow_falling 40 0"}, 45, 40);
    questionRegistry.add(q15);

    // ═══════════════════════════════════════════════════════════
    // Q16–Q22 : COMBAT & STRENGTH
    // ═══════════════════════════════════════════════════════════

    // Q16 - Attack damage vs Attack speed
    Question q16 = new Question(16, "Pukulan super kuat", "Pukulan sangat cepat",
        new String[]{"/attribute @s minecraft:attack_damage base set 25.0"},
        new String[]{"/attribute @s minecraft:attack_speed base set 16.0"}, 40, 40);
    questionRegistry.add(q16);

    // Q17 - Knockback resist vs Fire resistance
    Question q17 = new Question(17, "Tahan dari smackdown", "Tahan dari api",
        new String[]{"/attribute @s minecraft:knockback_resistance base set 1.0"},
        new String[]{"/effect give @s fire_resistance 45 0"}, 45, 45);
    questionRegistry.add(q17);

    // Q18 - Armor tinggi vs Attack damage tinggi
    Question q18 = new Question(18, "Kulit sekeras baja", "Pukulan sepedas cabai",
        new String[]{"/attribute @s minecraft:armor base set 20.0",
                     "/attribute @s minecraft:armor_toughness base set 10.0"},
        new String[]{"/attribute @s minecraft:attack_damage base set 15.0",
                     "/attribute @s minecraft:attack_speed base set 8.0"}, 40, 40);
    questionRegistry.add(q18);

    // Q19 - Knockback kuat + speed vs Knockback resist
    Question q19 = new Question(19, "Terpental jauh saat dipukul", "Tidak terpental sama sekali",
        new String[]{"/attribute @s minecraft:knockback_resistance base set 0.0",
                     "/attribute @s minecraft:movement_speed base set 0.4"},
        new String[]{"/attribute @s minecraft:knockback_resistance base set 1.0"}, 40, 40);
    questionRegistry.add(q19);

    // Q20 - Benteng berjalan vs Pedang tanpa ampun
    Question q20 = new Question(20, "Benteng berjalan", "Pedang tanpa ampun",
        new String[]{"/effect give @s resistance 40 2",
                     "/attribute @s minecraft:armor base set 25.0"},
        new String[]{"/attribute @s minecraft:attack_damage base set 30.0",
                     "/attribute @s minecraft:armor base set 0.0"}, 40, 40);
    questionRegistry.add(q20);

    // Q21 - Totem vs Strength III
    Question q21 = new Question(21, "Satu kali tidak bisa mati", "Pukulan mematikan sesaat",
        new String[]{"/give @s minecraft:totem_of_undying 1"},
        new String[]{"/effect give @s strength 30 3"}, 999, 30);
    questionRegistry.add(q21);

    // Q22 - Kuat + tahan banting vs Cepat + lompat
    Question q22 = new Question(22, "Kuat + tahan banting", "Cepat + lompat tinggi",
        new String[]{"/attribute @s minecraft:attack_damage base set 20.0",
                     "/attribute @s minecraft:knockback_resistance base set 1.0"},
        new String[]{"/attribute @s minecraft:movement_speed base set 0.35",
                     "/attribute @s minecraft:jump_strength base set 1.8"}, 40, 40);
    questionRegistry.add(q22);

    // ═══════════════════════════════════════════════════════════
    // Q23–Q30 : VISUAL & PERCEPTION
    // ═══════════════════════════════════════════════════════════

    // Q23 - Invisible vs Glowing
    Question q23 = new Question(23, "Menjadi tak terlihat", "Bersinar terang",
        new String[]{"/effect give @s invisibility 30 0"},
        new String[]{"/effect give @s glowing 35 0"}, 30, 35);
    questionRegistry.add(q23);

    // Q24 - Blindness vs Nausea
    Question q24 = new Question(24, "Menjadi buta total", "Mabuk kepala keras",
        new String[]{"/effect give @s blindness 45 0"},
        new String[]{"/effect give @s nausea 45 0"}, 45, 45);
    questionRegistry.add(q24);

    // Q25 - Night vision vs Water breathing
    Question q25 = new Question(25, "Melihat dengan jelas di malam hari", "Hidup di dalam air",
        new String[]{"/effect give @s night_vision 40 0"},
        new String[]{"/effect give @s water_breathing 45 0",
                     "/effect give @s dolphins_grace 45 0"}, 40, 45);
    questionRegistry.add(q25);

    // Q26 - Glowing + night vision vs Invisible + blindness
    Question q26 = new Question(26, "Bersinar tapi bisa lihat semuanya", "Tak terlihat tapi ikut gelap",
        new String[]{"/effect give @s glowing 35 0",
                     "/effect give @s night_vision 35 0"},
        new String[]{"/effect give @s invisibility 35 0",
                     "/effect give @s blindness 15 0"}, 35, 35);
    questionRegistry.add(q26);

    // Q27 - Speed + blindness vs Slow + night vision
    Question q27 = new Question(27, "Lari kencang tapi buta", "Berjalan lambat tapi melihat segalanya",
        new String[]{"/effect give @s speed 40 3",
                     "/effect give @s blindness 40 0"},
        new String[]{"/effect give @s slowness 40 1",
                     "/effect give @s night_vision 40 0"}, 40, 40);
    questionRegistry.add(q27);

    // Q28 - Glowing semua orang tahu posisi vs Biasa tapi semua melihatmu
    Question q28 = new Question(28, "Semua bisa melihatmu dari mana saja", "Kamu bisa melihat semua orang",
        new String[]{"/effect give @s glowing 60 0"},
        new String[]{"/effect give @s night_vision 60 0",
                     "/effect give @s speed 60 0"}, 60, 60);
    questionRegistry.add(q28);


    // Q29 - Invisible + slow vs Glowing + speed (chaos trade-off)
    Question q29 = new Question(29, "Tak terlihat + lambat", "Bersinar + cepat",
        new String[]{"/effect give @s invisibility 40 0",
                     "/effect give @s slowness 40 1"},
        new String[]{"/effect give @s glowing 40 0",
                     "/effect give @s speed 40 2"}, 40, 40);
    questionRegistry.add(q29);

    // ═══════════════════════════════════════════════════════════
    // Q31–Q37 : HEALTH & SURVIVAL
    // ═══════════════════════════════════════════════════════════

    // Q31 - HP tinggi vs HP rendah + speed
    Question q31 = new Question(31, "Nyawa berlipat ganda", "Setengah nyawa tapi secepat kilat",
        new String[]{"/attribute @s minecraft:max_health base set 40.0",
                     "/effect give @s instant_health 1 0"},
        new String[]{"/attribute @s minecraft:max_health base set 10.0",
                     "/attribute @s minecraft:movement_speed base set 0.45"}, 40, 40);
    questionRegistry.add(q31);

    // Q32 - Regenerasi vs Poison + speed
    Question q32 = new Question(32, "Terus menerus sembuh sendiri", "Terus menerus keracunan",
        new String[]{"/effect give @s regeneration 45 2"},
        new String[]{"/effect give @s poison 45 1",
                     "/effect give @s speed 45 2"}, 45, 45);
    questionRegistry.add(q32);

    // Q33 - Slow fall vs Lompat tinggi + fall dmg
    Question q33 = new Question(33, "Jatuh pelan seperti daun", "Lompat sangat tinggi tapi jatuh keras",
        new String[]{"/effect give @s slow_falling 45 0"},
        new String[]{"/attribute @s minecraft:jump_strength base set 2.8",
                     "/attribute @s minecraft:fall_damage_multiplier base set 3.0"}, 45, 45);
    questionRegistry.add(q33);

    // Q34 - Armor tinggi vs Regen + resistance
    Question q34 = new Question(34, "Kulit anti peluru", "Tubuh yang sembuh terus",
        new String[]{"/attribute @s minecraft:armor base set 30.0",
                     "/attribute @s minecraft:armor_toughness base set 15.0"},
        new String[]{"/effect give @s regeneration 45 1",
                     "/effect give @s resistance 45 0"}, 45, 45);
    questionRegistry.add(q34);

    // Q35 - Totem + slow vs No totem + kuat
    Question q35 = new Question(35, "Tidak mati tapi sangat lambat", "Sangat kuat tapi bisa mati sekali",
        new String[]{"/give @s minecraft:totem_of_undying 1",
                     "/effect give @s slowness 999 3"},
        new String[]{"/attribute @s minecraft:attack_damage base set 25.0",
                     "/attribute @s minecraft:movement_speed base set 0.4"}, 999, 40);
    questionRegistry.add(q35);

    // Q36 - Saturation vs Hunger + damage
    Question q36 = new Question(36, "Kenyang selamanya sesaat", "Selalu lapar tapi lebih kuat",
        new String[]{"/effect give @s saturation 45 5"},
        new String[]{"/effect give @s hunger 45 3",
                     "/attribute @s minecraft:attack_damage base set 18.0"}, 45, 45);
    questionRegistry.add(q36);

    // Q37 - Fire resist vs Water breathing + speed air
    Question q37 = new Question(37, "Kebal dari api dan lava", "Kebal dari air dan tenggelam",
        new String[]{"/effect give @s fire_resistance 45 0"},
        new String[]{"/effect give @s water_breathing 45 0",
                     "/effect give @s dolphins_grace 45 0"}, 45, 45);
    questionRegistry.add(q37);

    // ═══════════════════════════════════════════════════════════
    // Q38–Q44 : KOMBINASI KOMPLEKS
    // ═══════════════════════════════════════════════════════════

    // Q38 - Lompat tinggi + aman vs Cepat + gravitasi rendah
    Question q38 = new Question(38, "Lompat tinggi + jatuh aman", "Cepat + tanpa gravitasi",
        new String[]{"/attribute @s minecraft:jump_strength base set 2.5",
                     "/attribute @s minecraft:safe_fall_distance base set 50.0"},
        new String[]{"/attribute @s minecraft:movement_speed base set 0.4",
                     "/attribute @s minecraft:gravity base set 0.02"}, 40, 40);
    questionRegistry.add(q38);


    // Q39 - Speed + nausea vs Slow + strength
    Question q39 = new Question(39, "Berlari kencang sambil pusing", "Diam tapi mematikan",
        new String[]{"/effect give @s speed 35 3",
                     "/effect give @s nausea 35 0"},
        new String[]{"/effect give @s strength 35 2",
                     "/attribute @s minecraft:movement_speed base set 0.1"}, 35, 35);
    questionRegistry.add(q39);

    // Q40 - Kokoh + lambat vs Cepat + mudah terpental
    Question q40 = new Question(40, "Kokoh bak gunung, selambat siput", "Kencang bak angin, ringan bak bulu",
        new String[]{"/attribute @s minecraft:knockback_resistance base set 1.0",
                     "/effect give @s slowness 40 3"},
        new String[]{"/attribute @s minecraft:movement_speed base set 0.5",
                     "/attribute @s minecraft:knockback_resistance base set 0.0"}, 40, 40);
    questionRegistry.add(q40);

    // Q41 - Step height tinggi vs Interaction range jauh
    Question q41 = new Question(41, "Bisa naik tebing tanpa lompat", "Jangkauan serangan jauh",
        new String[]{"/attribute @s minecraft:step_height base set 2.0"},
        new String[]{"/attribute @s minecraft:attack_knockback base set 4.0"}, 45, 45);
    questionRegistry.add(q41);

    // Q42 - Sangat ringan melayang vs Sangat berat ke bawah
    Question q42 = new Question(42, "Tubuh sangat ringan melayang", "Tubuh sangat berat ke bawah",
        new String[]{"/attribute @s minecraft:gravity base set -0.02",
                     "/attribute @s minecraft:jump_strength base set 0.6"},
        new String[]{"/attribute @s minecraft:gravity base set 0.25",
                     "/attribute @s minecraft:movement_speed base set 0.3"}, 40, 40);
    questionRegistry.add(q42);

    // Q43 - Speed + jump vs Armor + damage
    Question q43 = new Question(43, "Kijang di padang savana", "Hantu yang berlari",
        new String[]{"/effect give @s speed 40 1",
                     "/effect give @s jump_boost 40 1"},
        new String[]{"/effect give @s speed 40 0",
                     "/effect give @s invisibility 40 0"}, 40, 40);
    questionRegistry.add(q43);

    // ═══════════════════════════════════════════════════════════
    // Q44–Q49 : SITUASIONAL & CHAOS
    // ═══════════════════════════════════════════════════════════

    // Q44 - CHAOS: semua naik tapi blindness vs Semua turun tapi invisible
    Question q44 = new Question(44, "Luar biasa... tapi tidak melihat apa-apa", "Biasa saja... tapi semua melihatmu",
        new String[]{"/attribute @s minecraft:movement_speed base set 0.45",
                     "/attribute @s minecraft:attack_damage base set 22.0",
                     "/attribute @s minecraft:jump_strength base set 2.2",
                     "/attribute @s minecraft:armor base set 15.0",
                     "/effect give @s blindness 60 0"},
        new String[]{"/effect give @s glowing 60 0",
                     "/effect give @s regeneration 60 0",
                     "/effect give @s speed 60 1"}, 60, 60);
    questionRegistry.add(q44);

    // ═══════════════════════════════════════════════════════════
    // Q45–Q49 : SPECIAL EFFECTS
    // ═══════════════════════════════════════════════════════════







    // Q45 - Random Teleport All vs Random Teleport Hiders
    Question q45 = new Question(45, "Semua pemain bertukar posisi", "Hiders bertukar posisi",
        new String[]{},
        new String[]{}, 0, 0)
        .setCustomA(randomTeleportAll())
        .setCustomB(randomTeleportHiders());
    questionRegistry.add(q45);

    // Q46 - Block Disguise Grass vs Block Disguise Stone
    Question q46 = new Question(46, "Menyamar sebagai rumput", "Menyamar sebagai batu",
        new String[]{},
        new String[]{}, 40, 40)
        .setCustomA(blockDisguise(Material.GRASS_BLOCK, 40))
        .setCustomB(blockDisguise(Material.STONE, 40));
    questionRegistry.add(q46);

    // Q47 - Bow & Arrow vs Crossbow
    Question q47 = new Question(47, "Dapatkan bow sekali pakai", "Dapatkan crossbow sekali pakai",
        new String[]{},
        new String[]{}, 0, 0)
        .setCustomA(giveItems(Material.BOW, Material.ARROW))
        .setCustomB(giveItems(Material.CROSSBOW, Material.ARROW));
    questionRegistry.add(q47);

    // Q48 - Mace & Wind Charge vs Ender Pearl
    Question q48 = new Question(48, "Dapatkan mace & wind charge", "Dapatkan ender pearl",
        new String[]{},
        new String[]{}, 0, 0)
        .setCustomA(giveItems(Material.MACE, Material.WIND_CHARGE))
        .setCustomB(giveItems(Material.ENDER_PEARL));
    questionRegistry.add(q48);

    // Q49 - Summon Firework vs Extra Speed
    Question q49 = new Question(49, "Summon kembang api", "Kecepatan ekstra",
        new String[]{},
        new String[]{"/effect give @s speed 30 2"}, 0, 30)
        .setCustomA(summonFirework());
    questionRegistry.add(q49);

}
}
