package com.example.pikr.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.pikr.models.Post;
import com.example.pikr.R;
import com.example.pikr.adapters.ProfileAdapter;
import com.example.pikr.models.Login;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

/**
 * Display past posts and basic user info
 */
public class MyActivityFragment extends Fragment {
    private Login loginInfo;
    private ListView mListView;
    private ProfileAdapter mAdapter;
    private DatabaseReference mRef;
    private ArrayList<Post> userPosts;
    private View view;

    public MyActivityFragment() {
        // Default constructor
    }

    public static MyActivityFragment newInstance() {
        return new MyActivityFragment();
    }

    /**
     * Initialize view and references to user data
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loginInfo = new Login(getActivity().getApplicationContext());
        userPosts = new ArrayList<>();

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        // Keys for users can't have periods
        String emailKey = loginInfo.getEmail().replace(".", CreateFragment.PERIOD_REPLACEMENT_KEY);
        mRef = database.getReference(emailKey);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_my_activity, container, false);
    }

    /**
     * Initialize the adapter to display the past posts of the user in a ListView
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        this.view = view;
        mListView = view.findViewById(R.id.profile_list_view);
        mAdapter = new ProfileAdapter(requireActivity(), 0, userPosts);
        mListView.setAdapter(mAdapter);

        setupDatabaseListener(view);
    }

    /**
     * Call helper methods to set up database subsections used to populate information about the user
     */
    private void setupDatabaseListener(final View currView) {
        String emailKey = loginInfo.getEmail().replace(".", CreateFragment.PERIOD_REPLACEMENT_KEY);
        final DatabaseReference friendsReference = FirebaseDatabase.getInstance().getReference().child("friends").child(emailKey);
        setupUserDatabaseListener();    // get post activity and private votes
        setupFriendsDatabaseListener(friendsReference, currView);   // Display number of friends at top of page
    }

    /**
     * Load the posts and display number of posts and name at the top
     */
    private void setupUserDatabaseListener(){
        mRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                mAdapter.clear();
                for (DataSnapshot postSnapshot : dataSnapshot.getChildren()) {
                    if(FeedFragment.isParsable(postSnapshot.getKey())) {
                        Post post = postSnapshot.getValue(Post.class);
                        if (post != null && !post.getDeleted()) {
                            mAdapter.add(post);
                        }
                    }
                }
                // Display total number of user-uploaded posts
                String numPosts = userPosts.size() + " Posts";
                ((TextView)view.findViewById(R.id.post_count_text)).setText(numPosts);
                mAdapter.notifyDataSetChanged();

                // Display user name at top of page
                String nameText = "Hello, " + loginInfo.getName() + "!";
                ((TextView) view.findViewById(R.id.hello_text)).setText(nameText);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(getActivity(), "Error loading profile", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Get reference to friends subsection to display total friends count on home page
     */
    private void setupFriendsDatabaseListener(DatabaseReference friendsReference, final View currView){
        friendsReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(dataSnapshot.hasChildren()) {
                    int numFriends = (int)dataSnapshot.getChildrenCount();
                    String friendsString = " Friends";
                    if(numFriends == 1) friendsString = " Friend";  // If one friend, singular version
                    ((TextView) currView.findViewById(R.id.num_friends_text)).setText(numFriends + friendsString);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("DatabaseError", "Error getting friends from dB");
            }
        });
    }
}
