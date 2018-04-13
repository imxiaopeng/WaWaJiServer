package com.zego.zegowawaji_server;

import android.Manifest;
import android.app.Application;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.widget.Toast;

import com.zego.base.utils.AppLogger;
import com.zego.base.utils.BuglyUtil;
import com.zego.base.utils.DeviceIdUtil;
import com.zego.base.utils.OSUtils;
import com.zego.base.utils.PkgUtil;
import com.zego.base.utils.PrefUtil;
import com.zego.zegoliveroom.ZegoLiveRoom;
import com.zego.zegoliveroom.constants.ZegoAvConfig;
import com.zego.zegoliveroom.constants.ZegoConstants;
import com.zego.zegowawaji_server.entity.GameConfig;
import com.zego.zegowawaji_server.service.GuardService;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * <p>Copyright © 2017 Zego. All rights reserved.</p>
 *
 * @author realuei on 30/10/2017.
 */

public class ZegoApplication extends Application {
    private final int REQUEST_PERMISSION_CODE = 222;
    static private ZegoApplication sInstance;
    private final static String CONFIG_FILE_NAME = "COM_ZEGO_ZEGOWAWAJI_CONFIG.txt";
    private long mAppId;
    private byte[] mSignKey;
    private boolean mIsUseTestEnv;
    private byte[] mServerSecret;
    private String mCompanyName;
    private GameConfig mDefaultGameConfig;

    private ZegoLiveRoom mZegoLiveRoom;

    static public ZegoApplication getAppContext() {
        return sInstance;
    }

    public ZegoLiveRoom getZegoLiveRoom() {
        return mZegoLiveRoom;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        sInstance = this;

        boolean isMainProcess = OSUtils.isMainProcess(this);
        AppLogger.getInstance().writeLog("******* Application (%s) onCreate *******", (isMainProcess ? "main" : "guard"));

        if (isMainProcess) {    // 仅在主进程中才初始化
            String[] appVersion = PkgUtil.getAppVersion(this);
            AppLogger.getInstance().writeLog("=== current app versionName: %s; versionCode: %s ===", appVersion[0], appVersion[1]);

            BuglyUtil.initCrashReport(this, true, ZegoLiveRoom.version(), ZegoLiveRoom.version2());

            boolean success = loadActivateConfig();
            if (success) {
                startGuardService();

                initUserInfo();

                setupZegoSDK();
            } else {
                Toast.makeText(this, getString(R.string.zg_toast_load_config_failed), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startGuardService() {
        Intent intent = new Intent(this, GuardService.class);
        startService(intent);   // *必须*先使用 startService 确保 service 的生命周期不绑定至该 Context 的生命周期
    }

    private boolean loadActivateConfig() {
        //FIXME to developer, 此处只是演示如何加载 appId & signKey & public key，并没有考虑安全性，
        // 具体实现时需要考虑安全性，比如每次都从网络下载

        mAppId = KeyConstants.mAppId;
        mSignKey =KeyConstants.mSignKey;
        mIsUseTestEnv = KeyConstants.mIsUseTestEnv;
        mServerSecret = KeyConstants.mServerSecret;
        mCompanyName = KeyConstants.mCompanyName;
        mDefaultGameConfig = new GameConfig();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                //校验权限
                //从本地文件读取配置
//                loadConfigFromFile(mAppId, mSignKey, mIsUseTestEnv, mServerSecret);
            }
        } else {
            //从本地文件读取配置
//            loadConfigFromFile(mAppId, mSignKey, mIsUseTestEnv, mServerSecret);
        }
        return true;
    }

    /**
     * 从文件加载配置信息
     *
     * @param mAppId
     * @param mSignKey
     * @param mIsUseTestEnv
     * @param mServerSecret
     */
    private void loadConfigFromFile(long mAppId, byte[] mSignKey, boolean mIsUseTestEnv, byte[] mServerSecret) {
        File conFile = new File(Environment.getExternalStorageDirectory(), CONFIG_FILE_NAME);
        if (conFile.exists()) {
            //如果文件存在，从文件中读取配置信息
            try {
                BufferedReader bur = new BufferedReader(new FileReader(conFile));
                int line = 0;
                String lineStr = "";
                while ((lineStr = bur.readLine()) != null) {
                    switch (line) {
                        case 0://读取 mAppId
                            try {
                                mAppId = Long.parseLong(lineStr);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            break;
                        case 1://读取 mIsUseTestEnv
                            try {
                                mIsUseTestEnv = Boolean.parseBoolean(lineStr);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            break;
                        case 2://读取 mServerSecret
                            try {
                                mServerSecret = lineStr.getBytes();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            break;
                        case 3://读取 mSignKey
                            try {
                                String[] hexArray = lineStr.split(",");
                                for (int i = 0; i < hexArray.length; i++) {
                                    if (i < mSignKey.length) {
                                        mSignKey[i] = hexStringToBytes(hexArray[i]);
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            break;

                    }
                    line++;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void initUserInfo() {
        String userId = PrefUtil.getInstance().getUserId();
        String userName = PrefUtil.getInstance().getUserName();
        if (TextUtils.isEmpty(userId) || TextUtils.isEmpty(userName)) {
            String deviceId = DeviceIdUtil.generateDeviceId(this);
            userId = String.format("WWJS_%s", deviceId);
            userName = String.format("WWJS_%s_%s", Build.MODEL.replaceAll(",", "."), deviceId);

            PrefUtil.getInstance().setUserId(userId);
            PrefUtil.getInstance().setUserName(userName);
        }
    }

    private void setupZegoSDK() {
        ZegoLiveRoom.setSDKContext(new ZegoLiveRoom.SDKContext() {
            @Override
            public String getSoFullPath() {
                return null;
            }

            @Override
            public String getLogPath() {
                return null;
            }

            @Override
            public Application getAppContext() {
                return sInstance;
            }
        });

        String userId = PrefUtil.getInstance().getUserId();
        String userName = PrefUtil.getInstance().getUserName();
        AppLogger.getInstance().writeLog("set userId & userName with : %s, %s", userId, userName);

        ZegoLiveRoom.setUser(userId, userName);
        ZegoLiveRoom.requireHardwareEncoder(true);
        ZegoLiveRoom.requireHardwareDecoder(true);

        AppLogger.getInstance().writeLog("use test env ? %s", mIsUseTestEnv);
        ZegoLiveRoom.setTestEnv(mIsUseTestEnv);

        ZegoLiveRoom.setConfig("camera_orientation_mode=90"); // 其中 0 度或者 180 度为横向采集；90 度或者 270 度为竖向采集。当竖向采集时，可以让摄像头距离娃娃机更近

        mZegoLiveRoom = new ZegoLiveRoom();

        initZegoSDK(mZegoLiveRoom);
    }

    private void initZegoSDK(ZegoLiveRoom liveRoom) {
        boolean success = liveRoom.initSDK(mAppId, mSignKey);
        if (!success) {
            AppLogger.getInstance().writeLog("Init ZegoLiveRoom SDK failed");
            Toast.makeText(sInstance, "", Toast.LENGTH_LONG).show();
            return;
        }

        // 推荐使用如下参数配置推流以达到最佳均衡效果
        // 采集分辨率：720 * 1280
        // 编码分辨率：480 * 640
        // 推流码率：600 * 1000 bps （在合适范围内，码率对视频效果基本无影响）
        int resolutionLevel;
        ZegoAvConfig config;
        int level = PrefUtil.getInstance().getLiveQuality();
        if (level < 0) {
            // 默认设置级别为"标准"
            resolutionLevel = ZegoAvConfig.Level.Generic;

            config = new ZegoAvConfig(resolutionLevel);

            // 保存默认设置
            PrefUtil.getInstance().setLiveQuality(resolutionLevel);
            PrefUtil.getInstance().setLiveQualityResolution(resolutionLevel);
            PrefUtil.getInstance().setLiveQualityFps(15);
            PrefUtil.getInstance().setLiveQualityBitrate(600 * 1000);
        } else if (level > ZegoAvConfig.Level.SuperHigh) {
            resolutionLevel = PrefUtil.getInstance().getLiveQualityResolution();

            config = new ZegoAvConfig(ZegoAvConfig.Level.High);
            config.setVideoBitrate(PrefUtil.getInstance().getLiveQualityBitrate());
            config.setVideoFPS(PrefUtil.getInstance().getLiveQualityFps());
        } else {
            resolutionLevel = level;
            config = new ZegoAvConfig(level);
        }

        String resolutionText = getResources().getStringArray(R.array.zg_resolutions)[resolutionLevel];
        String[] strWidthHeight = resolutionText.split("x");

        int width = Integer.parseInt(strWidthHeight[0].trim());
        int height = Integer.parseInt(strWidthHeight[1].trim());

        // 默认使用 720 * 1280 采集分辨率以达到最大可视角度及最佳推流质量(再高的采集分辨率，目前所使用的摄像头不支持)
        if (width < height) {
            config.setVideoCaptureResolution(720, 1280);
        } else {
            config.setVideoCaptureResolution(1280, 720);
        }
        config.setVideoEncodeResolution(width, height);

        liveRoom.setAVConfig(config);
        liveRoom.setAVConfig(config, ZegoConstants.PublishChannelIndex.AUX);
    }

    public byte[] getServerSecret() {
        return mServerSecret;
    }

    public String getCompanyName() {
        return mCompanyName;
    }

    public GameConfig getDefaultGameConfig() {
        return mDefaultGameConfig;
    }


    private byte hexStringToBytes(String hexString) {
        if (hexString == null || hexString.equals("")) {
            return -1;
        }
        hexString = hexString.toUpperCase();
        char[] hexChars = hexString.toCharArray();
        byte d;
        d = (byte) (charToByte(hexChars[0]) << 4 | charToByte(hexChars[1]));
        return d;
    }

    private byte charToByte(char c) {
        return (byte) "0123456789ABCDEF".indexOf(c);
    }
}
