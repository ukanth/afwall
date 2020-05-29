package dev.ukanth.ufirewall.util;

import java.util.Comparator;

import dev.ukanth.ufirewall.Api;

/**
 * Created by ukanth on 11/8/15.
 */
public class PackageComparator implements Comparator<Api.PackageInfoData> {

    @Override
    public int compare(Api.PackageInfoData o1, Api.PackageInfoData o2) {
        if (o1.firstseen != o2.firstseen) {
            return (o1.firstseen ? -1 : 1);
        }
        boolean o1_selected = o1.selected_3g || o1.selected_wifi || o1.selected_roam ||
                o1.selected_vpn || o1.selected_tether || o1.selected_lan || o1.selected_tor;
        boolean o2_selected = o2.selected_3g || o2.selected_wifi || o2.selected_roam ||
                o2.selected_vpn || o2.selected_tether || o2.selected_lan || o2.selected_tor;

        if (o1_selected == o2_selected) {
            switch (G.sortBy()) {
                case "s0":
                    return String.CASE_INSENSITIVE_ORDER.compare(o1.names.get(0), o2.names.get(0));
                case "s1":
                    return (o1.installTime > o2.installTime) ? -1: (o1.installTime < o2.installTime) ? 1 : 0;
                case "s2":
                    return (o2.uid > o1.uid) ? -1: (o2.uid < o1.uid) ? 0 : 1;
            }
        }
        if (o1_selected)
            return -1;
        return 1;
    }
}
