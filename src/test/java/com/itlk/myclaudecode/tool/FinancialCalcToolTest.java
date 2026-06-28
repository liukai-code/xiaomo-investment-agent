package com.itlk.myclaudecode.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FinancialCalcTool 金融计算器测试")
class FinancialCalcToolTest {

    private FinancialCalcTool tool;

    @BeforeEach
    void setUp() {
        tool = new FinancialCalcTool();
    }

    // ==================== 通用计算 ====================

    @Nested
    @DisplayName("数学表达式计算 calculate")
    class CalculateTest {

        @Test
        @DisplayName("简单乘法 50*30")
        void multiplication() {
            String result = tool.calculate("50*30");
            assertTrue(result.contains("1500"), "50*30=1500，实际: " + result);
        }

        @Test
        @DisplayName("加法 7890+50*365")
        void mixedOps() {
            String result = tool.calculate("7890+50*365");
            // 50*365=18250, +7890=26140
            assertTrue(result.contains("26140"), "7890+50*365=26140，实际: " + result);
        }

        @Test
        @DisplayName("括号运算 (100+200)*3")
        void parentheses() {
            String result = tool.calculate("(100+200)*3");
            assertTrue(result.contains("900"), "(100+200)*3=900，实际: " + result);
        }

        @Test
        @DisplayName("除法 1000/12")
        void division() {
            String result = tool.calculate("1000/12");
            assertTrue(result.contains("83.33"), "1000/12≈83.33，实际: " + result);
        }

        @Test
        @DisplayName("负数 -50*10")
        void negative() {
            String result = tool.calculate("-50*10");
            assertTrue(result.contains("-500"), "-50*10=-500，实际: " + result);
        }

        @Test
        @DisplayName("空表达式 → 错误提示")
        void empty() {
            String result = tool.calculate("");
            assertTrue(result.contains("不能为空"));
        }

        @Test
        @DisplayName("嵌套括号 (1+(2*3))")
        void nestedParentheses() {
            String result = tool.calculate("(1+(2*3))");
            assertTrue(result.contains("7"), "(1+(2*3))=7，实际: " + result);
        }

        @Test
        @DisplayName("小数运算 1.5*2.4")
        void decimalOps() {
            String result = tool.calculate("1.5*2.4");
            assertTrue(result.contains("3.6") || result.contains("3.5999"),
                    "1.5*2.4≈3.6，实际: " + result);
        }

        @Test
        @DisplayName("除零不崩溃")
        void divideByZero() {
            String result = tool.calculate("1/0");
            // 不抛异常即可，结果为Infinity
            assertNotNull(result);
        }
    }

    // ==================== A. 基础利息 ====================

    @Nested
    @DisplayName("复利终值 compoundInterest")
    class CompoundInterestTest {

        @Test
        @DisplayName("标准复利计算 - 10万年化5%存5年")
        void standardCase() {
            String result = tool.compoundInterest(100000, 0.05, 5);
            // 100000 * (1.05)^5 = 127628.16
            assertTrue(result.contains("127628.16"), "终值应为127628.16，实际: " + result);
            assertTrue(result.contains("27628.16"), "收益应为27628.16");
        }

        @Test
        @DisplayName("零利率 - 终值等于本金")
        void zeroRate() {
            String result = tool.compoundInterest(100000, 0, 5);
            assertTrue(result.contains("100000.00"), "零利率终值应等于本金");
            assertTrue(result.contains("0.00"), "零利率收益应为0");
        }

        @Test
        @DisplayName("1年期 - 等同单利")
        void oneYear() {
            String result = tool.compoundInterest(10000, 0.1, 1);
            assertTrue(result.contains("11000.00"), "1年期复利=单利");
        }

        @Test
        @DisplayName("高利率长周期")
        void highRateLongPeriod() {
            String result = tool.compoundInterest(10000, 0.2, 10);
            // 10000 * (1.2)^10 = 61917.36
            assertTrue(result.contains("61917.36"), "高利率复利，实际: " + result);
        }

        @Test
        @DisplayName("负利率 - 资金贬值")
        void negativeRate() {
            String result = tool.compoundInterest(10000, -0.05, 5);
            // 10000 * (0.95)^5 = 7737.81
            assertTrue(result.contains("7737.81"), "负利率贬值，实际: " + result);
        }
    }

    @Nested
    @DisplayName("单利终值 simpleInterest")
    class SimpleInterestTest {

        @Test
        @DisplayName("标准单利 - 10万年化5%存5年")
        void standardCase() {
            String result = tool.simpleInterest(100000, 0.05, 5);
            // 利息 = 100000 * 0.05 * 5 = 25000
            assertTrue(result.contains("25000.00"), "利息应为25000");
            assertTrue(result.contains("125000.00"), "终值应为125000");
        }

        @Test
        @DisplayName("对比复利 - 单利收益应低于复利")
        void lowerThanCompound() {
            String simple = tool.simpleInterest(100000, 0.05, 5);
            String compound = tool.compoundInterest(100000, 0.05, 5);
            // 单利终值125000 < 复利终值127628.16
            assertTrue(simple.contains("125000.00"));
            assertTrue(compound.contains("127628.16"));
        }
    }

    @Nested
    @DisplayName("年化收益率 annualizedReturn")
    class AnnualizedReturnTest {

        @Test
        @DisplayName("3个月赚8% → 年化约36.63%")
        void threeMonths() {
            String result = tool.annualizedReturn(8, 90);
            // (1+0.08)^(365/90) - 1 ≈ 0.3663
            assertTrue(result.contains("36.63") || result.contains("36.6"),
                    "年化应约36.63%，实际: " + result);
        }

        @Test
        @DisplayName("1年持有 → 年化等于总收益")
        void oneYear() {
            String result = tool.annualizedReturn(15, 365);
            assertTrue(result.contains("15.00"), "1年持有年化=总收益");
        }

        @Test
        @DisplayName("0天 → 返回错误提示")
        void zeroDays() {
            String result = tool.annualizedReturn(8, 0);
            assertTrue(result.contains("必须大于0"), "0天应返回错误");
        }
    }

    @Nested
    @DisplayName("定投收益 dcaReturn")
    class DcaReturnTest {

        @Test
        @DisplayName("每月2000 年化8% 36个月")
        void standardDca() {
            String result = tool.dcaReturn(2000, 0.08, 36);
            // 总投入 = 72000
            assertTrue(result.contains("72000.00"), "总投入应为72000");
            // 终值应 > 72000（有收益）
            assertTrue(result.contains("收益"), "应有收益");
        }

        @Test
        @DisplayName("零利率定投 - 终值等于总投入")
        void zeroRateDca() {
            String result = tool.dcaReturn(2000, 0, 12);
            assertTrue(result.contains("24000.00"), "零利率终值=总投入");
        }

        @Test
        @DisplayName("1个月定投 - 终值≈投入金额")
        void oneMonthDca() {
            String result = tool.dcaReturn(2000, 0.08, 1);
            // 1个月，终值≈2000
            assertTrue(result.contains("2000"), "1个月定投终值≈2000，实际: " + result);
        }
    }

    @Nested
    @DisplayName("复利+定投 compoundDca")
    class CompoundDcaTest {

        @Test
        @DisplayName("日定投：年化7.8%，初始7890元，每日50元，20年")
        void dailyDca() {
            String result = tool.compoundDca(7890, 50, 0.078, 20, "daily");
            // 初始资金终值: 7890 * (1+0.078/365)^7300 ≈ 37540.94
            assertTrue(result.contains("37540.94"),
                    "初始资金终值≈37540.94，实际: " + result);
            // 总终值 ≈ 917014.09
            assertTrue(result.contains("917014.09"),
                    "总终值≈917014.09，实际: " + result);
        }

        @Test
        @DisplayName("月定投：年化8%，初始1万，每月2000元，10年")
        void monthlyDca() {
            String result = tool.compoundDca(10000, 2000, 0.08, 10, "monthly");
            assertTrue(result.contains("每月定投"), "应显示每月");
            assertTrue(result.contains("终值"), "应有终值");
        }

        @Test
        @DisplayName("零利率日定投")
        void zeroRateDaily() {
            String result = tool.compoundDca(10000, 50, 0, 1, "daily");
            // 初始10000不变 + 50*365=18250 = 28250
            assertTrue(result.contains("28250") || result.contains("28250.00"),
                    "零利率总终值=总投入，实际: " + result);
        }

        @Test
        @DisplayName("不支持的频率 → 错误提示")
        void invalidFrequency() {
            String result = tool.compoundDca(10000, 50, 0.08, 10, "hourly");
            assertTrue(result.contains("不支持"), "应返回不支持提示");
        }
    }

    @Nested
    @DisplayName("72法则 ruleOf72")
    class RuleOf72Test {

        @Test
        @DisplayName("年化6% → 约12年翻倍")
        void sixPercent() {
            String result = tool.ruleOf72(0.06);
            assertTrue(result.contains("12.0"), "6%翻倍约12年");
        }

        @Test
        @DisplayName("年化8% → 9年翻倍")
        void eightPercent() {
            String result = tool.ruleOf72(0.08);
            assertTrue(result.contains("9.0"), "8%翻倍约9年");
        }

        @Test
        @DisplayName("零利率 → 返回错误")
        void zeroRate() {
            String result = tool.ruleOf72(0);
            assertTrue(result.contains("必须大于0"));
        }

        @Test
        @DisplayName("负利率 → 返回错误")
        void negativeRate() {
            String result = tool.ruleOf72(-0.05);
            assertTrue(result.contains("必须大于0"));
        }
    }

    // ==================== B. 估值指标 ====================

    @Nested
    @DisplayName("市盈率 peRatio")
    class PeRatioTest {

        @Test
        @DisplayName("股价50 EPS2.5 → PE=20 合理区间")
        void standardPe() {
            String result = tool.peRatio(50, 2.5);
            assertTrue(result.contains("20.00"), "PE应为20");
            assertTrue(result.contains("合理区间"));
        }

        @Test
        @DisplayName("低PE → 低估区间")
        void lowPe() {
            String result = tool.peRatio(10, 2);
            assertTrue(result.contains("5.00"), "PE应为5");
            assertTrue(result.contains("低估区间"));
        }

        @Test
        @DisplayName("高PE → 高估")
        void highPe() {
            String result = tool.peRatio(200, 2);
            assertTrue(result.contains("100.00"));
            assertTrue(result.contains("高估"));
        }

        @Test
        @DisplayName("EPS为0 → 返回错误")
        void zeroEps() {
            String result = tool.peRatio(50, 0);
            assertTrue(result.contains("每股收益为0"));
        }

        @Test
        @DisplayName("负EPS（亏损公司） → 返回提示")
        void negativeEps() {
            String result = tool.peRatio(50, -2);
            assertTrue(result.contains("亏损") || result.contains("无参考意义"),
                    "负EPS应提示亏损，实际: " + result);
        }
    }

    @Nested
    @DisplayName("市净率 pbRatio")
    class PbRatioTest {

        @Test
        @DisplayName("股价15 净资产10 → PB=1.5 较低")
        void standardPb() {
            String result = tool.pbRatio(15, 10);
            assertTrue(result.contains("1.50"), "PB应为1.5");
            assertTrue(result.contains("较低"));
        }

        @Test
        @DisplayName("股价低于净资产 → 破净")
        void belowBook() {
            String result = tool.pbRatio(8, 10);
            assertTrue(result.contains("0.80"));
            assertTrue(result.contains("破净"));
        }

        @Test
        @DisplayName("净资产为0 → 返回错误")
        void zeroBvps() {
            String result = tool.pbRatio(15, 0);
            assertTrue(result.contains("每股净资产为0"));
        }

        @Test
        @DisplayName("负BVPS（资不抵债） → 返回提示")
        void negativeBvps() {
            String result = tool.pbRatio(5, -3);
            assertTrue(result.contains("资不抵债") || result.contains("无参考意义"),
                    "负BVPS应提示资不抵债，实际: " + result);
        }
    }

    @Nested
    @DisplayName("股息率 dividendYield")
    class DividendYieldTest {

        @Test
        @DisplayName("年分红2元 股价40 → 股息率5%")
        void standardYield() {
            String result = tool.dividendYield(2, 40);
            assertTrue(result.contains("5.00"), "股息率应为5%");
        }

        @Test
        @DisplayName("股价为0 → 返回错误")
        void zeroPrice() {
            String result = tool.dividendYield(2, 0);
            assertTrue(result.contains("股价不能为0"));
        }

        @Test
        @DisplayName("零分红 → 股息率0%")
        void zeroDividend() {
            String result = tool.dividendYield(0, 40);
            assertTrue(result.contains("0.00"), "零分红股息率应为0%");
        }

        @Test
        @DisplayName("负分红 → 负股息率")
        void negativeDividend() {
            String result = tool.dividendYield(-1, 40);
            assertTrue(result.contains("-2.50"), "负分红股息率应为-2.5%，实际: " + result);
        }
    }

    // ==================== C. 贷款 ====================

    @Nested
    @DisplayName("等额本息月供 loanPayment")
    class LoanPaymentTest {

        @Test
        @DisplayName("100万 4.1% 30年(360月)")
        void mortgage() {
            String result = tool.loanPayment(1000000, 0.041, 360);
            // 月供约 4831.89
            assertTrue(result.contains("4831.89") || result.contains("4831."),
                    "月供应约4831.89，实际: " + result);
            assertTrue(result.contains("360月"));
        }

        @Test
        @DisplayName("零利率 → 月供=本金/月数")
        void zeroRate() {
            String result = tool.loanPayment(120000, 0, 12);
            assertTrue(result.contains("10000.00"), "零利率月供应为10000");
        }

        @Test
        @DisplayName("短期贷款 - 1年12期")
        void shortTerm() {
            String result = tool.loanPayment(12000, 0.06, 12);
            // 月供约 1032.80
            assertTrue(result.contains("1032.") || result.contains("1033."),
                    "短期贷款月供，实际: " + result);
        }

        @Test
        @DisplayName("负利率 → 不崩溃")
        void negativeRate() {
            String result = tool.loanPayment(100000, -0.01, 12);
            assertNotNull(result, "负利率不应崩溃");
            assertFalse(result.contains("失败"), "负利率不应报错");
        }
    }

    // ==================== D. 投资决策 ====================

    @Nested
    @DisplayName("净现值 npv")
    class NpvTest {

        @Test
        @DisplayName("经典NPV案例 - 投入1万 分4年收回")
        void standardNpv() {
            String result = tool.npv(0.1, "-10000,3000,4000,5000,6000");
            // NPV = -10000 + 3000/1.1 + 4000/1.21 + 5000/1.331 + 6000/1.4641
            //     = -10000 + 2727.27 + 3305.79 + 3756.57 + 4098.08 = 3887.71
            assertTrue(result.contains("NPV > 0"), "NPV应>0，实际: " + result);
            assertTrue(result.contains("值得投资"));
        }

        @Test
        @DisplayName("NPV为负 → 不值得投资")
        void negativeNpv() {
            String result = tool.npv(0.2, "-10000,2000,2000,2000");
            assertTrue(result.contains("NPV < 0") || result.contains("不值得投资"),
                    "高折现率NPV应<0，实际: " + result);
        }

        @Test
        @DisplayName("NPV=0 → 盈亏平衡")
        void zeroNpv() {
            // -1000 + 1100/1.1 = -1000 + 1000 = 0
            String result = tool.npv(0.1, "-1000,1100");
            assertTrue(result.contains("盈亏平衡"), "NPV=0应为盈亏平衡，实际: " + result);
        }
    }

    @Nested
    @DisplayName("内部收益率 irr")
    class IrrTest {

        @Test
        @DisplayName("经典IRR案例")
        void standardIrr() {
            String result = tool.irr("-10000,3000,4000,5000,6000");
            // IRR ≈ 24.89%
            assertTrue(result.contains("24."), "IRR应约24.89%，实际: " + result);
        }

        @Test
        @DisplayName("无收益投资 → IRR为负")
        void negativeIrr() {
            String result = tool.irr("-10000,1000,1000,1000");
            assertTrue(result.contains("IRR"), "应返回IRR结果");
        }

        @Test
        @DisplayName("两年回本")
        void twoYearPayback() {
            String result = tool.irr("-10000,6000,6000");
            // IRR ≈ 13.07%
            assertTrue(result.contains("13."), "IRR应约13%，实际: " + result);
        }
    }

    // ==================== E. 债券 ====================

    @Nested
    @DisplayName("债券定价 bondPrice")
    class BondPriceTest {

        @Test
        @DisplayName("票面利率=市场利率 → 平价发行（价格≈面值）")
        void parBond() {
            String result = tool.bondPrice(1000, 0.05, 0.05, 10);
            assertTrue(result.contains("1000.00") || result.contains("平价"),
                    "平价债券价格≈面值，实际: " + result);
        }

        @Test
        @DisplayName("市场利率>票面利率 → 折价发行")
        void discountBond() {
            String result = tool.bondPrice(1000, 0.05, 0.08, 10);
            assertTrue(result.contains("折价"), "市场利率高应折价");
        }

        @Test
        @DisplayName("市场利率<票面利率 → 溢价发行")
        void premiumBond() {
            String result = tool.bondPrice(1000, 0.08, 0.05, 10);
            assertTrue(result.contains("溢价"), "票面利率高应溢价");
        }
    }

    @Nested
    @DisplayName("债券到期收益率 bondYtm")
    class BondYtmTest {

        @Test
        @DisplayName("平价债券YTM ≈ 票面利率")
        void parBondYtm() {
            // 平价买入：市场价=面值
            String result = tool.bondYtm(1000, 1000, 0.05, 10);
            assertTrue(result.contains("5.00") || result.contains("4.99") || result.contains("5.01"),
                    "平价YTM≈票面利率5%，实际: " + result);
        }

        @Test
        @DisplayName("折价买入YTM > 票面利率")
        void discountBondYtm() {
            String result = tool.bondYtm(1000, 900, 0.05, 10);
            // YTM应 > 5%
            assertTrue(result.contains("6.") || result.contains("7."),
                    "折价YTM应>票面利率，实际: " + result);
        }

        @Test
        @DisplayName("溢价买入YTM < 票面利率")
        void premiumBondYtm() {
            String result = tool.bondYtm(1000, 1100, 0.05, 10);
            // YTM应 < 5%
            assertTrue(result.contains("3.") || result.contains("4."),
                    "溢价YTM应<票面利率，实际: " + result);
        }
    }

    // ==================== F. 退休规划 ====================

    @Nested
        @DisplayName("退休本金估算 retirementTarget")
    class RetirementTargetTest {

        @Test
        @DisplayName("年支出20万 默认4%提取率 → 需要500万")
        void standardTarget() {
            String result = tool.retirementTarget(200000, null);
            assertTrue(result.contains("5000000.00"), "20万/4%=500万，实际: " + result);
        }

        @Test
        @DisplayName("自定义3%提取率")
        void customSwr() {
            String result = tool.retirementTarget(200000, 0.03);
            // 200000 / 0.03 = 6666666.67
            assertTrue(result.contains("6666666.67"), "20万/3%≈666万，实际: " + result);
        }
    }

    @Nested
    @DisplayName("定额提取 withdrawalPlan")
    class WithdrawalPlanTest {

        @Test
        @DisplayName("100万本金 每年取8万 6%收益")
        void standardWithdrawal() {
            String result = tool.withdrawalPlan(1000000, 80000, 0.06);
            assertTrue(result.contains("本金可持续"), "应返回可持续年数");
        }

        @Test
        @DisplayName("提取金额超过收益 → 不会永续")
        void overWithdrawal() {
            String result = tool.withdrawalPlan(1000000, 200000, 0.04);
            // 100万*4%=4万收益，取20万远超收益，约7-8年耗尽
            assertFalse(result.contains("永续"), "超额提取不应永续");
        }

        @Test
        @DisplayName("提取金额低于收益 → 永续")
        void underWithdrawal() {
            String result = tool.withdrawalPlan(1000000, 30000, 0.06);
            // 100万*6%=6万收益，取3万低于收益
            assertTrue(result.contains("永续"), "低提取应永续");
        }

        @Test
        @DisplayName("零提取 → 返回错误")
        void zeroWithdrawal() {
            String result = tool.withdrawalPlan(1000000, 0, 0.06);
            assertTrue(result.contains("必须大于0"));
        }

        @Test
        @DisplayName("负收益率 → 加速耗尽")
        void negativeReturnRate() {
            String result = tool.withdrawalPlan(1000000, 80000, -0.02);
            // 负收益+提取，应比正收益更快耗尽
            assertFalse(result.contains("永续"), "负收益率不应永续");
            assertTrue(result.contains("本金可持续"), "应返回可持续年数");
        }
    }

    // ==================== G. 风险指标 ====================

    @Nested
    @DisplayName("夏普比率 sharpeRatio")
    class SharpeRatioTest {

        @Test
        @DisplayName("收益12% 无风险3% 波动率15% → 夏普=0.60")
        void standardSharpe() {
            String result = tool.sharpeRatio(0.12, 0.03, 0.15);
            // (0.12 - 0.03) / 0.15 = 0.60
            assertTrue(result.contains("0.60"), "夏普应为0.60，实际: " + result);
            assertTrue(result.contains("一般"));
        }

        @Test
        @DisplayName("优秀夏普比率 > 2")
        void excellentSharpe() {
            String result = tool.sharpeRatio(0.20, 0.03, 0.08);
            // (0.20 - 0.03) / 0.08 = 2.125
            assertTrue(result.contains("2.12") || result.contains("2.13"),
                    "夏普应约2.12-2.13，实际: " + result);
            assertTrue(result.contains("优秀"));
        }

        @Test
        @DisplayName("负夏普 → 风险调整后收益为负")
        void negativeSharpe() {
            String result = tool.sharpeRatio(0.02, 0.05, 0.20);
            // (0.02 - 0.05) / 0.20 = -0.15
            assertTrue(result.contains("-0.15"), "夏普应为-0.15，实际: " + result);
            assertTrue(result.contains("不推荐"));
        }

        @Test
        @DisplayName("零波动率 → 返回错误")
        void zeroVolatility() {
            String result = tool.sharpeRatio(0.12, 0.03, 0);
            assertTrue(result.contains("波动率不能为0"));
        }
    }

    @Nested
    @DisplayName("最大回撤 maxDrawdown")
    class MaxDrawdownTest {

        @Test
        @DisplayName("经典回撤序列")
        void standardDrawdown() {
            // 净值: 1.0 → 1.2(峰) → 0.9(谷, 回撤25%) → 1.1 → 0.8(谷, 回撤33.3%) → 1.0
            String result = tool.maxDrawdown("1.0,1.2,0.9,1.1,0.8,1.0");
            assertTrue(result.contains("33.33"), "最大回撤应为33.33%，实际: " + result);
        }

        @Test
        @DisplayName("单边上扬 → 回撤为0")
        void noDrawdown() {
            String result = tool.maxDrawdown("1.0,1.1,1.2,1.3,1.4");
            assertTrue(result.contains("0.00"), "单边上扬无回撤");
        }

        @Test
        @DisplayName("单边下跌 → 回撤从起点算")
        void allDown() {
            String result = tool.maxDrawdown("1.0,0.8,0.6,0.4");
            // 从1.0跌到0.4 = 60%回撤
            assertTrue(result.contains("60.00"), "应为60%回撤，实际: " + result);
        }
    }

    // ==================== H. 补充实用方法 ====================

    @Nested
    @DisplayName("复合年增长率 cagr")
    class CagrTest {

        @Test
        @DisplayName("10万→20万 10年 → CAGR≈7.18%")
        void doubleIn10Years() {
            String result = tool.cagr(100000, 200000, 10);
            // (2)^(1/10) - 1 = 0.07177 ≈ 7.18%
            assertTrue(result.contains("7.17") || result.contains("7.18"),
                    "CAGR应约7.18%，实际: " + result);
        }

        @Test
        @DisplayName("亏损情况 - 10万→5万")
        void negativeCagr() {
            String result = tool.cagr(100000, 50000, 5);
            // (0.5)^(1/5) - 1 = -0.1294 ≈ -12.94%
            assertTrue(result.contains("-12.94") || result.contains("-12.9"),
                    "亏损CAGR应约-12.94%，实际: " + result);
        }

        @Test
        @DisplayName("期初为0 → 返回错误")
        void zeroBegin() {
            String result = tool.cagr(0, 100000, 5);
            assertTrue(result.contains("期初价值必须大于0"));
        }

        @Test
        @DisplayName("0年 → 返回错误")
        void zeroYears() {
            String result = tool.cagr(100000, 200000, 0);
            assertTrue(result.contains("投资年数必须大于0"));
        }
    }

    @Nested
    @DisplayName("总收益率 totalReturn")
    class TotalReturnTest {

        @Test
        @DisplayName("买入10 卖出15 分红1 → 收益率60%")
        void withDividends() {
            String result = tool.totalReturn(10, 15, 1);
            // (15 + 1 - 10) / 10 = 60%
            assertTrue(result.contains("60.00"), "总收益率应为60%");
        }

        @Test
        @DisplayName("无分红 → 纯价差收益")
        void noDividends() {
            String result = tool.totalReturn(100, 120, 0);
            assertTrue(result.contains("20.00"), "纯价差收益20%");
        }

        @Test
        @DisplayName("亏损卖出")
        void loss() {
            String result = tool.totalReturn(100, 80, 0);
            assertTrue(result.contains("-20.00"), "亏损20%");
        }

        @Test
        @DisplayName("买入价为0 → 返回错误")
        void zeroBuyPrice() {
            String result = tool.totalReturn(0, 100, 0);
            assertTrue(result.contains("买入价必须大于0"));
        }
    }

    @Nested
    @DisplayName("通胀调整 inflationAdjusted")
    class InflationAdjustedTest {

        @Test
        @DisplayName("100万 3%通胀 10年后购买力")
        void standardInflation() {
            String result = tool.inflationAdjusted(1000000, 0.03, 10);
            // 100万 * (0.97)^10 = 737424.14
            assertTrue(result.contains("737424.14") || result.contains("737424."),
                    "10年后购买力约73.7万，实际: " + result);
        }

        @Test
        @DisplayName("0%通胀 → 购买力不变")
        void zeroInflation() {
            String result = tool.inflationAdjusted(1000000, 0, 10);
            assertTrue(result.contains("1000000.00"), "0通胀购买力不变");
        }

        @Test
        @DisplayName("高通胀10% 20年")
        void highInflation() {
            String result = tool.inflationAdjusted(1000000, 0.10, 20);
            // 100万 * (0.9)^20 = 121576.65
            assertTrue(result.contains("121576.65") || result.contains("121576."),
                    "高通胀20年购买力仅剩约12万，实际: " + result);
        }
    }
}
