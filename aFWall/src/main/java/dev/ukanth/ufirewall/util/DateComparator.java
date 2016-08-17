package dev.ukanth.ufirewall.util;

import java.util.Comparator;

import dev.ukanth.ufirewall.log.LogData;

/**
 * Created by ukanth on 27/7/16.
 */
public class DateComparator implements Comparator<LogData>{

    @Override
    public int compare(LogData o1, LogData o2) {
        Long o1_date = o1.getTimestamp();
        Long o2_date = o2.getTimestamp();
        return (o1_date > o2_date) ? -1: (o1_date < o2_date) ? 0 : 1;
    }
}
