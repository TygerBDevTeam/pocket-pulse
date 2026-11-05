package dev.pocketpulse.monitor;

public final class MetricMathTest {
    private static int checks;
    private static void close(float actual, float expected) {
        checks++;
        if (Math.abs(actual - expected) > .001f || !Float.isFinite(actual)) throw new AssertionError(actual + " != " + expected);
    }
    private static void missing(float value) {
        checks++;
        if (!Float.isNaN(value)) throw new AssertionError("Expected unavailable, got " + value);
    }
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        long[] before = MetricMath.cpuTimes("cpu  100 10 20 400 20 5 5 0 9 3");
        long[] after = MetricMath.cpuTimes("cpu  150 10 40 420 30 5 5 0 19 3");
        check(before[0] == 560 && before[1] == 420);
        close(MetricMath.cpuPercent(before, after), 70);
        missing(MetricMath.cpuPercent(null, after));
        missing(MetricMath.cpuPercent(after, before));
        missing(MetricMath.cpuPercent(before, before));
        missing(MetricMath.cpuPercent(new long[]{0,0}, new long[]{100,101}));
        check(MetricMath.cpuTimes("cpu0 1 2 3 4") == null);
        check(MetricMath.cpuTimes("cpu x 2 3 4") == null);
        check(MetricMath.cpuTimes(null) == null);
        close(MetricMath.gpuBusy("250 1000"), 25);
        close(MetricMath.gpuBusy("0 1000"), 0);
        missing(MetricMath.gpuBusy("0 0"));
        missing(MetricMath.gpuBusy("110 100"));
        missing(MetricMath.gpuBusy("-1 100"));
        missing(MetricMath.gpuBusy("1"));
        missing(MetricMath.gpuBusy("unavailable"));
        close(MetricMath.percent("0"), 0);
        close(MetricMath.percent("100"), 100);
        missing(MetricMath.percent("101"));
        missing(MetricMath.percent("NaN"));
        missing(MetricMath.percent("Infinity"));
        close(MetricMath.usedRamPercent(8L << 30, 3L << 30), 62.5f);
        missing(MetricMath.usedRamPercent(0, 0));
        missing(MetricMath.usedRamPercent(10, 11));
        close(MetricMath.processCpu(4, 2000), .2f);
        close(MetricMath.processCpu(200, 100), 200); // One-core normalization can exceed 100%.
        missing(MetricMath.processCpu(1, 0));
        MetricMath.History history = new MetricMath.History(3);
        history.add(1); history.add(2); history.add(Float.NaN); history.add(4);
        check(history.size() == 3);
        close(history.get(0), 2);
        missing(history.get(1));
        close(history.get(2), 4);
        history.clear();
        check(history.size() == 0);
        history.add(9);
        close(history.get(0), 9);
        System.out.println("PASS: " + checks + " telemetry regression checks");
    }
}
