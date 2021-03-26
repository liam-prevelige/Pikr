package com.example.pikr.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.example.pikr.R;
import com.example.pikr.adapters.FeedViewPagerAdapter;
import com.example.pikr.models.Login;
import com.example.pikr.models.Post;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

/**
 * Display a list of posts from all users on the app base in the feed
 */
public class FeedFragment extends Fragment {
    private int viewedPostIndex;
    private DatabaseReference allPostsRef, mRef, mAllUsers, mFriends;
    private String emailKey;
    private String state, previousState;        //which feed is being viewed
    private Login currLogin;
    private ArrayList<Post> postsForCreation;
    private ArrayList<Post> friendsPosts;
    private ArrayList<String> friendsPostsEmails;
    private FeedViewPagerAdapter mAdapter, mPrivateAdapter;
    private ViewPager viewPager, privateViewPager;

    public FeedFragment() {
        // Default constructor
    }

    public static FeedFragment newInstance() {
        return new FeedFragment();
    }

    /**
     * Initialize view and references to database that will load feed values
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        state = "public";
        previousState = "public";
        currLogin = new Login(getContext().getApplicationContext());
        postsForCreation = new ArrayList<>();

        setupDatabaseReferences();  // References to relevant subsections of dB for loading of feed

        mAdapter = new FeedViewPagerAdapter(getActivity().getSupportFragmentManager(),          //public adapter
                FragmentStatePagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        mPrivateAdapter = new FeedViewPagerAdapter(getActivity().getSupportFragmentManager(),   //private adapter
                FragmentStatePagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
    }

    /**
     * References to subsections of dB used to load private and public feed
     */
    private void setupDatabaseReferences(){
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        emailKey = currLogin.getEmail().replace(".", CreateFragment.PERIOD_REPLACEMENT_KEY);
        mRef = database.getReference(emailKey);
        mAllUsers = database.getReference();
        mFriends = database.getReference().child("friends").child(emailKey);
        allPostsRef = database.getReference("all posts");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_feed, container, false);
    }

    /**
     * Setup references to elements of view to be changed and get information from database to load feed
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        setupDatabaseListener();
        viewPager = view.findViewById(R.id.feed_view_pager);
        privateViewPager = view.findViewById(R.id.private_view_pager);
        viewPager.setAdapter(mAdapter);
        privateViewPager.setAdapter(mPrivateAdapter);
        setFeedButtonListeners(view);
        super.onViewCreated(view, savedInstanceState);
    }

    /**
     * Setup info for the buttons that change between public and private feed
     */
    private void setFeedButtonListeners(View view) {
        final Button publicFeed = view.findViewById(R.id.public_button);
        final Button privateFeed = view.findViewById(R.id.private_button);
        final String selectedButtonColor = "#98FB98";
        setupPublicFeedClick(publicFeed, privateFeed, selectedButtonColor);
        setupPrivateFeedClick(publicFeed, privateFeed, selectedButtonColor);
    }

    /**
     * Setup click listener for public feed button and change feed info accordingly
     */
    private void setupPublicFeedClick(final Button publicFeed, final Button privateFeed, final String selectedButtonColor){
        publicFeed.setBackgroundColor(Color.parseColor(selectedButtonColor));

        publicFeed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                state = "public";
                if (!state.equals(previousState)) {             //if going to public from private
                    updateAdapter();                            //update the associated adapter
                    viewPager.setVisibility(VISIBLE);           //change the visibilities of the viewPagers
                    privateViewPager.setVisibility(INVISIBLE);
                }
                previousState = "public";
                publicFeed.setBackgroundColor(Color.parseColor(selectedButtonColor));       //change the colors of the buttons appropriately
                privateFeed.setBackgroundColor(Color.WHITE);
            }
        });
    }

    /**
     * Setup click listener for private feed button and change feed info accordingly
     */
    private void setupPrivateFeedClick(final Button publicFeed, final Button privateFeed, final String selectedButtonColor){
        privateFeed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                state = "private";
                if (!state.equals(previousState)) {         //if going to private from public
                    updateAdapter();                        //take appropriate steps as with the public feed button
                    privateViewPager.setVisibility(VISIBLE);
                    viewPager.setVisibility(INVISIBLE);
                }
                previousState = "private";
                privateFeed.setBackgroundColor(Color.parseColor(selectedButtonColor));
                publicFeed.setBackgroundColor(Color.WHITE);
            }
        });
    }

    /**
     * Used for future tracking of viewed posts to update feed with most relevant content
     */
    private void setupDatabaseListener() {
        mRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                postsForCreation.clear();           //clear the list of posts to be displayed

                if (dataSnapshot.hasChild("viewed posts") && dataSnapshot.child("viewed posts").getValue() != null) {
                    viewedPostIndex = Integer.parseInt((String) dataSnapshot.child("viewed posts").getValue());     //get the index from the db
                } else {
                    viewedPostIndex = -1;
                    mRef.child("viewed posts").setValue("-1");
                }
                getAllPosts();          //load and add all the posts for the public feed that should be displayed
                setupFriends();         //load and add all posts for the private feed
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("DatabaseError", "Error getting viewed posts from dB");
            }
        });
    }

    /**
     * Use reference to list of all posts to populate public feed
     */
    private void getAllPosts(){
        allPostsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot postSnapshot : dataSnapshot.getChildren()) {
                    if (isParsable(postSnapshot.getKey())) {    // Non-post subsections of dB are not indexed w/ int count
                        Post post = postSnapshot.getValue(Post.class);
                        if (post != null && !post.getDeleted()) {              //add appropriate posts into the arrayList
                            if(postsForCreation.size() == 0){
                                postsForCreation.add(post);
                            }
                            else if(postsForCreation.get(postsForCreation.size()-1).getId() < post.getId()) {
                                postsForCreation.add(post);
                            }
                        }
                    }
                }
                updateAdapter();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("DatabaseError", "Failed to get posts from dB");
            }
        });
    }

    /**
     * Use reference to list of friends' posts to populate private feed
     */
    private void addFriendsPosts(final String email){
        mAllUsers.child(email).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot postSnapshot : dataSnapshot.getChildren()){
                    if (isParsable(postSnapshot.getKey())){    // Non-post subsections of dB are not indexed w/ int count
                        Post post = postSnapshot.getValue(Post.class);
                        if (post != null && !post.getDeleted()) {
                            friendsPosts.add(post);         //add the posts to the list
                            friendsPostsEmails.add(email);  //add the email to a separate list
                        }
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("DatabaseError", "Error loading friends posts from dB");
            }
        });
    }

    /**
     * Access friends of user
     * For all the posts of the user's friend, add the post and the associated email to appropriate lists
     */
    private void setupFriends(){
        mFriends.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                friendsPosts = new ArrayList<>();
                friendsPostsEmails = new ArrayList<>();
                for (DataSnapshot postSnapshot : dataSnapshot.getChildren()) {
                    String email = postSnapshot.getKey();
                    addFriendsPosts(email);
                }
                updateAdapter();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("DatabaseError", "Error loading friends from dB");
            }
        });
    }

    /**
     * Helper method to differentiate posts from other subsections of dB
     * Non-parsable keys are not posts
     */
    public static boolean isParsable(String input){
        try{
            Integer.parseInt(input);
            return true;
        } catch (final NumberFormatException e){
            return false;
        }
    }

    /**
     * Update adapter with the posts that need to be added
     * Handle differently based on whether post is from private or public feed
     */
    public void updateAdapter() {
        ArrayList<PostFragment> posts = new ArrayList<>();
        switch (state) {
            case "public":
                if (postsForCreation != null && postsForCreation.size() > 0) {
                        for (Post post : postsForCreation) {
                            // Specify post is from public feed with boolean for future updating of vote counts
                            posts.add(new PostFragment(post, post.getEmail(),true));
                        }
                }
                else {
                    Post post = emptyPost();
                    post.setId(-1);
                    posts.add(new PostFragment(post, post.getEmail(),true));
                }
                mAdapter.setPostFragment(posts);
                viewPager.setAdapter(mAdapter);
                break;
            case "private":
                if (friendsPosts != null && friendsPosts.size()>0){
                    for(int i = 0; i < friendsPosts.size(); i++){
                        // Specify post is from private feed with boolean for future updating of vote counts
                        posts.add(new PostFragment(friendsPosts.get(i), friendsPostsEmails.get(i), false));
                    }
                }
                else {
                    Post post = emptyPost();
                    post.setId(-1);
                    posts.add(new PostFragment(post, post.getEmail(),false));
                }
                mPrivateAdapter.setPostFragment(posts);
                privateViewPager.setAdapter(mPrivateAdapter);
                break;
        }
    }

    /**
     * Default post value when there's nothing to show in feed
     */
    private Post emptyPost(){
        Post post = new Post();
        post.setTitle("No more posts!");
        post.setDescription("You have reached the end of the feed.");
        post.setName("Admin");
        return post;
    }
}
