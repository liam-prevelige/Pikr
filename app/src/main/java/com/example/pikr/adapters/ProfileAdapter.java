package com.example.pikr.adapters;

import android.content.Context;
import android.content.Intent;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.pikr.activities.PastPostActivity;
import com.example.pikr.fragments.CreateFragment;
import com.example.pikr.models.Login;
import com.example.pikr.models.Post;
import com.example.pikr.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

/**
 * Adapter to display past posts from a user in MyActivityFragment
 */
public class ProfileAdapter extends ArrayAdapter<Post> {
    public static final String POST_ID_KEY = "id";
    private Context context;
    private ArrayList<Post> posts;
    private DatabaseReference allRef;
    private DatabaseReference mRef;
    private TextView publicVotes;
    private TextView privateVotes;
    private TextView title;
    private TextView time;

    /**
     * Constructor to get necessary info about view and posts being loaded
     */
    public ProfileAdapter(@NonNull Context context, int resource, @NonNull ArrayList<Post> objects) {
        super(context, resource, objects);
        this.context = context;
        posts = objects;
        allRef = FirebaseDatabase.getInstance().getReference().child("all posts");  // reference used to get public vote count

        Login currLogin = new Login(getContext().getApplicationContext());
        String emailKey = currLogin.getEmail().replace(".", CreateFragment.PERIOD_REPLACEMENT_KEY);
        mRef = FirebaseDatabase.getInstance().getReference().child(emailKey);       // reference used to get private vote count
    }

    /**
     * Load a summary of info for each post including title, date posted, and public/private votes
     * total count
     */
    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        final Post post = posts.get(posts.size()-1-position);       // Display most recent posts at the top
        allRef = FirebaseDatabase.getInstance().getReference("all posts");

        convertView = LayoutInflater.from(context).inflate(R.layout.post_entry, parent, false);

        setupViewRefs(convertView, post);

        String titleText= post.getTitle();

        if(titleText.length() > 15){            // If the title is too long, don't let it overflow in the view
            String newTitleText = titleText.substring(0, 16) + "...";
            title.setText(newTitleText);
        }
        else {
            title.setText(post.getTitle());
        }
        getVoteCounts((post.getId() + ""), convertView);            //set up the private and public votes to be displayed
        final View finalConvertView = convertView;

        setupPastPostClick(post, convertView, finalConvertView);        //set up the onClickListener for opening the post

        return convertView;
    }

    /**
     * Set up the references to everything relevant for change in the view and some default values
     */
    private void setupViewRefs(View convertView, Post post){
        title = convertView.findViewById(R.id.entry_title);
        time = convertView.findViewById(R.id.entry_time);
        publicVotes = convertView.findViewById(R.id.entry_public_votes);
        privateVotes = convertView.findViewById(R.id.entry_private_votes);

        time.setText(post.getDatetime());

        // Default public and private vote counts are 0, update values async
        publicVotes.setText("Public Votes: 0");
        privateVotes.setText("Private Votes: 0");
    }

    /**
     * If one of the posts are clicked in the adapter, start the PastPostActivity to see all details
     * at time of uploaded
     */
    private void setupPastPostClick(final Post post, View convertView, final View finalConvertView){
        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getContext(), PastPostActivity.class);
                intent.putExtra(POST_ID_KEY, post.getId());     //pass the Id of the post so it can be accessed in PastPostActivity
                finalConvertView.getContext().startActivity(intent);
            }
        });
    }

    /**
     * Access references to get number of public and private votes to display in relevant post
     * entry of adapter
     */
    private void getVoteCounts(String postId, final View view){
        setupAllPostsRef(postId, view);
        setupMyPostRef(postId, view);
    }

    /**
     * Access database subsection to get total public votes count for a post, update view accordingly
     */
    private void setupAllPostsRef(String postId, final View view){
        allRef.child(postId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(view != null) {
                    // Check whether any public votes for images has been cast
                    if(dataSnapshot.hasChild("vote counts")){
                        int publicVotesCount = 0;
                        if(dataSnapshot.child("vote counts").hasChildren()) {
                            // Iterate through vote count for all images and add them to one value to track total public vote count
                            for (DataSnapshot voteSnapshot : dataSnapshot.child("vote counts").getChildren()) {
                                publicVotesCount += Integer.parseInt((String) voteSnapshot.getValue());
                            }
                        }
                        // Update current element of view adapter with this public vote count
                        ((TextView)view.findViewById(R.id.entry_public_votes)).setText("Public Votes: " + publicVotesCount);
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("DatabaseError", "Error getting vote values from dB");
            }
        });
    }

    /**
     * Access database subsection to get total private votes count for a post, update view accordingly
     */
    private void setupMyPostRef(String postId, final View view){
        mRef.child(postId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(view != null) {
                    // Check whether any private votes for images has been cast
                    if(dataSnapshot.hasChild("vote counts")){
                        int privateVoteCounts = 0;
                        if(dataSnapshot.child("vote counts").hasChildren()) {
                            // Iterate through vote count for all images and add them to one value to track total private vote count
                            for (DataSnapshot voteSnapshot : dataSnapshot.child("vote counts").getChildren()) {
                                privateVoteCounts += Integer.parseInt((String) voteSnapshot.getValue());
                            }
                        }
                        // Update current element of view adapter with this private vote count
                        ((TextView)view.findViewById(R.id.entry_private_votes)).setText("Private Votes: " + privateVoteCounts);
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) { }
        });
    }
}
