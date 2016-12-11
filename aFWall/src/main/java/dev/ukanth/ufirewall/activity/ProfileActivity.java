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

import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.util.Profile;
import dev.ukanth.ufirewall.util.ProfileAdapter;

/**
 * Created by ukanth on 31/7/15.
 */
public class ProfileActivity extends AppCompatActivity {
    List<Profile> profilesList = new ArrayList<Profile>();
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
        Profile profile = profileAdapter.getItem(aInfo.position);
        menu.setHeaderTitle(getString(R.string.select) + " " + profile.getProfileName());
        //menu.add(0, MENU_RENAME, 0, getString(R.string.rename));
        //menu.add(0, MENU_CLONE, 0, getString(R.string.clone));
        menu.add(0, MENU_DELETE, 0, getString(R.string.delete));
    }


    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch(itemId) {
            case MENU_DELETE:
                AdapterView.AdapterContextMenuInfo aInfo = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
                if (aInfo.position > 3) {
                    G.removeAdditionalProfile(profilesList.get(aInfo.position).getProfileName());
                    profilesList.remove(aInfo.position);
                    profileAdapter.notifyDataSetChanged();
                }
                break;
            //case MENU_CLONE: break;
            //case MENU_RENAME: break;
        }

        return true;
    }


    private void initList() {
        // We populate the Profiles

        profilesList.add(new Profile(G.gPrefs.getString("default", getString(R.string.defaultProfile))));

        profilesList.add(new Profile(G.gPrefs.getString("profile1", getString(R.string.profile1))));
        profilesList.add(new Profile(G.gPrefs.getString("profile2", getString(R.string.profile2))));
        profilesList.add(new Profile(G.gPrefs.getString("profile3", getString(R.string.profile3))));

        List<String> pList = G.getAdditionalProfiles();
        for (String profileName : pList) {
            if (profileName != null && profileName.length() > 0) {
                profilesList.add(new Profile(profileName));
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
                        ProfileActivity.this.profilesList.add(new Profile(profileName));
                        G.addAdditionalProfile(profileName);
                        ProfileActivity.this.profileAdapter.notifyDataSetChanged(); // We notify the data model is changed
                    }
                }).show();

    }
}
