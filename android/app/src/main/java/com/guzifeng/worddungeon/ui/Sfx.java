package com.guzifeng.worddungeon.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * V11.8 游戏音效引擎：AudioTrack 实时合成短音效（正弦波 + 指数衰减包络），
 * 零外部音频资产、零权限。受设置页「打击音效」开关（wd_ui/sfx）与
 * 「音效音量」（wd_ui/vol_sfx，0–100）控制。全部短音 <300ms，异步播放不卡 UI。
 */
public final class Sfx {

    // 音效种类
    public static final int HIT = 0;      // 答对：短促上行双音
    public static final int CRIT = 1;     // 暴击/复仇：三音上行
    public static final int HURT = 2;     // 受击：低沉下行
    public static final int KILL = 3;     // 击杀：胜利琶音
    public static final int LEVEL = 4;    // 升级：长琶音 + 高八度
    public static final int FLIP = 5;     // 翻牌：快速下滑
    public static final int EPIC = 6;     // 史诗揭晓：明亮琶音
    public static final int WIN = 7;      // 结算胜利：琶音延音
    public static final int FAIL = 8;     // 结算失败：双下行音

    private static final int SR = 22050;                 // 采样率
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private static final byte[][] CACHE = new byte[9][]; // 预生成 PCM 缓存

    private Sfx() {}

    /** 播放指定音效（异步）。未开启音效开关时直接返回。 */
    public static void play(Context c, int kind) {
        try {
            if (kind < 0 || kind >= CACHE.length) return;
            if (c == null) return;
            SharedPreferences sp = c.getSharedPreferences("wd_ui", Context.MODE_PRIVATE);
            if (!sp.getBoolean("sfx", true)) return;   // 设置页「打击音效」开关
            int vol = sp.getInt("vol_sfx", 85);
            if (vol <= 0) return;
            float v = Math.max(0.05f, Math.min(1f, vol / 100f));
            byte[] pcm = CACHE[kind];
            if (pcm == null) {
                pcm = render(kind);
                CACHE[kind] = pcm;
            }
            final byte[] data = pcm;
            final float fv = v;
            POOL.execute(() -> {
                try {
                    AudioTrack at = new AudioTrack.Builder()
                            .setAudioAttributes(new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_GAME)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build())
                            .setAudioFormat(new AudioFormat.Builder()
                                    .setSampleRate(SR)
                                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                    .build())
                            .setBufferSizeInBytes(Math.max(data.length, SR))
                            .setTransferMode(AudioTrack.MODE_STATIC)
                            .build();
                    at.write(data, 0, data.length);
                    at.setVolume(fv);
                    at.play();
                    // 短音效，播放结束后释放
                    Thread.sleep(Math.max(80, data.length * 1000L / 2 / SR + 20));
                    try { at.stop(); } catch (Throwable ignore) {}
                    try { at.release(); } catch (Throwable ignore) {}
                } catch (Throwable ignore) {}
            });
        } catch (Throwable ignore) {}
    }

    /** 合成指定音效的 16bit 单声道 PCM。 */
    private static byte[] render(int kind) {
        short[] mix = new short[0];
        switch (kind) {
            case HIT:   mix = join(tone(660, 660, 55, 0.9), tone(880, 880, 70, 0.85)); break;
            case CRIT:  mix = join(tone(880, 880, 50, 0.9), tone(1100, 1100, 50, 0.85), tone(1320, 1320, 80, 0.8)); break;
            case HURT:  mix = tone(220, 140, 150, 0.85); break;
            case KILL:  mix = join(tone(523, 523, 65, 0.8), tone(659, 659, 65, 0.8),
                    tone(784, 784, 65, 0.8), tone(1046, 1046, 110, 0.8)); break;
            case LEVEL: mix = join(tone(523, 523, 80, 0.8), tone(659, 659, 80, 0.8),
                    tone(784, 784, 80, 0.8), tone(1046, 1046, 90, 0.85), tone(1318, 1318, 130, 0.85)); break;
            case FLIP:  mix = tone(900, 320, 95, 0.75); break;
            case EPIC:  mix = join(tone(1046, 1046, 70, 0.85), tone(1318, 1318, 70, 0.85),
                    tone(1568, 1568, 70, 0.85), tone(2093, 2093, 140, 0.85)); break;
            case WIN:   mix = join(tone(523, 523, 90, 0.8), tone(659, 659, 90, 0.8),
                    tone(784, 784, 90, 0.8), tone(1046, 1046, 140, 0.85),
                    tone(784, 784, 90, 0.75), tone(1046, 1046, 200, 0.8)); break;
            case FAIL:  mix = join(tone(330, 330, 110, 0.8), tone(262, 262, 110, 0.8), tone(196, 196, 160, 0.8)); break;
            default: return new byte[0];
        }
        byte[] out = new byte[mix.length * 2];
        for (int i = 0; i < mix.length; i++) {
            out[i * 2] = (byte) (mix[i] & 0xFF);
            out[i * 2 + 1] = (byte) ((mix[i] >> 8) & 0xFF);
        }
        return out;
    }

    /** 正弦（可滑音）+ 指数衰减包络。 */
    private static short[] tone(double f0, double f1, int ms, double amp) {
        int n = SR * ms / 1000;
        short[] s = new short[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double f = f0 + (f1 - f0) * i / n;
            double env = Math.exp(-3.2 * i / n);
            s[i] = (short) (amp * env * Math.sin(2 * Math.PI * phase) * 32767);
            phase += f / SR;
        }
        return s;
    }

    private static short[] join(short[]... parts) {
        int total = 0;
        for (short[] p : parts) total += p.length;
        short[] out = new short[total];
        int off = 0;
        for (short[] p : parts) {
            System.arraycopy(p, 0, out, off, p.length);
            off += p.length;
        }
        return out;
    }
}
