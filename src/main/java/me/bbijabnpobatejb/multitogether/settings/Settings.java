package me.bbijabnpobatejb.multitogether.settings;

import lombok.Getter;
import lombok.Setter;

/**
 * Настройки живут в памяти, как и связи: меняются из меню и командой, сбрасываются перезапуском.
 */
@Getter
@Setter
public final class Settings {

    public static final double MIN_LENGTH = 1.5;
    public static final double MAX_LENGTH = 32;
    public static final double DEFAULT_LENGTH = 5;

    public static final double MIN_STIFFNESS = 0.05;
    public static final double MAX_STIFFNESS = 1.0;
    public static final double DEFAULT_STIFFNESS = 0.35;

    /**
     * Длина цепи между поясами игроков, в блоках.
     */
    private double chainLength = DEFAULT_LENGTH;

    /**
     * Какую долю перетяжки цепь выбирает за тик: мягче — пружинит, жёстче — дёргает.
     */
    private double stiffness = DEFAULT_STIFFNESS;

    private ChainStyle style = ChainStyle.IRON;
    private boolean chainVisible = true;

    /**
     * Присевший на земле игрок упирается: его тянет в разы слабее.
     */
    private boolean sneakAnchor = true;

    /**
     * Возрождаться рядом с живым напарником по цепи, а не у себя на точке.
     */
    private boolean respawnTogether = true;

    /**
     * Телепорт одного (порталы, команды, жемчуг далеко) переносит всю цепочку.
     */
    private boolean teleportTogether = true;

    private boolean sounds = true;

    public void setChainLength(double chainLength) {
        this.chainLength = Math.clamp(Math.round(chainLength * 2) / 2.0, MIN_LENGTH, MAX_LENGTH);
    }

    public void setStiffness(double stiffness) {
        this.stiffness = Math.clamp(Math.round(stiffness * 100) / 100.0, MIN_STIFFNESS, MAX_STIFFNESS);
    }

    /**
     * Дальше этого после телепорта напарника переносит следом.
     */
    public double followDistance() {
        return Math.max(chainLength * 2.5, 12);
    }

    /**
     * Разрыв, при котором физика уже не вытянет: убежавшего возвращает на длину цепи.
     */
    public double snapDistance() {
        return Math.max(chainLength * 3, chainLength + 10);
    }

    public void reset() {
        chainLength = DEFAULT_LENGTH;
        stiffness = DEFAULT_STIFFNESS;
        style = ChainStyle.IRON;
        chainVisible = true;
        sneakAnchor = true;
        respawnTogether = true;
        teleportTogether = true;
        sounds = true;
    }
}
