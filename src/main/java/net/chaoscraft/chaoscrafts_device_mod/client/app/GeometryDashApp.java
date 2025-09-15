package net.chaoscraft.chaoscrafts_device_mod.client.app;

import com.mojang.blaze3d.vertex.PoseStack;
import net.chaoscraft.chaoscrafts_device_mod.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.chaoscraft.chaoscrafts_device_mod.client.async.AsyncTaskManager;
import net.chaoscraft.chaoscrafts_device_mod.client.screen.DraggableWindow;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceLocation;
import com.mojang.math.Axis;

public class GeometryDashApp implements IApp {
    private DraggableWindow window;
    private final AsyncTaskManager asyncManager = AsyncTaskManager.getInstance();

    // Game state
    private enum GameState { MENU, PLAYING, GAME_OVER }
    private volatile GameState state = GameState.MENU;

    // Player physics (float for smoother feel)
    private float playerY = 0f; // height above ground
    private float prevPlayerY = 0f;
    private float playerVel = 0f;
    private final int playerSize = 22;
    private final int cubeSize = playerSize; // visual cube unit for towers/spikes
    // gameplay mode/speed
    private volatile String currentMode = "Cube"; // Cube, Ship, Wave, Robot, UFO, Ball
    private volatile int activeSpeedMultiplier = 1; // 1..4

    // world
    private final AtomicInteger baseSpeed = new AtomicInteger(5);
    private final AtomicInteger score = new AtomicInteger(0);
    private final AtomicInteger highScore = new AtomicInteger(0);
    private final AtomicBoolean jumpRequested = new AtomicBoolean(false);
    private final AtomicLong lastUpdateTime = new AtomicLong(0);
    private final AtomicBoolean gameStarted = new AtomicBoolean(false);

    private final CopyOnWriteArrayList<Obstacle> obstacles = new CopyOnWriteArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final List<Orb> orbs = new ArrayList<>();

    private final Random rng = new Random();

    // visual rotation
    private float cubeAngle = 0f;
    // Added fields for improved rotation logic (distance-based 90° per cube width while airborne)
    private float takeoffBaseAngle = 0f;          // angle at the moment of leaving ground
    private float airborneDistanceAccum = 0f;     // horizontal distance the world moved past the player while airborne
    private boolean rotatingInAir = false;        // flag set on takeoff until landing

    // player texture (must include full path under textures/ and .png extension for direct blit)
    private static final ResourceLocation CUBE_TEXTURE = ResourceLocation.fromNamespaceAndPath("chaoscrafts_device_mod", "textures/gd_cube_icons/cube.png");

    // track prior grounded state for landing snap
    private boolean wasGrounded = true;

    @Override
    public void onOpen(DraggableWindow window) {
        this.window = window;
        resetGame();
        lastUpdateTime.set(System.currentTimeMillis());
        asyncManager.scheduleTask(this::gameUpdateLoop, 16, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private void gameUpdateLoop() {
        try {
            if (state == GameState.PLAYING && gameStarted.get()) {
                long currentTime = System.currentTimeMillis();
                long deltaMillis = currentTime - lastUpdateTime.get();
                lastUpdateTime.set(currentTime);
                float dt = Math.max(1f, deltaMillis) / 16.0f; // ~1 at 60+ fps
                asyncManager.executeOnMainThread(() -> updateGame(dt));
            }
        } finally {
            if (window != null) asyncManager.scheduleTask(this::gameUpdateLoop, 16, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void renderContent(GuiGraphics guiGraphics, PoseStack poseStack, DraggableWindow window, int mouseRelX, int mouseRelY, float partialTick) {
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8, cy = r[1] + 32;
        int cw = r[2] - 16, ch = r[3] - 40;

        // background
        for (int i = 0; i < ch; i++) {
            int mix = i * 255 / Math.max(1, ch);
            int a = 0xFF;
            int topColor = 0xFF1E1E1E;
            int bottomColor = 0xFF081022;
            int rcol = ((topColor >> 16) & 0xFF) * (255 - mix) / 255 + ((bottomColor >> 16) & 0xFF) * mix / 255;
            int gcol = ((topColor >> 8) & 0xFF) * (255 - mix) / 255 + ((bottomColor >> 8) & 0xFF) * mix / 255;
            int bcol = (topColor & 0xFF) * (255 - mix) / 255 + (bottomColor & 0xFF) * mix / 255;
            int col = (a << 24) | (rcol << 16) | (gcol << 8) | bcol;
            guiGraphics.fill(cx, cy + i, cx + cw, cy + i + 1, col);
        }

        int groundY = cy + ch - 30;
        drawParallax(guiGraphics, cx, cy, cw, ch, groundY);

        // player rendering with textured cube + rotation (use GuiGraphics pose stack so transform applies)
        int playerX = cx + 60;
        int py = Math.round(groundY - playerY - playerSize);
        guiGraphics.fill(playerX - 2, groundY - 4, playerX + playerSize + 2, groundY + 2, 0x33000000);
        PoseStack ps = guiGraphics.pose();
        ps.pushPose();
        float textureBase = 120f;
        float scale = playerSize / textureBase;
        ps.translate(playerX + playerSize / 2.0f, py + playerSize / 2.0f, 0);
        ps.scale(scale, scale, 1f);
        ps.mulPose(Axis.ZP.rotationDegrees(cubeAngle));
        guiGraphics.blit(CUBE_TEXTURE, (int)(-textureBase / 2f), (int)(-textureBase / 2f), 0, 0, 120, 120, 120, 120);
        ps.popPose();

        // Obstacles
        for (Obstacle o : obstacles) {
            if (o.type == ObstacleType.BLOCK) {
                // Draw assembled cubes (stacked)
                int cubesVert = Math.max(1, o.height / cubeSize);
                int cubesHor = Math.max(1, o.width / cubeSize);
                for (int vy = 0; vy < cubesVert; vy++) {
                    for (int hx = 0; hx < cubesHor; hx++) {
                        int bx = cx + o.x + hx * cubeSize;
                        int by = groundY - ((vy + 1) * cubeSize);
                        int light = 0xFF2196F3;
                        int shade = 0xFF64B5F6;
                        guiGraphics.fill(bx, by, bx + cubeSize, by + cubeSize, light);
                        guiGraphics.fill(bx + 3, by + 3, bx + cubeSize - 3, by + 9, shade);
                    }
                }
            } else if (o.type == ObstacleType.SPIKE) {
                // Draw 1x1 cube spike(s) to match cube theme
                int cubes = Math.max(1, o.width / cubeSize);
                for (int i = 0; i < cubes; i++) {
                    int sx = cx + o.x + i * cubeSize;
                    int sy = groundY - cubeSize;
                    guiGraphics.fill(sx, sy, sx + cubeSize, sy + cubeSize, 0xFFFFA726);
                }
            } else if (o.type == ObstacleType.PAD) {
                // pad as small glowing square
                int px = cx + o.x;
                int py2 = groundY - o.height;
                guiGraphics.fill(px, py2, px + o.width, py2 + o.height, 0xFFAA66FF);
                guiGraphics.fill(px + 2, py2 + 2, px + o.width - 2, py2 + o.height - 2, 0xFFEECCFF);
            } else if (o.type == ObstacleType.PORTAL) {
                // portal visual (mode + speed)
                int px = cx + o.x;
                int py2 = groundY - o.height;
                int col = 0xFF88FF88;
                switch (o.portalMode) {
                    case "Ship": col = 0xFF66CCFF; break;
                    case "Wave": col = 0xFFFFCC66; break;
                    case "UFO": col = 0xFFCC66FF; break;
                    case "Robot": col = 0xFF66FF66; break;
                    case "Ball": col = 0xFFFF6666; break;
                    default: col = 0xFF88FF88; break;
                }
                guiGraphics.fill(px, py2, px + cubeSize, py2 + cubeSize, col);
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(o.portalMode + " x" + o.portalSpeed), px - 6, py2 - 10, 0xFFFFFFFF, false);
            }
        }

        // Orbs
        synchronized (orbs) {
            for (Orb ob : orbs) {
                guiGraphics.fill(cx + ob.x, groundY - ob.y - 8, cx + ob.x + 12, groundY - ob.y + 4, 0xFFFFFF66);
            }
        }

        // Particles
        synchronized (particles) {
            Iterator<Particle> it = particles.iterator();
            while (it.hasNext()) {
                Particle p = it.next();
                p.life -= 1;
                if (p.life <= 0) { it.remove(); continue; }
                int alpha = 128 + (p.life * 127 / p.maxLife);
                int col = (alpha << 24) | (p.col & 0x00FFFFFF);
                guiGraphics.fill((int)p.x, (int)p.y, (int)p.x + 2, (int)p.y + 2, col);
                p.x += p.vx; p.y += p.vy; p.vy += 0.2f;
            }
        }

        // HUD
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Score: " + score.get()), cx + 10, cy + 6, 0xFFFFFFFF, false);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Best: " + highScore.get()), cx + 110, cy + 6, 0xFFFFFF00, false);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Mode: " + currentMode + "  Speed: " + activeSpeedMultiplier + "x"), cx + 220, cy + 6, 0xFF88FF88, false);

        if (state == GameState.MENU) {
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Geometry Dash — Click or press Space to jump"), cx + cw/2 - 120, cy + 30, 0xFFFFFFFF, false);
            guiGraphics.fill(cx + cw/2 - 60, cy + ch/2 - 10, cx + cw/2 + 60, cy + ch/2 + 20, 0xFF4C7BD1);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Start"), cx + cw/2 - 18, cy + ch/2 + 2, 0xFFFFFFFF, false);
        } else if (state == GameState.GAME_OVER) {
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Game Over"), cx + cw/2 - 30, cy + 30, 0xFFFFFFFF, false);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Score: " + score.get()), cx + cw/2 - 30, cy + 50, 0xFFFFFFFF, false);
            guiGraphics.fill(cx + cw/2 - 60, cy + ch/2 - 10, cx + cw/2 + 60, cy + ch/2 + 20, 0xFF4C7BD1);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Play Again"), cx + cw/2 - 28, cy + ch/2 + 2, 0xFFFFFFFF, false);
        }
    }

    private void drawParallax(GuiGraphics guiGraphics, int cx, int cy, int cw, int ch, int groundY) {
        long t = System.currentTimeMillis();
        int offsetFar = (int)((t / 80) % (cw + 200));
        int offsetNear = (int)((t / 50) % (cw + 300));
        for (int x = -200 + offsetFar; x < cw + 200; x += 200) {
            int mx = cx + x - 100;
            int my = groundY - 120;
            guiGraphics.fill(mx, my, mx + 160, groundY - 60, 0xFF2E3A59);
        }
        for (int x = -300 + offsetNear; x < cw + 300; x += 160) {
            int hx = cx + x - 80;
            int hy = groundY - 60;
            guiGraphics.fill(hx, hy, hx + 120, groundY - 30, 0xFF3E5A89);
        }
        guiGraphics.fill(cx, groundY, cx + cw, groundY + 30, 0xFF333333);
    }

    private void updateGame(float deltaTime) {
        // store previous
        prevPlayerY = playerY;

        // jump handling: tuned for Geometry Dash feel -- only jump when on ground or standing on block
        if (jumpRequested.getAndSet(false)) {
            if (playerY <= 0.001f) {
                playerVel = 14.0f; // stronger jump for farther airtime
                playSound(ModSounds.LAPTOP_TRACKPAD.get());
            } else {
                // if standing on block (playerY equals some block height)
                boolean standingOnBlock = false;
                for (Obstacle o : obstacles) {
                    if (o.type == ObstacleType.BLOCK) {
                        int ox = o.x;
                        if (ox < 60 + playerSize && ox + o.width > 60) {
                            if (Math.abs(playerY - o.height) < 0.5f && playerVel == 0f) { standingOnBlock = true; break; }
                        }
                    }
                }
                if (standingOnBlock) {
                    playerVel = 14.0f;
                    playSound(ModSounds.LAPTOP_TRACKPAD.get());
                }
            }
        }

        // integrate
        float gravity = 1.3f; // tuned gravity
        playerY += playerVel * deltaTime;
        playerVel -= gravity * deltaTime;
        if (playerY < 0f) { playerY = 0f; playerVel = 0f; }

        // obstacle movement
        int speed = Math.max(1, Math.round(baseSpeed.get() * (float)activeSpeedMultiplier));
        List<Obstacle> removed = new ArrayList<>();
        for (Obstacle o : obstacles) {
            int move = Math.max(1, Math.round(speed * deltaTime * 0.6f));
            o.x -= move;
            if (o.x + o.width < -50) removed.add(o);
        }
        obstacles.removeAll(removed);

        // spawn logic (mix blocks, spikes, pads)
        if (obstacles.isEmpty() || obstacles.get(obstacles.size()-1).x < 240 + rng.nextInt(160)) {
            int typeRoll = rng.nextInt(100);
            int startX = 420 + rng.nextInt(160);
            if (typeRoll < 50) {
                // block made of 1x1 cubes, max 2 cubes tall
                int cubesVert = 1 + rng.nextInt(2); // 1..2
                int cubesHor = 1 + rng.nextInt(3); // 1..3 wide
                int height = cubesVert * cubeSize;
                int width = cubesHor * cubeSize;
                obstacles.add(new Obstacle(startX, width, height, ObstacleType.BLOCK));
            } else if (typeRoll < 80) {
                // spike as single cube
                obstacles.add(new Obstacle(startX, cubeSize, cubeSize, ObstacleType.SPIKE));
            } else if (typeRoll < 90) {
                // jump pad
                obstacles.add(new Obstacle(startX, cubeSize, 8, ObstacleType.PAD));
            } else {
                // Portal: random mode + speed
                String[] modes = new String[]{"Ship","Wave","Robot","UFO","Ball"};
                String mode = modes[rng.nextInt(modes.length)];
                int speedMult = 1 + rng.nextInt(4); // 1..4
                Obstacle p = new Obstacle(startX, cubeSize, cubeSize, ObstacleType.PORTAL);
                p.portalMode = mode; p.portalSpeed = speedMult;
                obstacles.add(p);
            }
        }

        // pads and orbs collision
        synchronized (orbs) {
            Iterator<Orb> oit = orbs.iterator();
            while (oit.hasNext()) {
                Orb ob = oit.next();
                if (ob.x < 60 + playerSize && ob.x + 12 > 60) {
                    if (playerY <= ob.y + 8) {
                        // collect
                        playerVel = 14f; // orb boost
                        oit.remove();
                        score.addAndGet(50);
                    }
                }
                ob.x -= Math.max(1, Math.round(baseSpeed.get() * deltaTime * 0.6f));
            }
        }

        // collision & landing (robust)
        int pLeft = 60;
        int pRight = 60 + playerSize;
        float playerBottom = playerY; // distance above ground
        float playerTop = playerY + playerSize;

        for (Obstacle o : obstacles) {
            int ox = o.x;
            int oLeft = ox;
            int oRight = ox + o.width;
            boolean horizOverlap = (oLeft < pRight && oRight > pLeft);
            if (!horizOverlap) continue;

            if (o.type == ObstacleType.SPIKE) {
                // spikes are 1x1 cubes; if player hits their area it's fatal
                if (playerBottom < o.height + 2) { onPlayerDie(); return; }
            } else if (o.type == ObstacleType.PAD) {
                // landing on pad (player bottom near pad height 0..padHeight)
                if (playerBottom <= o.height + 2 && prevPlayerY > o.height + 2) {
                    // trigger pad boost
                    playerVel = 16f;
                    playSound(ModSounds.LAPTOP_TRACKPAD.get());
                    // remove pad after use
                    o.x = -9999; // mark for removal
                }
            } else if (o.type == ObstacleType.PORTAL) {
                // collect portal and change mode/speed
                if (playerBottom <= o.height + 2) {
                    currentMode = o.portalMode != null ? o.portalMode : "Cube";
                    activeSpeedMultiplier = Math.max(1, o.portalSpeed);
                    // temporary visual effect: spawn particles
                    synchronized (particles) { for (int i=0;i<10;i++) particles.add(new Particle(80 + rng.nextInt(40), 40 + rng.nextInt(40), rng.nextFloat()*4f-2f, -rng.nextFloat()*2f, 20 + rng.nextInt(20), 0xFF88FF88)); }
                    o.x = -9999; // remove
                }
            } else if (o.type == ObstacleType.BLOCK) {
                // landing when descending and crossing top (o.height is multiple of cubeSize)
                if (prevPlayerY >= o.height && playerBottom <= o.height && playerVel <= 0f) {
                    // land on block
                    playerY = o.height;
                    playerVel = 0f;
                } else {
                    // any other overlap with block is fatal (side/bottom)
                    if (playerBottom < o.height) { onPlayerDie(); return; }
                }
            }
        }

        // remove obstacles flagged (pads)
        obstacles.removeIf(o -> o.x < -100);

        // update score
        score.addAndGet(1);
        if (score.get() > highScore.get()) highScore.set(score.get());

        // spawn particles occasionally
        synchronized (particles) {
            if (rng.nextInt(6) == 0) particles.add(new Particle(130, 120, rng.nextFloat()*2f-1f, -1.5f, 20 + rng.nextInt(20), 0xFFFFA000));
        }

        // difficulty
        if (score.get() % 500 == 0) baseSpeed.incrementAndGet();

        // --- Revised rotation & ground hitbox logic (distance-based rotation) ---
        final float epsilonGround = 0.0005f; // ground epsilon
        boolean onGround = playerY <= epsilonGround;

        if (!onGround) {
            for (Obstacle o : obstacles) {
                if (o.type == ObstacleType.BLOCK) {
                    if (Math.abs(playerY - o.height) <= epsilonGround && o.x < (60 + playerSize) && o.x + o.width > 60) { onGround = true; break; }
                }
            }
        }

        // Detect takeoff
        if (wasGrounded && !onGround) {
            rotatingInAir = true;
            airborneDistanceAccum = 0f;
            takeoffBaseAngle = cubeAngle % 360f;
            if (takeoffBaseAngle < 0f) takeoffBaseAngle += 360f;
        }

        // Horizontal world speed relative to player (same factor used for obstacle movement: *0.6f)
        float horizontalSpeed = Math.max(1, baseSpeed.get()) * activeSpeedMultiplier * 0.6f;

        if (rotatingInAir && !onGround) {
            // accumulate distance and convert to angle: every playerSize moved -> +90°
            airborneDistanceAccum += horizontalSpeed * deltaTime; // distance this frame
            float rotations = (airborneDistanceAccum / playerSize) * 90f; // degrees derived from distance
            cubeAngle = takeoffBaseAngle + rotations;
            cubeAngle %= 360f;
            if (cubeAngle < 0f) cubeAngle += 360f;
        }

        // Landing snap
        if (!wasGrounded && onGround) {
            rotatingInAir = false;
            cubeAngle = Math.round(cubeAngle / 90f) * 90f;
            cubeAngle %= 360f;
            if (cubeAngle < 0f) cubeAngle += 360f;
        }

        wasGrounded = onGround;
        // --- End revised rotation logic ---
    }

    private void onPlayerDie() {
        state = GameState.GAME_OVER;
        gameStarted.set(false);
        playSound(ModSounds.LAPTOP_TRACKPAD.get());
        synchronized (particles) {
            for (int i = 0; i < 40; i++) particles.add(new Particle(80 + rng.nextInt(40), 40 + rng.nextInt(40), rng.nextFloat()*6f-3f, -rng.nextFloat()*3f, 30 + rng.nextInt(30), 0xFFAAAAAA));
        }
    }

    private void resetGame() {
        playerY = 0f; prevPlayerY = 0f; playerVel = 0f;
        obstacles.clear(); synchronized (orbs) { orbs.clear(); }
        score.set(0); baseSpeed.set(5); gameStarted.set(false); state = GameState.MENU;
        synchronized (particles) { particles.clear(); }
        currentMode = "Cube"; activeSpeedMultiplier = 1;
        // reset rotation state
        cubeAngle = 0f; takeoffBaseAngle = 0f; airborneDistanceAccum = 0f; rotatingInAir = false; wasGrounded = true;
    }

    @Override
    public boolean mouseClicked(DraggableWindow window, double mouseRelX, double mouseRelY, int button) {
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8, cy = r[1] + 32;
        int cw = r[2] - 16, ch = r[3] - 40;

        if (state == GameState.MENU) {
            if (mouseRelX >= cx + cw/2 - 60 && mouseRelX <= cx + cw/2 + 60 && mouseRelY >= cy + ch/2 - 10 && mouseRelY <= cy + ch/2 + 20) {
                state = GameState.PLAYING; gameStarted.set(true); lastUpdateTime.set(System.currentTimeMillis()); return true;
            }
        } else if (state == GameState.PLAYING) {
            jumpRequested.set(true); return true;
        } else if (state == GameState.GAME_OVER) {
            if (mouseRelX >= cx + cw/2 - 60 && mouseRelX <= cx + cw/2 + 60 && mouseRelY >= cy + ch/2 - 10 && mouseRelY <= cy + ch/2 + 20) {
                resetGame(); state = GameState.PLAYING; gameStarted.set(true); lastUpdateTime.set(System.currentTimeMillis()); return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(DraggableWindow window, int keyCode, int scanCode, int modifiers) {
        if (keyCode == 32) {
            if (state == GameState.MENU) { state = GameState.PLAYING; gameStarted.set(true); }
            else if (state == GameState.GAME_OVER) { resetGame(); state = GameState.PLAYING; gameStarted.set(true); }
            else jumpRequested.set(true);
            lastUpdateTime.set(System.currentTimeMillis());
            return true;
        }
        return false;
    }

    private void playSound(net.minecraft.sounds.SoundEvent sound) { Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F)); }

    @Override public void tick() {}
    @Override public void mouseReleased(DraggableWindow window, double mouseRelX, double mouseRelY, int button) {}
    @Override public boolean mouseDragged(DraggableWindow window, double mouseRelX, double mouseRelY, double dx, double dy) { return false; }
    @Override public boolean charTyped(DraggableWindow window, char codePoint, int modifiers) { return false; }
    @Override
    public boolean onClose(DraggableWindow window) { this.window = null; return true; }

    private static class Obstacle {
        volatile int x; int width; int height; ObstacleType type;
        // portal info
        String portalMode; int portalSpeed;
        Obstacle(int x, int w, int h, ObstacleType t) { this.x = x; this.width = w; this.height = h; this.type = t; }
    }
    private enum ObstacleType { BLOCK, SPIKE, PAD, PORTAL }

    private static class Particle { float x,y,vx,vy; int life,maxLife; int col; Particle(float x,float y,float vx,float vy,int life,int col){this.x=x;this.y=y;this.vx=vx;this.vy=vy;this.life=life;this.maxLife=life;this.col=col;} }

    private static class Orb { int x; int y; Orb(int x,int y){this.x=x;this.y=y;} }
}
