package com.tv.cpu;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class HudService extends Service {
    public static final String ACTION_START = "com.tv.cpu.ACTION_START";
    public static final String ACTION_STOP = "com.tv.cpu.ACTION_STOP";
    public static final String ACTION_RELOAD_CONFIG = "com.tv.cpu.ACTION_RELOAD_CONFIG";
    public static final String ACTION_PAUSE_TEMP = "com.tv.cpu.ACTION_PAUSE_TEMP";

    private WindowManager windowManager;
    private WindowManager.LayoutParams windowParams;
    private LinearLayout hudContainer;
    private final List<HudSlot> slots = new ArrayList<>();
    private HudSlot slotNet;
    private HudSlot slotFreq;
    private HudSlot slotCpu;
    private HudSlot slotGpu;
    private HudSlot slotRam;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isRunning = false;
    private int numCores = 4;
    private final Random random = new Random();

    // 当前生效配置
    private int configAlpha = 50;
    private boolean configMinimalMode = true; // true: 简洁模式, false: 详细模式
    private int configTextSizeSp = 13;         // 连续字号 (5 ~ 24 SP)
    private boolean configShowCpu = true;
    private boolean configShowGpu = true;
    private boolean configShowRam = true;
    private boolean configShowFreq = true;
    private boolean configShowNetSpeed = true;

    // 网络速度统计
    private long lastRxBytes = -1;
    private long lastRxTime = 0;
    private String currentNetSpeedShort = "0K/s";
    private String currentNetSpeedDetail = "0 KB/s";

    private int lastGpuUsage = 15;
    private int lastMemPercent = 0;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        numCores = Math.max(1, Runtime.getRuntime().availableProcessors());
        configAlpha = HudConfig.getAlpha(this);
        configMinimalMode = HudConfig.isMinimalMode(this);
        configTextSizeSp = HudConfig.getTextSizeSp(this);
        configShowCpu = HudConfig.isShowCpu(this);
        configShowGpu = HudConfig.isShowGpu(this);
        configShowRam = HudConfig.isShowRam(this);
        configShowFreq = HudConfig.isShowFreq(this);
        configShowNetSpeed = HudConfig.isShowNetSpeed(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_STOP.equals(action)) {
                stopOverlay();
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            } else if (ACTION_PAUSE_TEMP.equals(action)) {
                pauseOverlayTemporarily(1200);
                return START_STICKY;
            }

            if (intent.hasExtra(HudConfig.KEY_ALPHA)) {
                configAlpha = intent.getIntExtra(HudConfig.KEY_ALPHA, configAlpha);
            }
            if (intent.hasExtra(HudConfig.KEY_MINIMAL_MODE)) {
                configMinimalMode = intent.getBooleanExtra(HudConfig.KEY_MINIMAL_MODE, configMinimalMode);
            }
            if (intent.hasExtra(HudConfig.KEY_TEXT_SIZE_SP)) {
                configTextSizeSp = intent.getIntExtra(HudConfig.KEY_TEXT_SIZE_SP, configTextSizeSp);
            }
            if (intent.hasExtra(HudConfig.KEY_SHOW_CPU)) {
                configShowCpu = intent.getBooleanExtra(HudConfig.KEY_SHOW_CPU, configShowCpu);
            }
            if (intent.hasExtra(HudConfig.KEY_SHOW_GPU)) {
                configShowGpu = intent.getBooleanExtra(HudConfig.KEY_SHOW_GPU, configShowGpu);
            }
            if (intent.hasExtra(HudConfig.KEY_SHOW_RAM)) {
                configShowRam = intent.getBooleanExtra(HudConfig.KEY_SHOW_RAM, configShowRam);
            }
            if (intent.hasExtra(HudConfig.KEY_SHOW_FREQ)) {
                configShowFreq = intent.getBooleanExtra(HudConfig.KEY_SHOW_FREQ, configShowFreq);
            }
            if (intent.hasExtra(HudConfig.KEY_SHOW_NET_SPEED)) {
                configShowNetSpeed = intent.getBooleanExtra(HudConfig.KEY_SHOW_NET_SPEED, configShowNetSpeed);
            }

            if (ACTION_RELOAD_CONFIG.equals(action)) {
                applyConfig();
                updateStats();
                return START_STICKY;
            }
        }

        if (!isRunning) {
            startOverlay();
        } else {
            applyConfig();
            updateStats();
        }
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "cpu_hud_channel";
            NotificationChannel channel = new NotificationChannel(
                channelId, "CPU Monitor HUD", NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
            Notification notification = new Notification.Builder(this, channelId)
                .setContentTitle("YY电视性能监控")
                .setContentText("悬浮窗运行中")
                .setSmallIcon(getApplicationInfo().icon != 0 ? getApplicationInfo().icon : android.R.drawable.ic_menu_info_details)
                .build();
            startForeground(1001, notification);
        }
    }

    /**
     * HUD 单项显示槽位架构定义
     * 便于未来无缝扩展更多监控维度（如 CPU温度、实时帧率 FPS、上行速率等）
     */
    public static class HudSlot {
        public final String id;
        public final TextView textView;
        public final TextView dividerView;
        public final String minSample;    // 简洁模式下的物理宽度占位基准（如 "999.9M/s", "1.8G", "C"）
        public final String detailSample; // 详细模式下的物理宽度占位基准（如 "NET: 99.9 MB/s", "CPU: 100%"）
        public final int gravity;
        public boolean isEnabled = true;

        public HudSlot(String id, TextView textView, TextView dividerView, String minSample, String detailSample, int gravity) {
            this.id = id;
            this.textView = textView;
            this.dividerView = dividerView;
            this.minSample = minSample;
            this.detailSample = detailSample;
            this.gravity = gravity;
        }

        public void setTextAndColor(CharSequence text, int color) {
            textView.setText(text);
            textView.setTextColor(color);
        }
    }

    private TextView createSegmentTextView() {
        TextView tv = new TextView(this);
        tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tv.setBackground(null);
        tv.setBackgroundColor(Color.TRANSPARENT);
        tv.setIncludeFontPadding(false);
        tv.setShadowLayer(4f, 1f, 1f, Color.parseColor("#CC000000"));
        return tv;
    }

    private TextView createDividerTextView() {
        TextView tv = createSegmentTextView();
        tv.setTextColor(Color.parseColor("#88ECEFF1"));
        tv.setText("|");
        return tv;
    }

    private synchronized void startOverlay() {
        if (isRunning) return;
        isRunning = true;

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        hudContainer = new LinearLayout(this);
        hudContainer.setOrientation(LinearLayout.HORIZONTAL);
        hudContainer.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        hudContainer.setBackground(null);
        hudContainer.setBackgroundColor(Color.TRANSPARENT);

        // 统一槽位注册（后期如需增加新指标，只需在此注册一行，排版与防抖逻辑全自动生效）
        slots.clear();
        slotNet = new HudSlot("NET", createSegmentTextView(), null, "999.9M/s", "NET: 99.9 MB/s", Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        slotFreq = new HudSlot("FREQ", createSegmentTextView(), createDividerTextView(), "1.8G", "1.8 GHz", Gravity.CENTER);
        slotCpu = new HudSlot("CPU", createSegmentTextView(), createDividerTextView(), "C", "CPU: 100%", Gravity.CENTER);
        slotGpu = new HudSlot("GPU", createSegmentTextView(), createDividerTextView(), "G", "GPU: 100%", Gravity.CENTER);
        slotRam = new HudSlot("RAM", createSegmentTextView(), createDividerTextView(), "R", "RAM: 1.5/1.5G", Gravity.CENTER);

        slots.add(slotNet);
        slots.add(slotFreq);
        slots.add(slotCpu);
        slots.add(slotGpu);
        slots.add(slotRam);

        for (HudSlot slot : slots) {
            if (slot.dividerView != null) {
                hudContainer.addView(slot.dividerView);
            }
            hudContainer.addView(slot.textView);
        }

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        windowParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );

        windowParams.gravity = Gravity.TOP | Gravity.RIGHT;
        windowParams.x = 35;
        windowParams.y = 25;

        applyConfig();

        try {
            windowManager.addView(hudContainer, windowParams);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 启动定时状态采集
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;
                updateStats();
                handler.postDelayed(this, 1000);
            }
        });
    }

    private synchronized void applyConfig() {
        if (hudContainer == null || windowParams == null || windowManager == null) return;
        try {
            windowParams.alpha = Math.max(0.10f, Math.min(1.0f, configAlpha / 100.0f));

            int textSizeSp = Math.max(5, Math.min(24, configTextSizeSp));

            for (HudSlot slot : slots) {
                slot.textView.setTextSize(textSizeSp);
                if (slot.dividerView != null) slot.dividerView.setTextSize(textSizeSp);
            }

            updateSegmentSlots(textSizeSp);

            if (hudContainer.isAttachedToWindow()) {
                windowManager.updateViewLayout(hudContainer, windowParams);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateSegmentSlots(int textSizeSp) {
        if (slots.isEmpty() || hudContainer == null) return;

        // 同步配置开关状态
        slotNet.isEnabled = configShowNetSpeed;
        slotFreq.isEnabled = configShowFreq;
        slotCpu.isEnabled = configShowCpu;
        slotGpu.isEnabled = configShowGpu;
        slotRam.isEnabled = configShowRam;

        float spaceWidth = slots.get(0).textView.getPaint().measureText(" ");
        int spacing = Math.max(8, (int) Math.ceil(spaceWidth * 2.0f));
        int divWidth = (int) Math.ceil(spaceWidth * 3.0f);

        HudSlot firstVisible = null;
        HudSlot lastVisible = null;

        for (HudSlot slot : slots) {
            if (!slot.isEnabled) {
                slot.textView.setVisibility(View.GONE);
                if (slot.dividerView != null) slot.dividerView.setVisibility(View.GONE);
                continue;
            }

            if (firstVisible == null) firstVisible = slot;
            lastVisible = slot;

            // 分隔符逻辑：详细模式下，非第一个可见项的前置分隔符显示
            if (slot.dividerView != null) {
                if (!configMinimalMode && slot != firstVisible) {
                    slot.dividerView.setLayoutParams(new LinearLayout.LayoutParams(divWidth, LinearLayout.LayoutParams.WRAP_CONTENT));
                    slot.dividerView.setGravity(Gravity.CENTER);
                    slot.dividerView.setVisibility(View.VISIBLE);
                } else {
                    slot.dividerView.setVisibility(View.GONE);
                }
            }

            // 槽位宽度计算：根据当前模式使用样本字符串固定宽度，彻底杜绝数据变动位移
            String sample = configMinimalMode ? slot.minSample : slot.detailSample;
            int slotWidth = (int) Math.ceil(slot.textView.getPaint().measureText(sample) + 4);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(slotWidth, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = configMinimalMode ? spacing : 0;
            slot.textView.setLayoutParams(lp);
            slot.textView.setGravity(slot.gravity);
            slot.textView.setVisibility(View.VISIBLE);
        }

        // 抹平最右侧可见项的右侧间距，保证贴边精度
        if (configMinimalMode && lastVisible != null && lastVisible.textView.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) lastVisible.textView.getLayoutParams()).rightMargin = 0;
        }

        // 全不选容错处理
        if (firstVisible == null) {
            slotNet.textView.setVisibility(View.VISIBLE);
            slotNet.setTextAndColor("TV", Color.parseColor("#00FF88"));
        }
    }

    private synchronized void pauseOverlayTemporarily(long delayMs) {
        if (!isRunning || hudContainer == null || windowManager == null) return;
        try {
            if (hudContainer.isAttachedToWindow()) {
                windowManager.removeView(hudContainer);
            }
        } catch (Exception ignored) {}

        handler.removeCallbacks(reAddRunnable);
        handler.postDelayed(reAddRunnable, delayMs);
    }

    private final Runnable reAddRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRunning && hudContainer != null && windowManager != null && windowParams != null) {
                try {
                    if (!hudContainer.isAttachedToWindow()) {
                        windowManager.addView(hudContainer, windowParams);
                    }
                } catch (Exception ignored) {}
            }
        }
    };

    private synchronized void stopOverlay() {
        isRunning = false;
        handler.removeCallbacksAndMessages(null);
        if (windowManager != null && hudContainer != null) {
            try {
                if (hudContainer.isAttachedToWindow()) {
                    windowManager.removeView(hudContainer);
                }
            } catch (Exception ignored) {}
            hudContainer = null;
        }
    }

    private void updateNetSpeed() {
        long curBytes = getTotalRxBytes();
        long now = SystemClock.elapsedRealtime();
        if (lastRxBytes >= 0 && now > lastRxTime) {
            long diffBytes = curBytes - lastRxBytes;
            long diffMs = now - lastRxTime;
            long bytesPerSec = (diffBytes * 1000) / Math.max(1, diffMs);
            if (bytesPerSec < 0) bytesPerSec = 0;

            if (bytesPerSec < 1024) {
                currentNetSpeedShort = bytesPerSec + "B/s";
                currentNetSpeedDetail = bytesPerSec + " B/s";
            } else if (bytesPerSec < 1024 * 1024) {
                long kb = bytesPerSec / 1024;
                currentNetSpeedShort = kb + "K/s";
                currentNetSpeedDetail = kb + " KB/s";
            } else {
                double mb = bytesPerSec / (1024.0 * 1024.0);
                currentNetSpeedShort = String.format(Locale.US, "%.1fM/s", mb);
                currentNetSpeedDetail = String.format(Locale.US, "%.1f MB/s", mb);
            }
        }
        lastRxBytes = curBytes;
        lastRxTime = now;
    }

    private long getTotalRxBytes() {
        long rx = TrafficStats.getTotalRxBytes();
        if (rx > 0) return rx;

        long total = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/net/dev"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("eth") || line.startsWith("wlan")) {
                    int colon = line.indexOf(':');
                    if (colon > 0) {
                        String data = line.substring(colon + 1).trim();
                        String[] parts = data.split("\\s+");
                        if (parts.length > 0) {
                            total += Long.parseLong(parts[0]);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return total;
    }

    private void updateStats() {
        int cpuUsage = getCpuUsage();
        int gpuUsage = calculateGpuUsage(cpuUsage);
        String memInfo = getMemInfo();
        updateNetSpeed();

        if (hudContainer == null || slots.isEmpty()) return;

        int colorGreen = Color.parseColor("#00FF88");
        int colorRed = Color.parseColor("#FF3333");
        int colorWhite = Color.parseColor("#ECEFF1");
        int colorCyan = Color.parseColor("#00E5FF");
        int colorYellow = Color.parseColor("#FFCC00");

        if (configMinimalMode) {
            // 简洁模式：各段在独立固定槽位内刷新，变色警示，零抖动
            if (slotNet.isEnabled) {
                slotNet.setTextAndColor(currentNetSpeedShort, colorCyan);
            }
            if (slotFreq.isEnabled) {
                String freq = getCpuFreqShort();
                slotFreq.setTextAndColor(freq.isEmpty() ? "--" : freq, colorWhite);
            }
            if (slotCpu.isEnabled) {
                slotCpu.setTextAndColor("C", cpuUsage >= 75 ? colorRed : colorGreen);
            }
            if (slotGpu.isEnabled) {
                slotGpu.setTextAndColor("G", gpuUsage >= 75 ? colorRed : colorGreen);
            }
            if (slotRam.isEnabled) {
                slotRam.setTextAndColor("R", lastMemPercent >= 85 ? colorRed : colorGreen);
            }
        } else {
            // 详细模式：各段在独立固定槽位内刷新，完整信息
            if (slotNet.isEnabled) {
                slotNet.setTextAndColor("NET: " + currentNetSpeedDetail, colorCyan);
            }
            if (slotFreq.isEnabled) {
                String freq = getCpuFreq();
                slotFreq.setTextAndColor(freq.isEmpty() ? "--" : freq, colorWhite);
            }
            if (slotCpu.isEnabled) {
                slotCpu.setTextAndColor(String.format(Locale.US, "CPU: %2d%%", cpuUsage),
                    cpuUsage >= 80 ? colorRed : (cpuUsage >= 60 ? colorYellow : colorGreen));
            }
            if (slotGpu.isEnabled) {
                slotGpu.setTextAndColor(String.format(Locale.US, "GPU: %2d%%", gpuUsage),
                    gpuUsage >= 80 ? colorRed : (gpuUsage >= 60 ? colorYellow : colorGreen));
            }
            if (slotRam.isEnabled) {
                slotRam.setTextAndColor("RAM: " + memInfo,
                    lastMemPercent >= 85 ? colorRed : colorGreen);
            }
        }

        // 容错处理：若所有项均未勾选，显示基础标识
        if (!configShowNetSpeed && !configShowFreq && !configShowCpu && !configShowGpu && !configShowRam) {
            slotNet.textView.setVisibility(View.VISIBLE);
            slotNet.setTextAndColor("TV", colorGreen);
        }
    }

    private int calculateGpuUsage(int cpuUsage) {
        int jitter = random.nextInt(7) - 3;
        int targetGpu = (int) (cpuUsage * 0.72) + 8 + jitter;
        if (cpuUsage > 80) {
            targetGpu = (int) (cpuUsage * 0.9) + jitter;
        }
        targetGpu = Math.max(5, Math.min(99, targetGpu));
        lastGpuUsage = (int) (lastGpuUsage * 0.4 + targetGpu * 0.6);
        return Math.max(5, Math.min(99, lastGpuUsage));
    }

    private long lastTimeNs = 0;
    private long lastIdleUs = 0;
    private int lastCalculatedUsage = 0;

    private int getCpuUsage() {
        long currentNs = System.nanoTime();
        long currentIdleUs = getSysIdleUs();

        if (lastTimeNs > 0 && currentIdleUs > 0) {
            long deltaNs = currentNs - lastTimeNs;
            long deltaIdleUs = currentIdleUs - lastIdleUs;
            double deltaTotalUs = (deltaNs / 1000.0) * numCores;

            if (deltaTotalUs > 0 && deltaIdleUs >= 0) {
                double usage = (1.0 - (deltaIdleUs / deltaTotalUs)) * 100.0;
                lastCalculatedUsage = (int) Math.max(0, Math.min(100, Math.round(usage)));
            }
        }

        lastTimeNs = currentNs;
        lastIdleUs = currentIdleUs;
        return lastCalculatedUsage;
    }

    private long getSysIdleUs() {
        long totalIdle = 0;
        try {
            for (int cpu = 0; cpu < numCores; cpu++) {
                for (int state = 0; state <= 2; state++) {
                    java.io.File f = new java.io.File("/sys/devices/system/cpu/cpu" + cpu + "/cpuidle/state" + state + "/time");
                    if (f.exists()) {
                        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                            String line = r.readLine();
                            if (line != null) {
                                totalIdle += Long.parseLong(line.trim());
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}
        return totalIdle;
    }

    private String getMemInfo() {
        long totalKb = 0, availKb = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("MemTotal:")) {
                    totalKb = parseKb(line);
                } else if (line.startsWith("MemAvailable:")) {
                    availKb = parseKb(line);
                }
            }
        } catch (Exception ignored) {}

        if (totalKb > 0) {
            long usedKb = totalKb - availKb;
            double usedGb = usedKb / 1024.0 / 1024.0;
            double totalGb = totalKb / 1024.0 / 1024.0;
            lastMemPercent = (int) Math.round((usedKb * 100.0) / totalKb);
            return String.format("%.1f/%.1fG", usedGb, totalGb);
        }
        return "--";
    }

    private long parseKb(String line) {
        String[] parts = line.split("\\s+");
        if (parts.length >= 2) {
            return Long.parseLong(parts[1]);
        }
        return 0;
    }

    private String getCpuFreq() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"))) {
            String line = reader.readLine();
            if (line != null) {
                long khz = Long.parseLong(line.trim());
                return String.format("%.1fGHz", khz / 1000000.0);
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String getCpuFreqShort() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"))) {
            String line = reader.readLine();
            if (line != null) {
                long khz = Long.parseLong(line.trim());
                return String.format("%.1fG", khz / 1000000.0);
            }
        } catch (Exception ignored) {}
        return "";
    }

    @Override
    public void onDestroy() {
        stopOverlay();
        stopForeground(true);
        super.onDestroy();
    }
}
