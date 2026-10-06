package dev.ukanth.ufirewall.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parser for the custom startup / shutdown scripts.
 * <p>
 * Each line is one iptables command. It is never handed to a shell as written: it is split into
 * arguments here (with sh-like quoting), checked, and run as "{@code <iptables binary> <args>}"
 * with every argument quoted again. Shell features (;, &&, |, redirects, $(...), sourcing) are
 * rejected instead of being filtered by keyword.
 * <p>
 * The first word picks the address family:
 * <ul>
 * <li>{@code iptables}: IPv4 only</li>
 * <li>{@code ip6tables} / {@code $IP6TABLES}: IPv6 only</li>
 * <li>{@code $IPTABLES} or no binary at all (the line starts with an option such as {@code -A}):
 * both, with the binary of each pass</li>
 * </ul>
 * {@code $AFWALL_CHAIN} (or {@code ${AFWALL_CHAIN}}) is replaced by the main AFWall+ chain name,
 * e.g. "afwall" ("afwall10" for a secondary user). Lines starting with {@code #} are comments.
 */
public final class CustomScript {

    public enum Family {IPV4, IPV6, BOTH}

    public enum Problem {
        /** shell syntax (detail: the characters found) */
        SHELL_SYNTAX,
        /** unknown or misplaced variable (detail: the variable) */
        VARIABLE,
        UNTERMINATED_QUOTE,
        /** the line does not start with an iptables binary or option (detail: first word) */
        NOT_IPTABLES,
        /** no command option (-A, -I, ...) or more than one */
        COMMAND_COUNT,
        /** -L / -S / -C */
        LISTING,
        /** the command needs a chain name (detail: the command option) */
        MISSING_CHAIN,
        /** detail: the table name */
        UNKNOWN_TABLE,
        /** an option that would run another program (detail: the option) */
        FORBIDDEN_OPTION
    }

    /** A line that is not run. */
    public static final class Rejected {
        public final int lineNumber;
        public final String line;
        public final Problem problem;
        public final String detail;

        Rejected(int lineNumber, String line, Problem problem, String detail) {
            this.lineNumber = lineNumber;
            this.line = line;
            this.problem = problem;
            this.detail = detail;
        }

        @Override
        public String toString() {
            return "line " + lineNumber + " " + problem + (detail != null ? " (" + detail + ")" : "") + ": " + line;
        }
    }

    /** A valid line. */
    public static final class Command {
        public final int lineNumber;
        public final String line;
        public final Family family;
        /** iptables arguments, variables already replaced */
        public final List<String> args;
        /** "-A", "-I", "-D", "-R", "-N", "-X", "-F", "-Z", "-P" or "-E" */
        public final String op;
        public final String table;
        /** null for -F / -X / -Z without a chain */
        public final String chain;
        private final int opIndex;
        private final int ruleNumIndex;

        Command(int lineNumber, String line, Family family, List<String> args, String op,
                int opIndex, String table, String chain, int ruleNumIndex) {
            this.lineNumber = lineNumber;
            this.line = line;
            this.family = family;
            this.args = Collections.unmodifiableList(args);
            this.op = op;
            this.opIndex = opIndex;
            this.table = table;
            this.chain = chain;
            this.ruleNumIndex = ruleNumIndex;
        }

        /**
         * @param ipv6Pass    the address family pass being built
         * @param ipv6Enabled whether AFWall+ runs an IPv6 pass at all; if not, IPv6-only lines
         *                    run in the IPv4 pass (with ip6tables) so they are not lost
         */
        public boolean appliesTo(boolean ipv6Pass, boolean ipv6Enabled) {
            switch (family) {
                case IPV4:
                    return !ipv6Pass;
                case IPV6:
                    return ipv6Pass || !ipv6Enabled;
                default:
                    return true;
            }
        }

        /** @return true if ip6tables runs this line in the given pass */
        public boolean usesIp6tables(boolean ipv6Pass) {
            return family == Family.IPV6 || (family == Family.BOTH && ipv6Pass);
        }

        /** -A / -I: the rule is added, and {@link #deleteArgs()} removes it again */
        public boolean addsRule() {
            return "-A".equals(op) || "-I".equals(op);
        }

        /**
         * A failure that only means "already done": creating an existing chain, deleting a rule
         * or chain that is not there. Not worth a warning.
         */
        public boolean failureIsHarmless() {
            return "-N".equals(op) || "-D".equals(op) || "-X".equals(op);
        }

        /** @return the arguments that delete the rule this -A / -I line adds, or null */
        public List<String> deleteArgs() {
            if (!addsRule()) {
                return null;
            }
            List<String> del = new ArrayList<>(args);
            if (ruleNumIndex >= 0) {
                del.remove(ruleNumIndex);
            }
            del.set(opIndex, "-D");
            return del;
        }
    }

    public static final class Result {
        public final List<Command> commands;
        public final List<Rejected> rejected;

        Result(List<Command> commands, List<Rejected> rejected) {
            this.commands = Collections.unmodifiableList(commands);
            this.rejected = Collections.unmodifiableList(rejected);
        }
    }

    private static final Map<String, String> OPS = new HashMap<>();
    private static final Set<String> CHAIN_REQUIRED = new HashSet<>(Arrays.asList("-A", "-I", "-D", "-R", "-N", "-P", "-E"));
    private static final Set<String> LISTING_OPS = new HashSet<>(Arrays.asList(
            "-L", "--list", "-S", "--list-rules", "-C", "--check"));
    private static final Set<String> TABLES = new HashSet<>(Arrays.asList("filter", "nat", "mangle", "raw", "security"));
    private static final Pattern SAFE_ARG = Pattern.compile("[A-Za-z0-9_./:,=+@%^-]+");
    private static final Pattern RULE_NUM = Pattern.compile("[0-9]+");
    // placeholders for $IPTABLES / $IP6TABLES while splitting; only valid as the first word
    private static final String VAR_IPTABLES = "\u0000IPTABLES";
    private static final String VAR_IP6TABLES = "\u0000IP6TABLES";
    public static final String CHAIN_VARIABLE = "AFWALL_CHAIN";

    static {
        String[][] ops = {{"-A", "--append"}, {"-I", "--insert"}, {"-D", "--delete"}, {"-R", "--replace"},
                {"-N", "--new-chain"}, {"-X", "--delete-chain"}, {"-F", "--flush"}, {"-Z", "--zero"},
                {"-P", "--policy"}, {"-E", "--rename-chain"}};
        for (String[] op : ops) {
            OPS.put(op[0], op[0]);
            OPS.put(op[1], op[0]);
        }
    }

    private CustomScript() {
    }

    /**
     * @param script    the script text (may be null)
     * @param chainName the value of $AFWALL_CHAIN
     */
    public static Result parse(String script, String chainName) {
        List<Command> commands = new ArrayList<>();
        List<Rejected> rejected = new ArrayList<>();
        if (script == null) {
            return new Result(commands, rejected);
        }
        String[] lines = script.split("\r\n|\r|\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            Object parsed = parseLine(i + 1, line, chainName);
            if (parsed instanceof Command) {
                commands.add((Command) parsed);
            } else {
                rejected.add((Rejected) parsed);
            }
        }
        return new Result(commands, rejected);
    }

    private static final class SplitError extends Exception {
        final Problem problem;
        final String detail;

        SplitError(Problem problem, String detail) {
            super(problem.name());
            this.problem = problem;
            this.detail = detail;
        }
    }

    private static Object parseLine(int lineNumber, String line, String chainName) {
        List<String> tokens;
        try {
            tokens = split(line, chainName);
        } catch (SplitError e) {
            return new Rejected(lineNumber, line, e.problem, e.detail);
        }
        if (tokens.isEmpty()) {
            // only a trailing comment: "   # ..." is handled above, so this can't happen
            return new Rejected(lineNumber, line, Problem.COMMAND_COUNT, null);
        }

        String first = tokens.get(0);
        Family family;
        List<String> args;
        if ("iptables".equals(first)) {
            family = Family.IPV4;
            args = new ArrayList<>(tokens.subList(1, tokens.size()));
        } else if ("ip6tables".equals(first) || VAR_IP6TABLES.equals(first)) {
            family = Family.IPV6;
            args = new ArrayList<>(tokens.subList(1, tokens.size()));
        } else if (VAR_IPTABLES.equals(first)) {
            family = Family.BOTH;
            args = new ArrayList<>(tokens.subList(1, tokens.size()));
        } else if (first.startsWith("-")) {
            family = Family.BOTH;
            args = new ArrayList<>(tokens);
        } else {
            return new Rejected(lineNumber, line, Problem.NOT_IPTABLES, first);
        }

        String op = null;
        int opIndex = -1;
        String table = "filter";
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (a.equals(VAR_IPTABLES) || a.equals(VAR_IP6TABLES)) {
                return new Rejected(lineNumber, line, Problem.VARIABLE,
                        a.equals(VAR_IPTABLES) ? "$IPTABLES" : "$IP6TABLES");
            }
            if (a.equals("-M") || a.equals("--modprobe") || a.startsWith("--modprobe=")) {
                return new Rejected(lineNumber, line, Problem.FORBIDDEN_OPTION, a.split("=", 2)[0]);
            }
            if (LISTING_OPS.contains(a)) {
                return new Rejected(lineNumber, line, Problem.LISTING, a);
            }
            if (a.equals("-t") || a.equals("--table") || a.startsWith("--table=")) {
                String t = a.startsWith("--table=") ? a.substring("--table=".length())
                        : (i + 1 < args.size() ? args.get(++i) : "");
                if (!TABLES.contains(t)) {
                    return new Rejected(lineNumber, line, Problem.UNKNOWN_TABLE, t);
                }
                table = t;
                continue;
            }
            String norm = OPS.get(a);
            if (norm != null) {
                if (op != null) {
                    return new Rejected(lineNumber, line, Problem.COMMAND_COUNT, null);
                }
                op = norm;
                opIndex = i;
            }
        }
        if (op == null) {
            return new Rejected(lineNumber, line, Problem.COMMAND_COUNT, null);
        }

        String chain = null;
        int ruleNumIndex = -1;
        if (opIndex + 1 < args.size() && !args.get(opIndex + 1).startsWith("-")) {
            chain = args.get(opIndex + 1);
            if (op.equals("-I") && opIndex + 2 < args.size() && RULE_NUM.matcher(args.get(opIndex + 2)).matches()) {
                ruleNumIndex = opIndex + 2;
            }
        }
        if (chain == null && CHAIN_REQUIRED.contains(op)) {
            return new Rejected(lineNumber, line, Problem.MISSING_CHAIN, args.get(opIndex));
        }
        args.set(opIndex, op);
        return new Command(lineNumber, line, family, args, op, opIndex, table, chain, ruleNumIndex);
    }

    /**
     * Split a line into words like sh does for simple words: '...' is literal, "..." allows
     * $AFWALL_CHAIN, an unquoted # starts a comment. Everything that would make the shell do more
     * than run one command is an error.
     */
    private static List<String> split(String line, String chainName) throws SplitError {
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inToken = false;
        int n = line.length();
        int i = 0;
        while (i < n) {
            char c = line.charAt(i);
            if (Character.isWhitespace(c)) {
                if (inToken) {
                    tokens.add(cur.toString());
                    cur.setLength(0);
                    inToken = false;
                }
                i++;
            } else if (c == '#' && !inToken) {
                break;
            } else if (c == '\'') {
                int end = line.indexOf('\'', i + 1);
                if (end < 0) {
                    throw new SplitError(Problem.UNTERMINATED_QUOTE, null);
                }
                cur.append(line, i + 1, end);
                inToken = true;
                i = end + 1;
            } else if (c == '"') {
                i++;
                boolean closed = false;
                while (i < n) {
                    char d = line.charAt(i);
                    if (d == '"') {
                        closed = true;
                        i++;
                        break;
                    } else if (d == '$') {
                        i = appendVariable(line, i, cur, chainName, false);
                    } else if (d == '`' || d == '\\') {
                        throw new SplitError(Problem.SHELL_SYNTAX, String.valueOf(d));
                    } else {
                        cur.append(d);
                        i++;
                    }
                }
                if (!closed) {
                    throw new SplitError(Problem.UNTERMINATED_QUOTE, null);
                }
                inToken = true;
            } else if (c == '$') {
                i = appendVariable(line, i, cur, chainName, !inToken);
                inToken = true;
            } else if (";&|<>()`\\".indexOf(c) >= 0) {
                int end = i + 1;
                while (end < n && ";&|<>()`\\".indexOf(line.charAt(end)) >= 0) {
                    end++;
                }
                throw new SplitError(Problem.SHELL_SYNTAX, line.substring(i, end));
            } else {
                cur.append(c);
                inToken = true;
                i++;
            }
        }
        if (inToken) {
            tokens.add(cur.toString());
        }
        return tokens;
    }

    /**
     * @param wordStart true if the variable starts a word ($IPTABLES / $IP6TABLES are allowed
     *                  there; whether it is the first word is checked by the caller)
     * @return the index after the variable
     */
    private static int appendVariable(String line, int i, StringBuilder cur, String chainName, boolean wordStart)
            throws SplitError {
        int n = line.length();
        int start = i + 1;
        String name;
        int end;
        if (start < n && line.charAt(start) == '{') {
            int close = line.indexOf('}', start);
            if (close < 0) {
                throw new SplitError(Problem.SHELL_SYNTAX, "${");
            }
            name = line.substring(start + 1, close);
            end = close + 1;
        } else {
            end = start;
            while (end < n && (Character.isLetterOrDigit(line.charAt(end)) || line.charAt(end) == '_')) {
                end++;
            }
            name = line.substring(start, end);
        }
        if (name.isEmpty()) {
            throw new SplitError(Problem.SHELL_SYNTAX, line.substring(i, Math.min(n, i + 2)));
        }
        if (CHAIN_VARIABLE.equals(name)) {
            cur.append(chainName);
            return end;
        }
        boolean wordEnd = end >= n || Character.isWhitespace(line.charAt(end));
        if (wordStart && wordEnd && ("IPTABLES".equals(name) || "IP6TABLES".equals(name))) {
            cur.append("IPTABLES".equals(name) ? VAR_IPTABLES : VAR_IP6TABLES);
            return end;
        }
        throw new SplitError(Problem.VARIABLE, "$" + name);
    }

    /** Quote an argument for sh, leaving plain words as they are. */
    public static String shellQuote(String arg) {
        if (!arg.isEmpty() && SAFE_ARG.matcher(arg).matches()) {
            return arg;
        }
        return "'" + arg.replace("'", "'\\''") + "'";
    }

    /** @return "binary 'arg' ..." ready for the root shell */
    public static String toShell(String binary, List<String> args) {
        StringBuilder sb = new StringBuilder(binary);
        for (String a : args) {
            sb.append(' ').append(shellQuote(a));
        }
        return sb.toString();
    }
}
