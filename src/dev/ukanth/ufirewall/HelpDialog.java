/**
 * Dialog displayed when the "Help" menu option is selected
 * 
 * Copyright (C) 2009-2011  Rodrigo Zechin Rosauro
 * Copyright (C) 2011-2012  Umakanthan Chandran
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
 * @author Rodrigo Zechin Rosauro, Umakanthan Chandran
 * @version 1.1
 */
package dev.ukanth.ufirewall;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.view.View;

/**
 * Dialog displayed when the "Help" menu option is selected
 */
public class HelpDialog extends AlertDialog {
	@SuppressWarnings("deprecation")
	protected HelpDialog(Context context) {
		super(context);
		final View view = getLayoutInflater().inflate(R.layout.help_dialog, null);
		setButton(context.getText(R.string.close), (OnClickListener)null);
		setIcon(R.drawable.icon);
		String versionName ="";
		try {
			versionName = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
		} catch(NameNotFoundException e){
			
		}
		setTitle("AFWall+ " + versionName);
		setView(view);
	}
}
