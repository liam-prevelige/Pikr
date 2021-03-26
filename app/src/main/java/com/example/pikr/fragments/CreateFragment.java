package com.example.pikr.fragments;

import android.Manifest;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.pikr.BuildConfig;
import com.example.pikr.R;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabel;
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabeler;
import com.google.firebase.ml.vision.label.FirebaseVisionOnDeviceImageLabelerOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.soundcloud.android.crop.Crop;
import com.example.pikr.models.Login;
import com.example.pikr.models.Post;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Allow a user to create their own post and upload to the database for display in public/private feed
 * and MyActivityFragment
 */
public class CreateFragment extends Fragment {
    public static final CharSequence PERIOD_REPLACEMENT_KEY = "hgiasdvohekljh91-76";
    private static final int MAX_PHOTOS = 5;

    public String emailKey;
    private static final int RESULT_OK = -1;
    private static final int PHOTO_FROM_CAMERA_CODE = 0;
    private static final int PHOTO_FROM_GALLERY_CODE = 1;
    private Login currLogin;
    private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 2;
    private static final int GALLERY_IMAGE_ACTIVITY_REQUEST_CODE = 3;
    private TextView mTitle, mDescription;
    private Button mPostButton, mPhotoButton, mDeleteButton;
    private Bitmap correctedBitmap;
    private ImageView mImage;
    private File mFile;
    private Uri mUri;
    private LinearLayout mLinearLayout;
    private Post newPost;
    private DatabaseReference mRef;
    private DatabaseReference allPostsRef;
    private ArrayList<View> photoViews;
    private int allPostsIndex;
    private FirebaseVisionOnDeviceImageLabelerOptions imageLabelOptions;

    private StorageReference mStorageRef;
    private ArrayList<Uri> mUriList;
    private FragmentActivity activity;

    public CreateFragment() {
        // Required public constructor
    }

    /**
     * Initialize view and get references to necessary subsections of database
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currLogin = new Login(getContext().getApplicationContext());

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        emailKey = currLogin.getEmail().replace(".", PERIOD_REPLACEMENT_KEY);       // Periods aren't allowed as keys in dB, update with unique key
        mRef = database.getReference(emailKey);     // Reference for private feed
        allPostsRef = database.getReference("all posts");   // Reference for public feed
        activity = getActivity();
    }

    public CreateFragment newInstance() {
        return new CreateFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        checkPermissions();     // Ensure permissions for accessing photos/taking photos are enabled
        // Use on-device ML to label images and store identification to dB if confidence high enough
        imageLabelOptions = new FirebaseVisionOnDeviceImageLabelerOptions.Builder().setConfidenceThreshold(0.8f).build();
        mUriList = new ArrayList<>();   // List of references to selected images
        mStorageRef = FirebaseStorage.getInstance().getReference("Images");
        currLogin = new Login(getContext().getApplicationContext());    // Current login info will be stored to database upon upload
        newPost = new Post();
        return inflater.inflate(R.layout.fragment_entry, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupViewReferences(view);      // Get references to relevant elements of view
        photoViews = new ArrayList<>();

        setupDatabaseListener();    // Access dB to get index for current post being uploaded
        createPhotoButtonListener();    // Create click listener for user trying to upload photos
        createPublishPostListener();    // Create click listener for user trying to upload new post
        mLinearLayout = view.findViewById(R.id.photo_scroll);
    }

    /**
     * Get references to elements of view used to create Post object
     */
    private void setupViewReferences(View view){
        mTitle = view.findViewById(R.id.title_text);
        mDescription = view.findViewById(R.id.description_text);
        mPhotoButton = view.findViewById(R.id.add_photo_button);
        mPostButton = view.findViewById(R.id.save_post_button);
    }

    /**
     * Get reference to database to get a count of current posts in public feed and create index
     * of new post based off of this count
     */
    private void setupDatabaseListener(){
        allPostsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.getKey().equals("all posts")){
                    int postCount = 0;
                    // Iterate through all elements in the master list of votes and keep count of each for next index
                    for(DataSnapshot postSnapshot : dataSnapshot.getChildren()){
                        if(FeedFragment.isParsable(postSnapshot.getKey())){
                            postCount += 1;
                        }
                    }
                    allPostsIndex = postCount;
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("DatabaseError", "Failed to get image index to dB");
            }
        });
    }

    /**
     * Allow a user to add new images to their post upon click of related button
     */
    private void createPhotoButtonListener() {
        mPhotoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (photoViews.size()<MAX_PHOTOS) {
                    checkPermissions();                 //make sure permissions are set up
                    AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());        //create the dialog box for selecting pictures
                    builder.setTitle("Select Picture");
                    builder.setNeutralButton("Take from camera", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            onItemSelected(PHOTO_FROM_CAMERA_CODE);
                        }
                    });
                    builder.setPositiveButton("Choose from gallery", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            onItemSelected(PHOTO_FROM_GALLERY_CODE);
                        }
                    });
                    builder.show();
                }
                else
                    Toast.makeText(getContext(), "Maximum number of photos reached!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * When a user requests to upload a post, ensure all elements fit the format requirements, and push
     * the post values to the database and images to storage
     */
    private void createPublishPostListener(){
        mPostButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean complete = fillPostValues();            //make sure all the necessary values are filled in
                if(complete) {
                    newPost.setId(allPostsIndex);               //set the id of the post to be inserted into the db

                    mRef.child(String.valueOf(allPostsIndex)).setValue(newPost);        //add the post into the db for the user
                    if (mUri!=null) {
                        for (int i = 0; i<mUriList.size(); i++) {
                            fileUploader(allPostsIndex, mUriList.get(i), i);            //upload the file to get firebase storage
                        }
                    }
                    allPostsRef.child(String.valueOf(allPostsIndex)).setValue(newPost);    //add the post into the all posts section of the db
                    clearFragment();
                }
                else{
                    Toast.makeText(getContext(), "Please fill in all fields",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    /**
     * Displays text to user once submit button is clicked
     */
    private void clearFragment(){
        Toast.makeText(getContext(), "Post Uploading!", Toast.LENGTH_LONG).show();
    }

    /**
     * Create extension for file for storage int firebase using URI
     */
    private String getExtension(Uri uri){
        ContentResolver contentResolver = getActivity().getContentResolver();
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        return mimeTypeMap.getExtensionFromMimeType(contentResolver.getType(uri));
    }

    /**
     * Helper method for uploading images into firebase storage and dB
     */
    private void fileUploader(final int index, final Uri uri, final int i){
        final StorageReference reference = mStorageRef.child(System.currentTimeMillis()+"."+getExtension(uri));     //setup the reference
        reference.putFile(uri)
                .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        addURIReferenceToDB(reference, index, i);                   //get the url referencing the image in storage and store it in the db
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        Log.e("Failure", "Failed to load image to dB");
                    }
                });
    }

    /**
     * Use the Uri referencing user-selected image to upload the image to FirebaseStorage and update
     * the dB with a URL to the image for later loading
     */
    private void addURIReferenceToDB(StorageReference reference, final int index, final int i){
        reference.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
            @Override
            public void onSuccess(Uri uri) {
                // Once image has been uploaded to storage, add the corresponding info to firebase dB
                String imageReference = uri.toString();
                if(newPost == null) newPost = new Post();
                if (newPost.getPictures()==null) newPost.setPictures(new ArrayList<String>());
                loadImageToRecognition(newPost.getId(), i, imageReference);

                newPost.addPicture(imageReference);                 //add the picture url into the post
                mRef.child(String.valueOf(index)).child("photos").child("Uri"+i).setValue(imageReference);          //add the url into the db for the user and all posts
                allPostsRef.child(String.valueOf(index)).child("photos").child("Uri"+i).setValue(imageReference)
                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void aVoid) {
                                activity.getSupportFragmentManager().beginTransaction()
                                        .replace(R.id.fragment_container, new MyActivityFragment()).commit();
                            }
                        });
            }
        });
    }

    /**
     * Start an async process for getting the identification of an image using Firebase on-device ML,
     * and upload this value into dB
     *
     * Can be used later to create recommendation system that promotes user engagement
     */
    private void loadImageToRecognition(int postId, int imageCount, String imageURL){
        try {
            new DownloadImageTask(postId, imageCount, imageURL).execute();
        }
        catch (Exception e) {
            Log.e("Exception", "Error setting cover photo");
            e.printStackTrace();
        }
    }

    /**
     * Ensure the user has given permissions necessary for using the camera
     */
    private void checkPermissions() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA}, 0);
        }
    }

    /**
     * Determine whether user has allowed permissions, and handle outcome accordingly
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
        } else if (grantResults[0] == PackageManager.PERMISSION_DENIED || grantResults[1] == PackageManager.PERMISSION_DENIED) {
            AlertDialog.Builder alert = new AlertDialog.Builder(requireContext());              // Create dialog if user didn't grant permissions
            alert.setTitle("Important Permissions");
            alert.setMessage("These permissions are required for certain functions of the app");
            alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    checkPermissions();
                }
            });
            alert.show();
        }
    }

    /**
     * Update post object being uploaded into dB with user-input information
     */
    private boolean fillPostValues() {
        boolean complete = allValuesFilledOut();            //make sure all necessary fields have been filled out

        newPost.setTitle(mTitle.getText().toString());
        newPost.setDescription(mDescription.getText().toString());
        newPost.setName(currLogin.getName());
        newPost.setDatetime(getFormattedDateTime());
        newPost.setEmail(currLogin.getEmail());
        return complete;
    }

    /**
     * Ensure all values necessary for creating a properly-formatted post have been filled out
     */
    private boolean allValuesFilledOut() {
        if (getView() != null) {
            return !mTitle.getText().toString().trim().equals("")                  //must have a title
                    && !mDescription.getText().toString().trim().equals("")        //must have a description
                    && photoViews.size() > 1;                                      //must have at least 2 photos
        }
        return false;
    }

    /**
     * Format date and time of upload for later display in MyActivityFragment
     */
    private String getFormattedDateTime(){
        Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        int day = c.get(Calendar.DAY_OF_MONTH);

        SimpleDateFormat month_date = new SimpleDateFormat("MMM");
        String month_name = month_date.format(c.getTime());

        return month_name + " " + day + ", " + year;
    }

    /**
     * Manage process of upload/capturing photo based on the option the user has selected
     */
    private void onItemSelected(int code) {
        Intent intent;
        switch (code) {
            case PHOTO_FROM_CAMERA_CODE:
                intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);    //implicit intent for taking a picture
                try {
                    mFile = createImageFile();                      //create the file
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (mFile != null) {
                    Uri uri = FileProvider.getUriForFile(requireContext(), BuildConfig.APPLICATION_ID, mFile);      //setup uri
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
                }
                startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);        //take the picture and get the result
                break;
            case PHOTO_FROM_GALLERY_CODE:
                try {
                    mFile = createImageFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent, GALLERY_IMAGE_ACTIVITY_REQUEST_CODE);
                break;
        }
    }

    /**
     * Manage the image incoming and handle differently based on from where it's being returned
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        assert data != null;
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE:
                    Bitmap cameraBitmap = imageOrientationValidator(mFile);     // Ensure image is upright
                    setImageDetails(cameraBitmap);
                    break;
                case GALLERY_IMAGE_ACTIVITY_REQUEST_CODE:
                    Uri selectedImage = data.getData();
                    try {
                        Bitmap galleryBitmap = MediaStore.Images.Media.getBitmap(getActivity().getContentResolver(), selectedImage);
                        setImageDetails(galleryBitmap);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case Crop.REQUEST_CROP:
                    handleCrop(resultCode, data);   // Allow user to crop image for display
                    break;
            }
        }
    }

    /**
     * Ensure the image uploaded is the correct orientation for display and uploading
     */
    private Bitmap imageOrientationValidator(File photoFile) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(requireActivity().getContentResolver(), FileProvider.getUriForFile(requireActivity(),
                    BuildConfig.APPLICATION_ID,
                    photoFile));
            ExifInterface control = new ExifInterface(photoFile.getAbsolutePath());
            int orientation = control.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
            correctedBitmap = bitmap;
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    correctedBitmap = rotate(bitmap, 90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    correctedBitmap = rotate(bitmap, 180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    correctedBitmap = rotate(bitmap, 270);
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return correctedBitmap;
    }

    /**
     * Helper method for rotating Bitmap at a given angel
     */
    private Bitmap rotate(Bitmap bm, int angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);
    }

    /**
     * Get the details of the image location and crop
     */
    private void setImageDetails(Bitmap bm) {
        try {
            FileOutputStream fOut = new FileOutputStream(mFile);
            bm.compress(Bitmap.CompressFormat.JPEG, 100, fOut);
            fOut.flush();
            fOut.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mUri = FileProvider.getUriForFile(requireActivity(), BuildConfig.APPLICATION_ID, mFile);
        mUriList.add(mUri);
        beginCrop(mUri);
    }

    /**
     * Call the crop of the image, which will have a result that needs to be managed
     */
    private void beginCrop(Uri source) {
        if (mFile != null) {
            Uri destination = FileProvider.getUriForFile(requireActivity(), BuildConfig.APPLICATION_ID, mFile);
            Crop.of(source, destination).asSquare().start(requireActivity(), this);
        }
    }

    /**
     * Handle whatever the crop activity sends back and set the UI accordingly
     */
    private void handleCrop(int resultCode, Intent result) {
        if (resultCode != RESULT_OK)
            return;
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getActivity().getContentResolver(), Crop.getOutput(result));
            int scale = (int) getResources().getDimension(R.dimen.image_length);
            LayoutInflater inflater = LayoutInflater.from(getContext());
            View scrollView = inflater.inflate(R.layout.photo_item, mLinearLayout, false);
            mImage = scrollView.findViewById(R.id.imageView);
            mImage.setImageBitmap(Bitmap.createScaledBitmap(bitmap, scale, scale, true));
            mDeleteButton = scrollView.findViewById(R.id.photo_delete_button);
            setupDelete(scrollView, mUri);      // Allow the user to delete image from the listview
            photoViews.add(scrollView);
            mLinearLayout.addView(scrollView);              //add references to everything and add the view to the layout (displayed in UI)
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Create the file with unique formatting
     */
    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = "JPEG_" + timeStamp + "_";
        File storageDirectory = getActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(fileName, ".jpg", storageDirectory);
    }

    /**
     * Allow pictures to be deleted from the ListView
     */
    private void setupDelete(final View view, final Uri uri){
        mDeleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder alert = new AlertDialog.Builder(requireContext());
                alert.setTitle("Deletion Alert");
                alert.setMessage("Are you sure you want to delete this image?");
                alert.setPositiveButton("Yes, Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mLinearLayout.removeView(view);         //remove the pictures from the view
                        photoViews.remove(view);                //remove the tracked images
                        mUriList.remove(uri);                   //removed the associated uri
                    }
                });
                alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
                alert.show();
            }
        });
    }

    /**
     * Async task to conduct image recognition and upload the result to the dB in the corresponding
     * post value
     */
    private class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
        String url;
        int postId;
        int imageCount;

        /**
         * Constructor stores the postId to reference post in index, and have a separate image
         * recognition value for each image uploaded (stored using imageCount)
         */
        public DownloadImageTask(int postId, int imageCount, String url) {
            this.url = url;
            this.postId = postId;
            this.imageCount = imageCount;
        }

        /**
         * Load the image from the URL in storage
         */
        protected Bitmap doInBackground(String... urls) {
            String imageURL = url;
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
         * Once image is loaded from URL, conduct the image recognition process
         */
        protected void onPostExecute(Bitmap result) {
             conductImageRecognition(result);
        }

        /**
         * User the on-device firebase image labeler to update the item in the image in both the public
         * and private feed representation of post
         *
         * Future updates can have this value change feed based on posts user often interacts with
         */
        private void conductImageRecognition(Bitmap currPhoto){
            FirebaseVisionImageLabeler labeler = FirebaseVision.getInstance().getOnDeviceImageLabeler();
            FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(currPhoto);

            labeler.processImage(image)
                    .addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionImageLabel>>() {
                        @Override
                        public void onSuccess(List<FirebaseVisionImageLabel> labels) {
                            // Once image labeler has successfully created a list, get the most likely label
                            FirebaseVisionImageLabel label = labels.get(0);
                            FirebaseDatabase database = FirebaseDatabase.getInstance();

                            // Load references to post in both public and private feed
                            DatabaseReference mPhotoRef = database.getReference(emailKey).child(postId + "").child("image recognition");
                            DatabaseReference allPhotoRef = database.getReference("all posts").child(postId + "").child("image recognition");

                            // Only add the suggested label if confidence for the post is above 65%, otherwise unreliable so put "unknown"
                            if(label.getConfidence() > 65) {
                                mPhotoRef.child("Uri" + imageCount).setValue(label.getText());
                                allPhotoRef.child("Uri"+imageCount).setValue(label.getText());
                            }
                            else{
                                mPhotoRef.child("Uri"+imageCount).setValue("Unknown");
                                allPhotoRef.child("Uri"+imageCount).setValue("Unknown");
                            }
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.e("Exception", "Failed to label image");
                        }
                    });
        }
    }
}

