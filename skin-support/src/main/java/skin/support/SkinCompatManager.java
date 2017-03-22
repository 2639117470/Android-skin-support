package skin.support;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import skin.support.app.SkinLayoutInflater;
import skin.support.observe.SkinObservable;
import skin.support.utils.SkinConstants;
import skin.support.utils.SkinFileUtils;
import skin.support.utils.SkinLog;
import skin.support.utils.SkinCompatPref;
import skin.support.content.res.SkinCompatResources;

/**
 * Created by ximsfei on 17-1-10.
 */

public class SkinCompatManager extends SkinObservable {
    private static HashMap<Context, SkinCompatManager> sManagerMap = new HashMap<>();
//    private static volatile SkinCompatManager sInstance;
    private final Object mLock = new Object();
    private final Context mAppContext;
    private final SkinCompatResources mSkinCompatResources;
    private final SkinCompatPref mSkinCompatPref;
    private boolean mLoading = false;
    private List<SkinLayoutInflater> mInflaters = new ArrayList<>();
    private List<SkinLayoutInflater> mHookInflaters = new ArrayList<>();

    public interface SkinLoaderListener {
        void onStart();

        void onSuccess();

        void onFailed(String errMsg);
    }

//    public static SkinCompatManager init(Context context) {
//        if (sInstance == null) {
//            synchronized (SkinCompatManager.class) {
//                if (sInstance == null) {
//                    sInstance = new SkinCompatManager(context);
//                }
//            }
//        }
//        return sInstance;
//    }

    public static SkinCompatManager get(Context context) {
        Context applicationContext = context.getApplicationContext();
        SkinCompatManager manager = sManagerMap.get(applicationContext);
        if (manager == null) {
            synchronized (SkinCompatManager.class) {
                manager = sManagerMap.get(context.getApplicationContext());
                if (manager == null) {
                    manager = new SkinCompatManager(applicationContext);
                    sManagerMap.put(applicationContext, manager);
                }
            }
        }
        return manager;
    }

//    public static SkinCompatManager getInstance() {
//        return sInstance;
//    }

    private SkinCompatManager(Context context) {
        mAppContext = context.getApplicationContext();
//        SkinCompatPref.init(mAppContext);
//        SkinCompatResources.init(mAppContext);
        mSkinCompatResources = new SkinCompatResources(mAppContext);
        mSkinCompatPref = new SkinCompatPref(mAppContext);
    }

    public SkinCompatManager addInflater(SkinLayoutInflater inflater) {
        mInflaters.add(inflater);
        return this;
    }

    public List<SkinLayoutInflater> getInflaters() {
        return mInflaters;
    }

    public SkinCompatManager addHookInflater(SkinLayoutInflater inflater) {
        mHookInflaters.add(inflater);
        return this;
    }

    public List<SkinLayoutInflater> getHookInflaters() {
        return mHookInflaters;
    }

    public SkinCompatResources getRes() {
        return mSkinCompatResources;
    }

    public SkinCompatPref getPref() {
        return mSkinCompatPref;
    }

    public String getCurSkinName() {
        return mSkinCompatPref.getSkinName();
    }

    public void restoreDefaultTheme() {
        loadSkin("");
    }

    public AsyncTask loadSkin() {
        String skin = mSkinCompatPref.getSkinName();
        if (TextUtils.isEmpty(skin)) {
            return null;
        }
        return loadSkin(skin, null);
    }

    public AsyncTask loadSkin(SkinLoaderListener listener) {
        String skin = mSkinCompatPref.getSkinName();
        if (TextUtils.isEmpty(skin)) {
            return null;
        }
        return loadSkin(skin, listener);
    }

    public AsyncTask loadSkin(String skinName) {
        return loadSkin(skinName, null);
    }

    public AsyncTask loadSkin(String skinName, final SkinLoaderListener listener) {
        return new SkinLoadTask(listener).execute(skinName);
    }

    private class SkinLoadTask extends AsyncTask<String, Void, String> {
        private final SkinLoaderListener mListener;

        public SkinLoadTask(SkinLoaderListener listener) {
            mListener = listener;
        }

        protected void onPreExecute() {
            if (mListener != null) {
                mListener.onStart();
            }
        }

        @Override
        protected String doInBackground(String... params) {
            synchronized (mLock) {
                while (mLoading) {
                    try {
                        mLock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                mLoading = true;
            }
            try {
                if (params.length == 1) {
                    if (TextUtils.isEmpty(params[0])) {
                        mSkinCompatResources.setSkinResource(
                                mAppContext.getResources(), mAppContext.getPackageName());
                        return params[0];
                    }
                    SkinLog.d("skinPkgPath", params[0]);
                    String skinPkgPath = SkinFileUtils.getSkinDir(mAppContext) + File.separator + params[0];
                    // ToDo 方便调试, 每次点击都从assets中读取
//                    if (!isSkinExists(params[0])) {
                    copySkinFromAssets(params[0]);
                    if (!isSkinExists(params[0])) {
                        return null;
                    }
//                    }
                    String pkgName = initSkinPackageName(skinPkgPath);
                    Resources resources = initSkinResources(skinPkgPath);
                    if (resources != null && !TextUtils.isEmpty(pkgName)) {
                        mSkinCompatResources.setSkinResource(resources, pkgName);
                        return params[0];
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            mSkinCompatResources.setSkinResource(
                    mAppContext.getResources(), mAppContext.getPackageName());
            return null;
        }

        protected void onPostExecute(String skinName) {
            SkinLog.e("skinName = " + skinName);
            synchronized (mLock) {
                if (skinName != null) {
                    notifyUpdateSkin();
                    mSkinCompatPref.setSkinName(skinName).commitEditor();
                    if (mListener != null) mListener.onSuccess();
                } else {
                    mSkinCompatPref.setSkinName("").commitEditor();
                    if (mListener != null) mListener.onFailed("皮肤资源获取失败");
                }
                mLoading = false;
                mLock.notifyAll();
            }
        }
    }

    private String initSkinPackageName(String skinPkgPath) {
        PackageManager mPm = mAppContext.getPackageManager();
        PackageInfo info = mPm.getPackageArchiveInfo(skinPkgPath, PackageManager.GET_ACTIVITIES);
        return info.packageName;
    }

    @Nullable
    private Resources initSkinResources(String skinPkgPath) {
        try {
            AssetManager assetManager = AssetManager.class.newInstance();
            Method addAssetPath = assetManager.getClass().getMethod("addAssetPath", String.class);
            addAssetPath.invoke(assetManager, skinPkgPath);

            Resources superRes = mAppContext.getResources();
            return new Resources(assetManager, superRes.getDisplayMetrics(), superRes.getConfiguration());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private String copySkinFromAssets(String name) {
        String skinDir = SkinFileUtils.getSkinDir(mAppContext);
        String skinPath = skinDir + File.separator + name;
        try {
            InputStream is = mAppContext.getAssets().open(
                    SkinConstants.SKIN_DEPLOY_PATH
                            + File.separator
                            + name);
            File fileDir = new File(skinDir);
            if (!fileDir.exists()) {
                fileDir.mkdirs();
            }
            OutputStream os = new FileOutputStream(skinPath);
            int byteCount;
            byte[] bytes = new byte[1024];

            while ((byteCount = is.read(bytes)) != -1) {
                os.write(bytes, 0, byteCount);
            }
            os.close();
            is.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return skinPath;
    }

    public boolean isSkinExists(String skinName) {
        return !TextUtils.isEmpty(skinName)
                && new File(SkinFileUtils.getSkinDir(mAppContext) + File.separator + skinName).exists();
    }
}
