package com.example.pikr.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.pikr.R;
import com.example.pikr.fragments.CreateFragment;
import com.example.pikr.fragments.PostFragment;
import com.example.pikr.models.Login;
import com.example.pikr.models.Post;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Display images and allow a user to cast a vote on an image
 */
public class VotingActivity extends AppCompatActivity {
    private Post post;
    private HashMap<String, String> imagesURLs;
    private DownloadImageTask downloadImageTask;
    private DatabaseReference mRef;
    private String email;
    private boolean viewChanged;
    private boolean publicFeed;
    private int selectedImageIndex;
    private String emailKey;
    private Map<String, Object> voters;
    private LayoutInflater inflater;
    private ArrayList<View> images;

    /**
     * Load view and get post-related values passed from PostFragment
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voting);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        viewChanged = true;     // boolean to prevent repeat votes
        inflater = LayoutInflater.from(this);
        images = new ArrayList<>();

        loadFromIntent();
    }

    /**
     * Get relevant values loaded from intent for processing in this activity if they exist
     */
    private void loadFromIntent(){
        Intent passedIntents = getIntent();
        if(passedIntents!=null) {
            post = passedIntents.getExtras().getParcelable(PostFragment.POST_KEY);  // Post information from dB
            imagesURLs = (HashMap<String, String>)passedIntents.getSerializableExtra(PostFragment.IMAGES_KEY);  // URLs of images to display
            publicFeed = passedIntents.getExtras().getBoolean(PostFragment.IS_PUBLIC_FEED_KEY); // Handle votes differently depending on which feed user is in
            if(passedIntents.getExtras().getString(PostFragment.EMAIL_KEY)!=null){
                email = passedIntents.getExtras().getString(PostFragment.EMAIL_KEY);    // Get the email of user that posted
            }
            updateText();   // Update UI with post text information
            loadImages();   // Update UI using URLs of images
        }
    }

    /**
     * Inflate custom menu
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.voting_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * Check whether user has chosen to cast vote or return to feed
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.cast_vote) {
            // Get db reference and update voted value
            handleCastVote();
        }
        else{
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * When a user chooses to cast a vote, get a reference to dB depending on whether or not was sent
     * from public or private feed
     */
    private void handleCastVote(){
        if(downloadImageTask != null && (selectedImageIndex=downloadImageTask.getSelectedImageIndex())!= -2){
            FirebaseDatabase database = FirebaseDatabase.getInstance();

            if(publicFeed) {        // Update vote values in list of posts from public feed if sent from public
                mRef = database.getReference().child("all posts").child(post.getId() + "");
            }
            else{       // Otherwise update vote values in private feed
                if(email != null && !email.equals("")) {
                    emailKey = email.replace(".", CreateFragment.PERIOD_REPLACEMENT_KEY);   // dB keys can't have periods
                    mRef = database.getReference().child(emailKey).child(post.getId() + "");    // Update vote values in poster's subsection of dB
                }
            }
            // Add the casted vote value in proper subsection of dB
            setupDatabaseListener(mRef);
        }
    }

    /**
     * Update vote values in passed reference to subsection of dB
     */
    private void setupDatabaseListener(DatabaseReference mRef){
        mRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // If anyone has voted before, get emails of past voters in corresponding Map
                if(dataSnapshot.child("voters").getValue()!=null) {
                    voters = (Map<String, Object>)dataSnapshot.child("voters").getValue();
                }
                else voters = new HashMap<>();      // If no other people have voted, create a new empty hashmap

                int pastCount = 0;
                // If other people have voted on the selected image (key is Uri#) get this value and increment it by 1
                if(dataSnapshot.child("vote counts").child("Uri"+selectedImageIndex).getValue()!=null) {
                    pastCount = Integer.parseInt((String) dataSnapshot.child("vote counts").child("Uri"+selectedImageIndex).getValue());
                }
                // If first time view has been loaded, update the pastcount in dB by 1 (prevents repeat vote as dB is changed)
                if(viewChanged) updateVoteValues(pastCount);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("DatabaseError", "Error getting vote values from dB");
            }
        });
    }

    /**
     * Given the number of people who have already voted on an image, increment the count by one and
     * store in the proper dB subsection
     */
    private void updateVoteValues(int pastCount){
        viewChanged = false;    // prevent repeat votes as view updates
        final Login login = new Login(getApplicationContext());     // used to get current user's email to log that they have already voted

        Toast.makeText(getApplicationContext(), "Casting vote!", Toast.LENGTH_SHORT).show();    // notify user process is being undergone

        // Make sure the person hasn't already voted on this post before casting vote
        if(!voters.containsKey(login.getEmail().replace(".", CreateFragment.PERIOD_REPLACEMENT_KEY))) {
            int newCount = pastCount + 1;   // One vote is being cast, so increment past vote by 1
            // Update vote counts for current post with the new, updated value with the image-specific key "Uri#"
            mRef.child("vote counts").child("Uri" + selectedImageIndex).setValue(newCount + "").addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    // If the vote is successfully cast, add the user's email to the hashmap of voters (db doesn't allow sets)
                    // Prevents repeat votes
                    voters.put(login.getEmail().replace(".", CreateFragment.PERIOD_REPLACEMENT_KEY), "");
                    mRef.child("voters").setValue(voters).addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            viewChanged = false;
                            finish();   // Close the current voting activity and return to feed
                        }
                    });
                    viewChanged = false;
                }
            });
        }
        else{
            Toast.makeText(getApplicationContext(), "Cannot vote twice on same post, sorry!", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Use the current post to set the voting text correctly
     */
    private void updateText(){
        ((TextView)findViewById(R.id.votingTitleText)).setText(post.getTitle());
        ((TextView)findViewById(R.id.votingDescription)).setText(post.getDescription());
        ((TextView)findViewById(R.id.votingUsername)).setText(post.getName());
    }

    /**
     * Load images async to the voting activity for user to click on preference
     */
    private void loadImages(){
        try {
            int imageIndex = 0;
            for (String oneURL : imagesURLs.values()) {     // Start new asynctask for each photo to be loaded
                downloadImageTask = new DownloadImageTask((LinearLayout) findViewById(R.id.votingImagesLinearLayout), imageIndex);
                downloadImageTask.execute(oneURL);
                imageIndex += 1;
            }
        } catch (Exception e) {
            Log.e("Exception", "Error setting cover photo");
            e.printStackTrace();
        }
    }

    /**
     * AsyncTask used to load images from post for viewing by user
     */
    private class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
        private int imageIndex;
        private View view;
        private final ImageView imageView;
        private ProgressBar mProgressBar;

        /**
         * Get the current layout being updated and the index to ensure images are being loaded in
         * proper order
         */
        public DownloadImageTask(LinearLayout linearLayout, int imageIndex) {
            this.imageIndex = imageIndex;
            view = inflater.inflate(R.layout.progress_and_image, linearLayout, false);
            imageView = view.findViewById(R.id.voting_image_view);
            mProgressBar = view.findViewById(R.id.voting_progress_bar);     // only show progress bar while loading
            images.add(imageView);
            linearLayout.addView(view);
        }

        /**
         * Use worker thread to load images from provided URL
         */
        protected Bitmap doInBackground(String... urls) {
            String imageURL = urls[0];
            Bitmap bm = null;
            try {
                InputStream in = new java.net.URL(imageURL).openStream();
                bm = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                Log.e("Error", e.getMessage());
                e.printStackTrace();
            }
            return bm;
        }

        /**
         * Once image has been loaded, set the bitmap to the loaded image and create a click listener
         * so the user can select an image to vote on
         */
        protected void onPostExecute(Bitmap result) {
            mProgressBar.setVisibility(View.GONE);
            imageView.setVisibility(View.VISIBLE);
            imageView.setImageBitmap(result);
            imageView.setAdjustViewBounds(true);

            imageView.setPadding(10, 10, 10, 10);
            imageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // Set all images to default background before changing background of selected image
                    for (View v: images){
                        v.setPadding(10, 10, 10, 10);
                        v.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.background));
                    }
                    imageView.setPadding(15,15,15,15);
                    imageView.setBackgroundColor(Color.GREEN);  // Wrap image in green border to show which is selected
                    selectedImageIndex = imageView.getId();     // Store the index of image selected for later voting
                }
            });
            imageView.setId(imageIndex);
        }

        public int getSelectedImageIndex(){
            return selectedImageIndex;
        }
    }
}
