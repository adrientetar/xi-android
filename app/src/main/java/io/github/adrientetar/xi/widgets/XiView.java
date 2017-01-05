package io.github.adrientetar.xi.widgets;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Typeface;
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

import java.util.Arrays;

import io.github.adrientetar.xi.R;
import io.github.adrientetar.xi.objects.XiBridge;

/**
 * View that displays data from xi-core.
 */

public class XiView extends View {
    // Bridge
    private XiBridge bridge = null;
    private String tab;
    // TextView
    private TextKeyListener listener;
    private int firstLine = 0;
    private StaticLayout[] lines = {};
    private int totalLines = 0;
    private int cursorLine = 0;
    private SpannableString cursorText;
    private float yOffset = 0;
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
        super(context, attrs, defStyleAttr);
        this.setFocusable(true);
        this.setVerticalScrollBarEnabled(true);

        this.textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        this.textPaint.density = this.getResources().getDisplayMetrics().density;
        this.textPaint.setColor(Color.BLACK);
        this.textPaint.setTextAlign(Paint.Align.LEFT);
        this.textPaint.setTextSize((int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 20, this.getResources().getDisplayMetrics()));
        this.textPaint.setTypeface(Typeface.MONOSPACE);

        this.highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.highlightPaint.setColor(ContextCompat.getColor(this.getContext(), R.color.colorAccent));
        this.highlightPaint.setStyle(Paint.Style.STROKE);

        this.highlightPath = new Path();

        this.lines = new StaticLayout[this.getHeight() / this.getLineHeight() + 2]; // TODO: init to null
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public XiView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        this(context, attrs, defStyleAttr); // TODO: defStyleRes
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
        if (!tab.equals(this.tab)) {
            Log.w("Xi", "Invalid update tab.");
            return;
        }
        try {
            if (update.has("height")) {
                int totalLines = update.getInt("height");
                if (totalLines != this.totalLines) {
                    this.totalLines = totalLines;
                    this.requestLayout();
                }
            }
            int firstLine = update.getInt("first_line");
            this.updateLines(firstLine, update.getJSONArray("lines"));
            if (update.has("scrollto")) {
                int lineHeight = this.getLineHeight();
                int scrollToLine = update.getJSONArray("scrollto").getInt(0);
                int value = -1;
                if (scrollToLine * lineHeight <= this.firstLine * lineHeight - this.yOffset) {
                    value = scrollToLine * lineHeight;
                } else if ((scrollToLine + 1) * lineHeight > this.firstLine * lineHeight - this.yOffset + this.getHeight()) {
                    value = (scrollToLine + 1) * lineHeight - this.getHeight();
                }
                if (value != -1) {
                    this.setScrollY(value);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void updateLines(int firstLine, JSONArray lines) {
        int start = Math.max(this.firstLine, firstLine);
        int end = Math.min(this.firstLine + this.lines.length, firstLine + lines.length());
        int selStart = -1;
        int selEnd = -1;
        int wantWidth = this.getWidth();
        SpannableStringBuilder builder = new SpannableStringBuilder();
        SpannableString text;
        this.cursorLine = -1;
        for (int i = start; i < end; i++) {
            try {
                JSONArray line = lines.getJSONArray(i - firstLine);
                builder.append(line.getString(0));
                for (int j = 1; j < line.length(); j++) {
                    JSONArray annotation = line.getJSONArray(j);
                    switch (annotation.getString(0)) {
                        case "cursor":
                            if (selEnd != -1) {
                                Log.w("Xi", "selStart is set " + (selEnd == annotation.getInt(1)));
                            } else {
                                this.cursorLine = i - start;
                                selEnd = annotation.getInt(1);
                            }
                            break;
                        case "fg":
                            builder.setSpan(
                                    new ForegroundColorSpan(annotation.getInt(3)),
                                    annotation.getInt(1),
                                    annotation.getInt(2),
                                    Spanned.SPAN_INCLUSIVE_INCLUSIVE
                            );
                            break;
                        case "sel":
                            selStart = annotation.getInt(1);
                            selEnd = annotation.getInt(2);
                            break;
                    }
                }
            } catch (JSONException e) {
                Log.e("Xi", "Failure in updateLines().");
                e.printStackTrace();
                return;
            }
            if (selStart == -1) {
                selStart = selEnd;
            }
            if (selEnd != -1) {
                Selection.setSelection(builder, selStart, selEnd);
            }
            text = SpannableString.valueOf(builder);
            this.lines[i-this.firstLine] = new StaticLayout(
                    text, this.textPaint, wantWidth, Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
            if (this.cursorLine == (i - start)) {
                this.cursorText = text;
            }
            builder.clear();
            selStart = selEnd = -1;
        }
        this.invalidate();
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

        if (this.hasFocus()) {
            // XXX: do it
            this.getLinesPosition(event.getX(), event.getY());
            handled = true;
        } else {
            this.requestFocus();
            this.requestFocusFromTouch();
            final InputMethodManager imm = (InputMethodManager) this.getContext(
                ).getSystemService(Context.INPUT_METHOD_SERVICE);
            handled = imm != null && imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
        }
        return handled || superResult;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // this will undo Y scroll offset
        canvas.translate(0, this.getScrollY());
        // apply offset between the first tile and viewport
        canvas.translate(0, this.yOffset);

        int lineHeight = this.getLineHeight();
        int i = 0;
        for (StaticLayout layout: this.lines) {
            if (layout == null) {
                i += 1;
                continue;
            }
            if (i == this.cursorLine) {
                int selStart = Selection.getSelectionStart(this.cursorText);
                // TODO: make a function for this
                // XXX: selection doesn't necessarily happen only on the cursor line
                int selEnd = Selection.getSelectionEnd(this.cursorText);
                if (selStart == selEnd) {
                    this.highlightPaint.setStyle(Paint.Style.STROKE);
                } else {
                    this.highlightPaint.setStyle(Paint.Style.FILL);
                }
                layout.getCursorPath(selStart, this.highlightPath, this.cursorText);
            } else {
                this.highlightPath.reset();
            }
            layout.draw(canvas, this.highlightPath, this.highlightPaint, 0);
            canvas.translate(0, lineHeight);
            i += 1;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width;
        int height;

        if (widthMode == MeasureSpec.EXACTLY) {
            width = widthSize;
        } else {
            width = 0;
            for (StaticLayout layout : this.lines) {
                width = Math.max(width, layout.getWidth());
            }

            if (widthMode == MeasureSpec.AT_MOST) {
                width = Math.min(width, widthSize);
            }
            width = Math.max(width, this.getSuggestedMinimumWidth());
        }

        if (heightMode == MeasureSpec.EXACTLY) {
            height = heightSize;
        } else {
            height = this.totalLines * this.getLineHeight();
            if (heightMode == MeasureSpec.AT_MOST) {
                height = Math.min(height, heightSize);
            }
            height = Math.max(height, this.getSuggestedMinimumHeight());
        }

        this.setMeasuredDimension(width, height);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);

        int lineHeight = this.getLineHeight();
        int linesLength = this.lines.length;
        int prevFirstLine = this.firstLine;
        this.firstLine = t / lineHeight;
        if (this.firstLine > prevFirstLine) {
            int diff = this.firstLine - prevFirstLine;
            for (int i = diff; i < linesLength; i++) {
                this.lines[i - diff] = this.lines[i];
            }
            this.cursorLine -= diff;
            this.sendRenderLines(prevFirstLine + linesLength, this.firstLine + linesLength);
            this.bridge.sendScroll(this.tab, this.firstLine, this.firstLine + linesLength);
        } else if (this.firstLine < prevFirstLine) {
            int diff = prevFirstLine - this.firstLine;
            for (int i = linesLength - diff - 1; i >= 0; i--) {
                this.lines[i + diff] = this.lines[i];
            }
            this.cursorLine += diff;
            this.sendRenderLines(this.firstLine, prevFirstLine);
            this.bridge.sendScroll(this.tab, this.firstLine, this.firstLine + linesLength);
        }
        this.yOffset = this.firstLine * lineHeight - t; // TODO: round?
        this.invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (this.bridge == null) {
            return;
        }

        int linesLength = h / this.getLineHeight() + 2;
        int prevLinesLength = this.lines.length;
        if (linesLength != prevLinesLength) {
            this.lines = Arrays.copyOf(this.lines, linesLength);
            this.bridge.sendScroll(this.tab, this.firstLine, this.firstLine + this.lines.length);
            if (linesLength > prevLinesLength) {
                this.sendRenderLines(this.firstLine + prevLinesLength, this.firstLine + this.lines.length);
            }
        }
    }

    private Point getLinesPosition(float x, float y) {
        int line = (int)((y - this.yOffset) / this.getLineHeight()) + this.firstLine;
        int column = 0;//this.lines[line-this.firstLine].x_to_index(x);
        return new Point(line, column);
    }

    public int getLineHeight() {
        return this.textPaint.getFontMetricsInt(null);
    }

    private void sendRenderLines(int firstLine, int lastLine) {
        firstLine = Math.max(firstLine, this.firstLine);
        lastLine = Math.min(lastLine, this.firstLine + this.lines.length);
        if (firstLine == lastLine) {
            return;
        }
        final int f = firstLine;
        XiBridge.ResponseHandler handler = new XiBridge.ResponseHandler() {
            @Override
            public void invoke(Object result) {
                XiView.this.updateLines(f, (JSONArray) result);
            }
        };
        this.bridge.sendRenderLines(this.tab, firstLine, lastLine, handler);
    }
}
