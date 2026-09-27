package dev.ukanth.ufirewall.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns the iptables command list of a full rule apply into one {@code iptables-restore --noflush}
 * input plus a few plain commands.
 * <p>
 * The commands are replayed against an in-memory model of the chains they create or flush
 * ({@code -N}/{@code -F}), so the restore input contains the exact final state of those chains
 * and loads them in one atomic commit per table, instead of one full table rewrite per command.
 * Operations on built-in chains (policies, the jumps into our chains) must not touch the rules
 * other components keep there, so they are returned unchanged, in their original order, to run
 * as plain commands after the restore.
 * <p>
 * Returns {@code null} when the list contains anything that cannot be reproduced exactly (custom
 * script lines, unknown operations, chains we didn't create, ...); the caller then runs the
 * commands one by one as before. Kept free of Android dependencies so it can be unit tested.
 */
public final class IptablesRestorePlanner {

    private static final String NOCHK = "#NOCHK# ";
    private static final Set<String> BUILTIN_CHAINS = new HashSet<>(Arrays.asList(
            "INPUT", "OUTPUT", "FORWARD", "PREROUTING", "POSTROUTING"));

    public static final class Plan {
        /** input for {@code iptables-restore --noflush} */
        public final String restoreInput;
        /** built-in chain commands (same format as the input list) to run after the restore */
        public final List<String> postCommands;

        Plan(String restoreInput, List<String> postCommands) {
            this.restoreInput = restoreInput;
            this.postCommands = postCommands;
        }
    }

    private static final class Table {
        // chain -> rule specs, in declaration order
        final LinkedHashMap<String, List<String>> chains = new LinkedHashMap<>();
    }

    private IptablesRestorePlanner() {
    }

    /**
     * @param cmds commands without the binary name, e.g. "-A afwall -j RETURN",
     *             "#NOCHK# -N afwall", "-t nat -F afwall-tor", "-P OUTPUT DROP"
     * @return the plan, or null if the list can't be expressed exactly
     */
    public static Plan plan(List<String> cmds) {
        Map<String, Table> tables = new LinkedHashMap<>();
        tables.put("filter", new Table());
        tables.put("nat", new Table());
        List<String> post = new ArrayList<>();

        for (String raw : cmds) {
            if (raw == null) {
                continue;
            }
            boolean nochk = raw.startsWith(NOCHK);
            String cmd = (nochk ? raw.substring(NOCHK.length()) : raw).trim();
            if (cmd.isEmpty() || cmd.startsWith("#")) {
                return null; // #LITERAL# (custom script) or unknown marker
            }

            String table = "filter";
            if (cmd.startsWith("-t ")) {
                String[] t = cmd.split("\\s+", 3);
                if (t.length < 3) {
                    return null;
                }
                table = t[1];
                cmd = t[2];
            }
            Table model = tables.get(table);
            if (model == null) {
                return null;
            }

            String[] tok = cmd.split("\\s+", 3);
            if (tok.length < 2) {
                return null;
            }
            String op = tok[0];
            String chain = tok[1];
            String spec = tok.length > 2 ? tok[2].trim() : "";

            if (BUILTIN_CHAINS.contains(chain)) {
                if (!op.equals("-P") && !op.equals("-A") && !op.equals("-I") && !op.equals("-D")) {
                    return null;
                }
                post.add(raw);
                continue;
            }

            List<String> rules = model.chains.get(chain);
            switch (op) {
                case "-N":
                    if (rules != null) {
                        if (!nochk) {
                            return null; // would fail: chain exists
                        }
                    } else {
                        // may already exist in the kernel; declaring it creates or flushes it,
                        // which is what the -N/-F pairs of a full apply amount to
                        model.chains.put(chain, new ArrayList<>());
                    }
                    break;
                case "-F":
                    if (rules == null) {
                        if (!nochk) {
                            return null; // existence unknown: plain -F fails on a missing chain
                        }
                        model.chains.put(chain, new ArrayList<>());
                    } else {
                        rules.clear();
                    }
                    break;
                case "-A":
                    if (rules == null || spec.isEmpty()) {
                        return null; // chain not created by this list: its current rules are unknown
                    }
                    rules.add(spec);
                    break;
                case "-I": {
                    if (rules == null || spec.isEmpty()) {
                        return null;
                    }
                    int pos = 1;
                    String[] p = spec.split("\\s+", 2);
                    if (p[0].matches("\\d+")) {
                        pos = Integer.parseInt(p[0]);
                        if (p.length < 2) {
                            return null;
                        }
                        spec = p[1];
                    }
                    if (pos < 1 || pos > rules.size() + 1) {
                        return null;
                    }
                    rules.add(pos - 1, spec);
                    break;
                }
                case "-D":
                    if (rules == null || spec.isEmpty() || spec.matches("\\d+")) {
                        return null;
                    }
                    if (!rules.remove(spec) && !nochk) {
                        return null; // would fail: no such rule
                    }
                    break;
                default:
                    return null;
            }
        }

        StringBuilder in = new StringBuilder();
        for (Map.Entry<String, Table> e : tables.entrySet()) {
            Table t = e.getValue();
            if (t.chains.isEmpty()) {
                continue;
            }
            in.append('*').append(e.getKey()).append('\n');
            for (String chain : t.chains.keySet()) {
                in.append(':').append(chain).append(" - [0:0]\n");
            }
            for (Map.Entry<String, List<String>> c : t.chains.entrySet()) {
                for (String spec : c.getValue()) {
                    in.append("-A ").append(c.getKey()).append(' ').append(spec).append('\n');
                }
            }
            in.append("COMMIT\n");
        }
        if (in.length() == 0) {
            return null;
        }
        return new Plan(in.toString(), post);
    }
}
