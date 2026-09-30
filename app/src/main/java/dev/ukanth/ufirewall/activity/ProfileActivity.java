package dev.ukanth.ufirewall.activity;

import static dev.ukanth.ufirewall.util.G.isDonate;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.afollestad.materialdialogs.MaterialDialog;

import java.util.ArrayList;
import java.util.List;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.profiles.ProfileAdapter;
import dev.ukanth.ufirewall.profiles.ProfileData;
import dev.ukanth.ufirewall.profiles.ProfileHelper;
import dev.ukanth.ufirewall.util.AppRuleHelper;
import dev.ukanth.ufirewall.util.G;

/**
 * Created by ukanth on 31/7/15.
 */
public class ProfileActivity extends AppCompatActivity {
    List<ProfileData> profilesList = new ArrayList<ProfileData>();
    ProfileAdapter profileAdapter;

    protected static final int MENU_ADD = 100;
    //protected static final int MENU_CLONE = 101;
    protected static final int MENU_DELETE = 102;
    protected static final int MENU_RENAME = 103;
    protected static final int MENU_CLONE = 104;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profile_main);

        Toolbar toolbar = findViewById(R.id.profile_toolbar);
        setSupportActionBar(toolbar);
        setTitle(R.string.manage_profiles);

        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        initList();

        ListView listView = findViewById(R.id.listProfileView);
        profileAdapter = new ProfileAdapter(profilesList, this);
        listView.setAdapter(profileAdapter);
        // we register for the contextmneu
        registerForContextMenu(listView);
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        // Common options: Copy, Export to SD Card, Refresh
        menu.add(0, MENU_ADD, 0, getString(R.string.profile_add)).setIcon(R.drawable.plus).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        super.onCreateOptionsMenu(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case MENU_ADD:
                addNewProfile();
                break;
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }


    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterView.AdapterContextMenuInfo aInfo = (AdapterView.AdapterContextMenuInfo) menuInfo;
        //ProfileData profile = profileAdapter.getItem(aInfo.position);
        String name = ((TextView) aInfo.targetView.findViewById(R.id.pro_name)).getText().toString();
        menu.setHeaderTitle(getString(R.string.select) + " " + name);
        menu.add(0, MENU_CLONE, 0, getString(R.string.clone));
        menu.add(0, MENU_RENAME, 0, getString(R.string.rename));
        menu.add(0, MENU_DELETE, 0, getString(R.string.delete));
    }


    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        AdapterView.AdapterContextMenuInfo aInfo = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        String profileName = profilesList.get(aInfo.position).getName();
        switch (itemId) {
            case MENU_DELETE:
                // the default profile can't be deleted
                if (aInfo.position != 0) {
                    ProfileData data = ProfileHelper.getProfileByName(profileName);
                    if (data != null && ProfileHelper.deleteProfileByName(profileName)
                            && G.clearSharedPreferences(getApplicationContext(), data.getIdentifier())) {
                        AppRuleHelper.deleteRulesForProfile(data.getIdentifier());
                        profilesList.remove(aInfo.position);
                        profileAdapter.notifyDataSetChanged();
                    }
                }
                break;
            case MENU_CLONE:
                if ((G.isDoKey(getApplicationContext()) || isDonate())) {
                    ProfileData data = ProfileHelper.getProfileByName(profileName);
                    if(data != null) {
                        String exitingName = data.getName();
                        if(data != null) {
                            new MaterialDialog.Builder(this)
                                    .cancelable(true)
                                    .title(R.string.profile_rename)
                                    .inputType(InputType.TYPE_CLASS_TEXT)
                                    .input(exitingName, exitingName, (dialog, input) -> {
                                        String newName = input.toString();
                                        //copy data
                                        ProfileData data1 = null;
                                        try {
                                            data1 = data.clone();
                                            if (isNotDuplicate(newName)) {
                                                String identifier = newName.replaceAll("\\s+", "");
                                                data1.removeId();
                                                data1.setName(newName);
                                                data1.setIdentifier(identifier);
                                                data1.save();
                                                // rules are stored under the identifier, not the display name
                                                SharedPreferences fromShared = getSharedPreferences(data.getIdentifier(), Context.MODE_PRIVATE);
                                                SharedPreferences.Editor toShared = getSharedPreferences(identifier, Context.MODE_PRIVATE).edit();
                                                Api.copySharedPreferences(fromShared,toShared);
                                                AppRuleHelper.copyRulesToProfile(data.getIdentifier(), identifier);
                                                profilesList.add(data1);
                                                profileAdapter.notifyDataSetChanged();
                                            } else {
                                                Api.toast(getApplicationContext(), getString(R.string.profile_duplicate));
                                            }
                                        } catch (CloneNotSupportedException e) {
                                            Log.e(G.TAG, e.getMessage(), e);
                                        }


                                    }).show();
                        }
                    } else{
                        Log.i(G.TAG,"Unable to clone. Data from DB is empty");
                        Toast.makeText(getApplicationContext(), getString(R.string.unable_clone), Toast.LENGTH_LONG).show();
                    }

                } else{
                    Api.donateDialog(ProfileActivity.this, true);
                }
                break;
            case MENU_RENAME:
                ProfileData data2 = ProfileHelper.getProfileByName(profileName);
                if (data2 != null) {
                    renameProfile(data2, aInfo.position);
                }
                break;
        }
        return true;
    }


    private void initList() {
        profilesList = new ArrayList<>();
        // We populate the Profiles
        profilesList.add(new ProfileData(G.gPrefs.getString("default", getString(R.string.defaultProfile)), ""));

        profilesList.addAll(ProfileHelper.getProfiles());
    }

    private void renameProfile(final ProfileData data, final int position) {
        String exitingName = data.getName();
        new MaterialDialog.Builder(this)
                .cancelable(true)
                .title(R.string.profile_rename)
                .inputType(InputType.TYPE_CLASS_TEXT)
                .input(exitingName, exitingName, (dialog, input) -> {
                    String profileName = input.toString();
                    if (isNotDuplicate(profileName)) {
                        profilesList.remove(position);
                        data.setName(profileName);
                        data.save();
                        profilesList.add(position, data);
                        profileAdapter.notifyDataSetChanged();
                    } else {
                        Api.toast(getApplicationContext(), getString(R.string.profile_duplicate));
                    }

                }).show();
    }

    // Handle user click
    private void addNewProfile() {

        new MaterialDialog.Builder(this)
                .cancelable(true)
                .title(R.string.profile_add)
                .inputType(InputType.TYPE_CLASS_TEXT)
                .input(R.string.profile_add, R.string.profile_hint, (dialog, input) -> {
                    String profileName = input.toString();
                    if (isNotDuplicate(profileName)) {
                        String identifier = profileName.replaceAll("\\s+", "");
                        ProfileData data = new ProfileData(profileName, identifier);
                        data.save();
                        profilesList.add(data);
                        profileAdapter.notifyDataSetChanged();
                    } else {
                        Api.toast(getApplicationContext(), getString(R.string.profile_duplicate));
                    }
                    // We notify the data model is changed
                }).show();

    }

    private boolean isNotDuplicate(String profileName) {
        for (ProfileData data : profilesList) {
            if (data.getName().equals(profileName)) {
                return false;
            }
        }
        return true;
    }
}
