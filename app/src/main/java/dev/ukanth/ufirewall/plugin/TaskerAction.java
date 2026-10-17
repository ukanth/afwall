package dev.ukanth.ufirewall.plugin;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A Tasker/Locale action as saved by the editor: what it does and its settings. The editor signs
 * {@link #payload()}; {@link FireReceiver} checks the signature before running it.
 * <p>
 * Plain Java (no Android types) so it can be unit tested.
 */
final class TaskerAction {

    static final String ENABLE = "enable";
    static final String DISABLE = "disable";
    static final String APPLY = "apply";
    static final String PROFILE = "profile";
    static final String ALLOW = "allow";
    static final String BLOCK = "block";
    static final String STATUS = "status";

    static final List<String> ACTIONS = Collections.unmodifiableList(Arrays.asList(
            ENABLE, DISABLE, APPLY, PROFILE, ALLOW, BLOCK, STATUS));

    static final String NET_ALL = "all";
    static final List<String> NETWORKS = Collections.unmodifiableList(Arrays.asList(
            NET_ALL, "wifi", "mobile", "roaming", "vpn", "lan", "tether"));

    // bumped when the signed fields change
    static final String FORMAT = "1";

    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+");
    private static final int MAX_FIELD = 256;

    final String action;
    /**
     * Package name, or a template with a Tasker variable (e.g. {@code %app}); allow / block only.
     */
    final String pkg;
    final String network;
    final String profileId;
    final String profileName;
    /**
     * Random id given when the action is saved; shown in the recent actions.
     */
    final String id;
    final long created;
    /**
     * The app the action was saved in (e.g. Tasker), as the editor saw it.
     */
    final String caller;

    TaskerAction(String action, String pkg, String network, String profileId, String profileName,
                 String id, long created, String caller) {
        this.action = action;
        this.pkg = pkg;
        this.network = network;
        this.profileId = profileId;
        this.profileName = profileName;
        this.id = id;
        this.created = created;
        this.caller = caller;
    }

    /**
     * @return null if the action can be saved / run as is, else what is wrong (for the log)
     */
    String check() {
        if (!ACTIONS.contains(action)) {
            return "unknown action";
        }
        if (id == null || id.isEmpty() || created <= 0) {
            return "no id";
        }
        for (String field : new String[]{pkg, network, profileId, profileName, id, caller}) {
            if (field != null && field.length() > MAX_FIELD) {
                return "field too long";
            }
        }
        switch (action) {
            case ALLOW:
            case BLOCK:
                if (pkg == null || pkg.isEmpty()) {
                    return "no app";
                }
                if (!hasVariable(pkg) && !isPackageName(pkg)) {
                    return "invalid package name";
                }
                if (!NETWORKS.contains(network)) {
                    return "invalid network";
                }
                return null;
            case PROFILE:
                return profileId == null || profileId.isEmpty() ? "no profile" : null;
            default:
                return null;
        }
    }

    /**
     * The fields the signature covers, unambiguously: every value is length-prefixed, so content
     * can't be moved from one field to another.
     */
    byte[] payload() {
        StringBuilder sb = new StringBuilder("afwall-tasker\n");
        field(sb, "format", FORMAT);
        field(sb, "action", action);
        field(sb, "pkg", pkg);
        field(sb, "net", network);
        field(sb, "profileId", profileId);
        field(sb, "profileName", profileName);
        field(sb, "id", id);
        field(sb, "created", Long.toString(created));
        field(sb, "caller", caller);
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void field(StringBuilder sb, String key, String value) {
        sb.append(key).append('=');
        if (value == null) {
            sb.append('-');
        } else {
            sb.append(value.length()).append(':').append(value);
        }
        sb.append('\n');
    }

    /**
     * Actions that loosen the firewall; they post a notification when they run.
     */
    boolean loosens() {
        return DISABLE.equals(action) || ALLOW.equals(action);
    }

    boolean needsRoot() {
        return !STATUS.equals(action);
    }

    /**
     * The package to act on when the action runs: the saved name, or the value Tasker put in for
     * the variable.
     *
     * @param resolved the bundle's replaceable copy of {@link #pkg}
     * @return the package name, or null if the value isn't one (e.g. the variable wasn't set)
     */
    String packageToRun(String resolved) {
        if (!hasVariable(pkg)) {
            return pkg; // nothing to replace: ignore whatever the copy says
        }
        return resolved != null && isPackageName(resolved) ? resolved : null;
    }

    static boolean hasVariable(String value) {
        return value != null && value.indexOf('%') >= 0;
    }

    static boolean isPackageName(String value) {
        return value != null && value.length() <= MAX_FIELD && PACKAGE.matcher(value).matches();
    }
}
