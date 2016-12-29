package io.github.adrientetar.xi.widgets;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.text.Editable;
import android.text.Layout;
import android.text.Selection;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.method.TextKeyListener;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.github.adrientetar.xi.R;
import io.github.adrientetar.xi.objects.XiBridge;

/**
 * View that displays data from xi-core.
 */

public class XiView extends View {
    // Bridge
    private XiBridge bridge;
    private String tab;
    // TextView
    private TextKeyListener listener;
    private int firstLine;
    private int lastLine;
    private CharSequence text = SpannableString.valueOf("");
    private StaticLayout layout = null;
    // Drawing
    private final Paint highlightPaint;
    private final TextPaint textPaint;
    private Path highlightPath;

    public XiView(Context context) {
        this(context, null);
    }

    public XiView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, android.R.attr.textViewStyle);
    }

    public XiView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)  // XXX
    public XiView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        this.setFocusable(true);

        this.textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        this.textPaint.density = this.getResources().getDisplayMetrics().density;
        this.textPaint.setColor(Color.BLACK);
        this.textPaint.setTextAlign(Paint.Align.LEFT);
        this.textPaint.setTextSize((int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 20, this.getResources().getDisplayMetrics()));
        //this.textPaint.setTypeface(Typeface.MONOSPACE);

        this.highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.highlightPaint.setColor(ContextCompat.getColor(this.getContext(), R.color.colorAccent));
        this.highlightPaint.setStyle(Paint.Style.STROKE);

        this.highlightPath = new Path();
    }

    public void activateBridge(XiBridge bridge, String tab) {
        this.bridge = bridge;
        this.bridge.setUpdateListener(new XiBridge.OnUpdateListener() {
            @Override
            public void onUpdate(String tab, JSONObject update) {
                XiView.this.handleUpdate(tab, update);
            }
        });
        this.tab = tab;
        this.listener = new TextKeyListener(TextKeyListener.Capitalize.NONE, false) {
            @Override
            public boolean onKeyDown(View view, Editable content, int keyCode, KeyEvent event) {
                boolean ret = super.onKeyDown(view, content, keyCode, event);
                // TODO: does ret imply content.length()?
                if (ret || content.length() > 0) {
                    XiView view_ = XiView.this;
                    view_.bridge.sendInsert(view_.tab, content.toString());
                }
                return ret;
            }
        };
    }

    public void handleUpdate(String tab, JSONObject update) {
        // XXX: the parent activity should dispatch depending on tab. for now we operate single-tab.
        // this.tab should be removed as well
        if (!tab.equals(this.tab)) {
            Log.w("Xi", "Invalid update tab.");
            return;
        }
        if (update.has("height")) {
            // TODO: number of lines
        }
        try {
            int firstLine = update.getInt("first_line");
            this.updateLines(firstLine, update.getJSONArray("lines"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (update.has("scrollto")) {
            // TODO: scroll
        }
    }

    private void updateLines(int firstLine, JSONArray lines) {
        int start = firstLine;
        int end = firstLine + lines.length();
        SpannableStringBuilder builder = new SpannableStringBuilder();
        int selStart = -1;
        int selEnd = -1;
        int stringStart = 0;
        for (int i = start; i < end; i++) {
            try {
                JSONArray line = lines.getJSONArray(i - firstLine);
                builder.append(line.getString(0));
                for (int j = 1; j < line.length(); j++) {
                    JSONArray annotation = line.getJSONArray(j);
                    switch (annotation.getString(0)) {
                        case "cursor":
                            if (selStart != -1) {
                                Log.w("Xi", "selStart is set " + (selStart == stringStart + annotation.getInt(1)));
                            } else {
                                selStart = stringStart + annotation.getInt(1);
                            }
                            break;
                        case "fg":
                            builder.setSpan(
                                    new ForegroundColorSpan(annotation.getInt(3)),
                                    stringStart + annotation.getInt(1),
                                    stringStart + annotation.getInt(2),
                                    Spanned.SPAN_INCLUSIVE_INCLUSIVE
                            );
                            break;
                        case "sel":
                            selStart = stringStart + annotation.getInt(1);
                            selEnd = stringStart + annotation.getInt(2);
                            /*builder.setSpan(
                                    new BackgroundColorSpan(Color.RED),
                                    stringStart + annotation.getInt(1),
                                    stringStart + annotation.getInt(2),
                                    Spanned.SPAN_INCLUSIVE_INCLUSIVE
                            );*/
                            break;
                    }
                }
                stringStart = builder.length();
            } catch (JSONException e) {
                Log.e("Xi", "Failure in updateLines().");
                e.printStackTrace();
                return;
            }
        }
        if (selEnd == -1) {
            selEnd = selStart;
        }
        if (selStart != -1) {
            Selection.setSelection(
                    builder, selStart, selEnd);
        }
        this.text = SpannableString.valueOf(builder);
        this.layout = null;
        this.invalidate();
        // TODO: no need to call this if lineCount didn't change
        this.requestLayout();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        String command = null;
        String suffix = event.hasModifiers(KeyEvent.META_SHIFT_ON) ? "_and_modify_selection" : "";
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
                command = "insert_newline";
                break;
            case KeyEvent.KEYCODE_DEL:
                command = "delete_backward";
                break;
            case KeyEvent.KEYCODE_FORWARD_DEL:
                command = "delete_forward";
                break;
            case KeyEvent.KEYCODE_TAB:
                command = "insert_tab";
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                command = "move_up" + suffix;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                command = "move_right" + suffix;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                command = "move_down" + suffix;
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                command = "move_left" + suffix;
                break;
            case KeyEvent.KEYCODE_PAGE_UP:
                command = "page_up" + suffix;
                break;
            case KeyEvent.KEYCODE_PAGE_DOWN:
                command = "page_down" + suffix;
        }
        if (command != null) {
            this.bridge.sendEdit(this.tab, command);
            return true;
        } else {
            Editable text = new SpannableStringBuilder("");
            return this.listener.onKeyDown(this, text, keyCode, event);
        }
    }
    // onKeyMultiple


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean handled;
        boolean superResult = super.onTouchEvent(event);

        this.requestFocus();
        this.requestFocusFromTouch();
        final InputMethodManager imm = (InputMethodManager) this.getContext(
                ).getSystemService(Context.INPUT_METHOD_SERVICE);
        handled = imm != null && imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
        return handled || superResult;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // this will undo Y scroll offset
        //canvas.translate(0, this.getScrollY());

        // TODO: we could do this onPreDraw()
        if (this.layout == null) {
            this.makeNewLayout(this.getWidth());
        }

        int selStart = Selection.getSelectionStart(this.text);
        // TODO: make a function for this
        int selEnd = Selection.getSelectionEnd(this.text);
        if (selStart == selEnd) {
            this.highlightPaint.setStyle(Paint.Style.STROKE);
        } else {
            this.highlightPaint.setStyle(Paint.Style.FILL);
        }
        this.layout.getCursorPath(selStart, this.highlightPath, this.text);

        layout.draw(canvas, this.highlightPath, this.highlightPaint, 0);
        this.highlightPath.reset();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width = (int) Math.ceil(Layout.getDesiredWidth(this.text, this.textPaint));

        if (widthMode == MeasureSpec.EXACTLY) {
            width = widthSize;
        } else {
            if (widthMode == MeasureSpec.AT_MOST) {
                width = Math.min(width, widthSize);
            }
            width = Math.max(width, this.getSuggestedMinimumWidth());
        }

        if (this.layout == null) {
            this.makeNewLayout(width);
        }

        int height = this.layout.getLineTop(this.layout.getLineCount());

        if (heightMode == MeasureSpec.EXACTLY) {
            height = heightSize;
        } else {
            if (heightMode == MeasureSpec.AT_MOST) {
                height = Math.min(height, heightSize);
            }
            height = Math.max(height, this.getSuggestedMinimumHeight());
        }

        this.setMeasuredDimension(width, height);
    }

    private void makeNewLayout(int wantWidth) {
        this.layout = new StaticLayout(
                this.text, this.textPaint, wantWidth, Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
    }
}
