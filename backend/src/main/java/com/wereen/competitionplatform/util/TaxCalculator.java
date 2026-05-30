package com.wereen.competitionplatform.util;

import lombok.Data;

/**
 * 税务计算工具类
 *
 * 预留字段和方法用于后期扩展税务处理功能
 * 当前版本暂不实现具体税务计算逻辑
 */
public class TaxCalculator {

    /**
     * 免税额度（分）800元 = 80000分
     */
    private static final Long TAX_FREE_THRESHOLD = 80000L;

    /**
     * 个人所得税税率 20%
     */
    private static final double PERSONAL_INCOME_TAX_RATE = 0.20;

    /**
     * 费用扣除比例 20%
     */
    private static final double EXPENSE_DEDUCTION_RATE = 0.20;

    /**
     * 计算税后实发金额
     *
     * 当前版本：暂不扣税，直接返回原金额
     * 后期扩展：根据税法计算个人所得税
     *
     * @param prizeAmount 奖金金额（分）
     * @return 税务计算结果
     */
    public static TaxResult calculate(Long prizeAmount) {
        TaxResult result = new TaxResult();
        result.setPrizeAmount(prizeAmount);

        // TODO: 后期扩展 - 实现税务计算逻辑
        // 当前暂不扣税，taxAmount = 0
        result.setTaxAmount(0L);
        result.setActualAmount(prizeAmount);
        result.setTaxRate(0.0);
        result.setTaxCalculated(false); // 标记为未计算税务

        return result;
    }

    /**
     * 计算税后实发金额（完整版，预留）
     *
     * 税法规定：
     * 1. 800元以下免税
     * 2. 800-4000元：(奖金 - 800) × 20%
     * 3. 4000元以上：奖金 × (1-20%) × 20%
     *
     * @param prizeAmount 奖金金额（分）
     * @return 税务计算结果
     */
    @SuppressWarnings("unused")
    private static TaxResult calculateWithTax(Long prizeAmount) {
        TaxResult result = new TaxResult();
        result.setPrizeAmount(prizeAmount);

        if (prizeAmount <= TAX_FREE_THRESHOLD) {
            // 800元以下免税
            result.setTaxAmount(0L);
            result.setActualAmount(prizeAmount);
            result.setTaxRate(0.0);

        } else if (prizeAmount > TAX_FREE_THRESHOLD && prizeAmount <= 400000L) {
            // 800-4000元：(奖金 - 800) × 20%
            Long taxableIncome = prizeAmount - TAX_FREE_THRESHOLD;
            Long tax = (long) (taxableIncome * PERSONAL_INCOME_TAX_RATE);
            result.setTaxAmount(tax);
            result.setActualAmount(prizeAmount - tax);
            result.setTaxRate(PERSONAL_INCOME_TAX_RATE);

        } else {
            // 4000元以上：奖金 × (1-20%) × 20%
            Long taxableIncome = (long) (prizeAmount * (1 - EXPENSE_DEDUCTION_RATE));
            Long tax = (long) (taxableIncome * PERSONAL_INCOME_TAX_RATE);
            result.setTaxAmount(tax);
            result.setActualAmount(prizeAmount - tax);
            result.setTaxRate(PERSONAL_INCOME_TAX_RATE);
        }

        result.setTaxCalculated(true);
        return result;
    }

    /**
     * 批量计算税务
     *
     * @param prizeAmounts 奖金金额列表
     * @return 税务计算结果列表
     */
    public static java.util.List<TaxResult> batchCalculate(java.util.List<Long> prizeAmounts) {
        return prizeAmounts.stream()
                .map(TaxCalculator::calculate)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 税务计算结果
     */
    @Data
    public static class TaxResult {
        /**
         * 奖金金额（分，税前）
         */
        private Long prizeAmount;

        /**
         * 个税金额（分）
         */
        private Long taxAmount;

        /**
         * 实发金额（分，税后）
         */
        private Long actualAmount;

        /**
         * 税率
         */
        private Double taxRate;

        /**
         * 是否已计算税务（true-已计算 false-未计算）
         */
        private Boolean taxCalculated;

        /**
         * 格式化奖金金额（元）
         */
        public String getFormattedPrizeAmount() {
            return String.format("%.2f", prizeAmount / 100.0);
        }

        /**
         * 格式化税额（元）
         */
        public String getFormattedTaxAmount() {
            return String.format("%.2f", taxAmount / 100.0);
        }

        /**
         * 格式化实发金额（元）
         */
        public String getFormattedActualAmount() {
            return String.format("%.2f", actualAmount / 100.0);
        }
    }

    /**
     * 月度税务汇总（预留）
     *
     * 用于生成月度税务申报数据
     */
    @Data
    public static class MonthlyTaxSummary {
        /**
         * 年月 (YYYY-MM)
         */
        private String yearMonth;

        /**
         * 发放人数
         */
        private Integer totalCount;

        /**
         * 奖金总额（分）
         */
        private Long totalPrizeAmount;

        /**
         * 税款总额（分）
         */
        private Long totalTaxAmount;

        /**
         * 实发总额（分）
         */
        private Long totalActualAmount;

        /**
         * 申报状态 (PENDING-待申报 DECLARED-已申报)
         */
        private String declareStatus;

        /**
         * 申报时间
         */
        private java.time.LocalDateTime declaredAt;
    }

    /**
     * 生成月度税务汇总（预留方法）
     *
     * @param yearMonth 年月 (YYYY-MM)
     * @return 月度税务汇总
     */
    @SuppressWarnings("unused")
    public static MonthlyTaxSummary generateMonthlySummary(String yearMonth) {
        // TODO: 后期扩展 - 从数据库查询月度发放数据并汇总
        MonthlyTaxSummary summary = new MonthlyTaxSummary();
        summary.setYearMonth(yearMonth);
        summary.setDeclareStatus("PENDING");
        return summary;
    }
}
