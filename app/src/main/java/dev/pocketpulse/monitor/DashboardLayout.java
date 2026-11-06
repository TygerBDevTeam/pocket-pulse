package dev.pocketpulse.monitor;

/** Pixel geometry shared by the native views and regression tests. Heights use actual font metrics. */
final class DashboardLayout {
    final int columns, rows, profile, gap, cardWidth, cardHeight;
    final boolean scrolling;

    private DashboardLayout(int columns, int profile, int gap, int width, int height, boolean scrolling) {
        this.columns = columns; this.rows = (8 + columns - 1) / columns;
        this.profile = profile; this.gap = gap;
        this.cardWidth = Math.max(1, (width - (columns - 1) * gap) / columns);
        this.cardHeight = Math.max(1, height); this.scrolling = scrolling;
    }

    static int columns(int width, float density, float fontScale, boolean landscape, boolean autoFit) {
        float scale = Math.max(1f, fontScale);
        if (width >= Math.round(560 * density * scale)) return 4;
        // A short landscape window needs two rows, not four tall rows chosen from width alone.
        if (autoFit && landscape && width >= Math.round(420 * density * scale)) return 4;
        return width >= Math.round(290 * density * scale) ? 2 : 1;
    }

    static DashboardLayout choose(int width, int viewport, float density, int columns,
                                  boolean autoFit, boolean forceCompact, int[] minimumHeights) {
        int rows = (8 + columns - 1) / columns;
        int first = forceCompact ? 1 : 0;
        int last = autoFit ? minimumHeights.length - 1 : first;
        for (int profile = first; profile <= last; profile++) {
            int gap = Math.max(1, Math.round((profile >= 2 ? 4 : 10) * density));
            int height = Math.max(1, (viewport - (rows - 1) * gap) / rows);
            if (height >= minimumHeights[profile]) return new DashboardLayout(columns, profile, gap, width, height, false);
        }
        int gap = Math.max(1, Math.round((last >= 2 ? 4 : 10) * density));
        // Keep readable text for extremely small windows/large accessibility fonts.
        return new DashboardLayout(columns, last, gap, width, minimumHeights[last], true);
    }
}
