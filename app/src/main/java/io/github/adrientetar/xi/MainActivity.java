package io.github.adrientetar.xi;

import android.content.Intent;
import android.graphics.Rect;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.view.menu.MenuBuilder;
import android.support.v7.widget.ActionMenuView;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;

import io.github.adrientetar.xi.objects.XiBridge;
import io.github.adrientetar.xi.widgets.XiView;

public class MainActivity extends AppCompatActivity {
    private XiBridge bridge;
    private String filename;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.activity_main);

        final Intent intent = this.getIntent();
        if (intent.getAction(
                ).equals(Intent.ACTION_VIEW) || intent.getAction().equals(Intent.ACTION_EDIT) ||
                intent.getData() != null) {
            this.filename = intent.getData().getPath();
        } else {
            this.filename = null;
        }

        this.bridge = new XiBridge(this);
        this.bridge.sendNewTab(new XiBridge.ResponseHandler() {
            @Override
            public void invoke(Object result) {
                String tab = (String) result;
                XiView view = (XiView) findViewById(R.id.view);
                view.activateBridge(bridge, tab);
                //
                MainActivity.this.bridge.sendEdit(tab, "debug_run_plugin");
                //
                String filename = MainActivity.this.filename;
                if (filename != null) {
                    MainActivity.this.bridge.sendOpen(tab, filename);
                }
            }
        });

        ActionMenuView actions = (ActionMenuView) findViewById(R.id.commands);

        MenuBuilder menuBuilder = (MenuBuilder) actions.getMenu();
        menuBuilder.setCallback(new MenuBuilder.Callback() {
            @Override
            public boolean onMenuItemSelected(MenuBuilder menuBuilder, MenuItem menuItem) {
                String command;
                XiView view = (XiView) findViewById(R.id.view);
                switch (menuItem.getItemId()) {
                    case R.id.action_left_end:
                        command = "move_to_left_end_of_line";
                        break;
                    case R.id.action_right_end:
                        command = "move_to_right_end_of_line";
                        break;
                    case R.id.action_undo:
                        command = "undo";
                        break;
                    case R.id.action_redo:
                        command = "redo";
                        break;
                    case R.id.action_save:
                        // TODO: grey out save if filename is null instead?
                        if (MainActivity.this.filename != null) {
                            MainActivity.this.bridge.sendSave(view.getTab(), MainActivity.this.filename);
                        }
                        return true;
                    default:
                        command = null;
                }
                if (command != null) {
                    // XXX: getTab() could be null, but in this case the Menu shouldn't be
                    // activated till then. in practice, this is guaranteed by the widget flow.
                    MainActivity.this.bridge.sendEdit(view.getTab(), command);
                    return true;
                }
                return onOptionsItemSelected(menuItem);
            }

            @Override
            public void onMenuModeChange(MenuBuilder menuBuilder) {}
        });

        this.getMenuInflater().inflate(R.menu.commands, menuBuilder);

        // http://stackoverflow.com/a/37948358
        // TODO: investigate using a click listener on the editText instead, here we catch the
        // event after the fact and this probably won't work when we show bottom bar.
        View rootView = this.findViewById(android.R.id.content);
        // TODO: remove observer in dtor
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                MainActivity activity = MainActivity.this;
                View rootView = activity.findViewById(android.R.id.content);

                // nav bar height
                int navigationBarHeight = 0;
                int resourceId = activity.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
                if (resourceId > 0) {
                    navigationBarHeight = activity.getResources().getDimensionPixelSize(resourceId);
                }
                // status bar height
                int statusBarHeight = 0;
                resourceId = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
                if (resourceId > 0) {
                    statusBarHeight = activity.getResources().getDimensionPixelSize(resourceId);
                }

                // display window size for the app layout
                Rect rect = new Rect();
                rootView.getWindowVisibleDisplayFrame(rect);
                // screen height - (user app height + status + nav) ..... if non-zero, then there is a soft keyboard
                int keyboardHeight = rootView.getHeight() - (statusBarHeight + navigationBarHeight + rect.height());

                if (keyboardHeight <= 0) {
                    activity.onHideKeyboard();
                } else {
                    activity.onShowKeyboard(keyboardHeight);
                }
            }
        });
    }

    @SuppressWarnings("unused")
    public void onShowKeyboard(int height) {
        ActionBar actionBar = this.getSupportActionBar();
        Log.v("Xi", "Hide!");
        if (actionBar != null) {
            actionBar.hide();
        }
    }

    public void onHideKeyboard() {
        Log.v("Xi", "Show!");
        ActionBar actionBar = this.getSupportActionBar();
        if (actionBar != null) {
            actionBar.show();
        }
    }
}
