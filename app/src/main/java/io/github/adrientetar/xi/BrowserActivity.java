package io.github.adrientetar.xi;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;

public class BrowserActivity extends AppCompatActivity
        implements View.OnClickListener, NavigationView.OnNavigationItemSelectedListener {
    private boolean fabMenuEnabled = false;
    private ActionBarDrawerToggle toggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.activity_browser);
        Toolbar toolbar = (Toolbar) this.findViewById(R.id.toolbar);
        this.setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) this.findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BrowserActivity.this.setShowFabMenu(!BrowserActivity.this.getShowFabMenu());
            }
        });

        FloatingActionButton fab2 = (FloatingActionButton) this.findViewById(R.id.fab2);
        fab2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder builder = new AlertDialog.Builder(BrowserActivity.this);
                builder.setTitle("Enter a file name:");
                final EditText input = new EditText(BrowserActivity.this);
                builder.setView(input);
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        File file = BrowserActivity.this.getCurrentPath();
                        String text = input.getText().toString();
                        File create = new File(file, text);
                        try {
                            if (!create.createNewFile()) return;
                        } catch (IOException e) {
                            e.printStackTrace();
                            // TODO: show a snackbar to say file creation failed
                            return;
                        }
                        BrowserActivity.this.updateCurrentPath();
                    }
                });
                builder.setNegativeButton("Cancel", null);
                builder.show();
            }
        });

        FloatingActionButton fab3 = (FloatingActionButton) this.findViewById(R.id.fab3);
        fab3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: fab2 and fab3 duplicate code
                AlertDialog.Builder builder = new AlertDialog.Builder(BrowserActivity.this);
                builder.setTitle("Enter a directory name:");
                final EditText input = new EditText(BrowserActivity.this);
                builder.setView(input);
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        File file = BrowserActivity.this.getCurrentPath();
                        String text = input.getText().toString();
                        File create = new File(file, text);
                        if (create.mkdir()) {
                            BrowserActivity.this.updateCurrentPath();
                        }
                    }
                });
                builder.setNegativeButton("Cancel", null);
                builder.show();
            }
        });

        DrawerLayout drawer = (DrawerLayout) this.findViewById(R.id.drawer_layout);
        // NOTA: we use manual handling, see here if drawer+backstack gets abandoned
        // http://stackoverflow.com/a/28177091
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        this.toggle = toggle;

        NavigationView navigationView = (NavigationView) this.findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // initial path
        File path = this.getFilesDir();
        this.setTitle(path.getName());

        // bootstrap fragment
        if (savedInstanceState == null && findViewById(R.id.fragment_container) != null) {
            ItemFragment fragment = new ItemFragment();
            Bundle bundle = new Bundle();
            bundle.putString(ItemFragment.ARG_PATH, path.getAbsolutePath());
            fragment.setArguments(bundle);
            this.getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, fragment)
                    .commit();
        }

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) this.findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            boolean willBack = this.getSupportFragmentManager().getBackStackEntryCount() > 0;
            super.onBackPressed();
            if (willBack) {
                if (this.getSupportFragmentManager().getBackStackEntryCount() == 0) {
                    this.toggle.setDrawerIndicatorEnabled(true);
                }
                // XXX: racy
                this.setTitle(this.getCurrentPath().getName());
            }

        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.getMenuInflater().inflate(R.menu.browser, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.v("Xi", "onOptionsItemSelected! " + item.getItemId());

        int id = item.getItemId();

        if (id == android.R.id.home) {
            if (this.getSupportFragmentManager().getBackStackEntryCount() > 0) {
                this.onBackPressed();
            } else {
                DrawerLayout drawer = (DrawerLayout) this.findViewById(R.id.drawer_layout);
                if (drawer.isDrawerOpen(GravityCompat.START)) {
                    drawer.closeDrawer(GravityCompat.START);
                } else {
                    drawer.openDrawer(GravityCompat.START);
                }
            }
            return true;
        }

        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        this.toggle.onConfigurationChanged(newConfig);
    }

    @Override
    public void onClick(View view) {
        TextView textView = (TextView) view.findViewById(R.id.item_name);
        File path = new File(this.getCurrentPath(), (String) textView.getText());
        if (path.isDirectory()) {
            this.setCurrentPath(path);
        } else {
            // spawn MainActivity
            Intent intent = new Intent(this, MainActivity.class);
            intent.setAction(Intent.ACTION_EDIT);
            intent.setData(Uri.fromFile(path));
            this.startActivity(intent);
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_camera) {
            // Handle the camera action
        } else if (id == R.id.nav_gallery) {

        } else if (id == R.id.nav_slideshow) {

        } else if (id == R.id.nav_manage) {

        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        }

        DrawerLayout drawer = (DrawerLayout) this.findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private boolean getShowFabMenu() {
        return this.fabMenuEnabled;
    }

    private void setShowFabMenu(boolean show) {
        if (this.fabMenuEnabled == show) {
            return;
        }
        // TODO: refactor all this
        FloatingActionButton fab = (FloatingActionButton) this.findViewById(R.id.fab);
        FloatingActionButton fab2 = (FloatingActionButton) this.findViewById(R.id.fab2);
        FloatingActionButton fab3 = (FloatingActionButton) this.findViewById(R.id.fab3);
        if (show) {
            fab.animate().rotation(45).withLayer().setDuration(300).setInterpolator(new OvershootInterpolator(10f));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                fab.setElevation(fab.getElevation() + getResources().getDimension(R.dimen.fab_elevation));
            }
            fab2.animate().scaleX(1).scaleY(1).translationY(-getResources().getDimension(R.dimen.fab_push)).setDuration(200);
            fab3.animate().scaleX(1).scaleY(1).translationY(-getResources().getDimension(R.dimen.fab_push2)).setDuration(200).setStartDelay(50);
        } else {
            fab.animate().rotation(0).setInterpolator(new DecelerateInterpolator(3f));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                fab.setElevation(fab.getElevation() - getResources().getDimension(R.dimen.fab_elevation));
            }
            fab2.animate().scaleX(0).scaleY(0).translationY(0);
            fab3.animate().scaleX(0).scaleY(0).translationY(0);
        }
        this.fabMenuEnabled = show;
    }

    /* List directory */

    public File getCurrentPath() {
        ItemFragment fragment = (ItemFragment) this.getSupportFragmentManager(
                ).findFragmentById(R.id.fragment_container);
        return fragment.mFile;
    }

    public void setCurrentPath(File path) {
        ItemFragment fragment = new ItemFragment();
        Bundle bundle = new Bundle();
        bundle.putString(ItemFragment.ARG_PATH, path.getAbsolutePath());
        fragment.setArguments(bundle);
        this.getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
        this.toggle.setDrawerIndicatorEnabled(false);
        //
        this.setTitle(path.getName());
    }

    public void updateCurrentPath() {
        ItemFragment fragment = (ItemFragment) this.getSupportFragmentManager(
                ).findFragmentById(R.id.fragment_container);
        fragment.notifyDataSetChanged();
    }
}
