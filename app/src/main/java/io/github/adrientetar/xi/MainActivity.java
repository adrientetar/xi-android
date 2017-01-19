package io.github.adrientetar.xi;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import io.github.adrientetar.xi.objects.XiBridge;
import io.github.adrientetar.xi.widgets.XiView;

public class MainActivity extends AppCompatActivity {
    private XiBridge bridge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.activity_main);

        this.bridge = new XiBridge(this);
        this.bridge.sendNewTab(new XiBridge.ResponseHandler() {
            @Override
            public void invoke(Object result) {
                XiView view = (XiView) findViewById(R.id.view);
                view.activateBridge(bridge, (String) result);
            }
        });
    }
}
