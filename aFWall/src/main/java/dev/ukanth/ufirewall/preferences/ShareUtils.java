package dev.ukanth.ufirewall.preferences;

import java.util.HashSet;
import java.util.Set;


/**
 * Created by ukanth on 19/7/16.
 */
public class ShareUtils {
    @SuppressWarnings("unchecked")
    public static Set<String> toStringSet(Object value) {
        return (Set<String>)value;
    }

    public static Object serialize(Object value) {
        if (value instanceof Boolean) {
            return (Boolean)value ? 1 : 0;
        } else if (value instanceof Set<?>) {
            return ShareUtils.serializeStringSet(toStringSet(value));
        } else {
            return value;
        }
    }

    public static Object deserialize(Object value, int expectedType) {
        if (value == null) {
            return null;
        } else if (expectedType == ShareContract.TYPE_BOOLEAN) {
            return (Integer)value != 0;
        } else if (expectedType == ShareContract.TYPE_STRING_SET) {
            return ShareUtils.deserializeStringSet((String)value);
        } else {
            return value;
        }
    }

    public static String serializeStringSet(Set<String> stringSet) {
        StringBuilder sb = new StringBuilder();
        for (String s : stringSet) {
            sb.append(s.replace("\\", "\\\\").replace(";", "\\;"));
            sb.append(';');
        }
        return sb.toString();
    }

    public static Set<String> deserializeStringSet(String serializedString) {
        HashSet<String> stringSet = new HashSet<String>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < serializedString.length(); ++i) {
            char c = serializedString.charAt(i);
            if (c == '\\') {
                char next = serializedString.charAt(++i);
                sb.append(next);
            } else if (c == ';') {
                stringSet.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        return stringSet;
    }
}