/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.haibison.android.lockpattern.widget;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Debug;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;

import com.haibison.android.lockpattern.R;
import com.haibison.android.lockpattern.util.UI;

/**
 * Displays and detects the user's unlock attempt, which is a drag of a finger
 * across 9 regions of the screen.
 * <p/>
 * Is also capable of displaying a static pattern in "in progress", "wrong" or
 * "correct" states.
 */
public class LockPatternView extends View {

    /**
     * Aspect to use when rendering this view. View will be the minimum of
     * width/height.
     */
    private static final int ASPECT_SQUARE = 0;
    /**
     * Fixed width; height will be minimum of (w,h)
     */
    private static final int ASPECT_LOCK_WIDTH = 1;
    /**
     * Fixed height; width will be minimum of (w,h)
     */
    private static final int ASPECT_LOCK_HEIGHT = 2;

    /**
     * This is the width of the matrix (the number of dots per row and column).
     * Change this value to change the dimension of the pattern's matrix.
     * 
     * @since v2.7 beta
     * @author Thomas Breitbach
     */
    public static final int MATRIX_WIDTH = 3;

    /**
     * The size of the pattern's matrix.
     */
    public static final int MATRIX_SIZE = MATRIX_WIDTH * MATRIX_WIDTH;

    private static final boolean PROFILE_DRAWING = false;
    private boolean mDrawingProfilingStarted = false;

    private Paint mPaint = new Paint();
    private Paint mPathPaint = new Paint();

    /**
     * How many milliseconds we spend animating each circle of a lock pattern if
     * the animating mode is set. The entire animation should take this constant
     * * the length of the pattern to complete.
     */
    private static final int MILLIS_PER_CIRCLE_ANIMATING = 700;

    /**
     * This can be used to avoid updating the display for very small motions or
     * noisy panels. It didn't seem to have much impact on the devices tested,
     * so currently set to 0.
     */
    private static final float DRAG_THRESHHOLD = 0.0f;

    private OnPatternListener mOnPatternListener;
    private ArrayList<Cell> mPattern = new ArrayList<Cell>(MATRIX_SIZE);

    /**
     * Lookup table for the circles of the pattern we are currently drawing.
     * This will be the cells of the complete pattern unless we are animating,
     * in which case we use this to hold the cells we are drawing for the in
     * progress animation.
     */
    private boolean[][] mPatternDrawLookup = new boolean[MATRIX_WIDTH][MATRIX_WIDTH];

    /**
     * the in progress point: - during interaction: where the user's finger is -
     * during animation: the current tip of the animating line
     */
    private float mInProgressX = -1;
    private float mInProgressY = -1;

    private long mAnimatingPeriodStart;

    private DisplayMode mPatternDisplayMode = DisplayMode.Correct;
    private boolean mInputEnabled = true;
    private boolean mInStealthMode = false;
    private boolean mEnableHapticFeedback = true;
    private boolean mPatternInProgress = false;

    /**
     * TODO: move to attrs
     */
    private float mDiameterFactor = 0.10f;
    private final int mStrokeAlpha = 128;
    private float mHitFactor = 0.6f;

    private float mSquareWidth;
    private float mSquareHeight;

    private Bitmap mBitmapBtnDefault;
    private Bitmap mBitmapBtnTouched;
    private Bitmap mBitmapCircleDefault;
    private Bitmap mBitmapCircleGreen;
    private Bitmap mBitmapCircleRed;

    private Bitmap mBitmapArrowGreenUp;
    private Bitmap mBitmapArrowRedUp;

    private final Path mCurrentPath = new Path();
    private final Rect mInvalidate = new Rect();
    private final Rect mTmpInvalidateRect = new Rect();

    private int mBitmapWidth;
    private int mBitmapHeight;

    private int mAspect;
    private final Matrix mArrowMatrix = new Matrix();
    private final Matrix mCircleMatrix = new Matrix();

    private final int mPadding = 0;
    private final int mPaddingLeft = mPadding;
    private final int mPaddingRight = mPadding;
    private final int mPaddingTop = mPadding;
    private final int mPaddingBottom = mPadding;

    /**
     * Represents a cell in the MATRIX_WIDTH x MATRIX_WIDTH matrix of the unlock
     * pattern view.
     */
    public static class Cell implements Parcelable {

        int mRow;
        int mColumn;

        /*
         * keep # objects limited to MATRIX_SIZE
         */
        static Cell[][] sCells = new Cell[MATRIX_WIDTH][MATRIX_WIDTH];
        static {
            for (int i = 0; i < MATRIX_WIDTH; i++) {
                for (int j = 0; j < MATRIX_WIDTH; j++) {
                    sCells[i][j] = new Cell(i, j);
                }
            }
        }

        /**
         * @param row
         *            The row of the cell.
         * @param column
         *            The column of the cell.
         */
        private Cell(int row, int column) {
            checkRange(row, column);
            this.mRow = row;
            this.mColumn = column;
        }

        /**
         * Gets the row index.
         * 
         * @return the row index.
         */
        public int getRow() {
            return mRow;
        }// getRow()

        /**
         * Gets the column index.
         * 
         * @return the column index.
         */
        public int getColumn() {
            return mColumn;
        }// getColumn()

        /**
         * Gets the ID.It is counted from left to right, top to bottom of the
         * matrix, starting by zero.
         * 
         * @return the ID.
         */
        public int getId() {
            return mRow * MATRIX_WIDTH + mColumn;
        }// getId()

        /**
         * @param row
         *            The row of the cell.
         * @param column
         *            The column of the cell.
         */
        public static synchronized Cell of(int row, int column) {
            checkRange(row, column);
            return sCells[row][column];
        }

        /**
         * Gets a cell from its ID.
         * 
         * @param id
         *            the cell ID.
         * @return the cell.
         * @since v2.7 beta
         * @author Hai Bison
         */
        public static synchronized Cell of(int id) {
            return of(id / MATRIX_WIDTH, id % MATRIX_WIDTH);
        }// of()

        private static void checkRange(int row, int column) {
            if (row < 0 || row > MATRIX_WIDTH - 1) {
                throw new IllegalArgumentException("row must be in range 0-"
                        + (MATRIX_WIDTH - 1));
            }
            if (column < 0 || column > MATRIX_WIDTH - 1) {
                throw new IllegalArgumentException("column must be in range 0-"
                        + (MATRIX_WIDTH - 1));
            }
        }

        @Override
        public String toString() {
            return "(ROW=" + getRow() + ",COL=" + getColumn() + ")";
        }// toString()

        @Override
        public boolean equals(Object object) {
            if (object instanceof Cell)
                return getColumn() == ((Cell) object).getColumn()
                        && getRow() == ((Cell) object).getRow();
            return super.equals(object);
        }// equals()

        /*
         * PARCELABLE
         */

        @Override
        public int describeContents() {
            return 0;
        }// describeContents()

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(getColumn());
            dest.writeInt(getRow());
        }// writeToParcel()

        /**
         * Reads data from parcel.
         * 
         * @param in
         *            the parcel.
         */
        public void readFromParcel(Parcel in) {
            mColumn = in.readInt();
            mRow = in.readInt();
        }// readFromParcel()

        public static final Parcelable.Creator<Cell> CREATOR = new Parcelable.Creator<Cell>() {

            public Cell createFromParcel(Parcel in) {
                return new Cell(in);
            }// createFromParcel()

            public Cell[] newArray(int size) {
                return new Cell[size];
            }// newArray()
        };// CREATOR

        private Cell(Parcel in) {
            readFromParcel(in);
        }// Cell()
    }// Cell

    /**
     * How to display the current pattern.
     */
    public enum DisplayMode {

        /**
         * The pattern drawn is correct (i.e draw it in a friendly color)
         */
        Correct,

        /**
         * Animate the pattern (for demo, and help).
         */
        Animate,

        /**
         * The pattern is wrong (i.e draw a foreboding color)
         */
        Wrong
    }

    /**
     * The call back interface for detecting patterns entered by the user.
     */
    public static interface OnPatternListener {

        /**
         * A new pattern has begun.
         */
        void onPatternStart();

        /**
         * The pattern was cleared.
         */
        void onPatternCleared();

        /**
         * The user extended the pattern currently being drawn by one cell.
         * 
         * @param pattern
         *            The pattern with newly added cell.
         */
        void onPatternCellAdded(List<Cell> pattern);

        /**
         * A pattern was detected from the user.
         * 
         * @param pattern
         *            The pattern.
         */
        void onPatternDetected(List<Cell> pattern);
    }

    private final Context mContext;

    public LockPatternView(Context context) {
        this(context, null);
    }

    public LockPatternView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mContext = context;

        /*
         * TypedArray a = context.obtainStyledAttributes(attrs,
         * R.styleable.LockPatternView);
         */

        final String aspect = "";// a.getString(R.styleable.LockPatternView_aspect);

        if ("square".equals(aspect)) {
            mAspect = ASPECT_SQUARE;
        } else if ("lock_width".equals(aspect)) {
            mAspect = ASPECT_LOCK_WIDTH;
        } else if ("lock_height".equals(aspect)) {
            mAspect = ASPECT_LOCK_HEIGHT;
        } else {
            mAspect = ASPECT_SQUARE;
        }

        setClickable(true);

        mPathPaint.setAntiAlias(true);
        mPathPaint.setDither(true);
        mPathPaint.setColor(getContext().getResources().getColor(
                UI.resolveAttribute(getContext(),
                        R.attr.alp_42447968_color_pattern_path)));
        mPathPaint.setAlpha(mStrokeAlpha);
        mPathPaint.setStyle(Paint.Style.STROKE);
        mPathPaint.setStrokeJoin(Paint.Join.ROUND);
        mPathPaint.setStrokeCap(Paint.Cap.ROUND);

        /*
         * lot's of bitmaps!
         */
        mBitmapBtnDefault = getBitmapFor(UI.resolveAttribute(getContext(),
                R.attr.alp_42447968_drawable_btn_code_lock_default_holo));
        mBitmapBtnTouched = getBitmapFor(UI.resolveAttribute(getContext(),
                R.attr.alp_42447968_drawable_btn_code_lock_touched_holo));
        mBitmapCircleDefault = getBitmapFor(UI
                .resolveAttribute(
                        getContext(),
                        R.attr.alp_42447968_drawable_indicator_code_lock_point_area_default_holo));

        mBitmapCircleGreen = getBitmapFor(UI.resolveAttribute(getContext(),
                R.attr.aosp_drawable_indicator_code_lock_point_area_normal));
        mBitmapCircleRed = getBitmapFor(R.drawable.aosp_indicator_code_lock_point_area_red_holo);

        mBitmapArrowGreenUp = getBitmapFor(R.drawable.aosp_indicator_code_lock_drag_direction_green_up);
        mBitmapArrowRedUp = getBitmapFor(R.drawable.aosp_indicator_code_lock_drag_direction_red_up);

        /*
         * bitmaps have the size of the largest bitmap in this group
         */
        final Bitmap bitmaps[] = { mBitmapBtnDefault, mBitmapBtnTouched,
                mBitmapCircleDefault, mBitmapCircleGreen, mBitmapCircleRed };

        for (Bitmap bitmap : bitmaps) {
            mBitmapWidth = Math.max(mBitmapWidth, bitmap.getWidth());
            mBitmapHeight = Math.max(mBitmapHeight, bitmap.getHeight());
        }
    }// LockPatternView()

    private Bitmap getBitmapFor(int resId) {
        return BitmapFactory.decodeResource(getContext().getResources(), resId);
    }

    /**
     * @return Whether the view is in stealth mode.
     */
    public boolean isInStealthMode() {
        return mInStealthMode;
    }

    /**
     * @return Whether the view has tactile feedback enabled.
     */
    public boolean isTactileFeedbackEnabled() {
        return mEnableHapticFeedback;
    }

    /**
     * Set whether the view is in stealth mode. If true, there will be no
     * visible feedback as the user enters the pattern.
     * 
     * @param inStealthMode
     *            Whether in stealth mode.
     */
    public void setInStealthMode(boolean inStealthMode) {
        mInStealthMode = inStealthMode;
    }

    /**
     * Set whether the view will use tactile feedback. If true, there will be
     * tactile feedback as the user enters the pattern.
     * 
     * @param tactileFeedbackEnabled
     *            Whether tactile feedback is enabled
     */
    public void setTactileFeedbackEnabled(boolean tactileFeedbackEnabled) {
        mEnableHapticFeedback = tactileFeedbackEnabled;
    }

    /**
     * Set the call back for pattern detection.
     * 
     * @param onPatternListener
     *            The call back.
     */
    public void setOnPatternListener(OnPatternListener onPatternListener) {
        mOnPatternListener = onPatternListener;
    }

    /**
     * Set the pattern explicitely (rather than waiting for the user to input a
     * pattern).
     * 
     * @param displayMode
     *            How to display the pattern.
     * @param pattern
     *            The pattern.
     */
    public void setPattern(DisplayMode displayMode, List<Cell> pattern) {
        mPattern.clear();
        mPattern.addAll(pattern);
        clearPatternDrawLookup();
        for (Cell cell : pattern) {
            mPatternDrawLookup[cell.getRow()][cell.getColumn()] = true;
        }

        setDisplayMode(displayMode);
    }

    /**
     * Set the display mode of the current pattern. This can be useful, for
     * instance, after detecting a pattern to tell this view whether change the
     * in progress result to correct or wrong.
     * 
     * @param displayMode
     *            The display mode.
     */
    public void setDisplayMode(DisplayMode displayMode) {
        mPatternDisplayMode = displayMode;
        if (displayMode == DisplayMode.Animate) {
            if (mPattern.size() == 0) {
                throw new IllegalStateException(
                        "you must have a pattern to "
                                + "animate if you want to set the display mode to animate");
            }
            mAnimatingPeriodStart = SystemClock.elapsedRealtime();
            final Cell first = mPattern.get(0);
            mInProgressX = getCenterXForColumn(first.getColumn());
            mInProgressY = getCenterYForRow(first.getRow());
            clearPatternDrawLookup();
        }
        invalidate();
    }

    /**
     * Retrieves last display mode. This method is useful in case of storing
     * states and restoring them after screen orientation changed.
     * 
     * @return {@link DisplayMode}
     * @since v1.5.3 beta
     */
    public DisplayMode getDisplayMode() {
        return mPatternDisplayMode;
    }

    /**
     * Retrieves current displaying pattern. This method is useful in case of
     * storing states and restoring them after screen orientation changed.
     * 
     * @return current displaying pattern. <b>Note:</b> This is an independent
     *         list with the view's pattern itself.
     * @since v1.5.3 beta
     */
    @SuppressWarnings("unchecked")
    public List<Cell> getPattern() {
        return (List<Cell>) mPattern.clone();
    }

    private void notifyCellAdded() {
        sendAccessEvent(R.string.alp_42447968_lockscreen_access_pattern_cell_added);
        if (mOnPatternListener != null) {
            mOnPatternListener.onPatternCellAdded(mPattern);
        }
    }

    private void notifyPatternStarted() {
        sendAccessEvent(R.string.alp_42447968_lockscreen_access_pattern_start);
        if (mOnPatternListener != null) {
            mOnPatternListener.onPatternStart();
        }
    }

    private void notifyPatternDetected() {
        sendAccessEvent(R.string.alp_42447968_lockscreen_access_pattern_detected);
        if (mOnPatternListener != null) {
            mOnPatternListener.onPatternDetected(mPattern);
        }
    }

    private void notifyPatternCleared() {
        sendAccessEvent(R.string.alp_42447968_lockscreen_access_pattern_cleared);
        if (mOnPatternListener != null) {
            mOnPatternListener.onPatternCleared();
        }
    }

    /**
     * Clear the pattern.
     */
    public void clearPattern() {
        resetPattern();
    }

    /**
     * Reset all pattern state.
     */
    private void resetPattern() {
        mPattern.clear();
        clearPatternDrawLookup();
        mPatternDisplayMode = DisplayMode.Correct;
        invalidate();
    }

    /**
     * Clear the pattern lookup table.
     */
    private void clearPatternDrawLookup() {
        for (int i = 0; i < MATRIX_WIDTH; i++) {
            for (int j = 0; j < MATRIX_WIDTH; j++) {
                mPatternDrawLookup[i][j] = false;
            }
        }
    }

    /**
     * Disable input (for instance when displaying a message that will timeout
     * so user doesn't get view into messy state).
     */
    public void disableInput() {
        mInputEnabled = false;
    }

    /**
     * Enable input.
     */
    public void enableInput() {
        mInputEnabled = true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        final int width = w - mPaddingLeft - mPaddingRight;
        mSquareWidth = width / (float) MATRIX_WIDTH;

        final int height = h - mPaddingTop - mPaddingBottom;
        mSquareHeight = height / (float) MATRIX_WIDTH;
    }

    private int resolveMeasured(int measureSpec, int desired) {
        int result = 0;
        int specSize = MeasureSpec.getSize(measureSpec);
        switch (MeasureSpec.getMode(measureSpec)) {
        case MeasureSpec.UNSPECIFIED:
            result = desired;
            break;
        case MeasureSpec.AT_MOST:
            result = Math.max(specSize, desired);
            break;
        case MeasureSpec.EXACTLY:
        default:
            result = specSize;
        }
        return result;
    }

    @Override
    protected int getSuggestedMinimumWidth() {
        /*
         * View should be large enough to contain MATRIX_WIDTH side-by-side
         * target bitmaps
         */
        return MATRIX_WIDTH * mBitmapWidth;
    }

    @Override
    protected int getSuggestedMinimumHeight() {
        /*
         * View should be large enough to contain MATRIX_WIDTH side-by-side
         * target bitmaps
         */
        return MATRIX_WIDTH * mBitmapWidth;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int minimumWidth = getSuggestedMinimumWidth();
        final int minimumHeight = getSuggestedMinimumHeight();
        int viewWidth = resolveMeasured(widthMeasureSpec, minimumWidth);
        int viewHeight = resolveMeasured(heightMeasureSpec, minimumHeight);

        switch (mAspect) {
        case ASPECT_SQUARE:
            viewWidth = viewHeight = Math.min(viewWidth, viewHeight);
            break;
        case ASPECT_LOCK_WIDTH:
            viewHeight = Math.min(viewWidth, viewHeight);
            break;
        case ASPECT_LOCK_HEIGHT:
            viewWidth = Math.min(viewWidth, viewHeight);
            break;
        }
        /*
         * Log.v(TAG, "LockPatternView dimensions: " + viewWidth + "x" +
         * viewHeight);
         */
        setMeasuredDimension(viewWidth, viewHeight);
    }

    /**
     * Determines whether the point x, y will add a new point to the current
     * pattern (in addition to finding the cell, also makes heuristic choices
     * such as filling in gaps based on current pattern).
     * 
     * @param x
     *            The x coordinate.
     * @param y
     *            The y coordinate.
     */
    private Cell detectAndAddHit(float x, float y) {
        final Cell cell = checkForNewHit(x, y);
        if (cell != null) {

            /*
             * check for gaps in existing pattern
             */
            Cell fillInGapCell = null;
            final ArrayList<Cell> pattern = mPattern;
            if (!pattern.isEmpty()) {
                final Cell lastCell = pattern.get(pattern.size() - 1);
                int dRow = cell.mRow - lastCell.mRow;
                int dColumn = cell.mColumn - lastCell.mColumn;

                int fillInRow = lastCell.mRow;
                int fillInColumn = lastCell.mColumn;

                if (Math.abs(dRow) == 2 && Math.abs(dColumn) != 1) {
                    fillInRow = lastCell.mRow + ((dRow > 0) ? 1 : -1);
                }

                if (Math.abs(dColumn) == 2 && Math.abs(dRow) != 1) {
                    fillInColumn = lastCell.mColumn + ((dColumn > 0) ? 1 : -1);
                }

                fillInGapCell = Cell.of(fillInRow, fillInColumn);
            }

            if (fillInGapCell != null
                    && !mPatternDrawLookup[fillInGapCell.mRow][fillInGapCell.mColumn]) {
                addCellToPattern(fillInGapCell);
            }
            addCellToPattern(cell);
            if (mEnableHapticFeedback) {
                performHapticFeedback(
                        HapticFeedbackConstants.VIRTUAL_KEY,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                                | HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            }
            return cell;
        }
        return null;
    }

    private void addCellToPattern(Cell newCell) {
        mPatternDrawLookup[newCell.getRow()][newCell.getColumn()] = true;
        mPattern.add(newCell);
        notifyCellAdded();
    }

    /**
     * Helper method to find which cell a point maps to.
     * 
     * @param x
     * @param y
     * @return
     */
    private Cell checkForNewHit(float x, float y) {

        final int rowHit = getRowHit(y);
        if (rowHit < 0) {
            return null;
        }
        final int columnHit = getColumnHit(x);
        if (columnHit < 0) {
            return null;
        }

        if (mPatternDrawLookup[rowHit][columnHit]) {
            return null;
        }
        return Cell.of(rowHit, columnHit);
    }

    /**
     * Helper method to find the row that y falls into.
     * 
     * @param y
     *            The y coordinate
     * @return The row that y falls in, or -1 if it falls in no row.
     */
    private int getRowHit(float y) {

        final float squareHeight = mSquareHeight;
        float hitSize = squareHeight * mHitFactor;

        float offset = mPaddingTop + (squareHeight - hitSize) / 2f;
        for (int i = 0; i < MATRIX_WIDTH; i++) {

            final float hitTop = offset + squareHeight * i;
            if (y >= hitTop && y <= hitTop + hitSize) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Helper method to find the column x fallis into.
     * 
     * @param x
     *            The x coordinate.
     * @return The column that x falls in, or -1 if it falls in no column.
     */
    private int getColumnHit(float x) {
        final float squareWidth = mSquareWidth;
        float hitSize = squareWidth * mHitFactor;

        float offset = mPaddingLeft + (squareWidth - hitSize) / 2f;
        for (int i = 0; i < MATRIX_WIDTH; i++) {

            final float hitLeft = offset + squareWidth * i;
            if (x >= hitLeft && x <= hitLeft + hitSize) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mInputEnabled || !isEnabled()) {
            return false;
        }

        switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
            handleActionDown(event);
            return true;
        case MotionEvent.ACTION_UP:
            handleActionUp(event);
            return true;
        case MotionEvent.ACTION_MOVE:
            handleActionMove(event);
            return true;
        case MotionEvent.ACTION_CANCEL:
            /*
             * Original source check for mPatternInProgress == true first before
             * calling next three lines. But if we do that, there will be
             * nothing happened when the user taps at empty area and releases
             * the finger. We want the pattern to be reset and the message will
             * be updated after the user did that.
             */
            mPatternInProgress = false;
            resetPattern();
            notifyPatternCleared();

            if (PROFILE_DRAWING) {
                if (mDrawingProfilingStarted) {
                    Debug.stopMethodTracing();
                    mDrawingProfilingStarted = false;
                }
            }
            return true;
        }
        return false;
    }

    private void handleActionMove(MotionEvent event) {
        /*
         * Handle all recent motion events so we don't skip any cells even when
         * the device is busy...
         */
        final float radius = (mSquareWidth * mDiameterFactor * 0.5f);
        final int historySize = event.getHistorySize();
        mTmpInvalidateRect.setEmpty();
        boolean invalidateNow = false;
        for (int i = 0; i < historySize + 1; i++) {
            final float x = i < historySize ? event.getHistoricalX(i) : event
                    .getX();
            final float y = i < historySize ? event.getHistoricalY(i) : event
                    .getY();
            Cell hitCell = detectAndAddHit(x, y);
            final int patternSize = mPattern.size();
            if (hitCell != null && patternSize == 1) {
                mPatternInProgress = true;
                notifyPatternStarted();
            }
            /*
             * note current x and y for rubber banding of in progress patterns
             */
            final float dx = Math.abs(x - mInProgressX);
            final float dy = Math.abs(y - mInProgressY);
            if (dx > DRAG_THRESHHOLD || dy > DRAG_THRESHHOLD) {
                invalidateNow = true;
            }

            if (mPatternInProgress && patternSize > 0) {
                final ArrayList<Cell> pattern = mPattern;
                final Cell lastCell = pattern.get(patternSize - 1);
                float lastCellCenterX = getCenterXForColumn(lastCell.mColumn);
                float lastCellCenterY = getCenterYForRow(lastCell.mRow);

                /*
                 * Adjust for drawn segment from last cell to (x,y). Radius
                 * accounts for line width.
                 */
                float left = Math.min(lastCellCenterX, x) - radius;
                float right = Math.max(lastCellCenterX, x) + radius;
                float top = Math.min(lastCellCenterY, y) - radius;
                float bottom = Math.max(lastCellCenterY, y) + radius;

                /*
                 * Invalidate between the pattern's new cell and the pattern's
                 * previous cell
                 */
                if (hitCell != null) {
                    final float width = mSquareWidth * 0.5f;
                    final float height = mSquareHeight * 0.5f;
                    final float hitCellCenterX = getCenterXForColumn(hitCell.mColumn);
                    final float hitCellCenterY = getCenterYForRow(hitCell.mRow);

                    left = Math.min(hitCellCenterX - width, left);
                    right = Math.max(hitCellCenterX + width, right);
                    top = Math.min(hitCellCenterY - height, top);
                    bottom = Math.max(hitCellCenterY + height, bottom);
                }

                /*
                 * Invalidate between the pattern's last cell and the previous
                 * location
                 */
                mTmpInvalidateRect.union(Math.round(left), Math.round(top),
                        Math.round(right), Math.round(bottom));
            }
        }
        mInProgressX = event.getX();
        mInProgressY = event.getY();

        /*
         * To save updates, we only invalidate if the user moved beyond a
         * certain amount.
         */
        if (invalidateNow) {
            mInvalidate.union(mTmpInvalidateRect);
            invalidate(mInvalidate);
            mInvalidate.set(mTmpInvalidateRect);
        }
    }

    private void sendAccessEvent(int resId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            setContentDescription(mContext.getString(resId));
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
            setContentDescription(null);
        } else
            ViewCompat_v16.announceForAccessibility(this,
                    mContext.getString(resId));
    }

    private void handleActionUp(MotionEvent event) {
        /*
         * report pattern detected
         */
        if (!mPattern.isEmpty()) {
            mPatternInProgress = false;
            notifyPatternDetected();
            invalidate();
        }
        if (PROFILE_DRAWING) {
            if (mDrawingProfilingStarted) {
                Debug.stopMethodTracing();
                mDrawingProfilingStarted = false;
            }
        }
    }

    private void handleActionDown(MotionEvent event) {
        resetPattern();
        final float x = event.getX();
        final float y = event.getY();
        final Cell hitCell = detectAndAddHit(x, y);
        if (hitCell != null) {
            mPatternInProgress = true;
            mPatternDisplayMode = DisplayMode.Correct;
            notifyPatternStarted();
        } else {
            /*
             * Original source check for mPatternInProgress == true first before
             * calling this block. But if we do that, there will be nothing
             * happened when the user taps at empty area and releases the
             * finger. We want the pattern to be reset and the message will be
             * updated after the user did that.
             */
            mPatternInProgress = false;
            notifyPatternCleared();
        }
        if (hitCell != null) {
            final float startX = getCenterXForColumn(hitCell.mColumn);
            final float startY = getCenterYForRow(hitCell.mRow);

            final float widthOffset = mSquareWidth / 2f;
            final float heightOffset = mSquareHeight / 2f;

            invalidate((int) (startX - widthOffset),
                    (int) (startY - heightOffset),
                    (int) (startX + widthOffset), (int) (startY + heightOffset));
        }
        mInProgressX = x;
        mInProgressY = y;
        if (PROFILE_DRAWING) {
            if (!mDrawingProfilingStarted) {
                Debug.startMethodTracing("LockPatternDrawing");
                mDrawingProfilingStarted = true;
            }
        }
    }

    private float getCenterXForColumn(int column) {
        return mPaddingLeft + column * mSquareWidth + mSquareWidth / 2f;
    }

    private float getCenterYForRow(int row) {
        return mPaddingTop + row * mSquareHeight + mSquareHeight / 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final ArrayList<Cell> pattern = mPattern;
        final int count = pattern.size();
        final boolean[][] drawLookup = mPatternDrawLookup;

        if (mPatternDisplayMode == DisplayMode.Animate) {

            /*
             * figure out which circles to draw
             */

            /*
             * + 1 so we pause on complete pattern
             */
            final int oneCycle = (count + 1) * MILLIS_PER_CIRCLE_ANIMATING;
            final int spotInCycle = (int) (SystemClock.elapsedRealtime() - mAnimatingPeriodStart)
                    % oneCycle;
            final int numCircles = spotInCycle / MILLIS_PER_CIRCLE_ANIMATING;

            clearPatternDrawLookup();
            for (int i = 0; i < numCircles; i++) {
                final Cell cell = pattern.get(i);
                drawLookup[cell.getRow()][cell.getColumn()] = true;
            }

            /*
             * figure out in progress portion of ghosting line
             */

            final boolean needToUpdateInProgressPoint = numCircles > 0
                    && numCircles < count;

            if (needToUpdateInProgressPoint) {
                final float percentageOfNextCircle = ((float) (spotInCycle % MILLIS_PER_CIRCLE_ANIMATING))
                        / MILLIS_PER_CIRCLE_ANIMATING;

                final Cell currentCell = pattern.get(numCircles - 1);
                final float centerX = getCenterXForColumn(currentCell.mColumn);
                final float centerY = getCenterYForRow(currentCell.mRow);

                final Cell nextCell = pattern.get(numCircles);
                final float dx = percentageOfNextCircle
                        * (getCenterXForColumn(nextCell.mColumn) - centerX);
                final float dy = percentageOfNextCircle
                        * (getCenterYForRow(nextCell.mRow) - centerY);
                mInProgressX = centerX + dx;
                mInProgressY = centerY + dy;
            }
            /*
             * TODO: Infinite loop here...
             */
            invalidate();
        }

        final float squareWidth = mSquareWidth;
        final float squareHeight = mSquareHeight;

        float radius = (squareWidth * mDiameterFactor * 0.5f);
        mPathPaint.setStrokeWidth(radius);

        final Path currentPath = mCurrentPath;
        currentPath.rewind();

        /*
         * draw the circles
         */
        final int paddingTop = mPaddingTop;
        final int paddingLeft = mPaddingLeft;

        for (int i = 0; i < MATRIX_WIDTH; i++) {
            float topY = paddingTop + i * squareHeight;
            /*
             * float centerY = mPaddingTop + i * mSquareHeight + (mSquareHeight
             * / 2);
             */
            for (int j = 0; j < MATRIX_WIDTH; j++) {
                float leftX = paddingLeft + j * squareWidth;
                drawCircle(canvas, (int) leftX, (int) topY, drawLookup[i][j]);
            }
        }

        /*
         * TODO: the path should be created and cached every time we hit-detect
         * a cell only the last segment of the path should be computed here draw
         * the path of the pattern (unless the user is in progress, and we are
         * in stealth mode)
         */
        final boolean drawPath = (!mInStealthMode || mPatternDisplayMode == DisplayMode.Wrong);

        /*
         * draw the arrows associated with the path (unless the user is in
         * progress, and we are in stealth mode)
         */
        boolean oldFlag = (mPaint.getFlags() & Paint.FILTER_BITMAP_FLAG) != 0;
        /*
         * draw with higher quality since we render with transforms
         */
        mPaint.setFilterBitmap(true);
        if (drawPath) {
            for (int i = 0; i < count - 1; i++) {
                Cell cell = pattern.get(i);
                Cell next = pattern.get(i + 1);

                /*
                 * only draw the part of the pattern stored in the lookup table
                 * (this is only different in the case of animation).
                 */
                if (!drawLookup[next.mRow][next.mColumn]) {
                    break;
                }

                float leftX = paddingLeft + cell.mColumn * squareWidth;
                float topY = paddingTop + cell.mRow * squareHeight;

                drawArrow(canvas, leftX, topY, cell, next);
            }
        }

        if (drawPath) {
            boolean anyCircles = false;
            for (int i = 0; i < count; i++) {
                Cell cell = pattern.get(i);

                /*
                 * only draw the part of the pattern stored in the lookup table
                 * (this is only different in the case of animation).
                 */
                if (!drawLookup[cell.mRow][cell.mColumn]) {
                    break;
                }
                anyCircles = true;

                float centerX = getCenterXForColumn(cell.mColumn);
                float centerY = getCenterYForRow(cell.mRow);
                if (i == 0) {
                    currentPath.moveTo(centerX, centerY);
                } else {
                    currentPath.lineTo(centerX, centerY);
                }
            }

            /*
             * add last in progress section
             */
            if ((mPatternInProgress || mPatternDisplayMode == DisplayMode.Animate)
                    && anyCircles && count > 1) {
                currentPath.lineTo(mInProgressX, mInProgressY);
            }
            canvas.drawPath(currentPath, mPathPaint);
        }

        /*
         * restore default flag
         */
        mPaint.setFilterBitmap(oldFlag);
    }

    private void drawArrow(Canvas canvas, float leftX, float topY, Cell start,
            Cell end) {
        boolean green = mPatternDisplayMode != DisplayMode.Wrong;

        final int endRow = end.mRow;
        final int startRow = start.mRow;
        final int endColumn = end.mColumn;
        final int startColumn = start.mColumn;

        /*
         * offsets for centering the bitmap in the cell
         */
        final int offsetX = ((int) mSquareWidth - mBitmapWidth) / 2;
        final int offsetY = ((int) mSquareHeight - mBitmapHeight) / 2;

        /*
         * compute transform to place arrow bitmaps at correct angle inside
         * circle. This assumes that the arrow image is drawn at 12:00 with it's
         * top edge coincident with the circle bitmap's top edge.
         */
        Bitmap arrow = green ? mBitmapArrowGreenUp : mBitmapArrowRedUp;
        final int cellWidth = mBitmapWidth;
        final int cellHeight = mBitmapHeight;

        /*
         * the up arrow bitmap is at 12:00, so find the rotation from x axis and
         * add 90 degrees.
         */
        final float theta = (float) Math.atan2((double) (endRow - startRow),
                (double) (endColumn - startColumn));
        final float angle = (float) Math.toDegrees(theta) + 90.0f;

        /*
         * compose matrix
         */
        float sx = Math.min(mSquareWidth / mBitmapWidth, 1.0f);
        float sy = Math.min(mSquareHeight / mBitmapHeight, 1.0f);
        /*
         * transform to cell position
         */
        mArrowMatrix.setTranslate(leftX + offsetX, topY + offsetY);
        mArrowMatrix.preTranslate(mBitmapWidth / 2, mBitmapHeight / 2);
        mArrowMatrix.preScale(sx, sy);
        mArrowMatrix.preTranslate(-mBitmapWidth / 2, -mBitmapHeight / 2);
        /*
         * rotate about cell center
         */
        mArrowMatrix.preRotate(angle, cellWidth / 2.0f, cellHeight / 2.0f);
        /*
         * translate to 12:00 pos
         */
        mArrowMatrix.preTranslate((cellWidth - arrow.getWidth()) / 2.0f, 0.0f);
        canvas.drawBitmap(arrow, mArrowMatrix, mPaint);
    }

    /**
     * @param canvas
     * @param leftX
     * @param topY
     * @param partOfPattern
     *            Whether this circle is part of the pattern.
     */
    private void drawCircle(Canvas canvas, int leftX, int topY,
            boolean partOfPattern) {
        Bitmap outerCircle;
        Bitmap innerCircle;

        if (!partOfPattern
                || (mInStealthMode && mPatternDisplayMode != DisplayMode.Wrong)) {
            /*
             * unselected circle
             */
            outerCircle = mBitmapCircleDefault;
            innerCircle = mBitmapBtnDefault;
        } else if (mPatternInProgress) {
            /*
             * user is in middle of drawing a pattern
             */
            outerCircle = mBitmapCircleGreen;
            innerCircle = mBitmapBtnTouched;
        } else if (mPatternDisplayMode == DisplayMode.Wrong) {
            /*
             * the pattern is wrong
             */
            outerCircle = mBitmapCircleRed;
            innerCircle = mBitmapBtnDefault;
        } else if (mPatternDisplayMode == DisplayMode.Correct
                || mPatternDisplayMode == DisplayMode.Animate) {
            /*
             * the pattern is correct
             */
            outerCircle = mBitmapCircleGreen;
            innerCircle = mBitmapBtnDefault;
        } else {
            throw new IllegalStateException("unknown display mode "
                    + mPatternDisplayMode);
        }

        final int width = mBitmapWidth;
        final int height = mBitmapHeight;

        final float squareWidth = mSquareWidth;
        final float squareHeight = mSquareHeight;

        int offsetX = (int) ((squareWidth - width) / 2f);
        int offsetY = (int) ((squareHeight - height) / 2f);

        /*
         * Allow circles to shrink if the view is too small to hold them.
         */
        float sx = Math.min(mSquareWidth / mBitmapWidth, 1.0f);
        float sy = Math.min(mSquareHeight / mBitmapHeight, 1.0f);

        mCircleMatrix.setTranslate(leftX + offsetX, topY + offsetY);
        mCircleMatrix.preTranslate(mBitmapWidth / 2, mBitmapHeight / 2);
        mCircleMatrix.preScale(sx, sy);
        mCircleMatrix.preTranslate(-mBitmapWidth / 2, -mBitmapHeight / 2);

        canvas.drawBitmap(outerCircle, mCircleMatrix, mPaint);
        canvas.drawBitmap(innerCircle, mCircleMatrix, mPaint);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        return new SavedState(superState,
                LockPatternUtils.patternToString(mPattern),
                mPatternDisplayMode.ordinal(), mInputEnabled, mInStealthMode,
                mEnableHapticFeedback);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        final SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        setPattern(DisplayMode.Correct,
                LockPatternUtils.stringToPattern(ss.getSerializedPattern()));
        mPatternDisplayMode = DisplayMode.values()[ss.getDisplayMode()];
        mInputEnabled = ss.isInputEnabled();
        mInStealthMode = ss.isInStealthMode();
        mEnableHapticFeedback = ss.isTactileFeedbackEnabled();
    }

    /**
     * The parecelable for saving and restoring a lock pattern view.
     */
    private static class SavedState extends BaseSavedState {

        private final String mSerializedPattern;
        private final int mDisplayMode;
        private final boolean mInputEnabled;
        private final boolean mInStealthMode;
        private final boolean mTactileFeedbackEnabled;

        /**
         * Constructor called from {@link LockPatternView#onSaveInstanceState()}
         */
        private SavedState(Parcelable superState, String serializedPattern,
                int displayMode, boolean inputEnabled, boolean inStealthMode,
                boolean tactileFeedbackEnabled) {
            super(superState);
            mSerializedPattern = serializedPattern;
            mDisplayMode = displayMode;
            mInputEnabled = inputEnabled;
            mInStealthMode = inStealthMode;
            mTactileFeedbackEnabled = tactileFeedbackEnabled;
        }

        /**
         * Constructor called from {@link #CREATOR}
         */
        private SavedState(Parcel in) {
            super(in);
            mSerializedPattern = in.readString();
            mDisplayMode = in.readInt();
            mInputEnabled = (Boolean) in.readValue(null);
            mInStealthMode = (Boolean) in.readValue(null);
            mTactileFeedbackEnabled = (Boolean) in.readValue(null);
        }

        public String getSerializedPattern() {
            return mSerializedPattern;
        }

        public int getDisplayMode() {
            return mDisplayMode;
        }

        public boolean isInputEnabled() {
            return mInputEnabled;
        }

        public boolean isInStealthMode() {
            return mInStealthMode;
        }

        public boolean isTactileFeedbackEnabled() {
            return mTactileFeedbackEnabled;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(mSerializedPattern);
            dest.writeInt(mDisplayMode);
            dest.writeValue(mInputEnabled);
            dest.writeValue(mInStealthMode);
            dest.writeValue(mTactileFeedbackEnabled);
        }

        @SuppressWarnings("unused")
        public static final Parcelable.Creator<SavedState> CREATOR = new Creator<SavedState>() {

            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

}
