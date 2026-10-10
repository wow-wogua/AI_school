package com.shishi.growth;

import android.os.Bundle;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // 自定义原生插件须在 super.onCreate 前注册（bridge 初始化时收集插件清单）
        registerPlugin(AppUpdatePlugin.class);
        super.onCreate(savedInstanceState);
        // 批46② 平板避让兜底：Capacitor 的 CSS insets 注入（--safe-area-inset-*）在部分
        // 设备（平板/部分 WebView）上失效，顶部返回条被系统状态栏盖住点不到。这里在原生侧
        // 统一消费 systemBars insets，以 content 根 padding 让位——WebView 不再延伸到系统栏
        // 底下，Capacitor 注入给 CSS 的 insets 随之归零，不会双重避让；剩余 insets（如键盘）
        // 继续下传，不影响键盘弹起时的页面自适应。
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content),
                (v, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets.inset(bars.left, bars.top, bars.right, bars.bottom);
                });
    }
}
