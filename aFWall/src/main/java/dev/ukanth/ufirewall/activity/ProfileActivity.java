package dev.ukanth.ufirewall.activity;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.afollestad.materialdialogs.MaterialDialog;

import java.util.ArrayList;
import java.util.List;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.profiles.ProfileAdapter;
import dev.ukanth.ufirewall.profiles.ProfileData;
import dev.ukanth.ufirewall.profiles.ProfileHelper;
import dev.ukanth.ufirewall.util.G;

/**
 * Created by ukanth on 31/7/15.
 */
public class ProfileActivity extends AppCompatActivity {
    List<ProfileData> profilesList = new ArrayList<ProfileData>();
    ProfileAdapter profileAdapter;

    protected static final int MENU_ADD = 100;
    protected static final int MENU_CLONE = 101;
    protected static final int MENU_DELETE = 102;
    protected static final int MENU_RENAME = 103;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profile_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.profile_toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        initList();

        ListView listView = (ListView) findViewById(R.id.listProfileView);
        profileAdapter = new ProfileAdapter(profilesList, this);
        listView.setAdapter(profileAdapter);
        // we register for the contextmneu
        registerForContextMenu(listView);
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        // Common options: Copy, Export to SD Card, Refresh
        menu.add(0, MENU_ADD, 0, getString(R.string.profile_add)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
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
        ProfileData profile = profileAdapter.getItem(aInfo.position);
        menu.setHeaderTitle(getString(R.string.select) + " " + profile.getName());
        if (G.isProfileMigrated()) {
            menu.add(0, MENU_RENAME, 0, getString(R.string.rename));
            menu.add(0, MENU_CLONE, 0, getString(R.string.clone));
        }
        menu.add(0, MENU_DELETE, 0, getString(R.string.delete));
    }


    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case MENU_DELETE:
                AdapterView.AdapterContextMenuInfo aInfo = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
                String profileName = profilesList.get(aInfo.position).getName();
                if (!G.isProfileMigrated()) {
                    if (aInfo.position > 3) {
                        boolean deleted = G.removeAdditionalProfile(profileName);
                        if (deleted) {
                            profilesList.remove(aInfo.position);
                            profileAdapter.notifyDataSetChanged();
                        } else {
                            Api.toast(getApplicationContext(), getString(R.string.delete_profile));
                        }
                    } else {
                        //TODO: can't delete default profiles(1,2,3) msg - Use migrate option
                    }
                } else {
                    if (aInfo.position != 0) {
                        ProfileData data = ProfileHelper.getProfileByName(profileName);
                        if (data != null && ProfileHelper.deleteProfileByName(profileName)
                                && G.clearSharedPreferences(getApplicationContext(), data.getIdentifier())) {
                            profilesList.remove(aInfo.position);
                            profileAdapter.notifyDataSetChanged();
                        }
                    } else {
                        //can't delete default profile
                    }
                }
                break;
            case MENU_CLONE:
                break;
            case MENU_RENAME:
                break;
        }
        return true;
    }


    private void initList() {
        profilesList = new ArrayList<>();
        // We populate the Profiles
        profilesList.add(new ProfileData(G.gPrefs.getString("default", getString(R.string.defaultProfile)), ""));

        if (G.isProfileMigrated()) {
            List<ProfileData> profiles = ProfileHelper.getProfiles();
            for (ProfileData pro : profiles) {
                profilesList.add(pro);
            }
        } else {
            profilesList.add(new ProfileData(G.gPrefs.getString("profile1", getString(R.string.profile1)), ""));
            profilesList.add(new ProfileData(G.gPrefs.getString("profile2", getString(R.string.profile2)), ""));
            profilesList.add(new ProfileData(G.gPrefs.getString("profile3", getString(R.string.profile3)), ""));

            List<String> pList = G.getAdditionalProfiles();
            for (String profileName : pList) {
                if (profileName != null && profileName.length() > 0) {
                    profilesList.add(new ProfileData(profileName, profileName));
                }
            }
        }
    }


    // Handle user click
    public void addNewProfile() {

        new MaterialDialog.Builder(this)
                .cancelable(true)
                .title(R.string.profile_add)
                .inputType(InputType.TYPE_CLASS_TEXT)
                .input(R.string.profile_add, R.string.profile_hint, new MaterialDialog.InputCallback() {
                    @Override
                    public void onInput(MaterialDialog dialog, CharSequence input) {
                        String profileName = input.toString();
                        String identifier = profileName.replaceAll("\\s+", "");
                        ProfileData data = new ProfileData(profileName, identifier);
                        if (G.isProfileMigrated()) {
                            //store to database
                            data.save();
                        } else {
                            //still use old way
                            G.addAdditionalProfile(profileName);
                        }
                        ProfileActivity.this.profilesList.add(data);
                        ProfileActivity.this.profileAdapter.notifyDataSetChanged(); // We notify the data model is changed
                    }
                }).show();

    }
}
