package dev.ukanth.ufirewall.util;

import java.util.List;

/**
 * Created by ukanth on 22/11/16.
 */

public class Rule {

    String name;
    String desc;
    List<String> ipv4On;
    List<String> ipv4Off;
    List<String> ipv6On;
    List<String> ipv6Off;

    public List<String> getIpv4On() {
        return ipv4On;
    }

    public void setIpv4On(List<String> ipv4On) {
        this.ipv4On = ipv4On;
    }

    public List<String> getIpv4Off() {
        return ipv4Off;
    }

    public void setIpv4Off(List<String> ipv4Off) {
        this.ipv4Off = ipv4Off;
    }

    public List<String> getIpv6On() {
        return ipv6On;
    }

    public void setIpv6On(List<String> ipv6On) {
        this.ipv6On = ipv6On;
    }

    public List<String> getIpv6Off() {
        return ipv6Off;
    }

    public void setIpv6Off(List<String> ipv6Off) {
        this.ipv6Off = ipv6Off;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }
}
