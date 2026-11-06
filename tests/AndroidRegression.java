package dev.pocketpulse.monitor;

import org.json.JSONArray;
import org.json.JSONObject;

/** Runs on Android's own JSON implementation through dalvikvm, not a desktop substitute. */
public final class AndroidRegression {
    private static int checks;
    private static void check(boolean condition) { checks++; if (!condition) throw new AssertionError("Check " + checks); }
    private static JSONObject release(String tag, boolean draft, boolean prerelease, String url) throws Exception {
        return new JSONObject().put("tag_name", tag).put("draft", draft).put("prerelease", prerelease)
                .put("html_url", UpdateChecker.REPOSITORY + "/releases/tag/" + tag)
                .put("assets", new JSONArray().put(new JSONObject().put("name", "Pocket-Pulse-" + tag + ".apk")
                        .put("state", "uploaded").put("browser_download_url", url)));
    }
    private static String apk(String tag) { return UpdateChecker.REPOSITORY + "/releases/download/" + tag + "/Pocket-Pulse-" + tag + ".apk"; }
    private static UpdateChecker.Release newest(JSONArray releases) { return UpdateChecker.newest(releases, ReleaseVersion.parse("0.2.0")); }
    public static void main(String[] args) throws Exception {
        check(newest(new JSONArray()) == null);
        check(newest(new JSONArray().put(release("v0.1.0", false, false, apk("v0.1.0")))) == null);
        check(newest(new JSONArray().put(release("v0.2.0", false, false, apk("v0.2.0")))) == null);
        check(newest(new JSONArray().put(release("v0.3.0", true, false, apk("v0.3.0")))) == null);
        check(newest(new JSONArray().put(release("latest", false, false, apk("latest")))) == null);
        check(newest(new JSONArray().put(release("v0.3.0", false, false, "http://github.com/TygerBDevTeam/pocket-pulse/releases/download/bad.apk"))) == null);
        check(newest(new JSONArray().put(release("v0.3.0", false, false, "https://github.com.evil.test/TygerBDevTeam/pocket-pulse/releases/download/bad.apk"))) == null);
        check(newest(new JSONArray().put(release("v0.3.0", false, false, "https://github.com/another/repo/releases/download/bad.apk"))) == null);
        check(newest(new JSONArray().put(release("v0.3.0", false, false, "https://github.com@evil.test/TygerBDevTeam/pocket-pulse/releases/download/bad.apk"))) == null);
        JSONObject noApk = release("v0.3.0", false, false, apk("v0.3.0"));
        noApk.put("assets", new JSONArray()); check(newest(new JSONArray().put(noApk)) == null);
        JSONObject wrongAsset = release("v0.3.0", false, false, apk("v0.3.0"));
        wrongAsset.getJSONArray("assets").getJSONObject(0).put("name", "Other-App.apk");
        check(newest(new JSONArray().put(wrongAsset)) == null);
        JSONObject uploading = release("v0.3.0", false, false, apk("v0.3.0"));
        uploading.getJSONArray("assets").getJSONObject(0).put("state", "new");
        check(newest(new JSONArray().put(uploading)) == null);
        JSONObject wrongPage = release("v0.3.0", false, false, apk("v0.3.0"));
        wrongPage.put("html_url", "https://evil.test/release");
        check(newest(new JSONArray().put(wrongPage)) == null);
        UpdateChecker.Release test = newest(new JSONArray().put(release("v0.3.0", false, true, apk("v0.3.0"))));
        check(test != null && test.prerelease && test.tag.equals("v0.3.0"));
        check(newest(new JSONArray().put(release("v0.9.0", false, false, apk("v0.9.0")))
                .put(release("v0.10.0", false, false, apk("v0.10.0")))).tag.equals("v0.10.0"));
        check(newest(new JSONArray().put(release("v0.10.0", false, false, apk("v0.10.0")))
                .put(release("v0.9.0", false, false, apk("v0.9.0")))).tag.equals("v0.10.0"));
        check(newest(new JSONArray().put(release("v0.2.0-beta.1", false, true, apk("v0.2.0-beta.1")))) == null);
        check(newest(new JSONArray().put(release("v0.3.0-beta.1", false, true, apk("v0.3.0-beta.1")))) != null);
        check(newest(new JSONArray().put(JSONObject.NULL).put(new JSONObject())) == null);
        StringBuilder notes = new StringBuilder(); for(int i=0;i<2000;i++) notes.append('x');
        JSONObject longNotes = release("v0.3.0", false, false, apk("v0.3.0")).put("body", notes.toString());
        check(newest(new JSONArray().put(longNotes)).notes.length() == 1501);
        System.out.println("PASS: " + checks + " Android release-selection checks");
    }
}
