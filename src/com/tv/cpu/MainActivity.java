package com.tv.cpu;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    // 运行服务条目数据结构
    private static class ServiceItem {
        final String pkg;
        final String label;
        final int category; // 1: 绝对不能精简(红), 2: 推荐精简(绿), 3: 正常应用(蓝)

        ServiceItem(String pkg, String label, int category) {
            this.pkg = pkg;
            this.label = label;
            this.category = category;
        }
    }

    // 服务开关与状态
    private CheckBox cbServiceToggle;
    private TextView tvStatus;

    // 系统信息控件
    private TextView tvCpuInfo;
    private TextView tvGpuInfo;
    private TextView tvMemInfo;
    private TextView tvStorageInfo;
    private TextView tvEthInfo;
    private TextView tvWifiInfo;
    private TextView tvEthTitle;
    private TextView tvWifiTitle;
    private TextView tvServicesTitle;
    private ScrollView leftScrollView;
    private LinearLayout servicesContainer;
    private CheckBox cbShowServicePkg;
    private final List<ServiceItem> cachedServiceList = new ArrayList<>();

    // 显示设置项控件
    private CheckBox cbShowCpu;
    private CheckBox cbShowGpu;
    private CheckBox cbShowRam;
    private CheckBox cbShowFreq;
    private CheckBox cbShowNetSpeed;

    private CheckBox cbModeMinimal;
    private CheckBox cbModeDetail;

    private Button btnAlpha;
    private Button btnSize;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean isDestroyed = false;
    private boolean isInternalChecking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0F141C"));
        root.setPadding(40, 20, 40, 20);

        // 1. 顶部大标题
        TextView tvTitle = new TextView(this);
        tvTitle.setText("YY电视性能监控");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(26);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(0, 0, 0, 15);
        root.addView(tvTitle);

        // 2. 核心两栏主容器 (占满剩余纵向空间)
        LinearLayout mainCols = new LinearLayout(this);
        mainCols.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams colsLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f
        );
        mainCols.setLayoutParams(colsLp);
        root.addView(mainCols);

        // ====================== 左栏：独立滚动视图 (左边单独下拉，右边不动) ======================
        leftScrollView = new ScrollView(this);
        leftScrollView.setFillViewport(true);
        leftScrollView.setVerticalScrollBarEnabled(true);
        LinearLayout.LayoutParams leftScrollParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f);
        leftScrollParams.setMargins(0, 0, 20, 0);
        leftScrollView.setLayoutParams(leftScrollParams);

        LinearLayout leftCard = createCardLayout();
        leftCard.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        leftScrollView.addView(leftCard);
        mainCols.addView(leftScrollView);

        TextView leftTitle = createSectionTitle("📊 当前系统信息");
        leftCard.addView(leftTitle);

        tvCpuInfo = createInfoRow(leftCard, "CPU", "正在检测...");
        tvGpuInfo = createInfoRow(leftCard, "GPU", "正在检测...");
        tvMemInfo = createInfoRow(leftCard, "内存", "正在检测...");
        tvStorageInfo = createInfoRow(leftCard, "存储情况", "正在检测...");

        TextView[] ethRow = createInfoRowWithTitle(leftCard, "有线网卡", "正在检测...");
        tvEthTitle = ethRow[0];
        tvEthInfo = ethRow[1];

        TextView[] wifiRow = createInfoRowWithTitle(leftCard, "无线网卡", "正在检测...");
        tvWifiTitle = wifiRow[0];
        tvWifiInfo = wifiRow[1];

        // 当前加载服务 (带勾选框切换名称/包名)
        LinearLayout servicesRow = new LinearLayout(this);
        servicesRow.setOrientation(LinearLayout.VERTICAL);
        servicesRow.setPadding(0, 4, 0, 6);

        LinearLayout servicesHeader = new LinearLayout(this);
        servicesHeader.setOrientation(LinearLayout.HORIZONTAL);
        servicesHeader.setGravity(Gravity.CENTER_VERTICAL);
        servicesHeader.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        tvServicesTitle = new TextView(this);
        tvServicesTitle.setText("• 当前加载服务：");
        tvServicesTitle.setTextColor(Color.parseColor("#90A4AE"));
        tvServicesTitle.setTextSize(13);
        tvServicesTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        tvServicesTitle.setLayoutParams(titleLp);
        servicesHeader.addView(tvServicesTitle);

        cbShowServicePkg = createCompactCheckBox("显示包名", false, new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                renderServicesList();
            }
        });
        cbShowServicePkg.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (servicesContainer != null && servicesContainer.getChildCount() > 0) {
                        final View child = servicesContainer.getChildAt(0);
                        if (child != null) {
                            child.requestFocus();
                            if (leftScrollView != null) {
                                leftScrollView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Rect rect = new Rect();
                                        child.getDrawingRect(rect);
                                        leftScrollView.offsetDescendantRectToMyCoords(child, rect);
                                        rect.bottom += 60;
                                        leftScrollView.requestChildRectangleOnScreen(child, rect, false);
                                    }
                                });
                            }
                            return true;
                        }
                    }
                }
                return false;
            }
        });
        LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cbLp.setMargins(10, 0, 0, 0);
        cbShowServicePkg.setLayoutParams(cbLp);
        cbShowServicePkg.setPadding(8, 4, 8, 4);
        servicesHeader.addView(cbShowServicePkg);

        servicesRow.addView(servicesHeader);

        // 分类图例说明
        LinearLayout legendRow = new LinearLayout(this);
        legendRow.setOrientation(LinearLayout.HORIZONTAL);
        legendRow.setPadding(8, 3, 0, 8);

        TextView tvLegRed = new TextView(this);
        tvLegRed.setText("● 核心勿动 ");
        tvLegRed.setTextColor(Color.parseColor("#FF5252"));
        tvLegRed.setTextSize(11);
        legendRow.addView(tvLegRed);

        TextView tvLegGreen = new TextView(this);
        tvLegGreen.setText(" ● 建议精简 ");
        tvLegGreen.setTextColor(Color.parseColor("#00E676"));
        tvLegGreen.setTextSize(11);
        legendRow.addView(tvLegGreen);

        TextView tvLegBlue = new TextView(this);
        tvLegBlue.setText(" ● 正常应用");
        tvLegBlue.setTextColor(Color.parseColor("#40C4FF"));
        tvLegBlue.setTextSize(11);
        legendRow.addView(tvLegBlue);

        servicesRow.addView(legendRow);

        servicesContainer = new LinearLayout(this);
        servicesContainer.setOrientation(LinearLayout.VERTICAL);
        servicesContainer.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        servicesRow.addView(servicesContainer);

        leftCard.addView(servicesRow);

        // ====================== 右栏：开关与显示设置 (固定在右侧不动) ======================
        LinearLayout rightCard = createCardLayout();
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.25f);
        rightCard.setLayoutParams(rightParams);
        mainCols.addView(rightCard);

        // 右上半部分：服务控制（开启后面加打钩方框）
        TextView rightTitleService = createSectionTitle("⚡ 服务控制");
        rightCard.addView(rightTitleService);

        boolean running = isServiceRunning();
        cbServiceToggle = createStyledCheckBox("开启性能监控悬浮窗", running, new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isInternalChecking) return;
                if (isChecked) {
                    if (checkOverlayPermission()) {
                        startHud();
                    } else {
                        isInternalChecking = true;
                        cbServiceToggle.setChecked(false);
                        isInternalChecking = false;
                    }
                } else {
                    stopHud();
                }
            }
        });
        cbServiceToggle.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (cbShowServicePkg != null) {
                        cbShowServicePkg.requestFocus();
                        return true;
                    }
                }
                return false;
            }
        });
        rightCard.addView(cbServiceToggle);

        tvStatus = new TextView(this);
        tvStatus.setTextSize(13);
        tvStatus.setPadding(10, 5, 0, 15);
        rightCard.addView(tvStatus);
        updateStatus(running);

        // 分割线
        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#2A3442"));
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2);
        divParams.setMargins(0, 5, 0, 15);
        divider.setLayoutParams(divParams);
        rightCard.addView(divider);

        // 右下半部分：显示设置
        TextView rightTitleDisplay = createSectionTitle("🎛️ 显示设置");
        rightCard.addView(rightTitleDisplay);

        // 1. 自选显示内容 (方框打钩)
        TextView tvSubTitleContent = createSubTitle("• 自选显示内容：");
        rightCard.addView(tvSubTitleContent);

        // 单行不分行显示全部 5 项，等宽居中严格对齐：网络速度 -> CPU频率 -> CPU -> GPU -> RAM
        LinearLayout rowContent = new LinearLayout(this);
        rowContent.setOrientation(LinearLayout.HORIZONTAL);
        rowContent.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // 1. 网络速度 (第一项)
        cbShowNetSpeed = createCompactCheckBox("网络速度", HudConfig.isShowNetSpeed(this), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                HudConfig.setShowNetSpeed(MainActivity.this, isChecked);
                notifyConfigChanged();
            }
        });

        // 2. CPU频率 (第二项)
        cbShowFreq = createCompactCheckBox("CPU频率", HudConfig.isShowFreq(this), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                HudConfig.setShowFreq(MainActivity.this, isChecked);
                notifyConfigChanged();
            }
        });

        // 3. CPU (第三项)
        cbShowCpu = createCompactCheckBox("CPU", HudConfig.isShowCpu(this), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                HudConfig.setShowCpu(MainActivity.this, isChecked);
                notifyConfigChanged();
            }
        });

        // 4. GPU (第四项)
        cbShowGpu = createCompactCheckBox("GPU", HudConfig.isShowGpu(this), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                HudConfig.setShowGpu(MainActivity.this, isChecked);
                notifyConfigChanged();
            }
        });

        // 5. RAM (第五项)
        cbShowRam = createCompactCheckBox("RAM", HudConfig.isShowRam(this), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                HudConfig.setShowRam(MainActivity.this, isChecked);
                notifyConfigChanged();
            }
        });

        rowContent.addView(cbShowNetSpeed);
        rowContent.addView(cbShowFreq);
        rowContent.addView(cbShowCpu);
        rowContent.addView(cbShowGpu);
        rowContent.addView(cbShowRam);
        rightCard.addView(rowContent);

        // 2. 显示模式：简洁 vs 详细 (用方框勾选)
        TextView tvSubTitleMode = createSubTitle("• 显示模式：");
        rightCard.addView(tvSubTitleMode);

        LinearLayout rowModes = new LinearLayout(this);
        rowModes.setOrientation(LinearLayout.HORIZONTAL);
        rowModes.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        boolean isMinimal = HudConfig.isMinimalMode(this);

        cbModeMinimal = createCompactCheckBox("简洁模式", isMinimal, null);
        cbModeDetail = createCompactCheckBox("详细模式", !isMinimal, null);

        cbModeMinimal.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    cbModeDetail.setChecked(false);
                    HudConfig.setMinimalMode(MainActivity.this, true);
                    notifyConfigChanged();
                } else if (!cbModeDetail.isChecked()) {
                    cbModeMinimal.setChecked(true); // 保证至少保留一个
                }
            }
        });

        cbModeDetail.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    cbModeMinimal.setChecked(false);
                    HudConfig.setMinimalMode(MainActivity.this, false);
                    notifyConfigChanged();
                } else if (!cbModeMinimal.isChecked()) {
                    cbModeDetail.setChecked(true); // 保证至少保留一个
                }
            }
        });

        rowModes.addView(cbModeMinimal);
        rowModes.addView(cbModeDetail);
        rightCard.addView(rowModes);

        // 3. 透明度调节项（按左右方向键微调）
        btnAlpha = createSettingButton();
        updateAlphaButtonText();
        btnAlpha.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int current = HudConfig.getAlpha(MainActivity.this);
                current += 10;
                if (current > 100) current = 20;
                HudConfig.setAlpha(MainActivity.this, current);
                updateAlphaButtonText();
                notifyConfigChanged();
            }
        });
        btnAlpha.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    int current = HudConfig.getAlpha(MainActivity.this);
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        current = Math.max(10, current - 10);
                        HudConfig.setAlpha(MainActivity.this, current);
                        updateAlphaButtonText();
                        notifyConfigChanged();
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        current = Math.min(100, current + 10);
                        HudConfig.setAlpha(MainActivity.this, current);
                        updateAlphaButtonText();
                        notifyConfigChanged();
                        return true;
                    }
                }
                return false;
            }
        });
        rightCard.addView(btnAlpha);

        // 4. 窗口大小自由调节项（按左右方向键微调，范围 5 ~ 24 SP）
        btnSize = createSettingButton();
        updateSizeButtonText();
        btnSize.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int current = HudConfig.getTextSizeSp(MainActivity.this);
                current += 2;
                if (current > 24) current = 5;
                HudConfig.setTextSizeSp(MainActivity.this, current);
                updateSizeButtonText();
                notifyConfigChanged();
            }
        });
        btnSize.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    int current = HudConfig.getTextSizeSp(MainActivity.this);
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        current = Math.max(5, current - 1); // 最小值到 5 SP
                        HudConfig.setTextSizeSp(MainActivity.this, current);
                        updateSizeButtonText();
                        notifyConfigChanged();
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        current = Math.min(24, current + 1);
                        HudConfig.setTextSizeSp(MainActivity.this, current);
                        updateSizeButtonText();
                        notifyConfigChanged();
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        // 处于最底部，拦截下按键，防止焦点跳回顶部
                        return true;
                    }
                }
                return false;
            }
        });
        rightCard.addView(btnSize);

        // 占位 Spacer：将作者与项目信息推至右侧窗口最下方
        View spacer = new View(this);
        LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f
        );
        spacer.setLayoutParams(spacerLp);
        rightCard.addView(spacer);

        // 右侧窗口最底部两行信息（极小字号 9 SP）
        LinearLayout footerLayout = new LinearLayout(this);
        footerLayout.setOrientation(LinearLayout.VERTICAL);
        footerLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        footerLayout.setPadding(0, 10, 0, 4);

        TextView tvLine1 = new TextView(this);
        tvLine1.setText("GitHub: https://github.com/ichbinyytak");
        tvLine1.setTextColor(Color.parseColor("#607D8B"));
        tvLine1.setTextSize(9);
        tvLine1.setGravity(Gravity.CENTER_HORIZONTAL);
        tvLine1.setFocusable(false);
        footerLayout.addView(tvLine1);

        TextView tvLine2 = new TextView(this);
        tvLine2.setText("开源地址: https://github.com/ichbinyytak/YY_TvMonitor");
        tvLine2.setTextColor(Color.parseColor("#607D8B"));
        tvLine2.setTextSize(9);
        tvLine2.setGravity(Gravity.CENTER_HORIZONTAL);
        tvLine2.setFocusable(false);
        tvLine2.setPadding(0, 3, 0, 0);
        footerLayout.addView(tvLine2);

        rightCard.addView(footerLayout);

        setContentView(root);

        // 加载静态信息与启动定时器
        loadStaticSystemInfo();
        startDynamicInfoLoop();
    }

    private void updateAlphaButtonText() {
        int alpha = HudConfig.getAlpha(this);
        btnAlpha.setText(String.format("透明度：◀  %d %%  ▶  (按左右方向键微调)", alpha));
    }

    private void updateSizeButtonText() {
        int size = HudConfig.getTextSizeSp(this);
        btnSize.setText(String.format("窗口大小：◀  %d SP  ▶  (最小 5 SP，按左右微调)", size));
    }

    private void notifyConfigChanged() {
        Intent serviceIntent = new Intent(this, HudService.class);
        serviceIntent.setAction(HudService.ACTION_RELOAD_CONFIG);
        serviceIntent.putExtra(HudConfig.KEY_ALPHA, HudConfig.getAlpha(this));
        serviceIntent.putExtra(HudConfig.KEY_MINIMAL_MODE, HudConfig.isMinimalMode(this));
        serviceIntent.putExtra(HudConfig.KEY_TEXT_SIZE_SP, HudConfig.getTextSizeSp(this));
        serviceIntent.putExtra(HudConfig.KEY_SHOW_CPU, HudConfig.isShowCpu(this));
        serviceIntent.putExtra(HudConfig.KEY_SHOW_GPU, HudConfig.isShowGpu(this));
        serviceIntent.putExtra(HudConfig.KEY_SHOW_RAM, HudConfig.isShowRam(this));
        serviceIntent.putExtra(HudConfig.KEY_SHOW_FREQ, HudConfig.isShowFreq(this));
        serviceIntent.putExtra(HudConfig.KEY_SHOW_NET_SPEED, HudConfig.isShowNetSpeed(this));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void startHud() {
        Intent intent = new Intent(MainActivity.this, HudService.class);
        intent.setAction(HudService.ACTION_START);
        intent.putExtra(HudConfig.KEY_ALPHA, HudConfig.getAlpha(this));
        intent.putExtra(HudConfig.KEY_MINIMAL_MODE, HudConfig.isMinimalMode(this));
        intent.putExtra(HudConfig.KEY_TEXT_SIZE_SP, HudConfig.getTextSizeSp(this));
        intent.putExtra(HudConfig.KEY_SHOW_CPU, HudConfig.isShowCpu(this));
        intent.putExtra(HudConfig.KEY_SHOW_GPU, HudConfig.isShowGpu(this));
        intent.putExtra(HudConfig.KEY_SHOW_RAM, HudConfig.isShowRam(this));
        intent.putExtra(HudConfig.KEY_SHOW_FREQ, HudConfig.isShowFreq(this));
        intent.putExtra(HudConfig.KEY_SHOW_NET_SPEED, HudConfig.isShowNetSpeed(this));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        Toast.makeText(MainActivity.this, "已开启！按【返回】或【主页】回视频", Toast.LENGTH_SHORT).show();
        updateStatus(true);
    }

    private void stopHud() {
        Intent stopIntent = new Intent(MainActivity.this, HudService.class);
        stopIntent.setAction(HudService.ACTION_STOP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(stopIntent);
        } else {
            startService(stopIntent);
        }
        stopService(stopIntent);
        Toast.makeText(MainActivity.this, "HUD 悬浮窗已关闭", Toast.LENGTH_SHORT).show();
        updateStatus(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean running = isServiceRunning();
        updateStatus(running);
        if (cbServiceToggle != null) {
            isInternalChecking = true;
            cbServiceToggle.setChecked(running);
            isInternalChecking = false;
            cbServiceToggle.requestFocus();
        }
        refreshDynamicInfo();
    }

    @Override
    protected void onPause() {
        super.onPause();
        notifyPauseOverlayTemp();
    }

    private void notifyPauseOverlayTemp() {
        if (isServiceRunning()) {
            Intent pauseIntent = new Intent(this, HudService.class);
            pauseIntent.setAction(HudService.ACTION_PAUSE_TEMP);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(pauseIntent);
            } else {
                startService(pauseIntent);
            }
        }
    }

    private void updateStatus(boolean running) {
        if (tvStatus != null) {
            if (running) {
                tvStatus.setText("当前状态：🟢 悬浮窗正在运行中 (屏幕右上角)");
                tvStatus.setTextColor(Color.parseColor("#00FF88"));
            } else {
                tvStatus.setText("当前状态：⚪ 悬浮窗已关闭");
                tvStatus.setTextColor(Color.parseColor("#8899A6"));
            }
        }
    }

    private boolean isServiceRunning() {
        try {
            ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (manager != null) {
                for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(50)) {
                    if (HudService.class.getName().equals(service.service.getClassName())) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    // ================= 系统信息检测 =================
    private void loadStaticSystemInfo() {
        // CPU
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        String cpuName = getSystemProp("ro.boot.mi.cpu", "");
        if (cpuName.isEmpty()) cpuName = Build.HARDWARE;
        String maxFreq = getCpuMaxFreq();
        String arch = getCpuArchitecture();
        String cpuSummary = String.format("Amlogic %d核 %s %s (%s)", cores, cpuName, maxFreq, arch).trim();
        tvCpuInfo.setText(cpuSummary);

        // GPU
        String gpu = getSystemProp("ro.boot.mi.gpu", "");
        if (gpu.isEmpty()) gpu = "ARM Mali-G31";
        String hwDecoders = getHardwareDecodersSummary();
        tvGpuInfo.setText("ARM " + gpu + " (OpenGL ES 3.2)\n硬解支持: " + hwDecoders);

        // 网卡硬件规格动态探测
        if (tvEthTitle != null) {
            tvEthTitle.setText("• 有线网卡 (" + detectEthernetHardwareSpec() + ")：");
        }
        if (tvWifiTitle != null) {
            tvWifiTitle.setText("• 无线网卡 (" + detectWifiHardwareSpec() + ")：");
        }

        // 内存、存储与网络 (动态刷新)
        refreshDynamicInfo();
        refreshRunningServicesInfo();
    }

    private void refreshDynamicInfo() {
        refreshMemInfo();
        refreshStorageInfo();
        refreshNetworkInfo();
    }

    private void refreshNetworkInfo() {
        // 1. 有线网卡：内容为协商速率，IP (已连接) 或 未连接
        String ethIp = getInterfaceIp("eth");
        if (ethIp != null && !ethIp.isEmpty()) {
            String ethSpeed = getEthernetLinkSpeed();
            tvEthInfo.setText(ethSpeed + "，" + ethIp + " (已连接)");
            tvEthInfo.setTextColor(Color.parseColor("#00FF88"));
        } else {
            tvEthInfo.setText("未连接");
            tvEthInfo.setTextColor(Color.parseColor("#78909C"));
        }

        // 2. 无线网卡：内容为 72Mbps，IP (已连接) 或 未连接
        String wifiIp = getInterfaceIp("wlan");
        if (wifiIp != null && !wifiIp.isEmpty()) {
            int speed = 72;
            try {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    WifiInfo wi = wm.getConnectionInfo();
                    if (wi != null && wi.getLinkSpeed() > 0) {
                        speed = wi.getLinkSpeed();
                    }
                }
            } catch (Exception ignored) {}
            tvWifiInfo.setText(speed + "Mbps，" + wifiIp + " (已连接)");
            tvWifiInfo.setTextColor(Color.parseColor("#00FF88"));
        } else {
            tvWifiInfo.setText("未连接");
            tvWifiInfo.setTextColor(Color.parseColor("#78909C"));
        }
    }

    private void refreshRunningServicesInfo() {
        if (tvServicesTitle == null) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final LinkedHashMap<String, String> serviceMap = new LinkedHashMap<>();
                PackageManager pm = getPackageManager();

                // 1. 尝试从 dumpsys activity services 读取当前运行中的系统与应用服务
                try {
                    Process process = Runtime.getRuntime().exec(new String[]{"dumpsys", "activity", "services"});
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            int srIdx = line.indexOf("ServiceRecord{");
                            if (srIdx >= 0) {
                                int u0Idx = line.indexOf("u0 ", srIdx);
                                if (u0Idx >= 0) {
                                    String rest = line.substring(u0Idx + 3).trim();
                                    int slashIdx = rest.indexOf('/');
                                    if (slashIdx > 0) {
                                        String pkg = rest.substring(0, slashIdx).trim();
                                        if (!pkg.isEmpty() && !serviceMap.containsKey(pkg)) {
                                            String label = getPackageFriendlyLabel(pm, pkg);
                                            serviceMap.put(pkg, label);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    process.waitFor();
                    process.destroy();
                } catch (Throwable t) {
                    android.util.Log.w("MainActivity", "dumpsys services failed: " + t.getMessage());
                }

                // 2. 尝试从 ServiceManager.listServices 读取底层常驻系统服务
                try {
                    Class<?> smClass = Class.forName("android.os.ServiceManager");
                    Method listServicesMethod = smClass.getMethod("listServices");
                    String[] sysServices = (String[]) listServicesMethod.invoke(null);
                    if (sysServices != null && sysServices.length > 0) {
                        for (String sName : sysServices) {
                            if (sName == null || sName.isEmpty()) continue;
                            String pkg = mapSysServiceToPackage(sName);
                            if (!serviceMap.containsKey(pkg)) {
                                String label = mapSysServiceToLabel(sName, pkg, pm);
                                serviceMap.put(pkg, label);
                            }
                        }
                    }
                } catch (Throwable t) {
                    android.util.Log.w("MainActivity", "ServiceManager listServices failed: " + t.getMessage());
                }

                // 3. 补充 ActivityManager.getRunningServices
                try {
                    ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                    if (am != null) {
                        List<ActivityManager.RunningServiceInfo> rsList = am.getRunningServices(50);
                        if (rsList != null) {
                            for (ActivityManager.RunningServiceInfo rs : rsList) {
                                if (rs.service != null) {
                                    String pkg = rs.service.getPackageName();
                                    if (!serviceMap.containsKey(pkg)) {
                                        serviceMap.put(pkg, getPackageFriendlyLabel(pm, pkg));
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}

                // 确保自身也在列表中
                if (!serviceMap.containsKey("com.tv.cpu")) {
                    serviceMap.put("com.tv.cpu", "YY电视性能监控");
                }

                final List<ServiceItem> itemList = new ArrayList<>();
                for (Map.Entry<String, String> entry : serviceMap.entrySet()) {
                    String pkg = entry.getKey();
                    String label = entry.getValue();
                    int cat = classifyPackage(pkg, label, pm);
                    itemList.add(new ServiceItem(pkg, label, cat));
                }

                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isDestroyed || tvServicesTitle == null) return;
                        boolean changed = false;
                        if (itemList.size() != cachedServiceList.size()) {
                            changed = true;
                        } else {
                            for (int idx = 0; idx < itemList.size(); idx++) {
                                if (!itemList.get(idx).pkg.equals(cachedServiceList.get(idx).pkg)) {
                                    changed = true;
                                    break;
                                }
                            }
                        }
                        if (changed || cachedServiceList.isEmpty()) {
                            cachedServiceList.clear();
                            cachedServiceList.addAll(itemList);
                            renderServicesList();
                        }
                    }
                });
            }
        }).start();
    }

    private int classifyPackage(String pkg, String label, PackageManager pm) {
        String p = pkg != null ? pkg.toLowerCase() : "";
        String l = label != null ? label.toLowerCase() : "";

        // 0. 系统应用属性检测：若明确属于第三方用户自行安装的应用，归为蓝色正常应用
        if (pm != null && !p.isEmpty()) {
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                    // 非系统预置的第三方 App (如 TVBox, BBLL, 播放器, 桌面等)
                    return 3;
                }
            } catch (Throwable ignored) {}
        }

        // 1. 绝对不能精简的包 (红色 [核心勿动])
        // 覆盖 Android AOSP 原生核心、全主流电视芯片原厂驱动、电视遥控交互、音视频显示合成等
        if (p.equals("android") || p.equals("system_server") || p.equals("system")
            || p.startsWith("com.android.server") || p.startsWith("com.android.providers")
            || p.contains("surfacecomposer") || p.contains("surfaceflinger") || p.contains("hwcomposer") || p.contains("gralloc")
            || p.equals("com.android.systemui") || l.contains("系统界面")
            || p.equals("com.android.bluetooth") || l.contains("蓝牙") // 电视蓝牙遥控器核心依赖！
            || p.contains("remotecontroller") || p.contains("remote") || p.contains("rcservice") || l.contains("遥控器")
            || p.contains("droidlogic") || p.contains("amlogic") // 晶晨芯片底层与显示驱动
            || p.contains("rockchip")                           // 瑞芯微芯片驱动
            || p.contains("allwinner") || p.contains("cedar")   // 全志芯片驱动
            || p.contains("mediatek") || p.contains("mtk")      // 联发科芯片驱动
            || p.contains("hisilicon") || p.contains("hisi")    // 华为海思芯片驱动
            || p.contains("realtek")                            // 瑞昱芯片驱动
            || p.contains("qcom") || p.contains("qualcomm")     // 高通芯片驱动
            || p.contains("tvservice") || p.contains("tvinput") || p.contains("tuner") || l.contains("电视核心")
            || p.contains("keymaster") || p.contains("keystore") || p.contains("security") || p.contains("crypt")
            || p.contains("vold") || p.contains("installd") || p.contains("netd") || p.contains("thermal")
            || p.contains("audio") || p.contains("sound") || l.contains("音频")
            || p.contains("window") || p.contains("activity") || p.contains("power") || p.contains("display")
            || p.contains("package") || p.contains("input") || p.contains("ethernet") || p.contains("wifi") || p.contains("wlan")
            || p.contains("connectivity") || p.contains("mount") || p.contains("lock_settings")
            || p.contains("permission") || p.contains("user") || p.contains("contexthub")
            || p.contains("cec") || p.contains("hdmi")) {
            return 1;
        }

        // 2. 推荐精简的包 (绿色 [建议精简])
        // 常见电视系统广告、遥测打点、埋点统计、崩溃日志收集、多余后台推送、商业SDK、动态屏保、打印等
        if (p.contains("tvqs") || l.contains("系统加速")
            || p.contains("mi_connect") || l.contains("互联服务")
            || p.contains("smartshare") || l.contains("无线投屏")
            || p.contains("screensaver") || l.contains("屏保")
            || p.contains("push") || p.contains("jpush") || p.contains("getui") || p.contains("igexin") || p.contains("umeng") || l.contains("推送")
            || p.contains("stat") || p.contains("analytics") || p.contains("firebase") || p.contains("telemetry") || p.contains("beacon")
            || p.contains("incident") || p.contains("dropbox") || p.contains("logger") || p.contains("bugreport") || p.contains("diagnostic")
            || p.contains("watchlist") || p.contains("score") || p.contains("perfshielder")
            || p.contains("voiceinteraction") || p.contains("soundtrigger")
            || p.contains("wallpaper") || p.contains("dreams") || p.contains("adservice") || p.contains("advert")
            || p.contains("print") || p.contains("backup") || p.contains("updatelock") || p.contains("system_update") || p.contains("fota")) {
            return 2;
        }

        // 3. 正常的包 (蓝色 [正常应用])
        return 3;
    }

    private void renderServicesList() {
        if (servicesContainer == null || isDestroyed) return;

        // 记录重建前的聚焦项序号，防止刷新后焦点丢失跳回顶部
        int focusedIndex = -1;
        for (int i = 0; i < servicesContainer.getChildCount(); i++) {
            if (servicesContainer.getChildAt(i).hasFocus()) {
                focusedIndex = i;
                break;
            }
        }

        servicesContainer.removeAllViews();

        if (cachedServiceList.isEmpty()) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("正在统计运行服务...");
            tvEmpty.setTextColor(Color.parseColor("#90A4AE"));
            tvEmpty.setTextSize(12);
            tvEmpty.setPadding(8, 8, 8, 8);
            servicesContainer.addView(tvEmpty);
            return;
        }

        boolean showPkg = cbShowServicePkg != null && cbShowServicePkg.isChecked();
        tvServicesTitle.setText(String.format("• 当前加载服务 (共 %d 项)：", cachedServiceList.size()));

        for (int i = 0; i < cachedServiceList.size(); i++) {
            final int itemIndex = i;
            final ServiceItem item = cachedServiceList.get(i);
            final LinearLayout itemRow = new LinearLayout(this);
            itemRow.setOrientation(LinearLayout.VERTICAL);
            itemRow.setFocusable(true);
            itemRow.setFocusableInTouchMode(true);
            itemRow.setClickable(true);
            itemRow.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            itemRow.setPadding(8, 5, 8, 5);

            itemRow.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                            int nextIdx = itemIndex + 1;
                            if (servicesContainer != null && nextIdx < servicesContainer.getChildCount()) {
                                final View next = servicesContainer.getChildAt(nextIdx);
                                if (next != null) {
                                    next.requestFocus();
                                    if (leftScrollView != null) {
                                        leftScrollView.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                Rect rect = new Rect();
                                                next.getDrawingRect(rect);
                                                leftScrollView.offsetDescendantRectToMyCoords(next, rect);
                                                rect.bottom += 60;
                                                rect.top -= 60;
                                                leftScrollView.requestChildRectangleOnScreen(next, rect, false);
                                            }
                                        });
                                    }
                                    return true;
                                }
                            } else {
                                // 达到列表底部，拦截下按键，严禁跳回顶部
                                return true;
                            }
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                            int prevIdx = itemIndex - 1;
                            if (servicesContainer != null && prevIdx >= 0) {
                                final View prev = servicesContainer.getChildAt(prevIdx);
                                if (prev != null) {
                                    prev.requestFocus();
                                    if (leftScrollView != null) {
                                        leftScrollView.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                Rect rect = new Rect();
                                                prev.getDrawingRect(rect);
                                                leftScrollView.offsetDescendantRectToMyCoords(prev, rect);
                                                rect.bottom += 60;
                                                rect.top -= 60;
                                                leftScrollView.requestChildRectangleOnScreen(prev, rect, false);
                                            }
                                        });
                                    }
                                    return true;
                                }
                            } else if (cbShowServicePkg != null) {
                                cbShowServicePkg.requestFocus();
                                if (leftScrollView != null) {
                                    leftScrollView.smoothScrollTo(0, 0);
                                }
                                return true;
                            }
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                            if (cbServiceToggle != null) {
                                cbServiceToggle.requestFocus();
                                return true;
                            }
                        }
                    }
                    return false;
                }
            });

            itemRow.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        v.setBackgroundColor(Color.parseColor("#1F2C3F"));
                    } else {
                        v.setBackgroundColor(Color.TRANSPARENT);
                    }
                }
            });

            int nameColor;
            int pkgColor;
            String tag;
            if (item.category == 1) {
                nameColor = Color.parseColor("#FF5252");      // 红色：绝对不能精简
                pkgColor = Color.parseColor("#FF8A80");
                tag = "[核心勿动]";
            } else if (item.category == 2) {
                nameColor = Color.parseColor("#00E676");      // 绿色：推荐精简
                pkgColor = Color.parseColor("#B9F6CA");
                tag = "[建议精简]";
            } else {
                nameColor = Color.parseColor("#40C4FF");      // 蓝色：正常应用
                pkgColor = Color.parseColor("#80D8FF");
                tag = "[正常应用]";
            }

            LinearLayout nameRow = new LinearLayout(this);
            nameRow.setOrientation(LinearLayout.HORIZONTAL);
            nameRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView tvName = new TextView(this);
            tvName.setText("•  " + item.label);
            tvName.setTextColor(nameColor);
            tvName.setTextSize(13);
            tvName.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            tvName.setLayoutParams(nameLp);
            nameRow.addView(tvName);

            TextView tvTag = new TextView(this);
            tvTag.setText(tag);
            tvTag.setTextColor(nameColor);
            tvTag.setTextSize(11);
            tvTag.setPadding(6, 0, 2, 0);
            nameRow.addView(tvTag);

            itemRow.addView(nameRow);

            if (showPkg) {
                TextView tvPkg = new TextView(this);
                tvPkg.setText("   ↳ " + item.pkg);
                tvPkg.setTextColor(pkgColor);
                tvPkg.setTextSize(11);
                tvPkg.setPadding(12, 1, 0, 0);
                itemRow.addView(tvPkg);
            }

            View itemDivider = new View(this);
            itemDivider.setBackgroundColor(Color.parseColor("#151D28"));
            LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
            dLp.setMargins(0, 4, 0, 0);
            itemDivider.setLayoutParams(dLp);
            itemRow.addView(itemDivider);

            servicesContainer.addView(itemRow);
        }
    }

    private String getPackageFriendlyLabel(PackageManager pm, String pkg) {
        if ("com.tv.cpu".equals(pkg)) return "YY电视性能监控";
        if ("com.dangbei1.tvlauncher".equals(pkg)) return "当贝桌面";
        if ("com.duokan.airkan.tvbox".equals(pkg)) return "小米投屏";
        if ("com.android.systemui".equals(pkg)) return "系统界面";
        if ("com.android.bluetooth".equals(pkg)) return "蓝牙服务";
        if ("com.droidlogic".equals(pkg)) return "DroidLogic系统";
        if ("com.droidlogic.tvinput".equals(pkg)) return "TV信号输入";
        if ("com.xiaomi.mi_connect_service".equals(pkg)) return "小米互联服务";
        if ("com.xiaomi.mitv.remotecontroller.service".equals(pkg)) return "遥控器通信服务";
        if ("com.xiaomi.mitv.smartshare".equals(pkg)) return "无线投屏";
        if ("com.xiaomi.tvqs".equals(pkg)) return "系统加速";
        if ("android.ext.services".equals(pkg)) return "Android扩展服务";
        if ("com.google.android.inputmethod.pinyin".equals(pkg)) return "谷歌输入法";
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence cs = pm.getApplicationLabel(ai);
            if (cs != null && cs.length() > 0) {
                String label = cs.toString().trim();
                if (!label.equals(pkg)) return label;
            }
        } catch (Throwable ignored) {}
        return pkg;
    }

    private String mapSysServiceToPackage(String sName) {
        if ("SurfaceFlinger".equalsIgnoreCase(sName)) return "android.ui.ISurfaceComposer";
        if ("TvService".equalsIgnoreCase(sName)) return "mitv.internal.ITvService";
        if ("tvqs.TVQSService".equalsIgnoreCase(sName)) return "tvqs.ITVQSService";
        if ("bluetooth_manager".equalsIgnoreCase(sName)) return "com.android.bluetooth";
        if ("tv_input".equalsIgnoreCase(sName)) return "com.droidlogic.tvinput";
        if ("audio".equalsIgnoreCase(sName)) return "android.media.IAudioService";
        if ("wifi".equalsIgnoreCase(sName)) return "android.net.wifi.IWifiManager";
        if ("ethernet".equalsIgnoreCase(sName)) return "android.net.IEthernetManager";
        if ("window".equalsIgnoreCase(sName)) return "android.view.IWindowManager";
        if ("activity".equalsIgnoreCase(sName)) return "android.app.IActivityManager";
        if ("power".equalsIgnoreCase(sName)) return "android.os.IPowerManager";
        if ("display".equalsIgnoreCase(sName)) return "android.hardware.display.IDisplayManager";
        if ("package".equalsIgnoreCase(sName)) return "android.content.pm.IPackageManager";
        if ("input".equalsIgnoreCase(sName)) return "android.hardware.input.IInputManager";
        return "android.service." + sName;
    }

    private String mapSysServiceToLabel(String sName, String pkg, PackageManager pm) {
        if ("SurfaceFlinger".equalsIgnoreCase(sName)) return "SurfaceFlinger显示合成";
        if ("TvService".equalsIgnoreCase(sName)) return "电视核心服务";
        if ("tvqs.TVQSService".equalsIgnoreCase(sName)) return "小米系统加速服务";
        if ("bluetooth_manager".equalsIgnoreCase(sName)) return "蓝牙服务";
        if ("tv_input".equalsIgnoreCase(sName)) return "TV信号输入";
        if ("audio".equalsIgnoreCase(sName)) return "音频管理服务";
        if ("wifi".equalsIgnoreCase(sName)) return "无线Wi-Fi服务";
        if ("ethernet".equalsIgnoreCase(sName)) return "以太网服务";
        if ("window".equalsIgnoreCase(sName)) return "窗口管理服务";
        if ("activity".equalsIgnoreCase(sName)) return "活动进程管理";
        if ("power".equalsIgnoreCase(sName)) return "电源管理服务";
        if ("display".equalsIgnoreCase(sName)) return "显示输出服务";
        if ("package".equalsIgnoreCase(sName)) return "包管理服务";
        if ("input".equalsIgnoreCase(sName)) return "输入外设服务";
        return sName;
    }

    private String getEthernetSpeedDesc() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                Network[] networks = cm.getAllNetworks();
                for (Network n : networks) {
                    NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                    if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                        int downKbps = nc.getLinkDownstreamBandwidthKbps();
                        if (downKbps >= 900000) {
                            return "千兆以太网 (1000 Mbps)";
                        } else if (downKbps >= 90000) {
                            return "百兆以太网 (100 Mbps)";
                        } else if (downKbps > 0) {
                            return (downKbps / 1000) + " Mbps";
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return "百兆以太网 (100 Mbps)";
    }

    private String getEthernetInterfaceDesc() {
        try {
            File f = new File("/sys/class/net/eth0/device");
            if (f.exists()) {
                String path = f.getCanonicalPath();
                if (path.contains("usb")) {
                    return "USB 扩展接口";
                } else if (path.contains("ethernet") || path.contains("dwmac") || path.contains("platform")) {
                    return "板载内置 (Amlogic DWMAC)";
                }
            }
        } catch (Exception ignored) {}
        return "板载内置控制器";
    }

    private String getWifiHardwareDesc() {
        String chip = "";
        String iface = "USB 接口";
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/modules"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("rtl8723") || line.contains("8723")) {
                    chip = "Realtek RTL8723DU";
                    iface = "USB 接口 (2.4G 802.11n)";
                    break;
                } else if (line.contains("rtl8822") || line.contains("8822")) {
                    chip = "Realtek RTL8822CS";
                    iface = "SDIO 接口 (双频 802.11ac)";
                    break;
                } else if (line.contains("bcmdhd") || line.contains("ap6")) {
                    chip = "Broadcom / Ampak";
                    iface = "SDIO 接口 (双频 Wi-Fi)";
                    break;
                } else if (line.contains("mt7668")) {
                    chip = "MediaTek MT7668";
                    iface = "USB/PCIe (双频 802.11ac)";
                    break;
                }
            }
        } catch (Exception ignored) {}

        if (chip.isEmpty()) {
            chip = "无线网络适配器";
            iface = "内置 Wi-Fi (USB 接口)";
        }
        return chip + " · " + iface;
    }

    private String getInterfaceIp(String prefix) {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    NetworkInterface iface = interfaces.nextElement();
                    String name = iface.getName().toLowerCase();
                    if (name.startsWith(prefix.toLowerCase())) {
                        if (iface.isUp()) {
                            Enumeration<InetAddress> addresses = iface.getInetAddresses();
                            while (addresses.hasMoreElements()) {
                                InetAddress addr = addresses.nextElement();
                                if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                                    String ip = addr.getHostAddress();
                                    if (ip != null && !ip.isEmpty() && !"0.0.0.0".equals(ip)) {
                                        return ip;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String getCpuArchitecture() {
        if (Build.SUPPORTED_64_BIT_ABIS != null && Build.SUPPORTED_64_BIT_ABIS.length > 0) {
            return "ARMv8-A 64位";
        }
        String abi = Build.CPU_ABI;
        if (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0) {
            abi = Build.SUPPORTED_ABIS[0];
        }
        if (abi == null) abi = "";

        if (abi.startsWith("arm64")) {
            return "ARMv8-A 64位";
        } else if (abi.startsWith("armeabi-v7") || abi.contains("v7")) {
            return "ARMv7-A 32位";
        } else if (abi.startsWith("armeabi")) {
            return "ARMv6/v7 32位";
        } else if (abi.startsWith("x86_64")) {
            return "x86_64 64位";
        } else if (abi.startsWith("x86")) {
            return "x86 32位";
        }

        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.toLowerCase().startsWith("cpu architecture")) {
                    String[] parts = line.split(":");
                    if (parts.length >= 2) {
                        String archVal = parts[1].trim();
                        if ("7".equals(archVal)) return "ARMv7-A 32位";
                        if ("8".equals(archVal)) return "ARMv8-A 64位";
                    }
                }
            }
        } catch (Exception ignored) {}

        return "ARMv7-A 32位";
    }

    private String getHardwareDecodersSummary() {
        LinkedHashSet<String> formats = new LinkedHashSet<>();
        try {
            int count = MediaCodecList.getCodecCount();
            for (int i = 0; i < count; i++) {
                MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
                if (info.isEncoder()) continue;
                String name = info.getName();
                String lower = name.toLowerCase();
                if (lower.startsWith("omx.google.") || lower.startsWith("c2.android.") 
                        || lower.contains(".sw.") || lower.endsWith(".sw") 
                        || lower.contains("ffmpeg") || lower.contains("soft")) {
                    continue;
                }
                String[] types = info.getSupportedTypes();
                if (types == null) continue;
                for (String mime : types) {
                    String friendly = mapMimeToFriendly(mime);
                    if (friendly != null) {
                        formats.add(friendly);
                    }
                }
            }
        } catch (Exception ignored) {}

        checkAmlogicXmlCodecs(formats);

        if (formats.isEmpty()) {
            return "HEVC(H.265) / H.264 / MPEG-2";
        }

        StringBuilder sb = new StringBuilder();
        for (String f : formats) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append(f);
        }
        return sb.toString();
    }

    private String mapMimeToFriendly(String mime) {
        if (mime == null) return null;
        String m = mime.toLowerCase();
        if (m.equals("video/hevc")) return "HEVC(H.265)";
        if (m.equals("video/avc")) return "H.264";
        if (m.equals("video/av01")) return "AV1";
        if (m.equals("video/x-vnd.on2.vp9")) return "VP9";
        if (m.equals("video/avs2")) return "AVS2";
        if (m.equals("video/avs")) return "AVS";
        if (m.equals("video/mpeg2")) return "MPEG-2";
        if (m.equals("video/mp4v-es")) return "MPEG-4";
        if (m.equals("video/vc1") || m.equals("video/wvc1")) return "VC-1";
        if (m.equals("video/wmv3") || m.equals("video/x-ms-wmv")) return "WMV3";
        if (m.equals("video/3gpp")) return "H.263";
        if (m.equals("video/mjpeg")) return "MJPEG";
        return null;
    }

    private void checkAmlogicXmlCodecs(LinkedHashSet<String> formats) {
        String[] files = new String[]{"/vendor/etc/media_codecs.xml", "/vendor/etc/media_codecs_ext.xml"};
        for (String filePath : files) {
            File f = new File(filePath);
            if (!f.exists()) continue;
            try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("OMX.amlogic.") && line.contains(".awesome")) {
                        if (line.contains("hevc")) formats.add("HEVC(H.265)");
                        else if (line.contains("avc")) formats.add("H.264");
                        else if (line.contains("avs2")) formats.add("AVS2");
                        else if (line.contains("avs")) formats.add("AVS");
                        else if (line.contains("mpeg2")) formats.add("MPEG-2");
                        else if (line.contains("mpeg4")) formats.add("MPEG-4");
                        else if (line.contains("vc1") || line.contains("wvc1")) formats.add("VC-1");
                        else if (line.contains("wmv3")) formats.add("WMV3");
                        else if (line.contains("mjpeg")) formats.add("MJPEG");
                        else if (line.contains("vp9")) formats.add("VP9");
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private void refreshMemInfo() {
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
            int percent = (int) Math.round((usedKb * 100.0) / totalKb);
            tvMemInfo.setText(String.format("%.1f GB / %.1f GB (已用 %d%%)", usedGb, totalGb, percent));
        } else {
            tvMemInfo.setText("--");
        }
    }

    private void refreshStorageInfo() {
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            long blockSize = stat.getBlockSizeLong();
            long totalBlocks = stat.getBlockCountLong();
            long availBlocks = stat.getAvailableBlocksLong();

            double totalGb = (totalBlocks * blockSize) / (1024.0 * 1024.0 * 1024.0);
            double availGb = (availBlocks * blockSize) / (1024.0 * 1024.0 * 1024.0);
            double usedGb = totalGb - availGb;
            tvStorageInfo.setText(String.format("已用 %.1f GB / 总计 %.1f GB (剩余 %.1f GB)", usedGb, totalGb, availGb));
        } catch (Exception e) {
            tvStorageInfo.setText("--");
        }
    }

    private void startDynamicInfoLoop() {
        uiHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isDestroyed) return;
                refreshDynamicInfo();
                boolean running = isServiceRunning();
                updateStatus(running);
                if (cbServiceToggle != null && !cbServiceToggle.hasFocus()) {
                    isInternalChecking = true;
                    cbServiceToggle.setChecked(running);
                    isInternalChecking = false;
                }
                uiHandler.postDelayed(this, 2500);
            }
        }, 2500);
    }

    private String getCpuMaxFreq() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq"))) {
            String line = reader.readLine();
            if (line != null) {
                long khz = Long.parseLong(line.trim());
                return String.format("@ %.1fGHz", khz / 1000000.0);
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String getSystemProp(String key, String fallback) {
        try {
            Class<?> clazz = Class.forName("android.os.SystemProperties");
            Method method = clazz.getMethod("get", String.class, String.class);
            return (String) method.invoke(null, key, fallback);
        } catch (Exception e) {
            return fallback;
        }
    }

    private long parseKb(String line) {
        String[] parts = line.split("\\s+");
        if (parts.length >= 2) {
            return Long.parseLong(parts[1]);
        }
        return 0;
    }

    // ================= UI 辅助组件与焦点动效 =================
    private LinearLayout createCardLayout() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(25, 20, 25, 20);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#171F2C"));
        bg.setCornerRadius(14);
        bg.setStroke(1, Color.parseColor("#273344"));
        card.setBackground(bg);
        return card;
    }

    private TextView createSectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#00E5FF"));
        tv.setTextSize(18);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, 0, 0, 15);
        return tv;
    }

    private TextView createInfoRow(LinearLayout parent, String label, String initialVal) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 5, 0, 8);

        TextView lbl = new TextView(this);
        lbl.setText("• " + label + "：");
        lbl.setTextColor(Color.parseColor("#90A4AE"));
        lbl.setTextSize(13);
        lbl.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(lbl);

        TextView val = new TextView(this);
        val.setText(initialVal);
        val.setTextColor(Color.WHITE);
        val.setTextSize(14);
        val.setPadding(12, 2, 0, 0);
        val.setLineSpacing(3, 1.0f);
        row.addView(val);

        parent.addView(row);
        return val;
    }

    private TextView[] createInfoRowWithTitle(LinearLayout parent, String label, String initialVal) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 5, 0, 8);

        TextView lbl = new TextView(this);
        lbl.setText("• " + label + "：");
        lbl.setTextColor(Color.parseColor("#90A4AE"));
        lbl.setTextSize(13);
        lbl.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(lbl);

        TextView val = new TextView(this);
        val.setText(initialVal);
        val.setTextColor(Color.WHITE);
        val.setTextSize(14);
        val.setPadding(12, 2, 0, 0);
        val.setLineSpacing(3, 1.0f);
        row.addView(val);

        parent.addView(row);
        return new TextView[]{lbl, val};
    }

    private String cachedEthSpec = null;
    private String detectEthernetHardwareSpec() {
        if (cachedEthSpec != null) return cachedEthSpec;
        // 1. 尝试从 MDIO 总线探测 PHY 物理芯片驱动
        try {
            File mdioBusDir = new File("/sys/class/net/eth0/device/mdio_bus");
            if (mdioBusDir.exists() && mdioBusDir.isDirectory()) {
                File[] buses = mdioBusDir.listFiles();
                if (buses != null) {
                    for (File bus : buses) {
                        File[] subDevs = bus.listFiles();
                        if (subDevs != null) {
                            for (File dev : subDevs) {
                                File driverLink = new File(dev, "driver");
                                if (driverLink.exists()) {
                                    String target = driverLink.getCanonicalPath().toLowerCase();
                                    if (target.contains("internal phy") || target.contains("rtl8201") || target.contains("fe phy")) {
                                        cachedEthSpec = "百兆";
                                        return cachedEthSpec;
                                    } else if (target.contains("gigabit") || target.contains("rtl8211") || target.contains("1000")) {
                                        cachedEthSpec = "千兆";
                                        return cachedEthSpec;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2. 从设备树属性探测（Amlogic internal_phy 节点存在即为百兆）
        try {
            if (new File("/proc/device-tree/ethernet@ff3f0000/internal_phy").exists() ||
                new File("/sys/firmware/devicetree/base/ethernet@ff3f0000/internal_phy").exists()) {
                cachedEthSpec = "百兆";
                return cachedEthSpec;
            }
        } catch (Throwable ignored) {}

        // 3. 从系统 NetworkCapabilities 带宽属性探测
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                for (Network net : cm.getAllNetworks()) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                        int downKbps = caps.getLinkDownstreamBandwidthKbps();
                        if (downKbps >= 1000000) {
                            cachedEthSpec = "千兆";
                            return cachedEthSpec;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        cachedEthSpec = "百兆";
        return cachedEthSpec;
    }

    private String getEthernetLinkSpeed() {
        // 尝试从 sysfs 读取实时协商速率
        try {
            BufferedReader br = new BufferedReader(new FileReader("/sys/class/net/eth0/speed"));
            String line = br.readLine();
            br.close();
            if (line != null && !line.trim().isEmpty()) {
                int s = Integer.parseInt(line.trim());
                if (s > 0) return s + "Mbps";
            }
        } catch (Throwable ignored) {}

        // 尝试从 NetworkCapabilities 读取
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                for (Network net : cm.getAllNetworks()) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                        int downKbps = caps.getLinkDownstreamBandwidthKbps();
                        if (downKbps >= 1000000) return "1000Mbps";
                        if (downKbps >= 100000) return "100Mbps";
                    }
                }
            }
        } catch (Throwable ignored) {}

        return "100Mbps";
    }

    private String cachedWifiSpec = null;
    private String detectWifiHardwareSpec() {
        if (cachedWifiSpec != null) return cachedWifiSpec;
        String driver = getSystemProp("ro.wifi.driver", "").toLowerCase();
        if (driver.isEmpty()) {
            try {
                BufferedReader br = new BufferedReader(new FileReader("/proc/modules"));
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.toLowerCase();
                    if (line.contains("rtl8723") || line.contains("rtl8188") || line.contains("rtl8821") || line.contains("rtl8822") || line.contains("bcmdhd") || line.contains("aic8800")) {
                        driver = line.split("\\s+")[0];
                        break;
                    }
                }
                br.close();
            } catch (Throwable ignored) {}
        }

        if (driver.contains("8822") || driver.contains("8821") || driver.contains("8812") || driver.contains("ac")) {
            cachedWifiSpec = "双频 Wi-Fi 5";
        } else if (driver.contains("aic8800") || driver.contains("ax")) {
            cachedWifiSpec = "Wi-Fi 6";
        } else if (driver.contains("8723") || driver.contains("8188") || driver.contains("8189") || driver.contains("8192")) {
            cachedWifiSpec = "150M Wi-Fi 4";
        } else {
            cachedWifiSpec = "Wi-Fi 4";
        }
        return cachedWifiSpec;
    }

    private Button createSettingButton() {
        final Button btn = new Button(this);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(14);
        btn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        btn.setPadding(25, 12, 25, 12);

        final GradientDrawable normalBg = new GradientDrawable();
        normalBg.setColor(Color.parseColor("#1F2A38"));
        normalBg.setCornerRadius(8);
        btn.setBackground(normalBg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, 4, 0, 8);
        btn.setLayoutParams(lp);

        btn.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    GradientDrawable focusBg = new GradientDrawable();
                    focusBg.setColor(Color.parseColor("#2C3B4E"));
                    focusBg.setCornerRadius(8);
                    focusBg.setStroke(2, Color.parseColor("#00E5FF"));
                    btn.setBackground(focusBg);
                    btn.setTextColor(Color.parseColor("#00FF88"));
                    btn.setScaleX(1.02f);
                    btn.setScaleY(1.02f);
                } else {
                    btn.setBackground(normalBg);
                    btn.setTextColor(Color.WHITE);
                    btn.setScaleX(1.0f);
                    btn.setScaleY(1.0f);
                }
            }
        });
        return btn;
    }

    private TextView createSubTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#00E5FF"));
        tv.setTextSize(14);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, 6, 0, 4);
        return tv;
    }

    private CheckBox createCompactCheckBox(String text, boolean initialChecked, CompoundButton.OnCheckedChangeListener listener) {
        final CheckBox cb = new CheckBox(this);
        cb.setText(text);
        cb.setTextColor(Color.parseColor("#ECEFF1"));
        cb.setTextSize(12);
        cb.setChecked(initialChecked);
        if (listener != null) {
            cb.setOnCheckedChangeListener(listener);
        }
        cb.setPadding(6, 6, 6, 6);
        cb.setFocusable(true);
        cb.setClickable(true);

        final GradientDrawable normalBg = new GradientDrawable();
        normalBg.setColor(Color.parseColor("#17202B"));
        normalBg.setCornerRadius(8);
        cb.setBackground(normalBg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
        );
        lp.setMargins(2, 2, 2, 4);
        cb.setLayoutParams(lp);

        cb.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_UP && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                    cb.toggle();
                    return true;
                }
                return false;
            }
        });

        cb.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    GradientDrawable focusBg = new GradientDrawable();
                    focusBg.setColor(Color.parseColor("#253446"));
                    focusBg.setCornerRadius(8);
                    focusBg.setStroke(2, Color.parseColor("#00E5FF"));
                    cb.setBackground(focusBg);
                    cb.setTextColor(Color.parseColor("#00FF88"));
                    cb.setScaleX(1.02f);
                    cb.setScaleY(1.02f);
                } else {
                    cb.setBackground(normalBg);
                    cb.setTextColor(Color.parseColor("#ECEFF1"));
                    cb.setScaleX(1.0f);
                    cb.setScaleY(1.0f);
                }
            }
        });
        return cb;
    }

    private CheckBox createStyledCheckBox(String text, boolean initialChecked, CompoundButton.OnCheckedChangeListener listener) {
        final CheckBox cb = new CheckBox(this);
        cb.setText(text);
        cb.setTextColor(Color.parseColor("#ECEFF1"));
        cb.setTextSize(15);
        cb.setChecked(initialChecked);
        cb.setOnCheckedChangeListener(listener);
        cb.setPadding(15, 10, 15, 10);
        cb.setFocusable(true);
        cb.setClickable(true);

        final GradientDrawable normalBg = new GradientDrawable();
        normalBg.setColor(Color.parseColor("#17202B"));
        normalBg.setCornerRadius(8);
        cb.setBackground(normalBg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, 3, 0, 8);
        cb.setLayoutParams(lp);

        cb.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_UP && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                    cb.toggle();
                    return true;
                }
                return false;
            }
        });

        cb.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    GradientDrawable focusBg = new GradientDrawable();
                    focusBg.setColor(Color.parseColor("#253446"));
                    focusBg.setCornerRadius(8);
                    focusBg.setStroke(2, Color.parseColor("#00E5FF"));
                    cb.setBackground(focusBg);
                    cb.setTextColor(Color.parseColor("#00FF88"));
                    cb.setScaleX(1.02f);
                    cb.setScaleY(1.02f);
                } else {
                    cb.setBackground(normalBg);
                    cb.setTextColor(Color.parseColor("#ECEFF1"));
                    cb.setScaleX(1.0f);
                    cb.setScaleY(1.0f);
                }
            }
        });
        return cb;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            notifyPauseOverlayTemp();
            moveTaskToBack(true);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "需要授予悬浮窗权限", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return false;
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        isDestroyed = true;
        uiHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
