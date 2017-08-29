/**
 * Copyright Google Inc. All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.udacity.friendlychat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.BuildConfig;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    public static final int RC_SIGN_IN = 1;
    public static final int RC_PHOTO_PICKER = 2;

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    public static final String FRIENDLY_MSG_LENGTH_KEY = "friendly_msg_length";

    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;
    private Uri downloadUrl;

    private FirebaseDatabase mFirebaseDatabase;//used to connect to the realtime firebase database
    private DatabaseReference mMessagesDatabaseReference;//used to access specific parts of the database
    //private DatabaseReference mMessagesDatabaseReference1;
    private ChildEventListener mChildEventListener;
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;//Auth State listener for when the user signs in,signs out
    //attached to firebase auth and is attached in onResume since unique for every user
    private FirebaseStorage mFirebaseStorage;
    private StorageReference mChatPhotosStorageReference;

    private FirebaseRemoteConfig mFirebaseRemoteConfig;


    private String mUsername;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: " + requestCode + " " + resultCode);
        if (requestCode == RC_SIGN_IN) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Signed in!!", Toast.LENGTH_SHORT).show();
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Sign In Cancelled!!", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else if (requestCode == RC_PHOTO_PICKER && resultCode == RESULT_OK) {
            Log.d(TAG, "onActivityResult: Selected Pic");
            Uri selectedImageUri = data.getData();
            Log.d(TAG, "onActivityResult: " + selectedImageUri);
            StorageReference photoRef = mChatPhotosStorageReference.child(selectedImageUri.getLastPathSegment());
            //with last path segment suppose if we have content://localimages/foo/4 then returmed will be 4 that is the last address

            //upload file to firebase storage
            photoRef.putFile(selectedImageUri).addOnSuccessListener(this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {

                    downloadUrl = taskSnapshot.getDownloadUrl();
                    Log.d(TAG, "onSuccess: " + downloadUrl.toString());
                    FriendlyMessage friendlyMessage = new FriendlyMessage(null, mUsername, downloadUrl.toString());
                    mMessagesDatabaseReference.push().setValue(friendlyMessage);
                }
            });//returns an UploadTask object which is how much of the file has bee uploaded
        } else Log.d(TAG, "onActivityResult: " + "ERROR");

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mFirebaseDatabase = FirebaseDatabase.getInstance();
        mMessagesDatabaseReference = mFirebaseDatabase.getReference().child("messages");
        // mMessagesDatabaseReference1 = mFirebaseDatabase.getReference().child("chats");

        mFirebaseAuth = FirebaseAuth.getInstance();

        mFirebaseStorage = FirebaseStorage.getInstance();
        mChatPhotosStorageReference = mFirebaseStorage.getReference().child("chat_photos");

        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();


        mUsername = ANONYMOUS;

        // Initialize references to views
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendButton = (Button) findViewById(R.id.sendButton);

        // Initialize message ListView and its adapter
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        mMessageListView.setAdapter(mMessageAdapter);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Fire an intent to show an image picker
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("image/jpeg");
                i.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                Log.d(TAG, "onClick: " + "Photo Picked");
                startActivityForResult(Intent.createChooser(i, "Complete Action Using"), RC_PHOTO_PICKER);//Intent.createChooser(i,"Complete Action Using")
                Log.d(TAG, "onClick: " + "Activity Started");
            }
        });

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Send messages on click

                FriendlyMessage friendlyMessage = new FriendlyMessage(mMessageEditText.getText().toString(), mUsername, null);//current
                //message which has been entered in the edit text is now passed into pojo which maps into json
                Log.e(TAG, "onClick: " + friendlyMessage.getText());
                mMessagesDatabaseReference.push().setValue(friendlyMessage);//with push a unique id is assigned to each message everytime
                // mMessagesDatabaseReference1.push().setValue(friendlyMessage);


                // Clear input box
                mMessageEditText.setText("");
            }
        });
//        mChildEventListener = new ChildEventListener() {
//            @Override
//            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
//                //object of datasnapshot points to the current message which has been entered into the list
//                FriendlyMessage friendlyMessage = dataSnapshot.getValue(FriendlyMessage.class);//deserialize the message into simple object
//                mMessageAdapter.add(friendlyMessage);
//
//            }//whenever an new message is entered in the message list
//
//            @Override
//            public void onChildChanged(DataSnapshot dataSnapshot, String s) {
//            }//when the contents of an existing message gets changed
//
//            @Override
//            public void onChildRemoved(DataSnapshot dataSnapshot) {
//            }//when existing messages get deleted
//
//            @Override
//            public void onChildMoved(DataSnapshot dataSnapshot, String s) {
//            }//when the existing message changes its position
//
//            @Override
//            public void onCancelled(DatabaseError databaseError) {
//            }//some sort of error occured
//
//        };
//        mMessagesDatabaseReference.addChildEventListener(mChildEventListener);//reference defines what actually I am listening to
//        // and listener defines what exactly will happen to the data

        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {

                FirebaseUser user = firebaseAuth.getCurrentUser();//firebaseAuth can never be null
                if (user != null) {
                    //user is signed in and the current session is going on
                    Toast.makeText(MainActivity.this, "You are currently Signed in!!Welcome to Friendly Chat!!", Toast.LENGTH_SHORT).show();
                    onSignedInInitialize(user.getDisplayName());
                } else {
                    //user is signed out
                    onSignedOutCleanUp();
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(false)
                                    .setProviders(Arrays.asList(new AuthUI.IdpConfig.Builder(AuthUI.EMAIL_PROVIDER).build(),
                                            new AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER).build()
                                    ))
                                    .build(),
                            RC_SIGN_IN);

                }


            }
        };

        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder().
                setDeveloperModeEnabled(BuildConfig.DEBUG).build();
        mFirebaseRemoteConfig.setConfigSettings(configSettings);
        Map<String, Object> defaultConfigMap = new HashMap<>();
        defaultConfigMap.put(FRIENDLY_MSG_LENGTH_KEY, DEFAULT_MSG_LENGTH_LIMIT);
        mFirebaseRemoteConfig.setDefaults(defaultConfigMap);
        fetchConfig();


    }


    @Override
    protected void onPause() {
        super.onPause();
        if (mAuthStateListener != null) {
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }
        detachDatabaseReadListener();
        mMessageAdapter.clear();

    }

    @Override
    protected void onResume() {
        super.onResume();
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sign_out_menu:
                AuthUI.getInstance().signOut(this);//passing an activity instance and so the user is signed out
                return true;
            default:
                return super.onOptionsItemSelected(item);

        }
    }

    private void onSignedInInitialize(String username) {
        mUsername = username;
        attachDatabaseReadListener();//We are doing it so that user can read the messages if only he is signed in
    }

    private void onSignedOutCleanUp() {
        mUsername = ANONYMOUS;
        mMessageAdapter.clear();
        detachDatabaseReadListener();


    }

    private void attachDatabaseReadListener() {
        if (mChildEventListener == null) {
            mChildEventListener = new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                    //object of datasnapshot points to the current message which has been entered into the list
                    FriendlyMessage friendlyMessage = dataSnapshot.getValue(FriendlyMessage.class);//deserialize the message into simple object
                    mMessageAdapter.add(friendlyMessage);

                }//whenever an new message is entered in the message list

                @Override
                public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                }//when the contents of an existing message gets changed

                @Override
                public void onChildRemoved(DataSnapshot dataSnapshot) {
                }//when existing messages get deleted

                @Override
                public void onChildMoved(DataSnapshot dataSnapshot, String s) {
                }//when the existing message changes its position

                @Override
                public void onCancelled(DatabaseError databaseError) {
                }//some sort of error occured

            };
            mMessagesDatabaseReference.addChildEventListener(mChildEventListener);//reference defines what actually I am listening to
            // and listener defines what exactly will happen to the data
        }
    }



    private void detachDatabaseReadListener() {
        if (mChildEventListener != null) {
            mMessagesDatabaseReference.removeEventListener(mChildEventListener);
            mChildEventListener = null;
        }
    }

    public void fetchConfig() {
        long cacheExpiration = 3600;
        if (mFirebaseRemoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled()){
            cacheExpiration = 0;
        }
        mFirebaseRemoteConfig.fetch(cacheExpiration).
                addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                mFirebaseRemoteConfig.activateFetched();
                applyRetrievedLengthLimit();


            }
        }).
                addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.w(TAG, "onFailure: " + "ERROR FETCHING CONFIG",e );
                applyRetrievedLengthLimit();

            }
        });


    }
    private void applyRetrievedLengthLimit(){

        Long friendly_msg_length = mFirebaseRemoteConfig.getLong(FRIENDLY_MSG_LENGTH_KEY);
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(friendly_msg_length.intValue()) {
        }});
        Log.d(TAG, "applyRetrievedLengthLimit: " + FRIENDLY_MSG_LENGTH_KEY + "=" + friendly_msg_length);
    }
}





/*
"STEPS TO implement Google Sign-In for authentication in FriendlyChat.
 The SHA-1 is a type of hash representation for the debug keystore,
  which you can get with the keytool command line tool.
   Which is a long way of saying, the debug keystore is a bunch of letters and numbers, which you should keep secret,
   that identifies your computer."




C:\Program Files\Java\jdk1.7.0_79\bin>keytool -exportcert -alias androiddebugkey -keystore "C:\Users\HP\.android\debug.keystore" -list -v
Enter keystore password:
Alias name: androiddebugkey
Creation date: Jan 9, 2017
Entry type: PrivateKeyEntry
Certificate chain length: 1
Certificate[1]:
Owner: C=US, O=Android, CN=Android Debug
Issuer: C=US, O=Android, CN=Android Debug
Serial number: 1
Valid from: Mon Jan 09 13:03:51 IST 2017 until: Wed Jan 02 13:03:51 IST 2047
Certificate fingerprints:
         MD5:  A8:2B:56:D6:1A:E5:1B:09:3F:65:32:59:67:AA:7E:5E
         SHA1: 79:D0:4D:44:66:92:64:DF:28:BC:06:B9:40:6D:3A:5A:56:C8:B2:75
         SHA256: 49:00:D5:10:0D:0A:41:92:FC:6D:5F:C3:D3:E9:C8:39:E1:00:56:A4:2A:1D:BB:E8:61:34:19:33:AA:8E:4D:3A
         Signature algorithm name: SHA1withRSA
         Version: 1

 */


