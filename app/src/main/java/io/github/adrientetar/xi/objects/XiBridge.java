package io.github.adrientetar.xi.objects;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

/**
 * Bridge that spawns a xi-core process and provides a comm interface.
 *
 * The RPC APIs are similar to those of xi-gtk CoreConnection.
 *
 * Make sure to call activateWatcher() to start listening to xi-core stdout.
 */

public class XiBridge {
    private int id = 0;
    // Bridge to app
    private SparseArray<ResponseHandler> handlers;
    private OnUpdateListener listener = null;
    // Bridge to process
    private Process process;
    private BufferedWriter writer;
    // Bridge to polling thread
    private Handler handler;
    private WatcherThread watcher;

    // App interfaces

    public interface ResponseHandler {
        void invoke(Object result);
    }

    public interface OnUpdateListener {
        void onUpdate(String tab, JSONObject update);
    }

    public OnUpdateListener getUpdateListener() {
        return this.listener;
    }

    public void setUpdateListener(OnUpdateListener listener) {
        this.listener = listener;
    }

    //

    public XiBridge(Context ctx) {
        try {
            this.process = Runtime.getRuntime().exec(
                    ctx.getApplicationInfo().nativeLibraryDir + "/lib_xi-core_.so");
        } catch (java.io.IOException e) {
            Log.e("Xi", "Couldn't open dependant binary.");
            return;
        }

        OutputStream stdin = this.process.getOutputStream();
        InputStream stdout = this.process.getInputStream();

        this.writer = new BufferedWriter(new OutputStreamWriter(stdin));
        this.spawnInputWatcher(new BufferedReader(new InputStreamReader(stdout)));

        this.handlers = new SparseArray<>();
    }

    /* Receive */

    private void spawnInputWatcher(BufferedReader reader) {
        this.handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message message) {
                XiBridge.this.processMessage((String) message.obj);
            }
        };
        this.watcher = new WatcherThread(reader, this.handler);
    }

    private boolean processMessage(String line) {
        try {
            JSONObject root = new JSONObject(line);
            if (root.has("id")) {
                int id = root.getInt("id");
                ResponseHandler handler = this.handlers.get(id);
                if (handler != null) {
                    Object result = root.get("result");
                    handler.invoke(result);
                    this.handlers.remove(id);
                }
            } else {
                String method = root.getString("method");
                JSONObject params = root.getJSONObject("params");
                switch (method) {
                    case "update":
                        this.handleUpdate(params);
                        break;
                }
            }
        } catch (JSONException e) {
            Log.e("Xi", "Couldn't process message from back-end.");
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private void handleUpdate(JSONObject params) {
        if (this.listener == null) {
            return;
        }
        String tab;
        JSONObject update;
        try {
            tab = params.getString("tab");
            update = params.getJSONObject("update");
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        this.listener.onUpdate(tab, update);
    }

    public void activateWatcher() {
        if (this.watcher.isAlive()) {
            return;
        } else if (this.watcher.isInterrupted()) {
            BufferedReader reader = this.watcher.getReader();
            this.watcher = new WatcherThread(reader, this.handler);
        }
        this.watcher.start();
    }

    public void deactivateWatcher() {
        this.watcher.interrupt();
    }

    public void finish() {
        //this.process.destroy();
        try {
            // stdin <-> OutputStream as the OutputStream writes into the Process' stdin.
            this.process.getOutputStream().close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.watcher.interrupt();
    }

    /* Send */

    private void send(JSONObject root) {
        try {
            this.writer.write(root.toString());
            this.writer.write("\n");
            this.writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendNotification(String method, JSONObject params) {
        JSONObject root = new JSONObject();
        try {
            root.put("method", method);
            root.put("params", params);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        this.send(root);
    }

    private void sendRequest(String method, JSONObject params, ResponseHandler handler) {
        this.handlers.put(this.id, handler);
        JSONObject root = new JSONObject();
        try {
            root.put("id", this.id);
            root.put("method", method);
            root.put("params", params);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        this.id += 1;
        this.send(root);
    }

    public void sendEdit(String tab, String method) {
        this.sendEdit(tab, method, new JSONObject());
    }
    public void sendEdit(String tab, String method, JSONObject editParams) {
        JSONObject params = new JSONObject();
        try {
            params.put("method", method);
            params.put("tab", tab);
            params.put("params", editParams);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        this.sendNotification("edit", params);
    }

    private void sendEditArray(String tab, String method, JSONArray editParams) {
        JSONObject params = new JSONObject();
        try {
            params.put("method", method);
            params.put("tab", tab);
            params.put("params", editParams);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        this.sendNotification("edit", params);
    }

    private void sendEditRequest(String tab, String method, JSONObject editParams, ResponseHandler handler) {
        JSONObject params = new JSONObject();
        try {
            params.put("method", method);
            params.put("tab", tab);
            params.put("params", editParams);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        this.sendRequest("edit", params, handler);
    }

    public void sendNewTab(ResponseHandler handler) {
        this.sendRequest("new_tab", new JSONObject(), handler);
    }

    public void sendDeleteTab(String tab) {
        JSONObject params = new JSONObject();
        try {
            params.put("tab", tab);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        this.sendNotification("delete_tab", params);
    }

    public void sendInsert(String tab, String chars) {
        JSONObject params = new JSONObject();
        try {
            params.put("chars", chars);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        this.sendEdit(tab, "insert", params);
    }

    public void sendOpen(String tab, String filename) {
        JSONObject params = new JSONObject();
        try {
            params.put("filename", filename);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        this.sendEdit(tab, "open", params);
    }

    public void sendSave(String tab, String filename) {
        JSONObject params = new JSONObject();
        try {
            params.put("filename", filename);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        this.sendEdit(tab, "save", params);
    }

    public void sendScroll(String tab, int firstLine, int lastLine) {
        JSONArray params = new JSONArray();
        params.put(firstLine);
        params.put(lastLine);
        this.sendEditArray(tab, "scroll", params);
    }

    public void sendClick(String tab, int line, int column, int modifiers, int clickCount) {
        JSONArray params = new JSONArray();
        params.put(line);
        params.put(column);
        params.put(modifiers);
        params.put(clickCount);
        this.sendEditArray(tab, "click", params);
    }

    public void sendDrag(String tab, int line, int column, int modifiers) {
        JSONArray params = new JSONArray();
        params.put(line);
        params.put(column);
        params.put(modifiers);
        this.sendEditArray(tab, "drag", params);
    }

    public void sendRenderLines(String tab, int firstLine, int lastLine, ResponseHandler handler) {
        JSONObject params = new JSONObject();
        try {
            params.put("first_line", firstLine);
            params.put("last_line", lastLine);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        this.sendEditRequest(tab, "render_lines", params, handler);
    }
}

class WatcherThread extends Thread {
    private BufferedReader reader;
    private String line;
    private Handler handler;

    public WatcherThread(BufferedReader reader, Handler handler) {
        this.reader = reader;
        this.handler = handler;
    }

    public BufferedReader getReader() {
        return this.reader;
    }

    public void run() {
        Message message;
        while (!this.isInterrupted()) {
            try {
                /* We use ready() here instead of blocking on readLine() so the thread can check
                 * if it's getting interrupted. */
                // XXX: this polls the shit out of the buffer and heats up CPU. find another way
                if (this.reader.ready()) {
                    this.line = this.reader.readLine();
                    message = Message.obtain();
                    message.obj = this.line;
                    this.handler.sendMessage(message);
                } else {
                    this.sleep(100);
                }
            } catch (IOException e) {
                Log.e("Xi", "IO error in Watcher.");
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        Log.v("Xi", "Thread suspended!");
    }
}
