package com.example.pikr.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.pikr.R;
import com.example.pikr.models.Login;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Initial page of app, allow user to login with their registered email/password (or access
 * registration if not yet created)
 */
public class LoginActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("TEST", "onCreate()");
        super.onCreate(savedInstanceState);
        if(getActionBar()!=null) getActionBar().setTitle("Sign in");
        setContentView(R.layout.activity_login);
        signIn();       // setup and handle button click for signing in
        register();     // setup and handle button click for registering new acct
    }

    /**
     * Create listener for sign in button and check current login against those stored in firebase
     * authentication
     */
    private void signIn(){
        Button signInButton = findViewById(R.id.sign_in);
        signInButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                EditText emailEntry = findViewById(R.id.email);
                EditText passwordEntry = findViewById(R.id.password);
                if(noLoginInputErrors(emailEntry, passwordEntry)){
                    handleFirebaseSignIn();
                }
            }
        });
    }

    /**
     * Given an email and password, ensure everything has been entered correctly (value exists, correct
     * length and formatting). Produce an error otherwise
     */
    private boolean noLoginInputErrors(EditText emailEntry, EditText passwordEntry){
        boolean correctLoginInput = true;

        if(emailEntry.getText().toString().equals("")){     // Error if no email entered
            emailEntry.setError("This field is required");
            correctLoginInput = false;
        }
        // Use provided Android functionality to check whether the Email Address is properly formatted
        else if(!android.util.Patterns.EMAIL_ADDRESS.matcher(emailEntry.getText().toString()).matches()){
            emailEntry.setError("This email address is invalid");
            correctLoginInput = false;
        }
        if(passwordEntry.getText().toString().equals("")){      // Error if no password entered
            passwordEntry.setError("This field is required");
            correctLoginInput = false;
        }
        if(passwordEntry.getText().toString().length() < 7){    // Error if password incorrect length
            passwordEntry.setError("Password must be more than six characters");
            correctLoginInput = false;
        }
        return correctLoginInput;
    }

    /**
     * Check with Firebase authentication service whether or not an email exists
     *
     * If successful send to mainactivity, otherwise notify user of incorrect login
     */
    private void handleFirebaseSignIn(){
        final FirebaseAuth mAuth = FirebaseAuth.getInstance();  // Connect to firebase authentication

        // Get text entry for email/password
        EditText emailEntry = findViewById(R.id.email);
        EditText passwordEntry = findViewById(R.id.password);

        // Try signing in with current login using FirebaseAuthentication built in service
        mAuth.signInWithEmailAndPassword(emailEntry.getText().toString(), passwordEntry.getText().toString())
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // If successful load user's info from dB to shared preferences for later use
                            FirebaseUser user = mAuth.getCurrentUser();
                            String email = user.getEmail();
                            new Login(getApplicationContext(), email);
                            Toast.makeText(LoginActivity.this, "Success!", Toast.LENGTH_SHORT).show();

                            // Send user to main activity to access app features
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));

                        } else {
                            // If sign in fails, display a message to the user.
                            Toast.makeText(LoginActivity.this, "Sign in failed.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    /**
     * Setup registration button and send to registration activity if clicked
     */
    private void register(){
        Button registerButton = findViewById(R.id.register);
        registerButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            }
        });
    }
}
