package com.example.pikr.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.pikr.R;
import com.example.pikr.adapters.AddFriendsAdapter;
import com.example.pikr.models.Friend;
import com.example.pikr.models.Login;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Manage friends - view requests, send requests, and view friends list
 */
public class AddFriendsActivity extends AppCompatActivity {
    private DatabaseReference mUsers, mFriendRequests, mFriends;
    public static final CharSequence PERIOD_REPLACEMENT_KEY = "hgiasdvohekljh91-76";
    public static String state, previousState;
    private Set<String> allUserKeys, friendKeys, receivedKeys, sentKeys;
    public static String emailKey;
    private Button mRequestButton, mMyFriendsButton, mReceivedButton, mSentButton;
    private EditText mSearchText;
    private ListView mListView;
    private AddFriendsAdapter mAdapter;

    /**
     * Initialize the view and get friends list
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_friends);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        state = "my_friends";           // default screen views friends list
        previousState = "my_friends";   // no previous state, so set default to friends list

        setupFirebaseReferences();      // set up references to access any necessary elements of database
        setupViewReferences();          // create references to buttons/text/adapter that are changed

        // Adapter to manage friends list or requests
        mAdapter = new AddFriendsAdapter(this, 0, new ArrayList<Friend>());
        mListView.setAdapter(mAdapter);

        loadUsers();                    // load information from references to database
        setupRequestButton();           // setup click listener for sending friend request
        setupButtons();                 // setup click listener for myfriends/received/sent buttons
    }

    /**
     * Set up references to access necessary elements of Firebase realtime dB
     */
    private void setupFirebaseReferences(){
        Login currLogin = new Login(this.getApplicationContext());
        emailKey = currLogin.getEmail().replace(".", PERIOD_REPLACEMENT_KEY);

        mUsers = FirebaseDatabase.getInstance().getReference();
        mFriendRequests = FirebaseDatabase.getInstance().getReference().child("friend_request");
        mFriends = FirebaseDatabase.getInstance().getReference().child("friends");
    }

    /**
     * Set up references to buttons/text/adapter whose click is measured or text changed
     */
    private void setupViewReferences(){
        mRequestButton = findViewById(R.id.search_friends_button);
        mMyFriendsButton = findViewById(R.id.my_friends_button);
        mReceivedButton = findViewById(R.id.received_button);
        mSentButton = findViewById(R.id.sent_button);
        mSearchText = findViewById(R.id.search_friends_text);
        mListView = findViewById(R.id.my_friends_listview);
    }

    /**
     * Load relevant information from three areas of database
     */
    private void loadUsers(){
        addUsersDbListener();
        addFriendsDbListener();
        addFriendsRequestsDbListener();
    }

    /**
     * Get all the users in the database and add them to a set for later comparison
     * Used to ensure a real user is being added
     */
    private void addUsersDbListener(){
        mUsers.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                allUserKeys = new HashSet<>();
                for (DataSnapshot postSnapshot : dataSnapshot.getChildren()) {      // iterate through all dB elements
                    String email = postSnapshot.getKey();                           // get the identifying email of the user
                    // make sure the "email" isn't a non-email element of dB
                    if (!(email.equals("all posts"))&&!(email.equals(emailKey)&&!(email.equals("friends"))&&!(email.equals("friend_request")))){
                        allUserKeys.add(email);                                     // add the user to the set of all users
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("DatabaseError", "Error getting users DB info");
            }
        });
    }

    /**
     * Get a user's current list of friends from a reference to a user's "friends" subsection of dB
     */
    private void addFriendsDbListener() {
        mFriends.child(emailKey).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                friendKeys = new HashSet<>();
                for (DataSnapshot postSnapshot : dataSnapshot.getChildren()) {        // Iterate through all friends
                    String email = postSnapshot.getKey();
                    friendKeys.add(email);                                           // Add the friends' emails
                }
                updateAdapter();                                                     //update the adapter to ensure the most recently friends list is being properly displayed
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("DatabaseError", "Error getting friends DB info");
            }
        });
    }

    /**
     * Get a user's current friends requests from a reference to a user's "friend requests" subsection of dB
     */
    private void addFriendsRequestsDbListener(){
        mFriendRequests.child(emailKey).addValueEventListener(new ValueEventListener() {    //get the data for the "received" and "sent" tab
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                receivedKeys = new HashSet<>();
                sentKeys = new HashSet<>();
                for (DataSnapshot postSnapshot : dataSnapshot.getChildren()) {
                    String email = postSnapshot.getKey();
                    String requestType = "";
                    try {
                        requestType = postSnapshot.child("request_type").getValue().toString();             //get the type of request
                    }
                    catch(Exception e){
                        e.printStackTrace();
                    }
                    if (requestType.equals("received")){                    //if the request is received, then add the email to the "received" tab
                        receivedKeys.add(email);
                    }
                    else if (requestType.equals("sent")){                   //if this is a sent request, add the email to the "sent" tab
                        sentKeys.add(email);
                    }
                }
                updateAdapter();                                //update the displays to properly show sent and received friend requests
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("DatabaseError", "Error getting friends requests DB info");
            }
        });
    }

    /**
     * Connect a listener to the button the user clicks to send friend request
     *
     * Ensure proper email has been entered
     */
    private void setupRequestButton(){
        mRequestButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = mSearchText.getText().toString();                //get the text that has been input
                boolean noErrors = true;
                if (TextUtils.isEmpty(email)){                                 //email must not be empty
                    mSearchText.setError("Please enter an email");
                    noErrors = false;
                }
                else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()){        //email must be an email
                    mSearchText.setError("Invalid email");
                    noErrors = false;
                }
                if (noErrors){
                    boolean sent = false;
                    email = email.replace(".", PERIOD_REPLACEMENT_KEY);
                    for (String key: allUserKeys){
                        if (email.equals(key)
                                &&!friendKeys.contains(email)
                                &&!sentKeys.contains(email)
                                &&!receivedKeys.contains(email)){   //as long as the input email matches one of the actual users and the users are not already friends or have an ongoing request
                            mSearchText.setText("");                //clear the textView
                            sendFriendRequest(key);                 //send a friend request to the user
                            sent = true;
                            break;
                        }
                    }
                    if (!sent) Toast.makeText(AddFriendsActivity.this, "Invalid", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    /**
     * Update UI to properly display tab in friends request
     */
    private void updateAdapter(){
        mAdapter.clear();                   //clear the adapter to be added to
        switch(state){
            case "my_friends":              //if in the "my friends" tab
                if (friendKeys!=null) {
                    handleIteration(friendKeys);    // iterate through proper key and emails to adapter
                }
                break;
            case "received":                        //repeat the "my friends" tab process for the "received" tab
                if (receivedKeys!=null){
                    handleIteration(receivedKeys);  // iterate through proper key and emails to adapter
                }
                break;
            case "sent":                            //repeat the "my friends" tab process for the "received" tab
                if (sentKeys!=null) {
                    handleIteration(sentKeys);  // iterate through proper key and emails to adapter
                }
                break;
        }
        mAdapter.notifyDataSetChanged();                //notify the adapter of the changes
    }

    /**
     * Given a certain set of emails for an element of friends list, iterate through all emails
     * and add to listview adapter to display emails
     */
    private void handleIteration(Set<String> keySet){
        for(String key : keySet){
            key = key.replace(PERIOD_REPLACEMENT_KEY, ".");     // Format emails properly
            mAdapter.add(new Friend(key));
        }
    }

    /**
     * Given a friend request, add the email of the requester to relevant subsection of dB
     */
    private void sendFriendRequest(final String key) {
        // In section of dB used for requests for current user, add new email for the request with the "sent" value
        mFriendRequests.child(emailKey).child(key).child("request_type").setValue("sent")
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            mFriendRequests.child(key).child(emailKey).child("request_type").setValue("received")       //for the requested user, add the current user with a "received" value
                                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                                        @Override
                                        public void onSuccess(Void aVoid) {
                                            Toast.makeText(AddFriendsActivity.this, "Request sent", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                        }
                    }
                });
    }

    /**
     * Setup navigation between buttons changing listview display
     */
    private void setupButtons(){
        setupMyFriendsButton();
        setupReceivedButton();
        setupSentButton();
    }

    /**
     * Connect the "my friends" button with click listener so listview can be adapted to show current
     * list of friends
     */
    private void setupMyFriendsButton() {
        mMyFriendsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                state = "my_friends";                   //set the current state for the adapter
                if (!state.equals(previousState)) {     //if the state has changed, update the adapter
                    updateAdapter();
                }
                previousState = "my_friends";
                //update the color of the buttons' text to indicate the current tab
                mMyFriendsButton.setTextColor(ContextCompat.getColor(AddFriendsActivity.this, R.color.blue));
                mSentButton.setTextColor(ContextCompat.getColor(AddFriendsActivity.this, R.color.black));
                mReceivedButton.setTextColor(ContextCompat.getColor(AddFriendsActivity.this, R.color.black));
            }
        });
    }

    /**
     * Connect the friend requests button with click listener so listview can be adapted to show
     * relevant view
     */
    private void setupReceivedButton(){
        mReceivedButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                state = "received";
                if (!state.equals(previousState)) {
                    updateAdapter();
                }
                previousState = "received";
                //update the color of the buttons' text to indicate the current tab
                mReceivedButton.setTextColor(ContextCompat.getColor(AddFriendsActivity.this, R.color.blue));
                mSentButton.setTextColor(ContextCompat.getColor(AddFriendsActivity.this, R.color.black));
                mMyFriendsButton.setTextColor(ContextCompat.getColor(AddFriendsActivity.this, R.color.black));
            }
        });
    }

    /**
     * Connect the sent requests button with click listener so listview can be adapted to show
     * relevant view
     */
    private void setupSentButton(){
        mSentButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                state = "sent";
                if (!state.equals(previousState)) {
                    updateAdapter();
                }
                previousState = "sent";
                //update the color of the buttons' text to indicate the current tab
                mSentButton.setTextColor(ContextCompat.getColor(AddFriendsActivity.this, R.color.blue));
                mReceivedButton.setTextColor(ContextCompat.getColor(AddFriendsActivity.this, R.color.black));
                mMyFriendsButton.setTextColor(ContextCompat.getColor(AddFriendsActivity.this, R.color.black));
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        finish();
        return super.onOptionsItemSelected(item);
    }
}
