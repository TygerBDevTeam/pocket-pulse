package dev.pocketpulse.monitor;

public final class ReleaseVersionTest {
    private static int checks;
    private static void compare(String left, String right, int direction) {
        checks++;
        ReleaseVersion a = ReleaseVersion.parse(left), b = ReleaseVersion.parse(right);
        if (a == null || b == null || Integer.signum(a.compareTo(b)) != direction) throw new AssertionError(left + " vs " + right);
    }
    private static void invalid(String value) {
        checks++;
        if (ReleaseVersion.parse(value) != null) throw new AssertionError("Accepted invalid version: " + value);
    }
    public static void main(String[] args) {
        compare("0.2.0", "v0.1.0", 1);
        compare("0.10.0", "0.9.0", 1);
        compare("1.0.0", "0.99.99", 1);
        compare("v0.2.0", "0.2.0", 0);
        compare("0.1.0", "0.2.0", -1);
        compare("0.2.0+build.99", "0.2.0", 0);
        String[] chain = {"1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta", "1.0.0-beta", "1.0.0-beta.2", "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0"};
        for (int i = 1; i < chain.length; i++) compare(chain[i], chain[i-1], 1);
        compare("0.3.0-beta.1", "0.2.0", 1);
        compare("0.2.0-beta.1", "0.2.0", -1);
        compare("1.0.0-999999999999999999999999", "1.0.0-99", 1);
        for (String value : new String[]{null,"","latest","1.0","01.0.0","1.0.0-01","1.0.0-","1.0.0/evil","9999999999999999999999999.0.0"}) invalid(value);
        System.out.println("PASS: " + checks + " release version checks");
    }
}
