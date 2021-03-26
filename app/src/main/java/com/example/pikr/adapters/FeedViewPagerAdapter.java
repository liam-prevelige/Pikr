package com.example.pikr.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import com.example.pikr.fragments.PostFragment;

import java.util.ArrayList;

/**
 * Display a list of posts in feed as a set of fragments
 */
public class FeedViewPagerAdapter extends FragmentStatePagerAdapter {
    private ArrayList<PostFragment> postFragments = new ArrayList<>();
    private FragmentManager fm;

    public FeedViewPagerAdapter(@NonNull FragmentManager fm, int behavior) {
        super(fm, behavior);
        this.fm = fm;
        fm.getFragments().clear();
    }

    @NonNull
    @Override
    public Fragment getItem(int position) {
        return postFragments.get(position);
    }

    public void setPostFragment(ArrayList<PostFragment> postFragments){
        fm.getFragments().clear();                              //get rid of all the previous fragments
        this.postFragments = postFragments;
    }

    @Override
    public int getCount() {
        return postFragments.size();
    }
}
