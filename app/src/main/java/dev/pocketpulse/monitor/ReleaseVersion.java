package dev.pocketpulse.monitor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Semantic version comparison, independent of release dates or string ordering. */
final class ReleaseVersion implements Comparable<ReleaseVersion> {
    private static final Pattern FORMAT = Pattern.compile("^v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$");
    final long major, minor, patch;
    final String prerelease;

    private ReleaseVersion(long major, long minor, long patch, String prerelease) {
        this.major = major; this.minor = minor; this.patch = patch; this.prerelease = prerelease;
    }

    static ReleaseVersion parse(String value) {
        if (value == null || value.length() > 200) return null;
        Matcher match = FORMAT.matcher(value);
        if (!match.matches()) return null;
        try {
            String pre = match.group(4);
            if (pre != null) for (String part : pre.split("\\."))
                if (part.matches("[0-9]+") && part.length() > 1 && part.startsWith("0")) return null;
            return new ReleaseVersion(Long.parseLong(match.group(1)), Long.parseLong(match.group(2)), Long.parseLong(match.group(3)), pre);
        } catch (NumberFormatException ignored) { return null; }
    }

    @Override public int compareTo(ReleaseVersion other) {
        int result = Long.compare(major, other.major);
        if (result == 0) result = Long.compare(minor, other.minor);
        if (result == 0) result = Long.compare(patch, other.patch);
        if (result != 0) return result;
        if (prerelease == null) return other.prerelease == null ? 0 : 1;
        if (other.prerelease == null) return -1;
        String[] left = prerelease.split("\\."), right = other.prerelease.split("\\.");
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            boolean ln = left[i].matches("[0-9]+"), rn = right[i].matches("[0-9]+");
            if (ln && rn) {
                result = Integer.compare(left[i].length(), right[i].length());
                if (result == 0) result = left[i].compareTo(right[i]);
            } else if (ln != rn) result = ln ? -1 : 1;
            else result = left[i].compareTo(right[i]);
            if (result != 0) return result;
        }
        return Integer.compare(left.length, right.length);
    }
}
