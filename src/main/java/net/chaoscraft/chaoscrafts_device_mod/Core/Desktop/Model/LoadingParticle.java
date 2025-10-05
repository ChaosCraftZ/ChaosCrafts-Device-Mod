package net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Model;

/**
 * Represents a particle for the loading screen animation.
 */
public class LoadingParticle {
    public float x, y, vx, vy, life;

    public LoadingParticle(float x, float y, float vx, float vy, float life) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.life = life;
    }
}