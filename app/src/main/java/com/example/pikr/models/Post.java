package com.example.pikr.models;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;

/**
 * Post object - holds information for a post that the user is trying to upload to feed to get
 * votes on
 */
public class Post implements Parcelable {
    private String title;
    private String description;
    private String datetime;
    private ArrayList<String> pictures;
    private int id;
    private boolean deleted;
    private String name;
    private Bitmap coverBitmap;
    private String email;

    public Post(){
        deleted = false;    // By default deleted value is false, once true remove from Feed/MyActivity
    }

    public Post(String title, String description, String datetime){
        this.title = title;
        this.description = description;
        this.datetime = datetime;
        deleted = false;
    }

    /**
     * Necessary method for Parcelable implementation
     */
    protected Post(Parcel in) {
        title = in.readString();
        description = in.readString();
        datetime = in.readString();
        pictures = in.createStringArrayList();
        id = in.readInt();
        deleted = in.readByte() != 0;
        name = in.readString();
        coverBitmap = in.readParcelable(Bitmap.class.getClassLoader());
    }

    /**
     * Necessary method for Parcelable implementation
     */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(title);
        dest.writeString(description);
        dest.writeString(datetime);
        dest.writeStringList(pictures);
        dest.writeInt(id);
        dest.writeByte((byte) (deleted ? 1 : 0));
        dest.writeString(name);
        dest.writeParcelable(coverBitmap, flags);
    }

    /**
     * Necessary method for Parcelable implementation
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Necessary method for Parcelable implementation
     */
    public static final Creator<Post> CREATOR = new Creator<Post>() {
        @Override
        public Post createFromParcel(Parcel in) {
            return new Post(in);
        }

        @Override
        public Post[] newArray(int size) {
            return new Post[size];
        }
    };

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDatetime() {
        return datetime;
    }

    public void setDatetime(String datetime) {
        this.datetime = datetime;
    }

    public ArrayList<String> getPictures() {
        if(pictures!= null)
            return pictures;
        else
            return new ArrayList<>();
    }

    public void setPictures(ArrayList<String> pictures) {
        this.pictures = pictures;
    }

    public void addPicture(String uri){
        if(pictures==null) pictures = new ArrayList<String>();
        pictures.add(uri);
    }

    public boolean getDeleted(){
        return deleted;
    }

    public int getId(){
        return id;
    }

    public void setId(int newId){
        id = newId;
    }

    public void setName(String name){
        this.name = name;
    }

    public String getName(){
        return name;
    }

    public String getEmail(){
        return email;
    }

    public void setEmail(String email){
        this.email = email;
    }
}
