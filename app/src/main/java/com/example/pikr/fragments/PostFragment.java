package com.example.pikr.fragments;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.pikr.R;
import com.example.pikr.activities.PastPostActivity;
import com.example.pikr.activities.RegisterActivity;
import com.example.pikr.activities.VotingActivity;
import com.example.pikr.models.Login;
import com.example.pikr.models.Post;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.InputStream;
import java.util.HashMap;

/**
 * Fragment for individual post displayed in public/private feed
 */
public class PostFragment extends Fragment {
    public static final String POST_KEY = "send post";
    public static final String IMAGES_KEY = "send images";
    public static final String IS_PUBLIC_FEED_KEY = "is public feed";
    public static final String EMAIL_KEY = "email";


    ImageButton imageButton;
    private DatabaseReference mRef;
    private String emailKey;
    private ProgressBar mProgressBar;
    private Login currLogin;
    private View view;
    private Post post;
    private String email;
    private boolean isPublicFeed;
    HashMap<String, String> mapURLStrings;

    public PostFragment() {
        // Default constructor
    }

    /**
     * Store the post info for loading, the email of the user viewing the post, and where the post
     * is being loaded in
     */
    public PostFragment(Post post, String email, boolean isPublicFeed) {
        this.post = post;
        this.isPublicFeed = isPublicFeed;
        this.email = email;
    }

    public static PostFragment newInstance(String position) {
        return new PostFragment();
    }

    /**
     * Initialize variables and get references to means of getting user data
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mapURLStrings = new HashMap<>();
        currLogin = new Login(getContext().getApplicationContext());
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        // Firebase db can't have periods in key, swap to create new unique key
        emailKey = currLogin.getEmail().replace(".", CreateFragment.PERIOD_REPLACEMENT_KEY);

        // Get current post info from master list of posts
        mRef = database.getReference("all posts").child(post.getId() + "").child("photos");
        setupDatabaseListener();
    }

    /**
     * Helper method to get URLs of images to be loaded from storage
     */
    private void setupDatabaseListener(){
        mRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for(DataSnapshot currURLString : dataSnapshot.getChildren()) {
                    mapURLStrings.put(currURLString.getKey(), (String)currURLString.getValue());
                }
                updateValues(post);     // Update UI with post vals
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("DatabaseError", "Error loading URLs from dB");
            }
        });
    }

    /**
     * Initialize relevant references to view and display progress bar while waiting for image to load
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_feed_post, container, false);
        this.view = view;
        mProgressBar = view.findViewById(R.id.progress_view);
        imageButton = view.findViewById(R.id.postImageButton);
        imageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                handleClickToVote();
            }
        });
        if (post.getId() == -1){
            mProgressBar.setVisibility(View.INVISIBLE);
            imageButton.setVisibility(View.VISIBLE);
        }
        return view;
    }

    /**
     * If user clicks on image, start voting activity and pass relevant information via intent
     */
    private void handleClickToVote(){
        if(email.length() > 0 && !email.equals(currLogin.getEmail())) {
            Intent intent = new Intent(getActivity(), VotingActivity.class);
            intent.putExtra(POST_KEY, post);
            intent.putExtra(IMAGES_KEY, mapURLStrings);
            intent.putExtra(IS_PUBLIC_FEED_KEY, isPublicFeed);
            intent.putExtra(EMAIL_KEY, email);

            if (mapURLStrings.size() > 0 && post != null) {
                startActivity(new Intent(intent));
            } else {
                Toast.makeText(getContext(), "Error loading post. Please try again momentarily", Toast.LENGTH_SHORT).show();
            }
        }
        else{
            // If user requesting to vote has the same email as the person who posted, display error
            Toast.makeText(getContext(), "No voting on your own post, sorry!", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Update UI with values from passed Post object, with the exception of images that are loaded
     * asynchronously
     */
    private void updateValues(Post post) {
        ((TextView) view.findViewById(R.id.post_title_text)).setText(post.getTitle());
        try {
            for (String oneURL : mapURLStrings.values()) {
                new DownloadImageTask((ImageButton) view.findViewById(R.id.postImageButton)).execute(oneURL);
                break;
            }
        } catch (Exception e) {
            Log.e("Exception", "Error setting cover photo");
            e.printStackTrace();
        }
        ((TextView) view.findViewById(R.id.description_text)).setText(post.getDescription());
        ((TextView) view.findViewById(R.id.user_text)).setText(post.getName());
    }

    /**
     * Asynchronous class to load image based on URL into post fragment
     */
    private class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
        ImageButton bmImage;

        /**
         * Constructor needs reference to image being updated
         */
        public DownloadImageTask(ImageButton bmImage) {
            this.bmImage = bmImage;
        }

        /**
         * Load an image from URL async
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
         * Make the loaded bitmap visible and progress bar invisible
         */
        protected void onPostExecute(Bitmap result) {
            mProgressBar.setVisibility(View.INVISIBLE);
            imageButton.setVisibility(View.VISIBLE);
            bmImage.setImageBitmap(result);
        }
    }
}