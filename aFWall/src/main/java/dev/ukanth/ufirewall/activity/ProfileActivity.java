package dev.ukanth.ufirewall.activity;

import android.app.Dialog;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.util.Profile;
import dev.ukanth.ufirewall.util.ProfileAdapter;

/**
 * Created by ukanth on 31/7/15.
 */
public class ProfileActivity extends AppCompatActivity{
    List<Profile> profilesList = new ArrayList<Profile>();
    ProfileAdapter profileAdapter;

    protected static final int MENU_ADD = 100;

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
        Profile profile =  profileAdapter.getItem(aInfo.position);
        menu.setHeaderTitle(getString(R.string.select) +  " " + profile.getProfileName());
        menu.add(1, 1, 1, getString(R.string.delete));

    }


    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        AdapterView.AdapterContextMenuInfo aInfo = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        if(aInfo.position > 3) {
            G.removeAdditionalProfile(profilesList.get(aInfo.position).getProfileName());
            profilesList.remove(aInfo.position);
            profileAdapter.notifyDataSetChanged();
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
        for(String profileName : pList) {
            if(profileName !=null && profileName.length() > 0) {
                profilesList.add(new Profile(profileName));
            }
        }

    }


    // Handle user click
    public void addNewProfile() {
        final Dialog d = new Dialog(this);
        d.setContentView(R.layout.profile_adddialog);
        d.setTitle(getString(R.string.profile_add));
        d.setCancelable(true);

        final EditText edit = (EditText) d.findViewById(R.id.editTextProfile);
        Button b = (Button) d.findViewById(R.id.button1);
        b.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                String profileName = edit.getText().toString();
                ProfileActivity.this.profilesList.add(new Profile(profileName));
                G.addAdditionalProfile(profileName);
                ProfileActivity.this.profileAdapter.notifyDataSetChanged(); // We notify the data model is changed
                d.dismiss();
            }
        });
        d.show();
    }
}
