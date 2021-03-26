package com.example.pikr.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.pikr.R;
import com.example.pikr.fragments.CreateFragment;
import com.example.pikr.fragments.FeedFragment;
import com.example.pikr.fragments.MyActivityFragment;

/**
 * Main page of app
 *
 * Allow user to navigate between three fragments: the feed, a means of uploading a new post, and a
 * display of past posts and general activity info
 */
public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Initialize feed fragment as default view
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new FeedFragment()).commit();
    }

    /**
     * Load custom menu in top activity bar
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }


    /**
     * Handle clicks in custom menu by navigating user to relevant area of app
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch(item.getItemId()) {
            case R.id.add_friends:
                startActivity(new Intent(this, AddFriendsActivity.class));
                return true;
            case R.id.sign_out:
                finishAffinity();   // Clear stack so user can't navigate back to Main from Login with back key
                startActivity(new Intent(this, LoginActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Callback when feed option pressed in bottom navigation (this callback is set up in custom menu)
     *
     * Start and display proper fragment
     */
    public void onClickFeed(MenuItem item){             //create a new fragment based on whatever the user clicks
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new FeedFragment()).commit();
    }

    /**
     * Callback when Create pressed in bottom navigation (this callback is set up in custom menu)
     *
     * Start and display proper fragment
     */
    public void onClickNewPost(MenuItem item){
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new CreateFragment()).commit();
    }

    /**
     * Callback when MyActivity pressed in bottom navigation (this callback is set up in custom menu)
     *
     * Start and display proper fragment
     */
    public void onClickMyActivity(MenuItem item) {
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new MyActivityFragment()).commit();
    }
}
