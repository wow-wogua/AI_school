package com.aischool.server.service.report;

import java.math.BigDecimal;

/** 数值契约辅助：DECIMAL(10,2) → 与 golden JSON 一致的 int/double（97 而非 97.00，107.2 而非 107.20） */
public final class Num {

    private Num() {
    }

    public static Object of(BigDecimal bd) {
        if (bd == null) {
            return null;
        }
        BigDecimal stripped = bd.stripTrailingZeros();
        if (stripped.scale() <= 0) {
            return stripped.intValueExact();
        }
        return stripped.doubleValue();
    }

    /** 恒 Double 形态（整值也是 1020.0）：仅用于页面原样输出且样例为小数形态的字段（growthSymbol） */
    public static Object ofDouble(BigDecimal bd) {
        return bd == null ? null : bd.stripTrailingZeros().doubleValue();
    }

    public static double d(BigDecimal bd) {
        return bd == null ? 0d : bd.doubleValue();
    }
}
