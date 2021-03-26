package com.example.pikr.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
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
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Objects;

/**
 * Conduct profile registration and store corresponding information in firebase dB and authentication
 */
public class RegisterActivity extends AppCompatActivity {
    private static final int MIN_PASSWORD_LENGTH = 6;
    private EditText name, email, password, phone;
    private RadioButton isFemale, isMale;
    private Class changeToActivity;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        mAuth = FirebaseAuth.getInstance();
        changeToActivity = LoginActivity.class;     // Activity to be returned to - useful if implementing "edit profile"

        changeActionBar();
        registerButton();
    }

    /**
     *  Allow user to return back to login activity in action bar
     */
    private void changeActionBar(){
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.register_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * If a user chooses to register their profile, store info in dB if all values filled in
     * Otherwise return to Login
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.confirm_register) {
            handleRegistration();
        }
        else {
            if(changeToActivity.equals(LoginActivity.class)) finishAffinity();      // Clear activity stack if going back to Login
            startActivity(new Intent(this, changeToActivity));
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Create references to input fields
     */
    private void setProfileVariables(){
        name = findViewById(R.id.name);
        email = findViewById(R.id.email);
        password = findViewById(R.id.password);
        phone = findViewById(R.id.phone);
        isFemale = findViewById(R.id.is_female);
        isMale  = findViewById(R.id.is_male);
    }

    /**
     * Check whether required profile variables are empty or improperly formatted
     * If incorrect formatting, produce an error using helper method
     *
     * @return whether the text box inputs were all formatted correctly
     */
    private boolean someProfileVarsEmpty(){
        boolean invalid = false;
        if(name.getText().toString().equals("")){   // must have name
            requestError(name);
            invalid = true;
        }
        if(email.getText().toString().equals("")){  // must have email
            requestError(email);
            invalid = true;
        }
        // Use default Android method to determine whether the email was formatted properly
        if(!android.util.Patterns.EMAIL_ADDRESS.matcher(email.getText().toString()).matches()){
            email.setError("This email address is invalid");
            invalid = true;
        }
        if(password.getText().toString().equals("")){   // must have password
            requestError(password);
            invalid = true;
        }
        if(!(isMale.isChecked() || isFemale.isChecked())){  // must choose gender
            Toast.makeText(getApplicationContext(), "Gender is a required field", Toast.LENGTH_LONG).show();
            invalid = true;
        }
        return invalid;
    }

    /**
     * Helper method to provide an error for a required EditText component having an empty field
     */
    private void requestError(EditText errorProducer){
        errorProducer.setError("This field is required");
    }

    /**
     * If the register button is clicked, send info to dB
     */
    private void registerButton(){
        Button registerButton = findViewById(R.id.confirm_register);
        registerButton.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v) {
                handleRegistration();
            }
        });
    }

    /**
     * If all values properly formatted, add the information to firebase and return to Login
     */
    private void handleRegistration(){
        setProfileVariables();
        if(!someProfileVarsEmpty()){
            if(password.getText().toString().length() >= MIN_PASSWORD_LENGTH) {
                addProfileToFirebase();
                startActivity(new Intent(this, LoginActivity.class));
            }
            else{
                password.setError("Password must be at least six characters");
            }
        }
    }

    /**
     * Add information to firebase authentication and store login information to realtime dB
     */
    private void addProfileToFirebase(){
        // Add user as a valid input in authentication system
        mAuth.createUserWithEmailAndPassword(email.getText().toString(), password.getText().toString())
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        // Display message to user depending on whether addition of account was successful
                        if (task.isSuccessful()) {
                            Toast.makeText(RegisterActivity.this, "Successfully added account.",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(RegisterActivity.this, "Failed to add account, please try again",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });

        // Realtime dB doesn't allow periods in key, so replace with unique key
        String dBEmail = email.getText().toString().replace(".", Login.PERIOD_REPLACEMENT_KEY);

        // Create a reference based on email and add user information within
        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference().child(dBEmail);
        String gender = "";
        if(isMale.isChecked()) gender = "male";
        else gender = "female";
        databaseReference.child("gender").setValue(gender);
        databaseReference.child("name").setValue(name.getText().toString());
        databaseReference.child("phone").setValue(phone.getText().toString());
    }
}
