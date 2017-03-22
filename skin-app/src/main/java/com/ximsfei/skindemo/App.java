package com.ximsfei.skindemo;

import android.app.Application;

import skin.support.SkinCompatManager;
import skin.support.app.SkinCardViewInflater;
import skin.support.circleimageview.app.SkinCircleImageViewInflater;
import skin.support.design.app.SkinMaterialViewInflater;
import skin.support.flycotablayout.SkinFlycoTabLayoutManager;
import skin.support.flycotablayout.app.SkinFlycoTabLayoutInflater;

/**
 * Created by ximsfei on 2017/1/10.
 */

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
//        SkinCircleImageViewManager.init(this);
//        SkinMaterialManager.init(this);
//        SkinCardViewManager.init(this);
//        SkinFlycoTabLayoutManager.init(this);
//        SkinCompatManager.init(this).loadSkin();
        SkinCompatManager.get(this)
                .addInflater(new SkinMaterialViewInflater())    // material design
                .addInflater(new SkinCardViewInflater())        // CardView v7
                .addInflater(new SkinCircleImageViewInflater()) // hdodenhof/CircleImageView
                .addInflater(new SkinFlycoTabLayoutInflater())  // H07000223/FlycoTabLayout
                .loadSkin();
    }
}
