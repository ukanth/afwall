package dev.ukanth.ufirewall.log;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.util.PackageComparator;

/**
 * Created by ukanth on 19/11/16.
 */

public class MultiListPreference extends MultiSelectListPreference {

    protected Context context;
    protected CharSequence[] entries = {};
    protected CharSequence[] entryValues = {};


    public MultiListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public MultiListPreference(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        this.context = context;
    }

    @Override
    protected View onCreateDialogView() {
        ListView view = new ListView(getContext());
        view.setAdapter(adapter());

        return view;
    }

    @Override
    protected void showDialog(Bundle state) {
        initializeValues();
        setEntries(entries());
        setEntryValues(entryValues());
        super.showDialog(state);

        AlertDialog dialog = (AlertDialog)getDialog();
        if (dialog == null)
            return;
        //BUG: https://code.google.com/p/android/issues/detail?id=205487
        if (Build.VERSION.SDK_INT >= 23) {
            ListView listView = dialog.getListView();

            listView.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {
                    int size = view.getChildCount();
                    for (int i = 0; i < size; i++) {
                        View v = view.getChildAt(i);
                        if (v instanceof CheckedTextView)
                            ((CheckedTextView) v).refreshDrawableState();
                    }
                }

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    int size = view.getChildCount();
                    for (int i = 0; i < size; i++) {
                        View v = view.getChildAt(i);
                        if (v instanceof CheckedTextView)
                            ((CheckedTextView) v).refreshDrawableState();
                    }
                }
            });
        }

    }

    private void initializeValues() {
        ArrayList<CharSequence> entriesList = new ArrayList<CharSequence>();
        ArrayList<CharSequence> entryValuesList = new ArrayList<CharSequence>();

        List<Api.PackageInfoData> apps;
        if (Api.applications == null) {
            apps = Api.getApps(context, null);
        } else {
            apps = Api.applications;
        }

        try {
            Collections.sort(apps, new PackageComparator());
        } catch (Exception e) {
            Log.e(Api.TAG, "Exception on Sort " + e.getMessage());
        }
        for (int i = 0; i < apps.size(); i++) {
            entriesList.add(apps.get(i).toStringWithUID());
            entryValuesList.add(apps.get(i).uid + "");
        }

        entries = (CharSequence[]) entriesList.toArray(new CharSequence[entriesList.size()]);
        entryValues = (CharSequence[]) entryValuesList.toArray(new CharSequence[entryValuesList.size()]);
    }

    private ListAdapter adapter() {
        return new ArrayAdapter<String>(getContext(), android.R.layout.select_dialog_multichoice);
    }

    // TODO: adjust available entries with SettingsUtils.getAvailableCanteens()!
    private CharSequence[] entries() {
        return entries;
    }

    private CharSequence[] entryValues() {
        return entryValues;
    }
}
