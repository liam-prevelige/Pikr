package com.example.pikr.activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.pikr.R;
import com.example.pikr.adapters.ProfileAdapter;
import com.example.pikr.fragments.CreateFragment;
import com.example.pikr.models.Login;
import com.example.pikr.models.Post;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import java.io.InputStream;
import java.util.Objects;

/**
 * Allow user to view the information from a past post, and delete the post if requested
 */
public class PastPostActivity extends AppCompatActivity {
    private TextView title, description;
    private Post post;
    private DatabaseReference ref, allPosts;
    private LinearLayout mLinearLayout;
    private LayoutInflater inflater;
    private int publicImageCount;
    private int privateImageCount;
    private String emailKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();
        setContentView(R.layout.activity_past_post);

        // Keep track of number of images for indexing when updating vote values (async)
        publicImageCount = 0;
        privateImageCount = 0;

        setupViewReferences();      // Setup references for text/layout that will be changed
        setupDBReferences();        // Setup references to dB with relevant information
    }

    /**
     * Setup references for text/layout that will be updated as data comes in
     */
    private void setupViewReferences(){
        title = findViewById(R.id.history_title_text);
        description = findViewById(R.id.history_description_text);
        mLinearLayout = findViewById(R.id.history_photo_scroll);
    }

    /**
     * Setup references for subsections of dB whose information will be queried
     */
    private void setupDBReferences(){
        Login loginInfo = new Login(this.getApplicationContext());
        FirebaseDatabase database = FirebaseDatabase.getInstance();

        // Get email from SharedPreferences and format email properly for indexing into dB (periods not allowed as key in dB)
        emailKey = loginInfo.getEmail().replace(".", CreateFragment.PERIOD_REPLACEMENT_KEY);
        ref = database.getReference(emailKey);

        allPosts = database.getReference("all posts");      // Reference subsection of dB with all posts listed

        //query the database to load the post based on the ID passed through the intent
        Query query = ref.orderByChild("id").equalTo(Objects.requireNonNull(getIntent().getExtras()).getInt(ProfileAdapter.POST_ID_KEY));
        query.addListenerForSingleValueEvent(postValueEventListener);
    }

    /**
     * EventListener to load post info from post that user clicked on
     */
    ValueEventListener postValueEventListener = new ValueEventListener() {
        @Override
        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
            if (dataSnapshot.exists()){
                for (DataSnapshot snapshot: dataSnapshot.getChildren()) {
                    post = snapshot.getValue(Post.class);           //set the post to the post in the database
                    updateFields();                                 //update the display based on the post
                }
            }
        }
        @Override
        public void onCancelled(@NonNull DatabaseError databaseError) {
            Log.e("DatabaseError", "Error getting post value from dB");
        }
    };

    /**
     * EventListener to load images associated with post user clicked on
     */
    ValueEventListener photoEventListener = new ValueEventListener() {
        @Override
        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
            if (dataSnapshot.exists()){
                for (DataSnapshot snapshot: dataSnapshot.getChildren()){
                    try {
                        // Call the method to kick off the async task for uploading each photo of the post
                        addPhotoView((String) snapshot.getValue());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        @Override
        public void onCancelled(@NonNull DatabaseError databaseError) {
            Log.e("DatabaseError", "Error getting photo value from dB");
        }
    };

    /**
     * Start the async task to load image into display
     */
    private void addPhotoView(String url) {
        inflater = LayoutInflater.from(this);
        new DownloadImageTask().execute(url);
    }

    /**
     * Create asynctask to upload photos into the post being viewed as they come in
     */
    private class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
        private View scrollView;
        private ProgressBar mProgressBar;
        private ImageView imageView;

        @Override
        protected void onPreExecute() {
            scrollView = inflater.inflate(R.layout.history_photo_item, mLinearLayout, false);
            mProgressBar = scrollView.findViewById(R.id.past_progress_bar);         //display the progress view at the start
            imageView = scrollView.findViewById(R.id.history_imageView);
            updateVoteCounts(scrollView, post);     // display vote values associated with current image
            mLinearLayout.addView(scrollView);
            super.onPreExecute();
        }
        // Use the URL contained in the post to load the image from Firebase storage
        protected Bitmap doInBackground(String... urls) {
            String imageURL = urls[0];
            Bitmap bm = null;
            try {
                InputStream in = new java.net.URL(imageURL).openStream();
                bm = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return bm;
        }

        // Once the image comes in, update the view with the current photo just loaded
        protected void onPostExecute(Bitmap result) {
            int scale = (int) getResources().getDimension(R.dimen.history_image_length);
            mProgressBar.setVisibility(View.GONE);
            imageView.setVisibility(View.VISIBLE);
            imageView.setImageBitmap(Bitmap.createScaledBitmap(result, scale, scale, true));
            mProgressBar.setVisibility(View.GONE);      //remove the progress bar
            imageView.setVisibility(View.VISIBLE);      //replace the progress bar with the image
            imageView.setImageBitmap(Bitmap.createScaledBitmap(result, scale, scale, true));    //set the bitmap of the imageView
        }
    }

    /**
     * Get the public and private vote values for the current image and update these values
     * asynchronously
     */
    private void updateVoteCounts(final View view, final Post currPost){
        String postId = currPost.getId() + "";
        DatabaseReference allRef = FirebaseDatabase.getInstance().getReference().child("all posts");
        DatabaseReference mRef = FirebaseDatabase.getInstance().getReference().child(emailKey);

        setupAllPostsRef(allRef, postId, view);     // Use the ref from allposts subsection to get public votes
        setupLocalPostRef(mRef, postId, view);      // Use the ref from the user's subsection to get private votes
    }

    /**
     * Using a reference to the public feed section of posts, get the number of public votes
     *
     * Update the passed view which corresponds to the current image being processed
     */
    private void setupAllPostsRef(DatabaseReference allRef, String postId, final View view){
        allRef.child(postId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(view != null) {
                    // If public votes have been passed, get the vote counts and update view
                    if(dataSnapshot.hasChild("vote counts")){
                        int publicVotesCount = 0;
                        if(dataSnapshot.child("vote counts").hasChildren()) {       // If any photo has been voted for, this will exist
                            if (dataSnapshot.child("vote counts").child("Uri" + publicImageCount).exists()){    // Vote counts stored with key "Uri#"
                                publicVotesCount = Integer.parseInt((String) dataSnapshot.child("vote counts").child("Uri" + publicImageCount).getValue());
                            }
                        }
                        publicImageCount += 1;      // Keep a track of which image has been processed for index into dB

                        // Display the vote count in current image's view
                        ((TextView) view.findViewById(R.id.text_public_votes)).setText("Public Votes: " + publicVotesCount);
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("DatabaseError", "Error setting public votes from dB");
            }
        });
    }

    /**
     * Check subsection of dB specific to user to get the number of private votes
     */
    private void setupLocalPostRef(DatabaseReference mRef, String postId, final View view){
        mRef.child(postId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(view != null) {
                    // If private votes have been passed, get the vote counts and update view
                    if(dataSnapshot.hasChild("vote counts")){
                        int privateVoteCounts = 0;
                        if(dataSnapshot.child("vote counts").hasChildren()) {   // If any photo has been voted for, this will exist
                            if (dataSnapshot.child("vote counts").child("Uri"+privateImageCount).exists()){
                                privateVoteCounts = Integer.parseInt((String)dataSnapshot.child("vote counts").child("Uri"+privateImageCount).getValue());
                            }
                        }
                        privateImageCount += 1;     // Keep a track of which image has been processed for index into dB
                        ((TextView)view.findViewById(R.id.text_private_votes)).setText("Private Votes: " + privateVoteCounts);
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("DatabaseError", "Error setting public votes from dB");
            }
        });
    }

    /**
     * Update title and description with post info and setup images/vote counts for UI
     */
    private void updateFields(){
        if (post!=null) {
            title.setText(post.getTitle());
            description.setText(post.getDescription());
            setupPictures();
        }
    }

    /**
     * Query the database for links to the pictures
     */
    private void setupPictures(){
        Query query = ref.child(String.valueOf(post.getId())).child("photos");
        query.addListenerForSingleValueEvent(photoEventListener);
    }

    /**
     * Create custom action bar with correct title and ability to return to prior view
     */
    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle("Post Details");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_delete, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch(item.getItemId()){
            case android.R.id.home:
                finish();
                return true;
            case R.id.action_delete:
                deletePost();         // Set the delete post variable to true in dB
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Set the delete post variable to true in all posts and user-specific section of dB to remove
     * from feed and myactivity
     */
    private void deletePost(){
        ref.child(String.valueOf(post.getId())).child("deleted").setValue(true);
        allPosts.child(String.valueOf(post.getId())).child("deleted").setValue(true);
    }
}
