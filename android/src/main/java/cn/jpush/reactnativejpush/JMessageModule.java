package cn.jpush.reactnativejpush;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;


import java.io.File;
import java.io.FileNotFoundException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import cn.jpush.im.android.api.JMessageClient;
import cn.jpush.im.android.api.content.ImageContent;
import cn.jpush.im.android.api.content.MessageContent;
import cn.jpush.im.android.api.content.TextContent;
import cn.jpush.im.android.api.enums.ContentType;
import cn.jpush.im.android.api.enums.ConversationType;
import cn.jpush.im.android.api.event.MessageEvent;
import cn.jpush.im.android.api.model.Conversation;
import cn.jpush.im.android.api.event.NotificationClickEvent;
import cn.jpush.im.android.api.model.GroupInfo;
import cn.jpush.im.android.api.model.Message;
import cn.jpush.im.android.api.model.UserInfo;
import cn.jpush.im.api.BasicCallback;

public class JMessageModule extends ReactContextBaseJavaModule {

    private static String TAG = "JMessageModule";
    private Context mContext;
    private static String mEvent;
    private static Bundle mCachedBundle;
    private static ReactApplicationContext mRAC;

    private final static String RECEIVE_MESSAGE = "receiveMessage";

    private final static String NOTIFICATION_CLICK = "notificationClick";

    public JMessageModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public boolean canOverrideExistingModule() {
        return true;
    }

    @Override
    public String getName() {
        return "JMessageModule";
    }

    @Override
    public void initialize() {
        super.initialize();
    }

    @Override
    public void onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy();
    }
    /**
     * [初始化]
     */
    @ReactMethod
    public void initMessage() {
        mContext = getCurrentActivity();
        JMessageClient.init(getReactApplicationContext());
        JMessageClient.registerEventReceiver(this);
        // JCoreInterface.setDebugMode(JMessageModule.isDebug);
        Logger.toast(mContext, "Init message success");
        Logger.i(TAG, "init message Success!");
    }
    /**
     *注册
     * @param promise
     */
    @ReactMethod
    public void register(String userName, String password, final Promise promise) {
        mContext = getCurrentActivity();
        JMessageClient.register(userName, password, new BasicCallback() {
                        @Override
                        public void gotResult(int responseCode, String responseMessage) {
                          if (responseCode == 0) {
                            promise.resolve(0);
                          } else {
                            promise.reject("register fail");
                          }
                        }
                      });
    }
    /**
     *登录
     * @param promise
     */
    @ReactMethod
    public void login(String userName, String password, final Promise promise) {
        mContext = getCurrentActivity();
        JMessageClient.login(userName, password, new BasicCallback() {
                        @Override
                        public void gotResult(int responseCode, String responseMessage) {
                          Logger.i(TAG, responseMessage);
                          Log.d(TAG, responseMessage);
                          if (responseCode == 0) {
                            promise.resolve(responseCode);
                          } else {
                            promise.reject(String.valueOf(responseCode), responseMessage);
                          }
                        }
                      });
    }
    /**
     * 退出登录
     */
    @ReactMethod
    public void logout() {
        JMessageClient.logout();
    }
    /**
     * 用户是否登录
     * @param promise
     */
    @ReactMethod
    public void isLogIn(final Promise promise) {
        UserInfo info = JMessageClient.getMyInfo();
        System.out.println(info);
        boolean isLogIn = info != null && info.getUserID() != 0;
        promise.resolve(isLogIn);
    }
    /**
     * 接收消息事件
     * @param event
     */
    public void onEvent(MessageEvent event) {
      Message message = event.getMessage();
      mRAC = getReactApplicationContext();
      if (mRAC != null) {
          mRAC.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                  .emit(RECEIVE_MESSAGE, transformToWritableMap(message));
      }
    }
    /**
     * 接收点击事件
     * @param event
     */
    public void onEvent(NotificationClickEvent event) {
      Message message = event.getMessage();
      Context context = getCurrentActivity();
      mRAC = getReactApplicationContext();
      Logger.d(TAG, message.toString());
      if (isApplicationRunningBackground(context)) {
        Intent intent = new Intent();
        intent.setClassName(context.getPackageName(), context.getPackageName() + ".MainActivity");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
      }
      if (mRAC != null) {
          mRAC.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                  .emit(NOTIFICATION_CLICK, transformToWritableMap(message));
      }
    }

    private static boolean isApplicationRunningBackground(final Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
        if (!tasks.isEmpty()) {
            ComponentName topActivity = tasks.get(0).topActivity;
            if (!topActivity.getPackageName().equals(context.getPackageName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 发送单聊消息
     * @param username  用户名
     * @param type      消息类型
     * @param data      数据
     * @param promise
     */
    @ReactMethod
    public void sendSingleMessage(String username, String type, String data, final Promise promise) {
        Conversation conversation = Conversation.createSingleConversation(username);
        MessageContent content;
        sendMessage(conversation, type, data, promise);
    }

    private void sendMessage(Conversation conversation,
                             String type, String data,
                             final Promise promise) {
        MessageContent content;

        if (conversation == null) {
            JMessageException ex = JMessageException.CONVERSATION_INVALID;
            promise.reject(ex.getCode(), ex.getMessage());
            return;
        }
        switch (type) {
            case "text":
                content = new TextContent(data);
                break;
            // case "image":
            //     try {
                    // File imageFile = new File(data.getString("image"));
            //         content = new ImageContent(imageFile);
            //     } catch (FileNotFoundException e) {
            //         JMessageException ex = JMessageException.MESSAGE_CONTENT_NULL;
            //         promise.reject(ex.getCode(), ex.getMessage());
            //         return;
            //     }
            //     break;
            default:
                JMessageException ex = JMessageException.MESSAGE_CONTENT_TYPE_NOT_SUPPORT;
                promise.reject(ex.getCode(), ex.getMessage());
                return;
        }
        final Message message = conversation.createSendMessage(content);
        message.setOnSendCompleteCallback(new BasicCallback() {
            @Override
            public void gotResult(int responseCode, String responseDesc) {
                if (responseCode == 0) {
                    promise.resolve(transformToWritableMap(message));
                } else {
                    promise.reject(String.valueOf(responseCode), responseDesc);
                }
            }
        });
        JMessageClient.sendMessage(message);
    }

    private WritableMap transformToWritableMap(Message message) {
      WritableMap result = Arguments.createMap();
      if (message == null) return result;

      result.putString("msgId", Utils.defaultValue(message.getId(), "").toString());
      result.putString("serverMessageId", Utils.defaultValue(message.getServerMessageId(), "").toString());

      WritableMap from = Arguments.createMap();
      from.putString("type", message.getFromType());
      from.putString("name", message.getFromUser().getUserName());
      from.putString("nickname", message.getFromUser().getNickname());
      result.putMap("from", from);

      WritableMap target = Arguments.createMap();
      target.putInt("type", messagePropsToInt(message.getTargetType()));
      target.putString("typeDesc", messagePropsToString(message.getTargetType()));
      switch (message.getTargetType()) {
          case single:
              UserInfo userInfo = (UserInfo)message.getTargetInfo();
              target.putString("name", userInfo.getUserName());
              target.putString("nickname", userInfo.getNickname());
              break;
          case group:
              GroupInfo groupInfo = (GroupInfo)message.getTargetInfo();
              target.putString("name", groupInfo.getGroupName());
              target.putString("nickname", groupInfo.getGroupDescription());
              break;
          default:
              break;
      }
      result.putMap("target", target);
      result.putDouble("timestamp", message.getCreateTime());
      result.putInt("contentType", messagePropsToInt(message.getContentType()));
      result.putString("contentTypeDesc", messagePropsToString(message.getContentType()));
      result.putString("content", message.getContent().toJson());
      return result;
  }


  private String messagePropsToString(UserInfo.Gender gender) {
      if (gender == null) return null;
      switch (gender) {
          case unknown:
              return "Unknown";
          case male:
              return "Male";
          case female:
              return "Female";
          default:
              return null;
      }
  }

  private String messagePropsToString(ConversationType type) {
      if (type == null) return null;
      switch (type) {
          case single:
              return "Single";
          case group:
              return "Group";
          default:
              return null;
      }
  }

  private String messagePropsToString(ContentType type) {
      if (type == null) return null;
      switch (type) {
          case unknown:
              return "Unknown";
          case text:
              return "Text";
          case image:
              return "Image";
          case voice:
              return "Voice";
          case custom:
              return "Custom";
          case eventNotification:
              return "Event";
          case file:
              return "File";
          case location:
              return "Location";
          default:
              return null;
      }
  }

  private Integer messagePropsToInt(UserInfo.Gender gender) {
      if (gender == null) return null;
      switch (gender) {
          case unknown:
              return 0;
          case male:
              return 1;
          case female:
              return 2;
          default:
              return null;
      }
  }

  private Integer messagePropsToInt(ConversationType type) {
      if (type == null) return null;
      switch (type) {
          case single:
              return 1;
          case group:
              return 2;
          default:
              return null;
      }
  }

  private Integer messagePropsToInt(ContentType type) {
      if (type == null) return null;
      switch (type) {
          case unknown:
              return 0;
          case text:
              return 1;
          case image:
              return 2;
          case voice:
              return 3;
          case custom:
              return 4;
          case eventNotification:
              return 5;
          case file:
              return 6;
          case location:
              return 7;
          default:
              return null;
      }
  }
}
