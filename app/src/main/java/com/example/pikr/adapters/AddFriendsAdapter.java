package com.example.pikr.adapters;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pikr.R;
import com.example.pikr.activities.AddFriendsActivity;
import com.example.pikr.activities.ManageFriendActivity;
import com.example.pikr.activities.PastPostActivity;
import com.example.pikr.models.Friend;
import com.example.pikr.models.Post;

import java.util.ArrayList;

/**
 * Adapter to display list of friends, list of received friend requests, or list of sent friend
 * requests
 */
public class AddFriendsAdapter extends ArrayAdapter<Friend> {
    public static final String FRIEND_NAME_KEY = "name";
    private ArrayList<Friend> friends;
    private Context context;

    public AddFriendsAdapter(@NonNull Context context, int resource, @NonNull ArrayList<Friend> objects) {
        super(context, resource, objects);
        friends = objects;
        this.context = context;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (friends.size()>0) {
            final Friend friend = friends.get(friends.size() - 1 - position);       //display by latest to oldest
            convertView = LayoutInflater.from(context).inflate(R.layout.my_friend, parent, false);
            TextView name = convertView.findViewById(R.id.friend_text);
            name.setText(friend.getName());                                         //set the email to be the "friend text"
            final View finalConvertView = convertView;
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {                                   //when the user is clicked, start the ManageFriendActivity
                    Intent intent = new Intent(getContext(), ManageFriendActivity.class);
                    intent.putExtra(FRIEND_NAME_KEY, friend.getName());              //pass the ID of the friend to be referenced in the db
                    finalConvertView.getContext().startActivity(intent);
                }
            });
            return convertView;
        }
        return super.getView(position, convertView, parent);
    }
}
