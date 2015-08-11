package dev.ukanth.ufirewall.log;

import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.res.TypedArray;
import android.preference.ListPreference;
import android.util.AttributeSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.Api.PackageInfoData;
import dev.ukanth.ufirewall.util.PackageComparator;

public class MultiSelectListPreference extends ListPreference {
 
    private String separator;
    private static final String DEFAULT_SEPARATOR = ",";
    private boolean[] entryChecked;
    private Context ctx;
 
    public MultiSelectListPreference(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        this.ctx = context;
        if(getEntries() == null){
        	setData();	
        }
        entryChecked = new boolean[getEntries().length];
        separator = DEFAULT_SEPARATOR;
    }
 
    public MultiSelectListPreference(Context context) {
        this(context, null);
    }
    
	private void setData() {

        try {
            List<PackageInfoData> apps;
            if (Api.applications == null) {
                apps = Api.getApps(ctx, null);
            } else {
                apps =  Api.applications;
            }

            try {
                Collections.sort(apps, new PackageComparator());
            }catch(Exception e) {
                Log.e(Api.TAG, "Exception on Sort " + e.getMessage());
            }


            CharSequence[] entries = new CharSequence[apps.size()];
            CharSequence[] entryValues = new CharSequence[apps.size()];
            //int i = 0;
            for(int i=0; i < apps.size(); i++) {
                entries[i] = apps.get(i).toStringWithUID();
                entryValues[i] = apps.get(i).uid + "";
            }
            setEntries(entries);
            setEntryValues(entryValues);
        } catch(Exception e) {

        }

	}
 
    @Override
    protected void onPrepareDialogBuilder(Builder builder) {
    	 try {
             List<PackageInfoData> apps;
             if (Api.applications == null) {
                 apps = Api.getApps(ctx, null);
             } else {
                 apps =  Api.applications;
             }

             try {
                 Collections.sort(apps, new PackageComparator());
             }catch(Exception e) {
                 Log.e(Api.TAG, "Exception on Sort " + e.getMessage());
             }

             CharSequence[] entries = new CharSequence[apps.size()];
             CharSequence[] entryValues = new CharSequence[apps.size()];
             for(int i=0; i < apps.size(); i++) {
                 entries[i] = apps.get(i).toStringWithUID();
                 entryValues[i] = apps.get(i).uid + "";
             }
             setEntries(entries);
             setEntryValues(entryValues);

             if (entries == null || entryValues == null
                     || entries.length != entryValues.length) {
                 throw new IllegalStateException(
                         "MultiSelectListPreference requires an entries array and an entryValues "
                                 + "array which are both the same length");
             }

             restoreCheckedEntries();
             OnMultiChoiceClickListener listener = new DialogInterface.OnMultiChoiceClickListener() {
                 public void onClick(DialogInterface dialog, int which, boolean val) {
                     entryChecked[which] = val;
                 }
             };
             builder.setMultiChoiceItems(entries, entryChecked, listener);
         } catch (Exception e) {

         }

    }
 
    private CharSequence[] unpack(CharSequence val) {
        if (val == null || "".equals(val)) {
            return new CharSequence[0];
        } else {
            return ((String) val).split(separator);
        }
    }
    
    /**
     * Gets the entries values that are selected
     * 
     * @return the selected entries values
     */
    public CharSequence[] getCheckedValues() {
        return unpack(getValue());
    }
 
    private void restoreCheckedEntries() {
        CharSequence[] entryValues = getEntryValues();
 
        // Explode the string read in sharedpreferences
        CharSequence[] vals = unpack(getValue());
 
        if (vals != null) {
            List<CharSequence> valuesList = Arrays.asList(vals);
            for (int i = 0; i < entryValues.length; i++) {
                CharSequence entry = entryValues[i];
                entryChecked[i] = valuesList.contains(entry);
            }
        }
    }
 
    @Override
    protected void onDialogClosed(boolean positiveResult) {
        List<CharSequence> values = new ArrayList<CharSequence>();
 
        CharSequence[] entryValues = getEntryValues();
        if (positiveResult && entryValues != null) {
            for (int i = 0; i < entryValues.length; i++) {
                if (entryChecked[i] == true) {
                    String val = (String) entryValues[i];
                    values.add(val);
                }
            }
 
            String value = join(values, separator);
            setSummary(prepareSummary(values));
            setValueAndEvent(value);
        }
    }
 
    private void setValueAndEvent(String value) {
        if (callChangeListener(unpack(value))) {
            setValue(value);
        }
    }
 
    private CharSequence prepareSummary(List<CharSequence> joined) {
        List<String> titles = new ArrayList<String>();
        CharSequence[] entryTitle = getEntries();
        CharSequence[] entryValues = getEntryValues();
        int ix = 0;
        for (CharSequence value : entryValues) {
            if (joined.contains(value)) {
                titles.add((String) entryTitle[ix]);
            }
            ix += 1;
        }
        return join(titles, ", ");
    }
 
    @Override
    protected Object onGetDefaultValue(TypedArray typedArray, int index) {
        return typedArray.getTextArray(index);
    }
 
    @Override
    protected void onSetInitialValue(boolean restoreValue,
            Object rawDefaultValue) {
        String value = null;
        CharSequence[] defaultValue;
        if (rawDefaultValue == null) {
            defaultValue = new CharSequence[0];
        } else {
            defaultValue = (CharSequence[]) rawDefaultValue;
        }
        List<CharSequence> joined = Arrays.asList(defaultValue);
        String joinedDefaultValue = join(joined, separator);
        if (restoreValue) {
            if(joinedDefaultValue instanceof java.lang.String) {
                value = getPersistedString(joinedDefaultValue);
            }
        } else {
            value = joinedDefaultValue;
        }
 
        setSummary(prepareSummary(Arrays.asList(unpack(value))));
        setValueAndEvent(value);
    }
 
  
    protected static String join(Iterable<?> iterable, String separator) {
        Iterator<?> oIter;
        if (iterable == null || (!(oIter = iterable.iterator()).hasNext()))
            return "";
        StringBuilder oBuilder = new StringBuilder(String.valueOf(oIter.next()));
        while (oIter.hasNext())
            oBuilder.append(separator).append(oIter.next());
        return oBuilder.toString();
    }
 
}