/*
 * Coin Collection, an Android app that helps users track the coins that they've collected
 * Copyright (C) 2010-2016 Andrew Williams
 *
 * This file is part of Coin Collection.
 *
 * Coin Collection is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Coin Collection is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Coin Collection.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.coincollection;

import static com.coincollection.CoinPageCreator.getCollectionNameFilter;
import static com.spencerpages.MainApplication.APP_NAME;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.SQLException;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.spencerpages.BuildConfig;
import com.spencerpages.MainApplication;
import com.spencerpages.R;

import java.util.ArrayList;

/** Activity for managing each collection page
 *
 * http://developer.android.com/resources/tutorials/views/hello-gridview.html
 */
public class CollectionPage extends BaseActivity {
    private String mCollectionName;
    private ArrayList<CoinSlot> mCoinList;
    private CoinSlotAdapter mCoinSlotAdapter;

    // Saved Instance State Keywords

    // Intent Argument Keywords
    public final static String COLLECTION_NAME        = "Collection_Name";
    public final static String COLLECTION_TYPE_INDEX  = "Collection_Type_Index";
    private final static String VIEW_INDEX            = "view_index";
    private final static String VIEW_POSITION         = "view_position";
    private final static String COIN_LIST             = "coin_list";

    // Global "enum" values
    public static final int SIMPLE_DISPLAY = 0;
    public static final int ADVANCED_DISPLAY = 1;

    private int mDisplayType = SIMPLE_DISPLAY;

    /* Used in conjunction with the ListView in the advance view case to scroll the view to the last
     * location.  Defaults to the first item, and will be set by:
     *     1 The index and position saved in the Intent that started us
     *         - Used to pass data when switching from the simple view to the advanced view
     *     2 The index and position saved in the mSavedInstanceState
     *         - Used to pass data when the screen rotates
     *
     * Info in the mSavedInstanceState will overwrite the info from the Intent (so that if the user
     * switched from simple into advanced and then rotated the screen, where they were when they
     * rotated the screen will display
     */
    private int mViewIndex = 0;
    private int mViewPosition = 0;

    public static final String IS_LOCKED = "_isLocked";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Save off this bundle so that after the database is open we can use it
        // to get the previous CoinSlotAdapter, if present

        // Need to get the coin type from the intent that started this process
        int collectionTypeIndex = mCallingIntent.getIntExtra(COLLECTION_TYPE_INDEX, 0);
        CollectionInfo collectionTypeObj = MainApplication.COLLECTION_TYPES[collectionTypeIndex];

        // Capture the collection name from the saved instance state if it's there,
        // otherwise capture from the calling intent. Note that the calling intent
        // is updated with any renames before re-creating the view.
        if(savedInstanceState != null){
            mCollectionName = savedInstanceState.getString(COLLECTION_NAME);
        } else{
            mCollectionName = mCallingIntent.getStringExtra(COLLECTION_NAME);
        }

        // Restore the view index and position
        if(savedInstanceState != null){
            mViewIndex = savedInstanceState.getInt(VIEW_INDEX);
            mViewPosition = savedInstanceState.getInt(VIEW_POSITION);
        } else if(mCallingIntent.hasExtra(VIEW_INDEX)){
            mViewIndex = mCallingIntent.getIntExtra(VIEW_INDEX, 0);
            mViewPosition = mCallingIntent.getIntExtra(VIEW_POSITION, 0);
        }

        // Update the title
        // http://stackoverflow.com/questions/2198410/how-to-change-title-of-activity-in-android
        this.setTitle(mCollectionName);

        // Tell the user they can now lock the collections
        // Check whether it is the users first time using the app
        createAndShowHelpDialog("first_Time_screen3", R.string.tutorial_add_to_and_lock_collection);

        // Determine whether we should show the advanced view or the basic view
        mDisplayType = mDbAdapter.fetchTableDisplay(mCollectionName);

        // Update the icon
        if(mActionBar != null){
            mActionBar.setIcon(collectionTypeObj.getCoinImageIdentifier());
            // Set the actionbar so that clicking the icon takes you back
            // SO 1010877
            mActionBar.setDisplayHomeAsUpEnabled(true);
        }

        GridView gridview = null;
        ListView listview = null;

        if(mDisplayType == SIMPLE_DISPLAY) {

            setContentView(R.layout.standard_collection_page);
            gridview = findViewById(R.id.standard_collection_page);

        } else if(mDisplayType == ADVANCED_DISPLAY){

            setContentView(R.layout.advanced_collection_page);
            listview = findViewById(R.id.advanced_collection_page);

            // Make it so that the elements in the listview cells can get focus
            listview.setItemsCanFocus(true);
        }

        // Populate the coin list
        if(savedInstanceState == null){
            boolean populateAdvInfo = (mDisplayType == ADVANCED_DISPLAY);
            mCoinList = mDbAdapter.getCoinList(mCollectionName, populateAdvInfo);
        } else {

            // We have already loaded the advanced lists, so use those instead.
            // That way we have all of the state from before the page loaded.
            // yay
            if(BuildConfig.DEBUG) {
                Log.d(APP_NAME, "Successfully restored previous state");
            }
            mCoinList = savedInstanceState.getParcelableArrayList(COIN_LIST);
            // Search through the hasChanged history and see whether we should
            // re-display the "Unsaved Changes" view
            if (mCoinList != null){
                for(int i = 0; i < mCoinList.size(); i++){
                    if(mCoinList.get(i).hasIndexChanged()){
                        this.showUnsavedTextView();
                        break;
                    }
                }
            }
        }
        mCoinSlotAdapter = new CoinSlotAdapter(this, mCollectionName, collectionTypeObj, mCoinList, mDisplayType);

        OnScrollListener scrollListener = new OnScrollListener(){
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                 // Auto-generated method stub
                // TODO Something we can put here that isn't slow but fixes the scrolling issue
                // Anything we put here is going to be hit a lot :(
            }
            public void onScrollStateChanged(AbsListView view, int scrollState) {

              if(scrollState == OnScrollListener.SCROLL_STATE_IDLE){
                  // Refresh the view, fixing any layout issues
                  mCoinSlotAdapter.notifyDataSetChanged();
              }

              // If this is the advanced view, we want to hide the soft keyboard if it exists
              // This only gets called when a scroll starts (SCROLL_STATE_TOUCH_SCROLL),
              // when the person has flung the view (SCROLL_STATE_FLING), and when the
              // scrolling comes to an end (SCROLL_STATE_IDLE), so this won't cause any performance
              // issues
              // TODO Is there an easy way to determine if the soft keyboard is shown?
              InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
              imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        };

        if(mDisplayType == SIMPLE_DISPLAY){

            // Apply the adapter to handle each entry in the grid
            gridview.setAdapter(mCoinSlotAdapter);

            // Restore the position in the list that the user was at
            // (or go to the default of the first item)
            gridview.setSelection(mViewIndex);

            // Set the scroll listener so that the view re-adjusts to the new view
            gridview.setOnScrollListener(scrollListener);

            // Set the onClick listener that will handle changing the coin state
            gridview.setOnItemClickListener((parent, v, position, id) -> {
                // Need to check whether the collection is locked
                SharedPreferences mainPreferences = getSharedPreferences(MainApplication.PREFS, MODE_PRIVATE);

                if(mainPreferences.getBoolean(mCollectionName + IS_LOCKED, false)){
                    // Collection is locked
                    showLockedMessage();
                } else {
                    // Preference doesn't exist or Collection is unlocked

                    CoinSlot coinSlot = mCoinList.get(position);
                    try {
                        mDbAdapter.toggleInCollection(mCollectionName, coinSlot);
                    } catch (SQLException e) {
                        showCancelableAlert(mRes.getString(R.string.error_updating_database));
                    }

                    // Update the mCoinSlotAdapters copy of the coins in this collection
                    boolean oldValue = coinSlot.isInCollection();
                    coinSlot.setInCollection(!oldValue);

                    // And have the adapter redraw with this new info

                    mCoinSlotAdapter.notifyDataSetChanged();
                }
            });

        } else if(mDisplayType == ADVANCED_DISPLAY){
            // Apply the adapter to handle each entry in the list
            listview.setAdapter(mCoinSlotAdapter);

            // Restore the position in the list that the user was at
            // (or go to the default of the first item)
            listview.setSelectionFromTop(mViewIndex, mViewPosition);

            // Set the scroll listener so that the view re-adjusts to the new view
            listview.setOnScrollListener(scrollListener);

            // Set the onClick listener for the whole view to provide a notice
            // to users if the collection is locked. There's also a onClick listener
            // on the imageView in CoinSlotAdapter
            listview.setOnItemClickListener((parent, v, position, id) -> {
                // Need to check whether the collection is locked
                SharedPreferences mainPreferences = getSharedPreferences(MainApplication.PREFS, MODE_PRIVATE);
                if(mainPreferences.getBoolean(mCollectionName + IS_LOCKED, false)){
                    // Collection is locked
                    showLockedMessage();
                }
            });

        }
    }

    /**
     * Report unsaved changes to the user
     */
    public void showUnsavedTextView() {

        TextView unsavedMessageView = findViewById(R.id.unsaved_message_textview);
        unsavedMessageView.setVisibility(View.VISIBLE);
    }

    /**
     * Hide the unsaved changes view
     */
    private void hideUnsavedTextView() {

        TextView unsavedMessageView = findViewById(R.id.unsaved_message_textview);
        unsavedMessageView.setVisibility(View.GONE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
                
        inflater.inflate(R.menu.collection_page_menu_all, menu);
        
        // Need to check the preferences to see whether the collection is locked or unlocked
        SharedPreferences mainPreferences = getSharedPreferences(MainApplication.PREFS, MODE_PRIVATE);
        MenuItem item = menu.findItem(R.id.lock_unlock_collection);

        if(mainPreferences.getBoolean(mCollectionName + IS_LOCKED, false)){
            // Current Locked, set text to unlock it
            item.setTitle(R.string.unlock_collection);
        } else {
            // Currently unlocked, set text to lock it
            // Default is unlocked
            if(mDisplayType == ADVANCED_DISPLAY){
                item.setTitle(R.string.lock_collection_adv);
            } else {
                item.setTitle(R.string.lock_collection);
            }
        }

        // If we are in the advanced mode, we need to show the save and switch view
        MenuItem changeViewItem = menu.findItem(R.id.change_view);

        if(mDisplayType == ADVANCED_DISPLAY){
            changeViewItem.setTitle(R.string.simple_view_string);
            //saveItem.setVisible(true);
        } else {
            changeViewItem.setTitle(R.string.advanced_view_string);
            //saveItem.setVisible(false);
        }

        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        int itemId = item.getItemId();
        if (itemId == R.id.lock_unlock_collection) {
            // Need to check the preferences to see whether the collection is locked or unlocked
            SharedPreferences mainPreferences = getSharedPreferences(MainApplication.PREFS, MODE_PRIVATE);
            SharedPreferences.Editor editor = mainPreferences.edit();

            boolean isLocked = mainPreferences.getBoolean(mCollectionName + IS_LOCKED, false);
            boolean finishedSuccessfully = true;

            // If we are going from unlocked to lock in advance mode, we need to save the
            // changes the user may have made (if any)
            if (mDisplayType == ADVANCED_DISPLAY &&
                    !isLocked &&
                    this.doUnsavedChangesExist()) {

                // In the advanced display case, we also need to save

                // TODO Show some kind of spinner

                for (int i = 0; i < mCoinList.size(); i++) {
                    CoinSlot coinSlot = mCoinList.get(i);
                    if (coinSlot.hasIndexChanged()) {
                        try {
                            mDbAdapter.updateAdvInfo(mCollectionName, coinSlot);
                        } catch (SQLException e) {
                            finishedSuccessfully = false;
                            // Keep going, though
                            continue;
                        }
                        // Mark this data as being unchanged
                        coinSlot.setIndexChanged(false);
                    }
                }

                if (finishedSuccessfully) {
                    // Hide the unsaved changes view
                    Toast.makeText(this, mRes.getString(R.string.changes_saved), Toast.LENGTH_SHORT).show();
                    this.hideUnsavedTextView();
                } else {
                    showCancelableAlert(mRes.getString(R.string.error_updating_database));
                }
            }

            if (finishedSuccessfully) {
                if (isLocked) {
                    // Locked, change to unlocked
                    editor.putBoolean(mCollectionName + IS_LOCKED, false);
                    // Change the text for next time
                    if (mDisplayType == SIMPLE_DISPLAY) {
                        item.setTitle(R.string.lock_collection);
                    }
                    // Don't update in the advance case, because we are going to blow
                    // away this
                } else {
                    // Unlocked or preference doesn't exist, change preference to locked
                    editor.putBoolean(mCollectionName + IS_LOCKED, true);
                    // Change the text for next time
                    if (mDisplayType == SIMPLE_DISPLAY) {
                        item.setTitle(R.string.unlock_collection);
                    }
                }
            }

            // Save changes
            // TODO Consider not saving these if in advance mode and the db update
            // fails below
            editor.apply();

            if (mDisplayType == ADVANCED_DISPLAY) {
                // We need to restart the view so we can show the locked
                // view.  Also, at this point there are no unsaved changes

                // Save the position that the user was at for convenience
                // http://stackoverflow.com/questions/3014089/maintain-save-restore-scroll-position-when-returning-to-a-listview
                ListView listview = findViewById(R.id.advanced_collection_page);
                int index = listview.getFirstVisiblePosition();
                View v = listview.getChildAt(0);
                int top = (v == null) ? 0 : v.getTop();

                mCallingIntent.putExtra(VIEW_INDEX, index);
                mCallingIntent.putExtra(VIEW_POSITION, top);
                mCallingIntent.putExtra(COLLECTION_NAME, mCollectionName);

                finish();
                startActivity(mCallingIntent);
            }

            return true;
        } else if (itemId == R.id.change_view) {
            if (mDisplayType == SIMPLE_DISPLAY) {
                // Setup the advanced view
                try {
                    mDbAdapter.updateTableDisplay(mCollectionName, ADVANCED_DISPLAY);
                } catch (SQLException e) {
                    showCancelableAlert(mRes.getString(R.string.error_updating_database));
                }

                // Save the position that the user was at for convenience
                // http://stackoverflow.com/questions/3014089/maintain-save-restore-scroll-position-when-returning-to-a-listview
                GridView gridview = findViewById(R.id.standard_collection_page);
                int index = gridview.getFirstVisiblePosition();
                View v = gridview.getChildAt(0);
                int top = (v == null) ? 0 : v.getTop();

                mCallingIntent.putExtra(VIEW_INDEX, index);
                mCallingIntent.putExtra(VIEW_POSITION, top);
                mCallingIntent.putExtra(COLLECTION_NAME, mCollectionName);

                // Restart the activity
                finish();
                startActivity(mCallingIntent);

                return true;

            } else if (mDisplayType == ADVANCED_DISPLAY) {
                // Setup the basic view

                // We need to see if there are any unsaved changes, and if so,
                // present an alert

                if (this.doUnsavedChangesExist()) {

                    showUnsavedChangesAlertViewChange(mRes);
                    return true;
                }

                // The user doesn't have any unsaved changes

                try {
                    mDbAdapter.updateTableDisplay(mCollectionName, SIMPLE_DISPLAY);
                } catch (SQLException e) {
                    showCancelableAlert(mRes.getString(R.string.error_updating_database));
                }

                // Save the position that the user was at for convenience
                // http://stackoverflow.com/questions/3014089/maintain-save-restore-scroll-position-when-returning-to-a-listview
                ListView listview = findViewById(R.id.advanced_collection_page);
                int index = listview.getFirstVisiblePosition();
                View v = listview.getChildAt(0);
                int top = (v == null) ? 0 : v.getTop();

                mCallingIntent.putExtra(VIEW_INDEX, index);
                mCallingIntent.putExtra(VIEW_POSITION, top);
                mCallingIntent.putExtra(COLLECTION_NAME, mCollectionName);

                // Restart the activity
                finish();
                startActivity(mCallingIntent);
                return true;
            }

            // Shouldn't get here
            return true;
        } else if (itemId == R.id.rename_collection) {
            // Prompt user for new name via alert dialog
            showRenamePrompt();
            return true;
        } else if (itemId == android.R.id.home) {
            // To support having a back arrow on the page

            if (this.doUnsavedChangesExist()) {
                // If we have unsaved changes, don't go back right away but
                // instead let the user decide
                showUnsavedChangesAlertAndExitActivity();
            } else {
                this.onBackPressed();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Updates the collection name when the user renames a collection
     * @param newCollectionName Name of the new collection
     */
    private void updateCollectionName(String newCollectionName){

        String oldCollectionName = mCollectionName;

        // Do nothing if the name isn't actually changed
        if (newCollectionName.equals(oldCollectionName)){
            return;
        }

        // Make sure the new name isn't taken and is valid
        int checkNameResult = mDbAdapter.checkCollectionName(newCollectionName);
        if(checkNameResult != -1){
            Toast.makeText(this, mRes.getString(checkNameResult), Toast.LENGTH_SHORT).show();
            return;
        }

        // Perform all actions needed to rename the collection

        // Update database
        try {
            mDbAdapter.updateCollectionName(oldCollectionName, newCollectionName);
        } catch (SQLException e) {
            showCancelableAlert(mRes.getString(R.string.error_updating_database));
        }

        // Update app state
        SharedPreferences mainPreferences = getSharedPreferences(MainApplication.PREFS, MODE_PRIVATE);
        SharedPreferences.Editor editor = mainPreferences.edit();
        boolean isLocked = mainPreferences.getBoolean(oldCollectionName + IS_LOCKED, false);
        editor.remove(oldCollectionName + IS_LOCKED);
        editor.putBoolean(newCollectionName + IS_LOCKED, isLocked);
        editor.apply();

        // Update current view
        mCollectionName = newCollectionName;
        mCoinSlotAdapter.setTableName(newCollectionName);
        this.setTitle(newCollectionName);
    }

    /**
     * Prompts the user to rename the collection
     */
    private void showRenamePrompt(){
        // Create a text box for the new collection name
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        // Make a filter to block out bad characters
        InputFilter nameFilter = getCollectionNameFilter();
        input.setFilters(new InputFilter[]{nameFilter});
        input.setText(mCollectionName);

        // Build the alert dialog
        showAlert(newBuilder()
                .setTitle(mRes.getString(R.string.select_collection_name))
                .setView(input)
                .setPositiveButton(mRes.getString(R.string.okay), (dialog, which) -> {
                    dialog.dismiss();
                    String newName = input.getText().toString();
                    if (newName.equals("")) {
                        Toast.makeText(CollectionPage.this, mRes.getString(R.string.dialog_enter_collection_name), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    updateCollectionName(newName);
                })
                .setNegativeButton(mRes.getString(R.string.cancel), (dialog, which) -> dialog.cancel()));
    }
    
    private boolean doUnsavedChangesExist(){

        if(mDisplayType == ADVANCED_DISPLAY){
            // There are probably better ways to do this check, but this one is easy
            TextView unsavedChangesView = this.findViewById(R.id.unsaved_message_textview);
            return (unsavedChangesView.getVisibility() == View.VISIBLE);
        } else {
            // In the simple view, there will never be unsaved changes
            return false;
        }
    }
    
    @Override
    // http://android-developers.blogspot.com/2009/12/back-and-other-hard-keys-three-stories.html
    public boolean onKeyDown(final int keyCode, final KeyEvent event)  {

        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            // If the back key is pressed, we want to warn the user if there are unsaved changes

            if(this.doUnsavedChangesExist()){
                showUnsavedChangesAlertAndExitActivity();
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);	
    }
    
    /* We have one problem, specifically with the advancedView, where all of the
     * state is stored inside lists in the instance.  Normally, on an orientation
     * change or something, onDestroy and then onCreate would be called, destroying
     * the CoinSlotAdapter instance and all the state with it.  To prevent this,
     * we want to save off this object and use it for the newly created view.
     * http://stackoverflow.com/questions/7088816/my-views-are-being-reset-on-orientation-change
     * http://stackoverflow.com/questions/4249897/how-to-send-objects-through-bundle
     */
    
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState)
    {
        super.onSaveInstanceState(outState);

        // In the advanced view, if we change orientation or something we need
        // to save off the lists storing the uncommitted changes of coin grades,
        // coin quantities, and coin notes.  This is pretty hacked together,
        // so fix sometime, maybe
        
        int index;
        int top;
        
        if(mDisplayType == ADVANCED_DISPLAY){

            // Finally, save off the position of the listview
            ListView listview = findViewById(R.id.advanced_collection_page);

            // save index and top position
            // http://stackoverflow.com/questions/3014089/maintain-save-restore-scroll-position-when-returning-to-a-listview
            index = listview.getFirstVisiblePosition();
            View v = listview.getChildAt(0);
            top = (v == null) ? 0 : v.getTop();

        } else {

            GridView gridview = findViewById(R.id.standard_collection_page);

            index = gridview.getFirstVisiblePosition();
            View v = gridview.getChildAt(0);
            top = (v == null) ? 0 : v.getTop();
        }

        // Save off these lists that may have unsaved user data
        outState.putParcelableArrayList(COIN_LIST, mCoinList);
        outState.putInt(VIEW_INDEX, index);
        outState.putInt(VIEW_POSITION, top);
        outState.putString(COLLECTION_NAME, mCollectionName);
    }

    /**
     * Displays to the user that the collection is locked
     */
    private void showLockedMessage() {
        String text = mRes.getString(R.string.collection_locked);
        Toast toast = Toast.makeText(CollectionPage.this, text, Toast.LENGTH_SHORT);
        toast.show();
    }
}
