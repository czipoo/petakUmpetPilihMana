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

public class PilihManaManager {
    private final PetakUmpetPilihMana plugin;
    private final Random random = new Random();
    
    // Active WYR cycle state
    private boolean choiceActive = false;
    private Question currentQuestion = null;
    private final Map<UUID, Integer> playerChoices = new HashMap<>(); // UUID -> 1 (A) or 2 (B)
    private final Map<UUID, Question> activeTestQuestions = new HashMap<>(); // UUID -> active test question

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

    // Map to restore attributes (Player UUID -> Map of Attributes -> Default Values)
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
        int durationA; // seconds
        int durationB; // seconds
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

    public void registerChoice(Player p, int choice) {
        if (!choiceActive) return;
        playerChoices.put(p.getUniqueId(), choice);
        String selectionText = (choice == 1) ? currentQuestion.optionA : currentQuestion.optionB;
        p.sendMessage("§a[PILIHAN] §fKamu berhasil memilih: §e" + selectionText);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.5f);
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
            p.sendMessage("§cPertanyaan nomor " + questionId + " tidak ditemukan (pilih 1-55).");
            return;
        }

        activeTestQuestions.put(p.getUniqueId(), found);
        p.sendMessage("§e§l======================================");
        p.sendMessage("§6§l=== TESTING PILIH MANA #" + found.id + " ===");

        p.sendMessage("§f[1] §a" + found.optionA);
        p.sendMessage("§7--- atau ---");
        p.sendMessage("§f[2] §b" + found.optionB);
        p.sendMessage("§e§l======================================");
        p.sendMessage("§e§lKetik 1 atau 2 di chat untuk menguji efek!");
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
    }

    public void registerTestChoice(Player p, int choice) {
        Question q = activeTestQuestions.remove(p.getUniqueId());
        if (q == null) return;

        String selectionText = (choice == 1) ? q.optionA : q.optionB;
        p.sendMessage("§a[TEST PILIHAN] §fKamu memilih: §e" + selectionText);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);

        applyEffect(p, q, choice == 1);
    }

    public void triggerPilihMana() {
        if (questionRegistry.isEmpty()) return;
        
        // Pick a random question
        currentQuestion = questionRegistry.get(random.nextInt(questionRegistry.size()));
        playerChoices.clear();
        choiceActive = true;

        // Broadcast standard format to all players
        Bukkit.broadcastMessage("§e§l==============================");
        Bukkit.broadcastMessage("§6§l=== PILIH MANA (Would You Rather) ===");

        Bukkit.broadcastMessage("§f[1] §a" + currentQuestion.optionA);
        Bukkit.broadcastMessage("§7--- atau ---");
        Bukkit.broadcastMessage("§f[2] §b" + currentQuestion.optionB);
        Bukkit.broadcastMessage("§e§l==============================");
        Bukkit.broadcastMessage("§e§lKetik 1 atau 2 di chat untuk memilih! (Waktu: 10 detik)");

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.EVENT_RAID_HORN, 1f, 1f);
        }

        // Wait 10 seconds for selection window
        new BukkitRunnable() {
            int countdown = 10;
            @Override
            public void run() {
                if (!plugin.getGameManager().isGameRunning()) {
                    this.cancel();
                    choiceActive = false;
                    return;
                }
                
                if (countdown > 0) {
                    if (countdown <= 3) {
                        Bukkit.broadcastMessage("§c§lPilihan ditutup dalam " + countdown + " detik...");
                        for (Player online : Bukkit.getOnlinePlayers()) {
                            online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                        }
                    }
                    countdown--;
                } else {
                    this.cancel();
                    choiceActive = false;
                    compileChoices();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void compileChoices() {
        Bukkit.broadcastMessage("§6§l[PILIHAN] §fWaktu memilih habis! Menerapkan efek...");
        GameManager gm = plugin.getGameManager();

        for (Player p : gm.getParticipants()) {
            if (!p.isOnline()) continue;

            UUID uuid = p.getUniqueId();
            if (!playerChoices.containsKey(uuid)) {
                // Glow penalty for 5 seconds
                p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, false, false));
                p.sendMessage("§c§l[PENALTI] §fKamu tidak memilih! Efek glowing diberikan selama 5 detik.");
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1f);
            } else {
                int choice = playerChoices.get(uuid);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
                if (choice == 1) {
                    applyEffect(p, currentQuestion, true);
                } else {
                    applyEffect(p, currentQuestion, false);
                }
            }
        }
    }

    private void applyEffect(Player p, Question q, boolean isOptionA) {
        String[] cmds = isOptionA ? q.cmdA : q.cmdB;
        int duration = isOptionA ? q.durationA : q.durationB;
        CustomEffect custom = isOptionA ? q.customA : q.customB;

        p.sendMessage("§a[EFEK] §fMenerapkan efek pilihanmu selama §e" + duration + "s§f!");

        // 1. Run standard commands replacing @s
        if (cmds != null) {
            for (String cmd : cmds) {
                String cmdProcessed = cmd.replace("@s", p.getName()).trim();
                if (cmdProcessed.startsWith("/")) {
                    cmdProcessed = cmdProcessed.substring(1);
                }
                // Handle disguise natively if it's disguised as something
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

        // 2. Run custom effects if defined
        if (custom != null) {
            custom.execute(p, this);
        }

        // 3. Schedule effect reset
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
        // Clear namespace
        if (disguiseType.contains(":")) {
            disguiseType = disguiseType.split(":")[1];
        }
        String finalDisguise = disguiseType;

        // Perform disguise
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "disguiseplayer " + p.getName() + " " + finalDisguise);
        p.performCommand("disguise " + finalDisguise);

        // Schedule undisguise
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
        // Reset custom movement sets
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

        // Reset attributes to default
        resetAttributesToDefault(p);
    }

    public void resetAllActiveEffects() {
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

        for (Player p : Bukkit.getOnlinePlayers()) {
            resetAttributesToDefault(p);
            // Potion cleanups
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

            // Disguise cleanup
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "undisguiseplayer " + p.getName());
            p.performCommand("undisguise");
        }
    }

    private void resetAttributesToDefault(Player p) {
        String name = p.getName();
        String[] attributes = {
                "minecraft:generic.sneaking_speed 0.3",
                "minecraft:generic.movement_speed 0.1",
                "minecraft:generic.gravity 0.08",
                "minecraft:generic.jump_strength 0.42",
                "minecraft:generic.step_height 0.6",
                "minecraft:generic.fall_damage_multiplier 1.0",
                "minecraft:generic.safe_fall_distance 3.0",
                "minecraft:generic.attack_damage 1.0",
                "minecraft:generic.max_health 20.0",
                "minecraft:player.entity_interaction_range 3.0",
                "minecraft:generic.knockback_resistance 0.0",
                "minecraft:generic.attack_knockback 0.0",
                "minecraft:generic.attack_speed 4.0",
                "minecraft:generic.scale 1.0"
        };
        for (String attr : attributes) {
            String[] split = attr.split(" ");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "attribute " + name + " " + split[0] + " base set " + split[1]);
        }
    }

    // Getters for movement hook checks
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

                // 1. Ticking Footprints (#24 A)
                activeFootprints.forEach((uuid, expiry) -> {
                    if (now < expiry) {
                        Player target = Bukkit.getPlayer(uuid);
                        if (target != null && target.isOnline()) {
                            // Find all other players in 20 block radius
                            for (Player other : Bukkit.getOnlinePlayers()) {
                                if (other.equals(target) || !other.getWorld().equals(target.getWorld())) continue;
                                if (other.getLocation().distance(target.getLocation()) <= 20) {
                                    target.spawnParticle(Particle.SMOKE, other.getLocation(), 3, 0.1, 0.01, 0.1, 0.01);
                                }
                            }
                        }
                    }
                });

                // 2. Ticking Highlight (#25 A)
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

                // 3. Ticking Reveal (#25 B)
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

                // 4. Ticking Sculk Reveal (#28 A)
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

                // 5. Ticking Ambient Sounds (#28 B)
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

                // 6. Ticking Ambient Particles (#30 B)
                activeAmbientParticles.forEach((uuid, expiry) -> {
                    if (now < expiry) {
                        Player target = Bukkit.getPlayer(uuid);
                        if (target != null && target.isOnline()) {
                            target.getWorld().spawnParticle(Particle.ENTITY_EFFECT, target.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 1.0);
                        }
                    }
                });

                // 7. Ticking Mutes (#29 A)
                activeMutes.forEach((uuid, expiry) -> {
                    if (now < expiry) {
                        Player target = Bukkit.getPlayer(uuid);
                        if (target != null && target.isOnline()) {
                            target.stopSound(SoundCategory_MASTER_SOUNDS());
                        }
                    }
                });

                // 8. Ticking Compass Trackers (#24 B & #27 B)
                activeCompassTrackers.forEach((trackerUuid, expiry) -> {
                    if (now < expiry) {
                        Player tracker = Bukkit.getPlayer(trackerUuid);
                        if (tracker != null && tracker.isOnline()) {
                            // Find nearest active hider/seeker
                            Player nearest = null;
                            double nearestDist = Double.MAX_VALUE;
                            for (Player other : plugin.getGameManager().getParticipants()) {
                                if (other.equals(tracker) || !other.isOnline()) continue;
                                if (!other.getWorld().equals(tracker.getWorld())) continue;
                                if (plugin.getGameListener().getGhostPlayers().contains(other.getUniqueId())) continue;

                                double d = other.getLocation().distance(tracker.getLocation());
                                if (d < nearestDist) {
                                    nearestDist = d;
                                    nearest = other;
                                }
                            }
                            if (nearest != null) {
                                tracker.setCompassTarget(nearest.getLocation());
                                tracker.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                                        new net.md_5.bungee.api.chat.TextComponent("§eKompas menunjuk ke: §a" + nearest.getName() + " §7(" + (int) nearestDist + "m)"));
                            }
                        }
                    }
                });
            }

            private org.bukkit.SoundCategory SoundCategory_MASTER_SOUNDS() {
                try {
                    return org.bukkit.SoundCategory.MASTER;
                } catch (Exception e) {
                    return null;
                }
            }
        }.runTaskTimer(plugin, 0L, 10L); // Tick every 0.5 seconds
    }

    // Handles movement particle trails and hearing step amplification
    public void handleStepEvents(Player movingPlayer, Location to) {
        long now = System.currentTimeMillis();

        // Snowflake trail
        if (activeSnowflakeTrails.containsKey(movingPlayer.getUniqueId())) {
            movingPlayer.getWorld().spawnParticle(Particle.SNOWFLAKE, movingPlayer.getLocation(), 4, 0.1, 0.01, 0.1, 0.01);
            // Water to ice (Frost Walker implementation)
            Location feet = movingPlayer.getLocation();
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    Location loc = feet.clone().add(x, -1, z);
                    if (loc.getBlock().getType() == Material.WATER) {
                        loc.getBlock().setType(Material.FROSTED_ICE);
                        // Auto-revert ice after 3 seconds
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (loc.getBlock().getType() == Material.FROSTED_ICE) {
                                    loc.getBlock().setType(Material.WATER);
                                }
                            }
                        }.runTaskLater(plugin, 60L);
                    }
                }
            }
        }

        // Flame trail
        if (activeFlameTrails.containsKey(movingPlayer.getUniqueId())) {
            movingPlayer.getWorld().spawnParticle(Particle.FLAME, movingPlayer.getLocation(), 3, 0.1, 0.01, 0.1, 0.01);
        }

        // Hearing steps amplification (#23 B)
        activeStepAmplifiers.forEach((uuid, expiry) -> {
            if (now < expiry) {
                Player hearingPlayer = Bukkit.getPlayer(uuid);
                if (hearingPlayer != null && hearingPlayer.isOnline() && !hearingPlayer.equals(movingPlayer)) {
                    if (movingPlayer.getLocation().getWorld().equals(hearingPlayer.getWorld()) &&
                        movingPlayer.getLocation().distance(hearingPlayer.getLocation()) <= 30) {
                        // Exclude sneaking players (who make no sound)
                        if (!movingPlayer.isSneaking()) {
                            hearingPlayer.playSound(movingPlayer.getLocation(), Sound.BLOCK_STONE_STEP, 1.8f, 0.7f);
                        }
                    }
                }
            }
        });
    }

    private void registerAllQuestions() {
        long defaultDuration = 40000; // 40 seconds in ms

        // Question 1
        Question q1 = new Question(1, "Kaki yang tidak pernah berbunyi", "Kaki yang melesat tanpa henti",
                new String[]{"/attribute @s minecraft:generic.sneaking_speed base set 1.0"},
                new String[]{"/attribute @s minecraft:generic.movement_speed base set 0.4"}, 40, 30);
        questionRegistry.add(q1);

        // Question 2
        Question q2 = new Question(2, "Tubuh yang melayang perlahan", "Tubuh yang tenggelam ke dalam tanah",
                new String[]{"/attribute @s minecraft:generic.gravity base set 0.01"},
                new String[]{"/effect give @s slowness 35 2"}, 35, 35);
        questionRegistry.add(q2);

        // Question 3
        Question q3 = new Question(3, "Melompat seperti makhluk kenyal", "Memanjat seperti makhluk berbisa",
                new String[]{"/attribute @s minecraft:generic.jump_strength base set 2.0"},
                new String[]{"/attribute @s minecraft:generic.step_height base set 2.0"}, 40, 40);
        questionRegistry.add(q3);

        // Question 4
        Question q4 = new Question(4, "Kaki yang bisa meluncur di atas air", "Kaki yang bisa menempel di semua permukaan",
                new String[]{"/attribute @s minecraft:generic.movement_speed base set 0.15"},
                new String[]{"/attribute @s minecraft:generic.step_height base set 3.0"}, 40, 40)
                .setCustomA((p, m) -> m.activeSnowflakeTrails.put(p.getUniqueId(), System.currentTimeMillis() + 40000));
        questionRegistry.add(q4);

        // Question 5
        Question q5 = new Question(5, "Tubuh yang ringan seperti kapas", "Tubuh yang berat seperti besi",
                new String[]{"/attribute @s minecraft:generic.gravity base set 0.02", "/effect give @s slow_falling 35 0"},
                new String[]{"/attribute @s minecraft:generic.gravity base set 0.25"}, 35, 35);
        questionRegistry.add(q5);

        // Question 6
        Question q6 = new Question(6, "Loncat sangat tinggi tapi susah mendarat", "Berlari sangat jauh tapi susah berhenti",
                new String[]{"/attribute @s minecraft:generic.jump_strength base set 1.5", "/attribute @s minecraft:generic.fall_damage_multiplier base set 0.1"},
                new String[]{"/attribute @s minecraft:generic.movement_speed base set 0.35"}, 40, 40);
        questionRegistry.add(q6);

        // Question 7
        Question q7 = new Question(7, "Tangan yang sangat cekatan", "Pukulan yang sangat kuat",
                new String[]{"/effect give @s haste 40 2"},
                new String[]{"/attribute @s minecraft:generic.attack_damage base set 20.0"}, 40, 40);
        questionRegistry.add(q7);

        // Question 8
        Question q8 = new Question(8, "Tubuh yang kebal segala api", "Tubuh yang kebal tekanan air",
                new String[]{"/effect give @s fire_resistance 60 0"},
                new String[]{"/effect give @s water_breathing 60 0", "/effect give @s dolphins_grace 60 0"}, 60, 60);
        questionRegistry.add(q8);

        // Question 9
        Question q9 = new Question(9, "Tubuh yang menyatu dengan kegelapan", "Tubuh yang bersinar dari dalam",
                new String[]{"/effect give @s invisibility 30 0"},
                new String[]{"/effect give @s glowing 35 0"}, 30, 35)
                .setCustomA((p, m) -> m.activeNoSprints.put(p.getUniqueId(), System.currentTimeMillis() + 30000));
        questionRegistry.add(q9);

        // Question 10
        Question q10 = new Question(10, "Menyatu dengan tanah di bawah kakimu", "Menyatu dengan dinding di sekitarmu",
                new String[]{"disguise as minecraft:dirt"},
                new String[]{"disguise as minecraft:stone_bricks"}, 45, 45);
        questionRegistry.add(q10);

        // Question 11
        Question q11 = new Question(11, "Wujud hewan kecil yang tenang", "Wujud hewan kecil yang melompat",
                new String[]{"disguise as cat"},
                new String[]{"disguise as rabbit"}, 45, 45);
        questionRegistry.add(q11);

        // Question 12
        Question q12 = new Question(12, "Hidup di dalam air", "Hidup di dalam api",
                new String[]{"/effect give @s water_breathing 60 0", "/effect give @s dolphins_grace 60 0"},
                new String[]{"/effect give @s fire_resistance 60 0"}, 60, 60);
        questionRegistry.add(q12);

        // Question 13
        Question q13 = new Question(13, "Menjadi bagian dari perabotan ruangan", "Menjadi bagian dari dekorasi dinding",
                new String[]{"disguise as chest"},
                new String[]{"disguise as bookshelf"}, 45, 45);
        questionRegistry.add(q13);

        // Question 14
        Question q14 = new Question(14, "Wujud makhluk yang semua orang hindari", "Wujud makhluk yang semua orang abaikan",
                new String[]{"disguise as creeper"},
                new String[]{"disguise as villager"}, 45, 45);
        questionRegistry.add(q14);

        // Question 15
        Question q15 = new Question(15, "Wujud makhluk terbang malam", "Wujud makhluk mungil bersayap",
                new String[]{"disguise as phantom"},
                new String[]{"disguise as bat"}, 45, 45);
        questionRegistry.add(q15);

        // Question 16
        Question q16 = new Question(16, "Wujud penyihir berbahaya", "Wujud makhluk laut bercahaya",
                new String[]{"disguise as witch"},
                new String[]{"disguise as glow_squid"}, 45, 45);
        questionRegistry.add(q16);

        // Question 17
        Question q17 = new Question(17, "Menyatu dengan struktur bawah tanah", "Menyatu dengan bebatuan berlumut",
                new String[]{"disguise as deepslate"},
                new String[]{"disguise as moss_block"}, 45, 45);
        questionRegistry.add(q17);

        // Question 18
        Question q18 = new Question(18, "Wujud makhluk yang disegani", "Wujud makhluk yang diremehkan",
                new String[]{"disguise as ravager"},
                new String[]{"disguise as chicken"}, 45, 45);
        questionRegistry.add(q18);

        // Question 19
        Question q19 = new Question(19, "Wujud tengkorak berjalan", "Wujud mayat yang limbung",
                new String[]{"disguise as skeleton"},
                new String[]{"disguise as zombie", "/effect give @s slowness 45 0"}, 45, 45);
        questionRegistry.add(q19);

        // Question 20
        Question q20 = new Question(20, "Wujud rubah yang licin", "Wujud serigala yang liar",
                new String[]{"disguise as fox"},
                new String[]{"disguise as wolf"}, 45, 45);
        questionRegistry.add(q20);

        // Question 21
        Question q21 = new Question(21, "Wujud makhluk kenyal yang melompat", "Wujud makhluk berkaki delapan",
                new String[]{"disguise as slime", "/effect give @s jump_boost 40 2"},
                new String[]{"disguise as spider"}, 40, 40);
        questionRegistry.add(q21);

        // Question 22
        Question q22 = new Question(22, "Berbaur dengan bebatuan kasar", "Berbaur dengan bebatuan halus",
                new String[]{"disguise as cobblestone"},
                new String[]{"disguise as smooth_stone"}, 45, 45);
        questionRegistry.add(q22);

        // Question 23
        Question q23 = new Question(23, "Mata yang menembus kegelapan", "Telinga yang mendengar lebih jauh",
                new String[]{"/effect give @s night_vision 45 0"},
                null, 45, 30)
                .setCustomB((p, m) -> m.activeStepAmplifiers.put(p.getUniqueId(), System.currentTimeMillis() + 30000));
        questionRegistry.add(q23);

        // Question 24
        Question q24 = new Question(24, "Mata yang melihat jejak yang baru lewat", "Kompas yang menunjuk pemain terdekat",
                null, null, 20, 20)
                .setCustomA((p, m) -> m.activeFootprints.put(p.getUniqueId(), System.currentTimeMillis() + 20000))
                .setCustomB((p, m) -> m.activeCompassTrackers.put(p.getUniqueId(), System.currentTimeMillis() + 20000));
        questionRegistry.add(q24);

        // Question 25
        Question q25 = new Question(25, "Aura yang membaca panas tubuh di sekitar", "Pandangan yang tembus segala benda",
                null, new String[]{"/effect give @s night_vision 40 0"}, 15, 15)
                .setCustomA((p, m) -> m.activeHighlightRadius.put(p.getUniqueId(), System.currentTimeMillis() + 15000))
                .setCustomB((p, m) -> m.activeRevealRadius.put(p.getUniqueId(), System.currentTimeMillis() + 15000));
        questionRegistry.add(q25);

        // Question 26
        Question q26 = new Question(26, "Meninggalkan jejak api di setiap langkah", "Meninggalkan jejak es di setiap langkah",
                new String[]{"/effect give @s fire_resistance 35 0"},
                null, 35, 35)
                .setCustomA((p, m) -> m.activeFlameTrails.put(p.getUniqueId(), System.currentTimeMillis() + 35000))
                .setCustomB((p, m) -> m.activeSnowflakeTrails.put(p.getUniqueId(), System.currentTimeMillis() + 35000));
        questionRegistry.add(q26);

        // Question 27
        Question q27 = new Question(27, "Posisimu tersiar ke semua orang sekali", "Arahmu terbaca selama beberapa saat",
                null, null, 0, 15)
                .setCustomA((p, m) -> Bukkit.broadcastMessage("§c[POSISI] §e" + p.getName() + " §fada di: §aX: " + p.getLocation().getBlockX() + ", Y: " + p.getLocation().getBlockY() + ", Z: " + p.getLocation().getBlockZ()))
                .setCustomB((p, m) -> {
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (!online.equals(p)) {
                            m.activeCompassTrackers.put(online.getUniqueId(), System.currentTimeMillis() + 15000);
                        }
                    }
                });
        questionRegistry.add(q27);

        // Question 28
        Question q28 = new Question(28, "Bisa merasakan setiap getaran di sekitar", "Suara alam yang menutupi segalanya",
                null, null, 20, 20)
                .setCustomA((p, m) -> m.activeSculkReveal.put(p.getUniqueId(), System.currentTimeMillis() + 20000))
                .setCustomB((p, m) -> m.activeAmbientAmplifiers.put(p.getUniqueId(), System.currentTimeMillis() + 20000));
        questionRegistry.add(q28);

        // Question 29
        Question q29 = new Question(29, "Dunia yang tiba-tiba sunyi total", "Mata yang tiba-tiba gelap total",
                null, new String[]{"/effect give @s darkness 20 2"}, 20, 20)
                .setCustomA((p, m) -> m.activeMutes.put(p.getUniqueId(), System.currentTimeMillis() + 20000));
        questionRegistry.add(q29);

        // Question 30
        Question q30 = new Question(30, "Bersin keras yang terdengar semua orang", "Aura yang memanggil perhatian",
                new String[]{"/playsound entity.panda.sneeze ambient @a ~ ~ ~ 1 1"},
                null, 0, 20)
                .setCustomB((p, m) -> m.activeAmbientParticles.put(p.getUniqueId(), System.currentTimeMillis() + 20000));
        questionRegistry.add(q30);

        // Question 31
        Question q31 = new Question(31, "Arah kiri dan kanan yang tertukar", "Arah maju dan mundur yang tertukar",
                null, null, 30, 30)
                .setCustomA((p, m) -> m.activeADSwaps.put(p.getUniqueId(), System.currentTimeMillis() + 30000))
                .setCustomB((p, m) -> m.activeWSSwaps.put(p.getUniqueId(), System.currentTimeMillis() + 30000));
        questionRegistry.add(q31);

        // Question 32
        Question q32 = new Question(32, "Muncul di tempat yang tidak kamu duga", "Bertukar tempat dengan orang yang tidak kamu duga",
                null, null, 0, 0)
                .setCustomA((p, m) -> {
                    double angle = m.random.nextDouble() * 2 * Math.PI;
                    double dist = 10 + m.random.nextDouble() * 30; // 10-40 blocks
                    Location target = p.getLocation().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
                    target.setY(p.getWorld().getHighestBlockYAt(target) + 1);
                    p.teleport(target);
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                })
                .setCustomB((p, m) -> {
                    List<Player> choices = new ArrayList<>(m.plugin.getGameManager().getParticipants());
                    choices.remove(p);
                    if (!choices.isEmpty()) {
                        Player other = choices.get(m.random.nextInt(choices.size()));
                        Location locA = p.getLocation().clone();
                        Location locB = other.getLocation().clone();
                        p.teleport(locB);
                        other.teleport(locA);
                        p.playSound(locB, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                        other.playSound(locA, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                    }
                });
        questionRegistry.add(q32);

        // Question 33
        Question q33 = new Question(33, "Kamu berdiri, tapi dunia di sekitarmu berputar", "Kamu bergerak, tapi kakimu tidak mematuhi",
                new String[]{"/effect give @s nausea 20 2"},
                new String[]{"/effect give @s slowness 25 3", "/effect give @s jump_boost 25 0"}, 20, 25);
        questionRegistry.add(q33);

        // Question 34
        Question q34 = new Question(34, "Tubuh yang tidak bisa berhenti melayang", "Tubuh yang tidak bisa berhenti berlari",
                new String[]{"/effect give @s levitation 30 0"},
                new String[]{"/attribute @s minecraft:generic.movement_speed base set 0.6"}, 30, 15)
                .setCustomB((p, m) -> m.activeNoSprints.put(p.getUniqueId(), System.currentTimeMillis() - 1000)); // acts as dummy sprint
        questionRegistry.add(q34);

        // Question 35
        Question q35 = new Question(35, "Bertukar wujud jadi makhluk tinggi misterius", "Bertukar wujud jadi makhluk besar berkuasa",
                new String[]{"disguise as enderman"},
                new String[]{"disguise as iron_golem"}, 45, 45);
        questionRegistry.add(q35);

        // Question 36
        Question q36 = new Question(36, "Pandangan yang hanya sejauh tembok tipis", "Pandangan yang bergoyang tak karuan",
                new String[]{"/effect give @s darkness 25 2"},
                new String[]{"/effect give @s nausea 20 1", "/effect give @s blindness 20 0"}, 25, 20);
        questionRegistry.add(q36);

        // Question 37
        Question q37 = new Question(37, "Dunia yang tiba-tiba jadi malam", "Dunia yang tiba-tiba jadi siang",
                new String[]{"/time set midnight"},
                new String[]{"/time set noon"}, 0, 0);
        questionRegistry.add(q37);

        // Question 38
        Question q38 = new Question(38, "Semua warnamu tiba-tiba hilang", "Semua keseimbanganmu tiba-tiba hilang",
                new String[]{"/effect give @s blindness 15 0", "/effect give @s darkness 15 1"},
                new String[]{"/effect give @s nausea 20 1", "/effect give @s slow_falling 20 0"}, 15, 20);
        questionRegistry.add(q38);

        // Question 39
        Question q39 = new Question(39, "Kepala yang tiba-tiba terasa sangat berat", "Kaki yang tiba-tiba terasa sangat ringan",
                new String[]{"/effect give @s slowness 30 2", "/attribute @s minecraft:generic.jump_strength base set 0.0"},
                new String[]{"/effect give @s jump_boost 30 3", "/effect give @s slow_falling 30 0"}, 30, 30);
        questionRegistry.add(q39);

        // Question 40
        Question q40 = new Question(40, "Tubuh yang mengecil seperti tikus", "Tubuh yang membesar seperti raksasa",
                new String[]{"/attribute @s minecraft:generic.scale base set 0.3"},
                new String[]{"/attribute @s minecraft:generic.scale base set 2.8"}, 45, 45);
        questionRegistry.add(q40);

        // Question 41
        Question q41 = new Question(41, "Gravitasi yang hampir menghilang", "Gravitasi yang berlipat tiga",
                new String[]{"/attribute @s minecraft:generic.gravity base set 0.005"},
                new String[]{"/attribute @s minecraft:generic.gravity base set 0.25"}, 35, 35);
        questionRegistry.add(q41);

        // Question 42
        Question q42 = new Question(42, "Lompatan yang menembus langit", "Lompatan yang bahkan tidak ada",
                new String[]{"/attribute @s minecraft:generic.jump_strength base set 3.0"},
                new String[]{"/attribute @s minecraft:generic.jump_strength base set 0.0"}, 40, 40);
        questionRegistry.add(q42);

        // Question 43
        Question q43 = new Question(43, "Kaki yang melangkahi semua rintangan", "Kaki yang tersandung batu kecil pun",
                new String[]{"/attribute @s minecraft:generic.step_height base set 3.0"},
                new String[]{"/attribute @s minecraft:generic.step_height base set 0.0"}, 40, 40);
        questionRegistry.add(q43);

        // Question 44
        Question q44 = new Question(44, "Tubuh yang jatuh seperti bulu", "Tubuh yang jatuh seperti batu besar",
                new String[]{"/attribute @s minecraft:generic.fall_damage_multiplier base set 0.0", "/attribute @s minecraft:generic.safe_fall_distance base set 100.0"},
                new String[]{"/attribute @s minecraft:generic.fall_damage_multiplier base set 5.0", "/attribute @s minecraft:generic.safe_fall_distance base set 0.0"}, 40, 40);
        questionRegistry.add(q44);

        // Question 45
        Question q45 = new Question(45, "Tubuh yang tidak tergoyahkan", "Pukulan yang melempar jauh",
                new String[]{"/attribute @s minecraft:generic.knockback_resistance base set 1.0"},
                new String[]{"/attribute @s minecraft:generic.attack_knockback base set 5.0"}, 40, 40);
        questionRegistry.add(q45);

        // Question 46
        Question q46 = new Question(46, "Nyawa yang terasa tak terbatas", "Nyawa yang terasa seperti kertas",
                new String[]{"/attribute @s minecraft:generic.max_health base set 60.0", "/effect give @s instant_health 1 4"},
                new String[]{"/attribute @s minecraft:generic.max_health base set 4.0"}, 45, 45);
        questionRegistry.add(q46);

        // Question 47
        Question q47 = new Question(47, "Tangan yang menjangkau sangat jauh", "Tangan yang hampir tidak menjangkau apapun",
                new String[]{"/attribute @s minecraft:player.entity_interaction_range base set 8.0"},
                new String[]{"/attribute @s minecraft:player.entity_interaction_range base set 0.5"}, 40, 40);
        questionRegistry.add(q47);

        // Question 48
        Question q48 = new Question(48, "Kecepatan jalan yang membuat angin cemburu", "Kecepatan sneak yang tidak terdeteksi radar",
                new String[]{"/attribute @s minecraft:generic.movement_speed base set 0.45"},
                new String[]{"/attribute @s minecraft:generic.sneaking_speed base set 1.0"}, 40, 40);
        questionRegistry.add(q48);

        // Question 49
        Question q49 = new Question(49, "Serangan secepat kilat", "Serangan selambat siput",
                new String[]{"/attribute @s minecraft:generic.attack_speed base set 16.0"},
                new String[]{"/attribute @s minecraft:generic.attack_speed base set 0.5"}, 40, 40);
        questionRegistry.add(q49);

        // Question 50
        Question q50 = new Question(50, "Tubuh yang mengecil setengahnya", "Tubuh yang membesar dua kali",
                new String[]{"/attribute @s minecraft:generic.scale base set 0.5"},
                new String[]{"/attribute @s minecraft:generic.scale base set 2.0"}, 45, 45);
        questionRegistry.add(q50);

        // Question 51
        Question q51 = new Question(51, "Jatuh dari ketinggian apapun tanpa cedera", "Bahkan jatuh satu blok terasa menyakitkan",
                new String[]{"/attribute @s minecraft:generic.safe_fall_distance base set 100.0"},
                new String[]{"/attribute @s minecraft:generic.safe_fall_distance base set -10.0"}, 40, 40);
        questionRegistry.add(q51);

        // Question 52
        Question q52 = new Question(52, "Tubuh yang terus menyusut", "Tubuh yang terus membesar",
                new String[]{"/attribute @s minecraft:generic.scale base set 0.15"},
                new String[]{"/attribute @s minecraft:generic.scale base set 4.0"}, 45, 45);
        questionRegistry.add(q52);

        // Question 53
        Question q53 = new Question(53, "Gravitasi yang membuatmu seperti balon", "Gravitasi yang membuatmu seperti batu bata",
                new String[]{"/attribute @s minecraft:generic.gravity base set -0.05"},
                new String[]{"/attribute @s minecraft:generic.gravity base set 0.3"}, 35, 35);
        questionRegistry.add(q53);

        // Question 54
        Question q54 = new Question(54, "Lompatan setinggi pohon", "Gerakan sneak secepat berlari",
                new String[]{"/attribute @s minecraft:generic.jump_strength base set 1.5"},
                new String[]{"/attribute @s minecraft:generic.sneaking_speed base set 0.9"}, 40, 40);
        questionRegistry.add(q54);

        // Question 55
        Question q55 = new Question(55, "Tubuh yang tidak mempan dilempar apapun", "Tubuh yang terbang ke mana-mana saat terkena",
                new String[]{"/attribute @s minecraft:generic.knockback_resistance base set 1.0", "/attribute @s minecraft:generic.max_health base set 40.0"},
                new String[]{"/attribute @s minecraft:generic.knockback_resistance base set 0.0"}, 40, 40);
        questionRegistry.add(q55);
    }
}
