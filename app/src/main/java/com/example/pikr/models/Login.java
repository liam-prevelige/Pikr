package com.example.pikr.models;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Store information related to one individual's Login/Profile
 *
 * Load information to SharedPreferences based on inputs in Firebase db
 * Use SharedPreference so information can be accessed across activities and app sessions
 */
public class Login {
    public static final CharSequence PERIOD_REPLACEMENT_KEY = "hgiasdvohekljh91-76";
    private SharedPreferences profile;
    private DatabaseReference databaseReference;
    private String name;
    private String gender;
    private String phone;

    /**
     * Called everywhere except LoginActvity, gets information from application sharedpreferences
     */
    public Login(Context applicationContext){
        profile = PreferenceManager.getDefaultSharedPreferences(applicationContext);
    }

    /**
     * Only called in LoginActivity so user information can be added into sharedpreferences
     * from db and accessed throughout app w/out reloading each time
     */
    public Login(Context applicationContext, String email){
        profile = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        profile.edit().putString("email", email).apply();

        FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
        String emailKey = email.replace(".", PERIOD_REPLACEMENT_KEY);
        databaseReference = firebaseDatabase.getReference().child(emailKey);
        setDefaultStringVals();
        setupDatabaseListener();
    }

    private void setDefaultStringVals(){
        name = "";
        gender = "";
        phone = "";
    }

    /**
     * Get user-related information from database upon login
     */
    private void setupDatabaseListener() {
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.child("name").exists()) {
                    name = (String) dataSnapshot.child("name").getValue();
                    profile.edit().putString("name", name).apply();
                }
                if (dataSnapshot.child("gender").exists()) {
                    gender = (String) dataSnapshot.child("gender").getValue();
                    profile.edit().putString("gender", gender).apply();
                }
                if (dataSnapshot.child("phone").exists()) {
                    phone = (String) dataSnapshot.child("phone").getValue();
                    profile.edit().putString("phone", phone).apply();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("DatabaseError", "Error loading profile");
            }
        });
    }

    /**
     * Always return values when getters called from SharedPreferences so dB isn't reloaded
     */


    public String getName(){
        return profile.getString("name", "empty");
    }

    public String getGender(){
        return profile.getString("gender", "empty");
    }

    public String getEmail(){
        return profile.getString("email", "empty");
    }

    public String getPhone(){
        return profile.getString("phone", "empty");
    }
}
