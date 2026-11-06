package dev.pocketpulse.monitor;

public final class DashboardLayoutTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Layout check " + checks); }
    private static DashboardLayout choose(int width, int viewport, float density, boolean compact) {
        int columns = DashboardLayout.columns(width, density, 1, true, true);
        int[] minimums = new int[]{Math.round(180*density),Math.round(155*density),Math.round(125*density),Math.round(90*density)};
        return DashboardLayout.choose(width, viewport, density, columns, true, compact, minimums);
    }
    public static void main(String[] args) {
        DashboardLayout normal = choose(920, 500, 1.25f, false);
        check(normal.columns == 4 && normal.profile == 0 && !normal.scrolling);
        // The old 155dp minimum overflows this 1280x960 / 320dpi content viewport.
        DashboardLayout nova = choose(1216, 568, 2, false);
        check(nova.columns == 4 && nova.profile == 2 && !nova.scrolling);
        check(nova.cardHeight * nova.rows + nova.gap * (nova.rows-1) <= 568);
        DashboardLayout narrowLandscape = choose(960, 470, 2, false);
        check(narrowLandscape.columns == 4 && !narrowLandscape.scrolling);
        check(DashboardLayout.columns(960, 2, 1, true, false) == 2);
        DashboardLayout tiny = choose(448, 80, 1, false);
        check(tiny.scrolling && tiny.profile == 3 && tiny.cardHeight >= 90);
        DashboardLayout manual = DashboardLayout.choose(920, 220, 1.25f, 4, false, false, new int[]{225,195,155,112});
        check(manual.scrolling && manual.profile == 0);
        check(choose(920,500,1.25f,true).profile >= 1);
        check(DashboardLayout.columns(400,1,1,false,true) == 2);
        check(DashboardLayout.columns(240,1,1,false,true) == 1);
        check(DashboardLayout.columns(448,1,1.5f,true,true) == 2);
        for (float density : new float[]{1,1.25f,1.5f,1.75f,2,2.25f,2.5f,3}) {
            for (int width = 300; width <= 1800; width += 37) {
                for (int viewport = 100; viewport <= 1200; viewport += 43) {
                    DashboardLayout plan = choose(width, viewport, density, false);
                    check(plan.cardWidth > 0 && plan.cardHeight > 0);
                    check(plan.cardWidth * plan.columns + (plan.columns-1)*plan.gap <= width);
                    check(plan.scrolling || plan.cardHeight * plan.rows + (plan.rows-1)*plan.gap <= viewport);
                }
            }
        }
        System.out.println("PASS: " + checks + " dashboard layout checks");
    }
}
