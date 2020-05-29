/**
 * Class to store wifi/3G/tethering status and LAN IP ranges.
 *
 * Copyright (C) 2013 Kevin Cernekee
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Kevin Cernekee
 * @version 1.0
 */

package dev.ukanth.ufirewall;

public class InterfaceDetails {
	// firewall policy
	public boolean isRoaming = false;

	public boolean isWifiTethered = false;
	public boolean tetherWifiStatusKnown = false;

	public boolean isBluetoothTethered = false;
	public boolean tetherBluetoothStatusKnown = false;

	public boolean isUsbTethered = false;
	public boolean tetherUsbStatusKnown = false;

	public String lanMaskV4 = "";
	public String lanMaskV6 = "";
	// TODO: identify DNS servers instead of opening up port 53/udp to all LAN hosts

	// supplementary info
	String wifiName = "";
	boolean netEnabled = false;
	boolean noIP = false;
	public int netType = -1;

	public boolean equals(InterfaceDetails that) {
        return this.isRoaming == that.isRoaming &&
                this.isWifiTethered == that.isWifiTethered &&
                this.tetherWifiStatusKnown == that.tetherWifiStatusKnown &&
                this.isBluetoothTethered == that.isBluetoothTethered &&
                this.tetherBluetoothStatusKnown == that.tetherBluetoothStatusKnown &&
                this.isUsbTethered == that.isUsbTethered &&
                this.tetherUsbStatusKnown == that.tetherUsbStatusKnown &&
                this.lanMaskV4.equals(that.lanMaskV4) &&
                this.lanMaskV6.equals(that.lanMaskV6) &&
                this.wifiName.equals(that.wifiName) &&
                this.netEnabled == that.netEnabled &&
                this.netType == that.netType &&
                this.noIP == that.noIP;
    }
}