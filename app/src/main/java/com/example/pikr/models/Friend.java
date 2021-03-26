package com.example.pikr.models;

/**
 * Object representing friends of user and relevant information
 */
public class Friend {
    private String name;
    private String profileUrl;

    public Friend(){
        name = "";
        profileUrl = "";
    }

    public Friend(String name){
        this.name = name;
        profileUrl = "";
    }

    public Friend(String name, String profileUrl){
        this.name = name;
        this.profileUrl = profileUrl;
    }

    public String getProfileUrl() {
        return profileUrl;
    }

    public void setProfileUrl(String profileUrl) {
        this.profileUrl = profileUrl;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
