/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.example.delaroy.employeedirectory;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlertDialog;
import android.app.LoaderManager;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NavUtils;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.example.delaroy.employeedirectory.data.EmployeeContract;
import com.hendraanggrian.bundler.BindExtra;
import com.hendraanggrian.bundler.Bundler;
import com.hendraanggrian.kota.content.Themes;
import com.hendraanggrian.reveallayout.Radius;
import com.hendraanggrian.reveallayout.RevealableLayout;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Allows user to create a new employee or edit an existing one.
 */
public class EmployeeEditor extends AppCompatActivity implements View.OnClickListener,
        LoaderManager.LoaderCallbacks<Cursor> {

    /** Identifier for the employee data loader */
    private static final int EXISTING_EMPLOYEE_LOADER = 0;

    /** Content URI for the existing employee (null if it's a new employee) */
    private Uri mCurrentEmployeeUri;


    private EditText mFirstNameEditText;
    private EditText mLastNameEditText;
    private EditText mTitle;
    private EditText mDepartment;
    private EditText mCity;
    private EditText mPhone;
    private EditText mEmail;

    /** EditText field to enter the employee's gender */
    private Spinner mGenderSpinner;

    //TODO
    private ImageView profileImageView;
    private Button pickImage;

    private static final int SELECT_PHOTO = 1;
    private static final int CAPTURE_PHOTO = 2;

    private ProgressDialog progressBar;
    private int progressBarStatus = 0;
    private Handler progressBarbHandler = new Handler();
    private boolean hasImageChanged = false;
    Bitmap thumbnail;

    /**
     * Gender of the employee. The possible valid values are in the EmployeeContract.java file:
     * {@link com.example.delaroy.employeedirectory.data.EmployeeContract.EmployeeEntry#GENDER_UNKNOWN}, {@link com.example.delaroy.employeedirectory.data.EmployeeContract.EmployeeEntry#GENDER_MALE}, or
     * {@link com.example.delaroy.employeedirectory.data.EmployeeContract.EmployeeEntry#GENDER_FEMALE}.
     */
    private int mGender = EmployeeContract.EmployeeEntry.GENDER_UNKNOWN;


    /** Boolean flag that keeps track of whether the employee has been edited (true) or not (false) */
    private boolean mEmployeeHasChanged = false;


    View rootLayout;

    public static final String EXTRA_RECT = "com.example.delaroy.employeedirectory";
    @BindExtra(EXTRA_RECT)
    Rect rect;

    @BindView(R.id.revealLayout)
    RevealableLayout revealLayout;

    @BindView(R.id.layout)
    ViewGroup layout;

    /**
     * OnTouchListener that listens for any user touches on a View, implying that they are modifying
     * the view, and we change the mEmployeeHasChanged boolean to true.
     */
    private View.OnTouchListener mTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            mEmployeeHasChanged = true;
            return false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        Bundler.bindExtras(this);
        ButterKnife.bind(this);


        // Examine the intent that was used to launch this activity,
        // in order to figure out if we're creating a new employee or editing an existing one.
        Intent intent = getIntent();
        mCurrentEmployeeUri = intent.getData();

        // If the intent DOES NOT contain a employee content URI, then we know that we are
        // creating a new employee.
        if (mCurrentEmployeeUri == null) {
            // This is a new employee, so change the app bar to say "Add an Employee"
            setTitle(getString(R.string.editor_activity_title_new_employee));

            // Invalidate the options menu, so the "Delete" menu option can be hidden.
            // (It doesn't make sense to delete a employee that hasn't been created yet.)
            invalidateOptionsMenu();
        } else {
            // Otherwise this is an existing employee, so change app bar to say "Edit Employee"
            setTitle(getString(R.string.editor_activity_title_edit_employee));

            // Initialize a loader to read the employee data from the database
            // and display the current values in the editor
            getLoaderManager().initLoader(EXISTING_EMPLOYEE_LOADER, null, this);
        }

        // Find all relevant views that we will need to read user input from

        mFirstNameEditText = (EditText) findViewById(R.id.edit_employee_firstname);
        mLastNameEditText = (EditText) findViewById(R.id.edit_employee_lastname);
        mTitle = (EditText) findViewById(R.id.edit_employee_title);
        mDepartment = (EditText) findViewById(R.id.edit_employee_department);
        mCity = (EditText) findViewById(R.id.edit_employee_city);
        mPhone = (EditText) findViewById(R.id.edit_employee_phone);
        mEmail = (EditText) findViewById(R.id.edit_employee_email);
        mGenderSpinner = (Spinner) findViewById(R.id.spinner_gender);




        // Setup OnTouchListeners on all the input fields, so we can determine if the user
        // has touched or modified them. This will let us know if there are unsaved changes
        // or not, if the user tries to leave the editor without saving.
        mFirstNameEditText.setOnTouchListener(mTouchListener);
        mLastNameEditText.setOnTouchListener(mTouchListener);
        mTitle.setOnTouchListener(mTouchListener);
        mDepartment.setOnTouchListener(mTouchListener);
        mCity.setOnTouchListener(mTouchListener);
        mPhone.setOnTouchListener(mTouchListener);
        mEmail.setOnTouchListener(mTouchListener);
        mGenderSpinner.setOnTouchListener(mTouchListener);

        setupSpinner();

        rootLayout = findViewById(R.id.root_layout);

        layout.post(new Runnable() {
            @Override
            public void run() {
                Animator animator = revealLayout.reveal(layout, rect.centerX(), rect.centerY(), Radius.GONE_ACTIVITY);
                animator.setDuration(1000);
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (Build.VERSION.SDK_INT >= 21) {
                            getWindow().setStatusBarColor(Themes.getColor(getTheme(), R.attr.colorAccent, true));
                        }
                    }
                });
                animator.start();
            }
        });

        //TODO
        profileImageView = (ImageView) findViewById(R.id.profileImageView);
        pickImage = (Button) findViewById(R.id.pick_image);

        pickImage.setOnClickListener(this);

        if (ContextCompat.checkSelfPermission(EmployeeEditor.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            profileImageView.setEnabled(false);
            ActivityCompat.requestPermissions(EmployeeEditor.this, new String[] { Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE }, 0);
        } else {
            profileImageView.setEnabled(true);
        }


    }


    /**
     * Setup the dropdown spinner that allows the user to select the gender of the employee.
     */
    private void setupSpinner() {
        // Create adapter for spinner. The list options are from the String array it will use
        // the spinner will use the default layout
        ArrayAdapter genderSpinnerAdapter = ArrayAdapter.createFromResource(this,
                R.array.array_gender_options, android.R.layout.simple_spinner_item);

        // Specify dropdown layout style - simple list view with 1 item per line
        genderSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);

        // Apply the adapter to the spinner
        mGenderSpinner.setAdapter(genderSpinnerAdapter);

        // Set the integer mSelected to the constant values
        mGenderSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selection = (String) parent.getItemAtPosition(position);
                if (!TextUtils.isEmpty(selection)) {
                    if (selection.equals(getString(R.string.gender_male))) {
                        mGender = EmployeeContract.EmployeeEntry.GENDER_MALE;
                    } else if (selection.equals(getString(R.string.gender_female))) {
                        mGender = EmployeeContract.EmployeeEntry.GENDER_FEMALE;
                    } else {
                        mGender = EmployeeContract.EmployeeEntry.GENDER_UNKNOWN;
                    }
                }
            }

            // Because AdapterView is an abstract class, onNothingSelected must be defined
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                mGender = EmployeeContract.EmployeeEntry.GENDER_UNKNOWN;
            }
        });
    }

    /**
     * Get user input from editor and save employee into database.
     */
    private void saveEmployee() {
        // Read from input fields
        // Use trim to eliminate leading or trailing white space
        String firstnameString = mFirstNameEditText.getText().toString().trim();
        String lastnameString = mLastNameEditText.getText().toString().trim();
        String titleString = mTitle.getText().toString().trim();
        String departmentString = mDepartment.getText().toString().trim();
        String cityString = mCity.getText().toString().trim();
        String phoneString = mPhone.getText().toString().trim();
        String emailString = mEmail.getText().toString().trim();

        //TODO
        profileImageView.setDrawingCacheEnabled(true);
        profileImageView.buildDrawingCache();
        Bitmap bitmap = profileImageView.getDrawingCache();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        byte[] data = baos.toByteArray();



        // Check if this is supposed to be a new employee
        // and check if all the fields in the editor are blank
        if (mCurrentEmployeeUri == null &&
                TextUtils.isEmpty(firstnameString) && TextUtils.isEmpty(lastnameString) &&
                TextUtils.isEmpty(titleString) && TextUtils.isEmpty(departmentString) &&
                TextUtils.isEmpty(cityString) && TextUtils.isEmpty(phoneString) &&
                TextUtils.isEmpty(emailString) && mGender == EmployeeContract.EmployeeEntry.GENDER_UNKNOWN) {
            // Since no fields were modified, we can return early without creating a new employee.
            // No need to create ContentValues and no need to do any ContentProvider operations.
            return;
        }

        // Create a ContentValues object where column names are the keys,
        // and employee attributes from the editor are the values.
        ContentValues values = new ContentValues();
        values.put(EmployeeContract.EmployeeEntry.COLUMN_FIRSTNAME, firstnameString);
        values.put(EmployeeContract.EmployeeEntry.COLUMN_LASTNAME, lastnameString);
        values.put(EmployeeContract.EmployeeEntry.COLUMN_TITLE, titleString);
        values.put(EmployeeContract.EmployeeEntry.COLUMN_DEPARTMENT, departmentString);
        values.put(EmployeeContract.EmployeeEntry.COLUMN_CITY, cityString);
        values.put(EmployeeContract.EmployeeEntry.COLUMN_PHONE, phoneString);
        values.put(EmployeeContract.EmployeeEntry.COLUMN_IMAGE, data);
        values.put(EmployeeContract.EmployeeEntry.COLUMN_EMAIL, emailString);

        values.put(EmployeeContract.EmployeeEntry.COLUMN_EMPLOYEE_GENDER, mGender);
        // If the weight is not provided by the user, don't try to parse the string into an
        // integer value. Use 0 by default.


        // Determine if this is a new or existing employee by checking if mCurrentEmployeeUri is null or not
        if (mCurrentEmployeeUri == null) {
            // This is a NEW employee, so insert a new employee into the provider,
            // returning the content URI for the new employee.
            Uri newUri = getContentResolver().insert(EmployeeContract.EmployeeEntry.CONTENT_URI, values);

            // Show a toast message depending on whether or not the insertion was successful.
            if (newUri == null) {
                // If the new content URI is null, then there was an error with insertion.
                Toast.makeText(this, getString(R.string.editor_insert_employee_failed),
                        Toast.LENGTH_SHORT).show();
            } else {
                // Otherwise, the insertion was successful and we can display a toast.
                Toast.makeText(this, getString(R.string.editor_insert_employee_successful),
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            // Otherwise this is an EXISTING employee, so update the employee with content URI: mCurrentEmployeeUri
            // and pass in the new ContentValues. Pass in null for the selection and selection args
            // because mCurrentEmployeeUri will already identify the correct row in the database that
            // we want to modify.
            int rowsAffected = getContentResolver().update(mCurrentEmployeeUri, values, null, null);

            // Show a toast message depending on whether or not the update was successful.
            if (rowsAffected == 0) {
                // If no rows were affected, then there was an error with the update.
                Toast.makeText(this, getString(R.string.editor_update_employee_failed),
                        Toast.LENGTH_SHORT).show();
            } else {
                // Otherwise, the update was successful and we can display a toast.
                Toast.makeText(this, getString(R.string.editor_update_employee_successful),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu options from the res/menu/menu_editor.xml file.
        // This adds menu items to the app bar.
        getMenuInflater().inflate(R.menu.menu_editor, menu);
        return true;
    }

    /**
     * This method is called after invalidateOptionsMenu(), so that the
     * menu can be updated (some menu items can be hidden or made visible).
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        // If this is a new employee, hide the "Delete" menu item.
        if (mCurrentEmployeeUri == null) {
            MenuItem menuItem = menu.findItem(R.id.action_delete);
            menuItem.setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // User clicked on a menu option in the app bar overflow menu
        switch (item.getItemId()) {
            // Respond to a click on the "Save" menu option
            case R.id.action_save:
                // Save employee to database
                saveEmployee();
                // Exit activity
                finish();
                return true;
            // Respond to a click on the "Delete" menu option
            case R.id.action_delete:
                // Pop up confirmation dialog for deletion
                showDeleteConfirmationDialog();
                return true;
            // Respond to a click on the "Up" arrow button in the app bar
            case android.R.id.home:
                // If the employee hasn't changed, continue with navigating up to parent activity
                // which is the {@link EmployeeActivity}.
                if (!mEmployeeHasChanged) {
                    NavUtils.navigateUpFromSameTask(EmployeeEditor.this);
                    return true;
                }

                // Otherwise if there are unsaved changes, setup a dialog to warn the user.
                // Create a click listener to handle the user confirming that
                // changes should be discarded.
                DialogInterface.OnClickListener discardButtonClickListener =
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                // User clicked "Discard" button, navigate to parent activity.
                                NavUtils.navigateUpFromSameTask(EmployeeEditor.this);
                            }
                        };

                // Show a dialog that notifies the user they have unsaved changes
                showUnsavedChangesDialog(discardButtonClickListener);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * This method is called when the back button is pressed.
     */
    @Override
    public void onBackPressed() {

        Animator animator = revealLayout.reveal(layout, rect.centerX(), rect.centerY(), Radius.ACTIVITY_GONE);
        animator.setDuration(1000);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                if (Build.VERSION.SDK_INT >= 21) {
                    getWindow().setStatusBarColor(Themes.getColor(getTheme(), R.attr.colorPrimaryDark, true));
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                layout.setVisibility(View.INVISIBLE);
                finish();
                overridePendingTransition(0, 0);
            }
        });
        animator.start();
        // If the employee hasn't changed, continue with handling back button press
        if (!mEmployeeHasChanged) {
            super.onBackPressed();
            return;
        }

        // Otherwise if there are unsaved changes, setup a dialog to warn the user.
        // Create a click listener to handle the user confirming that changes should be discarded.
        DialogInterface.OnClickListener discardButtonClickListener =
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // User clicked "Discard" button, close the current activity.
                        finish();
                    }
                };

        // Show dialog that there are unsaved changes
        showUnsavedChangesDialog(discardButtonClickListener);


    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        // Since the editor shows all employee attributes, define a projection that contains
        // all columns from the employee table
        String[] projection = {
                EmployeeContract.EmployeeEntry._ID,
                EmployeeContract.EmployeeEntry.COLUMN_FIRSTNAME,
                EmployeeContract.EmployeeEntry.COLUMN_LASTNAME,
                EmployeeContract.EmployeeEntry.COLUMN_TITLE,
                EmployeeContract.EmployeeEntry.COLUMN_DEPARTMENT,
                EmployeeContract.EmployeeEntry.COLUMN_CITY,
                EmployeeContract.EmployeeEntry.COLUMN_PHONE,
                EmployeeContract.EmployeeEntry.COLUMN_IMAGE,
                EmployeeContract.EmployeeEntry.COLUMN_EMAIL,
                EmployeeContract.EmployeeEntry.COLUMN_EMPLOYEE_GENDER,
        };

        // This loader will execute the ContentProvider's query method on a background thread
        return new CursorLoader(this,   // Parent activity context
                mCurrentEmployeeUri,         // Query the content URI for the current employee
                projection,             // Columns to include in the resulting Cursor
                null,                   // No selection clause
                null,                   // No selection arguments
                null);                  // Default sort order
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        // Bail early if the cursor is null or there is less than 1 row in the cursor
        if (cursor == null || cursor.getCount() < 1) {
            return;
        }

        // Proceed with moving to the first row of the cursor and reading data from it
        // (This should be the only row in the cursor)
        if (cursor.moveToFirst()) {
            // Find the columns of employee attributes that we're interested in
            int firstnameColumnIndex = cursor.getColumnIndex(EmployeeContract.EmployeeEntry.COLUMN_FIRSTNAME);
            int lastnameColumnIndex = cursor.getColumnIndex(EmployeeContract.EmployeeEntry.COLUMN_LASTNAME);
            int titleColumnIndex = cursor.getColumnIndex(EmployeeContract.EmployeeEntry.COLUMN_TITLE);
            int departmentColumnIndex = cursor.getColumnIndex(EmployeeContract.EmployeeEntry.COLUMN_DEPARTMENT);
            int cityColumnIndex = cursor.getColumnIndex(EmployeeContract.EmployeeEntry.COLUMN_CITY);
            int phoneColumnIndex = cursor.getColumnIndex(EmployeeContract.EmployeeEntry.COLUMN_PHONE);
            int imageColumnIndex = cursor.getColumnIndex(EmployeeContract.EmployeeEntry.COLUMN_IMAGE);
            int emailColumnIndex = cursor.getColumnIndex(EmployeeContract.EmployeeEntry.COLUMN_EMAIL);
            int genderColumnIndex = cursor.getColumnIndex(EmployeeContract.EmployeeEntry.COLUMN_EMPLOYEE_GENDER);


            // Extract out the value from the Cursor for the given column index
            String firstName = cursor.getString(firstnameColumnIndex);
            String lastName = cursor.getString(lastnameColumnIndex);
            String title = cursor.getString(titleColumnIndex);
            String department = cursor.getString(departmentColumnIndex);
            String city = cursor.getString(cityColumnIndex);
            String phone = cursor.getString(phoneColumnIndex);
            byte[] image = cursor.getBlob(imageColumnIndex);
            String email = cursor.getString(emailColumnIndex);
            int gender = cursor.getInt(genderColumnIndex);


            // Update the views on the screen with the values from the database
            mFirstNameEditText.setText(firstName);
            mLastNameEditText.setText(lastName);
            mTitle.setText(title);
            mDepartment.setText(department);
            mCity.setText(city);
            mPhone.setText(phone);
            Bitmap bmp = BitmapFactory.decodeByteArray(image, 0, image.length);
            profileImageView.setImageBitmap(Bitmap.createScaledBitmap(bmp, 200,
                    200, false));
            mEmail.setText(email);

            // Gender is a dropdown spinner, so map the constant value from the database
            // into one of the dropdown options (0 is Unknown, 1 is Male, 2 is Female).
            // Then call setSelection() so that option is displayed on screen as the current selection.
            switch (gender) {
                case EmployeeContract.EmployeeEntry.GENDER_MALE:
                    mGenderSpinner.setSelection(1);
                    break;
                case EmployeeContract.EmployeeEntry.GENDER_FEMALE:
                    mGenderSpinner.setSelection(2);
                    break;
                default:
                    mGenderSpinner.setSelection(0);
                    break;
            }
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // If the loader is invalidated, clear out all the data from the input fields.
        mFirstNameEditText.setText("");
        mLastNameEditText.setText("");
        mTitle.setText("");
        mDepartment.setText("");
        mCity.setText("");
        mPhone.setText("");
        mEmail.setText("");
        mGenderSpinner.setSelection(0); // Select "Unknown" gender
    }

    /**
     * Show a dialog that warns the user there are unsaved changes that will be lost
     * if they continue leaving the editor.
     *
     * @param discardButtonClickListener is the click listener for what to do when
     *                                   the user confirms they want to discard their changes
     */
    private void showUnsavedChangesDialog(
            DialogInterface.OnClickListener discardButtonClickListener) {
        // Create an AlertDialog.Builder and set the message, and click listeners
        // for the postivie and negative buttons on the dialog.
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.unsaved_changes_dialog_msg);
        builder.setPositiveButton(R.string.discard, discardButtonClickListener);
        builder.setNegativeButton(R.string.keep_editing, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked the "Keep editing" button, so dismiss the dialog
                // and continue editing the employee.
                if (dialog != null) {
                    dialog.dismiss();
                }
            }
        });

        // Create and show the AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    /**
     * Prompt the user to confirm that they want to delete this employee.
     */
    private void showDeleteConfirmationDialog() {
        // Create an AlertDialog.Builder and set the message, and click listeners
        // for the postivie and negative buttons on the dialog.
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.delete_dialog_msg);
        builder.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked the "Delete" button, so delete the employee.
                deleteEmployee();
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked the "Cancel" button, so dismiss the dialog
                // and continue editing the employee.
                if (dialog != null) {
                    dialog.dismiss();
                }
            }
        });

        // Create and show the AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    /**
     * Perform the deletion of the employee in the database.
     */
    private void deleteEmployee() {
        // Only perform the delete if this is an existing employee.
        if (mCurrentEmployeeUri != null) {
            // Call the ContentResolver to delete the employee at the given content URI.
            // Pass in null for the selection and selection args because the mCurrentEmployeeUri
            // content URI already identifies the employee that we want.
            int rowsDeleted = getContentResolver().delete(mCurrentEmployeeUri, null, null);

            // Show a toast message depending on whether or not the delete was successful.
            if (rowsDeleted == 0) {
                // If no rows were deleted, then there was an error with the delete.
                Toast.makeText(this, getString(R.string.editor_delete_employee_failed),
                        Toast.LENGTH_SHORT).show();
            } else {
                // Otherwise, the delete was successful and we can display a toast.
                Toast.makeText(this, getString(R.string.editor_delete_employee_successful),
                        Toast.LENGTH_SHORT).show();
            }
        }

        // Close the activity
        finish();
    }

    //TODO
    @Override
    public void onClick(View view) {
        switch (view.getId()) {

            case R.id.pick_image:
                new MaterialDialog.Builder(this)
                        .title(R.string.uploadImages)
                        .items(R.array.uploadImages)
                        .itemsIds(R.array.itemIds)
                        .itemsCallback(new MaterialDialog.ListCallback() {
                            @Override
                            public void onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
                                switch (which){
                                    case 0:
                                        Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                                        photoPickerIntent.setType("image/*");
                                        startActivityForResult(photoPickerIntent, SELECT_PHOTO);
                                        break;
                                    case 1:
                                        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                                        startActivityForResult(intent, CAPTURE_PHOTO);
                                        break;
                                    case 2:
                                        profileImageView.setImageResource(R.drawable.ic_account_circle_black);
                                        break;
                                }
                            }
                        })
                        .show();
                break;

        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 0) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                profileImageView.setEnabled(true);
            }
        }
    }

    public void setProgressBar(){
        progressBar = new ProgressDialog(this);
        progressBar.setCancelable(true);
        progressBar.setMessage("Please wait...");
        progressBar.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressBar.setProgress(0);
        progressBar.setMax(100);
        progressBar.show();
        progressBarStatus = 0;
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (progressBarStatus < 100){
                    progressBarStatus += 30;

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    progressBarbHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setProgress(progressBarStatus);
                        }
                    });
                }
                if (progressBarStatus >= 100) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    progressBar.dismiss();
                }

            }
        }).start();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == SELECT_PHOTO){
            if(resultCode == RESULT_OK) {
                try {
                    final Uri imageUri = data.getData();
                    final InputStream imageStream = getContentResolver().openInputStream(imageUri);
                    final Bitmap selectedImage = BitmapFactory.decodeStream(imageStream);
                    //set Progress Bar
                    setProgressBar();
                    //set profile picture form gallery
                    profileImageView.setImageBitmap(selectedImage);


                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }

        }else if(requestCode == CAPTURE_PHOTO){
            if(resultCode == RESULT_OK) {
                onCaptureImageResult(data);
            }
        }
    }

    private void onCaptureImageResult(Intent data) {
        thumbnail = (Bitmap) data.getExtras().get("data");

        //set Progress Bar
        setProgressBar();
        //set profile picture form camera
        profileImageView.setMaxWidth(200);
        profileImageView.setImageBitmap(thumbnail);

    }

}