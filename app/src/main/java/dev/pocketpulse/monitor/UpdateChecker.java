package dev.pocketpulse.monitor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;

/** Explicit, cancellable user-requested checks only. Never part of telemetry sampling. */
final class UpdateChecker {
    static final String REPOSITORY = "https://github.com/TygerBDevTeam/pocket-pulse";
    private static final String API = "https://api.github.com/repos/TygerBDevTeam/pocket-pulse/releases?per_page=20";
    private volatile HttpsURLConnection connection;
    private volatile boolean cancelled;

    static final class Release {
        final String tag, notes, page, apk;
        final boolean prerelease;
        Release(String tag, String notes, String page, String apk, boolean prerelease) {
            this.tag = tag; this.notes = notes; this.page = page; this.apk = apk; this.prerelease = prerelease;
        }
    }

    void cancel() {
        cancelled = true;
        HttpsURLConnection current = connection;
        if (current != null) current.disconnect();
    }

    Release check(String installed) throws Exception {
        ReleaseVersion current = ReleaseVersion.parse(installed);
        if (current == null) throw new IOException("The installed version could not be compared.");
        HttpsURLConnection request = (HttpsURLConnection) new URL(API).openConnection();
        connection = request;
        if (cancelled) { request.disconnect(); throw new IOException("Cancelled"); }
        request.setConnectTimeout(7000); request.setReadTimeout(7000);
        request.setInstanceFollowRedirects(false);
        request.setRequestProperty("Accept", "application/vnd.github+json");
        request.setRequestProperty("User-Agent", "Pocket-Pulse/" + installed);
        try {
            int code = request.getResponseCode();
            if (code == 403 || code == 429) throw new IOException("GitHub temporarily limited update checks. Try again later.");
            if (code != 200) throw new IOException("GitHub could not provide releases (HTTP " + code + ").");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream stream = request.getInputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    if (cancelled || Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
                    if (bytes.size() + read > 1048576) throw new IOException("The release response was too large. Open GitHub instead.");
                    bytes.write(buffer, 0, read);
                }
            }
            return newest(new JSONArray(bytes.toString("UTF-8")), current);
        } finally { connection = null; request.disconnect(); }
    }

    static Release newest(JSONArray releases, ReleaseVersion current) {
        Release best = null;
        ReleaseVersion newest = current;
        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.optJSONObject(i);
            if (release == null || release.optBoolean("draft", false)) continue;
            String tag = release.optString("tag_name", "");
            ReleaseVersion version = ReleaseVersion.parse(tag);
            if (version == null || version.compareTo(newest) <= 0) continue;
            String page = release.optString("html_url", "");
            if (!repositoryUrl(page, "/releases/tag/")) continue;
            JSONArray assets = release.optJSONArray("assets");
            if (assets == null) continue;
            for (int j = 0; j < assets.length(); j++) {
                JSONObject asset = assets.optJSONObject(j);
                if (asset == null || !"uploaded".equals(asset.optString("state"))) continue;
                String name = asset.optString("name", ""), apk = asset.optString("browser_download_url", "");
                if (!name.startsWith("Pocket-Pulse-") || !name.endsWith(".apk") || !repositoryUrl(apk, "/releases/download/")) continue;
                String notes = release.optString("body", "");
                if (notes.length() > 1500) notes = notes.substring(0, 1500) + "…";
                best = new Release(tag, notes, page, apk, release.optBoolean("prerelease", false));
                newest = version;
                break;
            }
        }
        return best;
    }

    private static boolean repositoryUrl(String url, String suffix) {
        try {
            URI uri = new URI(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && "github.com".equalsIgnoreCase(uri.getHost())
                    && uri.getUserInfo() == null && uri.getPort() == -1
                    && uri.getPath().startsWith("/TygerBDevTeam/pocket-pulse" + suffix);
        } catch (Exception ignored) { return false; }
    }
}
