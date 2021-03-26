package com.example.pikr.activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.pikr.R;
import com.example.pikr.adapters.AddFriendsAdapter;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Objects;

/**
 * Allow user to manage friend list and sent/received requests after clicking on particular email
 */
public class ManageFriendActivity extends AppCompatActivity {
    private static final String B2_CODE = "button_2";
    private static final String CANCEL_CODE = "cancel";
    private Button mAcceptRequest, mButton2;
    private TextView mTextView;
    private String mEmail;
    private DatabaseReference mRequests, mFriends;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friend_manage);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        setupViewsAndDbReferences();    // Get references to text/buttons and relevant pieces of dB
        setupMainFriendsButton();       // Handle clicks on tabs to view info about friends and sent/received requests
        if (AddFriendsActivity.state.equals("received")) {
            setupRejectFriendButton();  // If checking received requests, setup option to deny friend request
        }
    }

    /**
     * Setup references to text/buttons that will be monitored and relevant pieces of dB
     */
    private void setupViewsAndDbReferences(){
        mRequests = FirebaseDatabase.getInstance().getReference().child("friend_request");
        mFriends = FirebaseDatabase.getInstance().getReference().child("friends");
        mAcceptRequest = findViewById(R.id.button_1_friend);
        mTextView = findViewById(R.id.manage_friend_text);
        mEmail = Objects.requireNonNull(getIntent().getExtras()).getString(AddFriendsAdapter.FRIEND_NAME_KEY);

        // Format and display email properly
        String emailText = "Email: "+ mEmail;
        mTextView.setText(emailText);
        // Period change is necessary since dB doesn't allow periods in key
        mEmail = mEmail.replace(".", AddFriendsActivity.PERIOD_REPLACEMENT_KEY);
    }

    /**
     * Handle user click differently depending on the tab that was selected when user clicked on
     */
    private void setupMainFriendsButton(){
        switch (AddFriendsActivity.state){
            case "my_friends":
                mAcceptRequest.setText(R.string.remove_friend);      //add the ability to remove a friend
                mAcceptRequest.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        removeFriend();
                        finish();
                    }
                });
                break;
            case "sent":
                mAcceptRequest.setText(R.string.cancel_request);    //add the ability to cancel a request that has been sent
                mAcceptRequest.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        cancelRequest(CANCEL_CODE);           //remove the request from the db
                        finish();
                    }
                });
                break;
            case "received":
                mAcceptRequest.setOnClickListener(new View.OnClickListener() {   //two buttons are only required for accepting or denying a friend request
                    @Override
                    public void onClick(View v) {           //accept the received friend request
                        addFriend();
                        cancelRequest("");              //delete the request in the db
                        finish();
                    }
                });
                break;
        }
    }

    /**
     * Setup a button that's an option when user is in received requests tab to deny requests
     */
    private void setupRejectFriendButton(){
        mButton2 = findViewById(R.id.button_2_friend);
        mButton2.setVisibility(View.VISIBLE);                   //make this button only visible when coming from the received tab
        mButton2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {                     //second button for denying a friend request
                cancelRequest(B2_CODE);
                finish();
            }
        });
    }

    /**
     * Remove a friend request from dB depending on whether a user cancels a sent request or declined
     * and incoming request
     */
    private void cancelRequest(final String code){
        mRequests.child(AddFriendsActivity.emailKey).child(mEmail).removeValue()    // Remove email of request from dB
                .addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                mRequests.child(mEmail).child(AddFriendsActivity.emailKey).removeValue()
                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {     // If info in dB has been updated, notify user
                        // Notification text depends on whether user canceling sent request or declining incoming request
                        if (code.equals(B2_CODE)){
                            Toast.makeText(ManageFriendActivity.this, "Request Declined", Toast.LENGTH_SHORT).show();
                        }
                        else if (code.equals(CANCEL_CODE)) {
                            Toast.makeText(ManageFriendActivity.this, "Request Canceled", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
    }

    /**
     * If adding a new friend, update dB and notify user once action is completed
     */
    private void addFriend(){        //add a friend in the db
        mFriends.child(AddFriendsActivity.emailKey).child(mEmail).setValue("")      //add user1 to user2's friends list
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()){
                            mFriends.child(mEmail).child(AddFriendsActivity.emailKey).setValue("")      //add user2 to the user1's friends list
                                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                                @Override
                                public void onSuccess(Void aVoid) {
                                    Toast.makeText(ManageFriendActivity.this, "Successfully Added", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }
                });
    }

    /**
     * If removing a friend, update dB and notify once user request has completed
     */
    private void removeFriend(){     //remove the friends from the db, make sure to do this on the end of each friend
        mFriends.child(AddFriendsActivity.emailKey).child(mEmail).removeValue().addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                mFriends.child(mEmail).child(AddFriendsActivity.emailKey).removeValue().addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Toast.makeText(ManageFriendActivity.this, "Friend Removed", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        finish();
        return super.onOptionsItemSelected(item);
    }
}
