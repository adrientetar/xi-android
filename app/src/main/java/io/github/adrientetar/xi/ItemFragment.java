package io.github.adrientetar.xi;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Demonstrates the use of {@link RecyclerView} with a {@link LinearLayoutManager} and a
 * {@link GridLayoutManager}.
 */
public class ItemFragment extends Fragment {

    public static final String ARG_PATH = "path";
    private static final String KEY_LAYOUT_MANAGER = "layoutManager";
    private static final int SPAN_COUNT = 2;

    private enum LayoutManagerType {
        GRID_LAYOUT_MANAGER,
        LINEAR_LAYOUT_MANAGER
    }

    protected LayoutManagerType mCurrentLayoutManagerType;

    protected RadioButton mLinearLayoutRadioButton;
    protected RadioButton mGridLayoutRadioButton;

    protected RecyclerView mRecyclerView;
    protected FileAdapter mAdapter;
    protected RecyclerView.LayoutManager mLayoutManager;
    protected List<FileItem> mDataset;
    protected File mFile;

    protected View.OnClickListener mCallback;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = this.getArguments();
        if (args != null) {
            mFile = new File(args.getString(ItemFragment.ARG_PATH));
        }

        mDataset = new ArrayList<>();
        initDataset();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        Activity activity = this.getActivity();
        try {
            mCallback = (View.OnClickListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnHeadlineSelectedListener");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_item_list, container, false);

        mRecyclerView = (RecyclerView) rootView.findViewById(R.id.item_list);

        // LinearLayoutManager is used here, this will layout the elements in a similar fashion
        // to the way ListView would layout elements. The RecyclerView.LayoutManager defines how
        // elements are laid out.
        mLayoutManager = new LinearLayoutManager(getActivity());

        mCurrentLayoutManagerType = LayoutManagerType.LINEAR_LAYOUT_MANAGER;

        if (savedInstanceState != null) {
            // Restore saved layout manager type.
            //mCurrentLayoutManagerType = (LayoutManagerType) savedInstanceState
            //        .getSerializable(KEY_LAYOUT_MANAGER);
        }
        setRecyclerViewLayoutManager(mCurrentLayoutManagerType);

        mAdapter = new FileAdapter(mDataset);
        // Set CustomAdapter as the adapter for RecyclerView.
        mRecyclerView.setAdapter(mAdapter);

        /*mLinearLayoutRadioButton = (RadioButton) rootView.findViewById(R.id.linear_layout_rb);
        mLinearLayoutRadioButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setRecyclerViewLayoutManager(LayoutManagerType.LINEAR_LAYOUT_MANAGER);
            }
        });

        mGridLayoutRadioButton = (RadioButton) rootView.findViewById(R.id.grid_layout_rb);
        mGridLayoutRadioButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setRecyclerViewLayoutManager(LayoutManagerType.GRID_LAYOUT_MANAGER);
            }
        });*/

        return rootView;
    }

    /**
     * Set RecyclerView's LayoutManager to the one given.
     *
     * @param layoutManagerType Type of layout manager to switch to.
     */
    public void setRecyclerViewLayoutManager(LayoutManagerType layoutManagerType) {
        int scrollPosition = 0;

        // If a layout manager has already been set, get current scroll position.
        if (mRecyclerView.getLayoutManager() != null) {
            scrollPosition = ((LinearLayoutManager) mRecyclerView.getLayoutManager())
                    .findFirstCompletelyVisibleItemPosition();
        }

        switch (layoutManagerType) {
            case GRID_LAYOUT_MANAGER:
                mLayoutManager = new GridLayoutManager(getActivity(), SPAN_COUNT);
                mCurrentLayoutManagerType = LayoutManagerType.GRID_LAYOUT_MANAGER;
                break;
            case LINEAR_LAYOUT_MANAGER:
            default:
                mLayoutManager = new LinearLayoutManager(getActivity());
                mCurrentLayoutManagerType = LayoutManagerType.LINEAR_LAYOUT_MANAGER;
        }

        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.scrollToPosition(scrollPosition);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save currently selected layout manager.
        savedInstanceState.putSerializable(KEY_LAYOUT_MANAGER, mCurrentLayoutManagerType);
        super.onSaveInstanceState(savedInstanceState);
    }

    /**
     * Generates Strings for RecyclerView's adapter. This data would usually come
     * from a local content provider or remote server.
     */
    private void initDataset() {
        mDataset.clear();

        if (mFile != null) {
            File[] files = mFile.listFiles();
            if (files != null) {
                int drawable;
                for (File file : files) {
                    if (file.isDirectory()) {
                        if (file.canRead()) {
                            drawable = R.drawable.ic_folder_grey_24dp;
                        } else {
                            drawable = R.drawable.ic_folder_open_grey_24dp;
                        }
                    } else {
                        drawable = R.drawable.ic_file_grey_24dp;
                    }
                    mDataset.add(new FileItem(file.getName(), drawable));
                }
            }
        }
    }

    public void notifyDataSetChanged() {
        initDataset();
        mAdapter.notifyDataSetChanged();
    }

    public class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {
        private List<FileItem> itemsData;

        public FileAdapter(List<FileItem> itemsData) {
            this.itemsData = itemsData;
        }

        // Create new views (invoked by the layout manager)
        @Override
        public FileAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                       int viewType) {
            View itemLayoutView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.fragment_item, null);
            itemLayoutView.setOnClickListener(mCallback);

            ViewHolder viewHolder = new ViewHolder(itemLayoutView);
            return viewHolder;
        }

        @Override
        public void onBindViewHolder(ViewHolder viewHolder, int position) {

            viewHolder.txtViewTitle.setText(itemsData.get(position).name);
            viewHolder.imgViewIcon.setImageResource(itemsData.get(position).drawable);
        }

        // inner class to hold a reference to each item of RecyclerView
        public class ViewHolder extends RecyclerView.ViewHolder {

            public TextView txtViewTitle;
            public ImageView imgViewIcon;

            public ViewHolder(View itemLayoutView) {
                super(itemLayoutView);
                txtViewTitle = (TextView) itemLayoutView.findViewById(R.id.item_name);
                imgViewIcon = (ImageView) itemLayoutView.findViewById(R.id.item_drawable);
            }
        }

        @Override
        public int getItemCount() {
            return itemsData.size();
        }
    }
}

class FileItem {
    public final String name;
    public final int drawable;

    public FileItem(String name, int drawable) {
        this.name = name;
        this.drawable = drawable;
    }

    @Override
    public String toString() {
        return name;
    }
}