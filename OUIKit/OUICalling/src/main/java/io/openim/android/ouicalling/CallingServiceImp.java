package io.openim.android.ouicalling;

import android.app.Dialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.alibaba.android.arouter.core.LogisticsCenter;
import com.alibaba.android.arouter.facade.Postcard;
import com.alibaba.android.arouter.facade.annotation.Route;
import com.alibaba.android.arouter.launcher.ARouter;
import com.hjq.permissions.Permission;

import java.util.ArrayList;
import java.util.List;

import io.openim.android.ouicalling.service.AudioVideoService;
import io.openim.android.ouicore.base.BaseApp;
import io.openim.android.ouicore.entity.CallHistory;
import io.openim.android.ouicore.im.IMUtil;
import io.openim.android.ouicore.services.CallingService;
import io.openim.android.ouicore.utils.ActivityManager;
import io.openim.android.ouicore.utils.BackgroundStartPermissions;
import io.openim.android.ouicore.utils.Common;
import io.openim.android.ouicore.utils.Constant;
import io.openim.android.ouicore.utils.HasPermissions;
import io.openim.android.ouicore.utils.L;
import io.openim.android.ouicore.utils.MediaPlayerUtil;
import io.openim.android.ouicore.utils.NotificationUtil;
import io.openim.android.ouicore.utils.Routes;
import io.openim.android.sdk.OpenIMClient;
import io.openim.android.sdk.enums.ConversationType;
import io.openim.android.sdk.listener.OnBase;
import io.openim.android.sdk.models.CustomSignalingInfo;
import io.openim.android.sdk.models.MeetingStreamEvent;
import io.openim.android.sdk.models.RoomCallingInfo;
import io.openim.android.sdk.models.SignalingInfo;
import io.openim.android.sdk.models.UserInfo;
import io.openim.keepalive.Alive;

@Route(path = Routes.Service.CALLING)
public class CallingServiceImp implements CallingService {
    private OnServicePriorLoginCallBack onServicePriorLoginCallBack;
    public static final String TAG = "CallingServiceImp";
    private Context context;
    public CallDialog callDialog;
    private SignalingInfo signalingInfo;
    public static final int A_NOTIFY_ID = 100;

    public void setSignalingInfo(SignalingInfo signalingInfo) {
        this.signalingInfo = signalingInfo;
    }

    @Override
    public void startAudioVideoService(Context base) {
//        Alive.restart(base);
    }

    @Override
    public void stopAudioVideoService(Context base) {
//        Alive.finishService(base);
    }

    @Override
    public void setOnServicePriorLoginCallBack(OnServicePriorLoginCallBack onServicePriorLoginCallBack) {
        this.onServicePriorLoginCallBack = onServicePriorLoginCallBack;
    }

    @Override
    public OnServicePriorLoginCallBack getOnServicePriorLoginCallBack() {
        return onServicePriorLoginCallBack;
    }

    @Override
    public void initKeepAlive(String precessName) {
        Alive.init(context, precessName, AudioVideoService.class);
    }


    @Override
    public void init(Context context) {
        this.context = context;
    }

    @Override
    public void onInvitationCancelled(SignalingInfo s) {
        L.e(TAG, "----onInvitationCancelled-----");
        cancelNotify();
        if (null == callDialog) return;
        callDialog.callingVM.renewalDB(callDialog.buildPrimaryKey(),
            (realm, callHistory) -> callHistory.setFailedState(1));
        callDialog.dismiss();
    }

    @Override
    public void onInvitationTimeout(SignalingInfo s) {
        L.e(TAG, "----onInvitationTimeout-----");
    }

    @Override
    public void onInviteeAccepted(SignalingInfo s) {
        L.e(TAG, "----onInviteeAccepted-----");
        if (null == callDialog) return;
        callDialog.otherSideAccepted();
        callDialog.callingVM.renewalDB(callDialog.buildPrimaryKey(),
            (realm, callHistory) -> callHistory.setSuccess(true));
    }

    @Override
    public void onInviteeAcceptedByOtherDevice(SignalingInfo s) {
        L.e(TAG, "----onInviteeAcceptedByOtherDevice-----");
    }

    @Override
    public void onInviteeRejected(SignalingInfo signalingInfo) {
        L.e(TAG, "----onInviteeRejected-----");
        if (null == callDialog) return;
        callDialog.callingVM.renewalDB(callDialog.buildPrimaryKey(), (realm, callHistory) -> {
            callHistory.setSuccess(false);
            callHistory.setFailedState(2);
        });
        callDialog.dismiss();

    }

    @Override
    public void onInviteeRejectedByOtherDevice(SignalingInfo s) {
        L.e(TAG, "----onInviteeRejectedByOtherDevice-----");
        cancelNotify();
    }


    @Override
    public void onReceiveNewInvitation(SignalingInfo signalingInfo) {
        L.e(TAG, "----onReceiveNewInvitation-----");
        if (callDialog != null) return;
        Common.wakeUp(context);
        setSignalingInfo(signalingInfo);

        boolean isSystemAlert =
            new HasPermissions(BaseApp.inst(), Permission.SYSTEM_ALERT_WINDOW).isAllGranted();
        Intent hangIntent;
        boolean backgroundStart=BackgroundStartPermissions.INSTANCE.isBackgroundStartAllowed(context);
        if (isSystemAlert && backgroundStart) {
            hangIntent =
                new Intent(context, LockPushActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(hangIntent);
        } else {
            if (BaseApp.inst().isAppBackground.val()){
                Postcard postcard = ARouter.getInstance().build(Routes.Main.HOME);
                LogisticsCenter.completion(postcard);
                hangIntent = new Intent(context, postcard.getDestination())
                    .putExtra(Constant.IS_CALL, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                MediaPlayerUtil.INSTANCE.initMedia(BaseApp.inst(), R.raw.incoming_call_ring);
                MediaPlayerUtil.INSTANCE.loopPlay();

                PendingIntent hangPendingIntent = PendingIntent.getActivity(context, 1002, hangIntent
                    , PendingIntent.FLAG_MUTABLE);

                Notification notification =
                    NotificationUtil.builder(NotificationUtil.CALL_CHANNEL_ID)
                        .setPriority(Notification.PRIORITY_MAX)
                        .setContentTitle("OpenIM")
                        .setContentText(context.getString(io.openim.android.ouicore.R.string.receive_call_invite))
                        .setAutoCancel(true).setOngoing(true).setContentIntent(hangPendingIntent).setCustomHeadsUpContentView(new RemoteViews(BaseApp.inst().getPackageName(), R.layout.layout_call_invite)).build();

                NotificationUtil.sendNotify(A_NOTIFY_ID, notification);
            }else {
              buildCallDialog(ActivityManager.getActivityStack().peek(), null,
                    false).show();
            }
        }
    }

    private void cancelNotify() {
        NotificationUtil.cancelNotify(A_NOTIFY_ID);
        if (BaseApp.inst().isAppBackground.val()) IMUtil.sendNotice(A_NOTIFY_ID);
        MediaPlayerUtil.INSTANCE.pause();
        MediaPlayerUtil.INSTANCE.release();
    }

    public Dialog buildCallDialog(Context context,
                                  DialogInterface.OnDismissListener dismissListener,
                                  boolean isCallOut) {
        try {
            if (callDialog != null) return callDialog;
            if (signalingInfo.getInvitation().getSessionType() != ConversationType.SINGLE_CHAT)
                callDialog = new GroupCallDialog(context, this, isCallOut);
            else callDialog = new CallDialog(context, this, isCallOut);
            callDialog.bindData(signalingInfo);
            if (!callDialog.callingVM.isCallOut) {
                callDialog.setOnDismissListener(dismissListener);
                if (!Common.isScreenLocked() && Common.hasSystemAlertWindow()) {
                    callDialog.setOnShowListener(dialog -> ARouter.getInstance().build(Routes.Main.HOME).navigation());
                }
            }
            insetDB();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return callDialog;
    }

    @Override
    public Dialog buildCallDialog(DialogInterface.OnDismissListener dismissListener,
                                  boolean isCallOut) {
        return buildCallDialog(context, dismissListener, isCallOut);
    }

    @Override
    public void call(SignalingInfo signalingInfo) {
        if (isCalling()) return;
        setSignalingInfo(signalingInfo);
        buildCallDialog(null, true);

        callDialog.show();
    }

    @Override
    public void join(SignalingInfo signalingInfo) {
        if (isCalling()) return;
        setSignalingInfo(signalingInfo);
        GroupCallDialog callDialog = (GroupCallDialog) buildCallDialog(null, false);
        callDialog.changeView();
        callDialog.joinToShow();
    }

    private boolean isCalling() {
        if (null != callDialog) {
            Toast.makeText(context, io.openim.android.ouicore.R.string.now_calling,
                Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }


    @Override
    public void onHangup(SignalingInfo signalingInfo) {
        L.e(TAG, "----onHangup-----");
        if (null == callDialog || callDialog.callingVM.isGroup) return;
        callDialog.callingVM.renewalDB(callDialog.buildPrimaryKey(),
            (realm, callHistory) -> callHistory.setDuration((int) (System.currentTimeMillis() - callHistory.getDate())));
        callDialog.dismiss();
    }

    @Override
    public void onRoomParticipantConnected(RoomCallingInfo s) {

    }

    @Override
    public void onRoomParticipantDisconnected(RoomCallingInfo s) {

    }

    @Override
    public void onMeetingStreamChanged(MeetingStreamEvent e) {

    }

    @Override
    public void onReceiveCustomSignal(CustomSignalingInfo s) {

    }

    @Override
    public void onStreamChange(String s) {

    }


    private void insetDB() {
        if (callDialog.callingVM.isGroup) return;
        List<String> ids = new ArrayList<>();
        ids.add(callDialog.callingVM.isCallOut ?
            signalingInfo.getInvitation().getInviteeUserIDList().get(0) :
            signalingInfo.getInvitation().getInviterUserID());

        boolean isCallOut = !callDialog.callingVM.isCallOut;
        OpenIMClient.getInstance().userInfoManager.getUsersInfo(new OnBase<List<UserInfo>>() {
            @Override
            public void onError(int code, String error) {
            }

            @Override
            public void onSuccess(List<UserInfo> data) {
                if (data.isEmpty() || null == callDialog) return;
                UserInfo userInfo = data.get(0);
                BaseApp.inst().realm.executeTransactionAsync(realm -> {
                    if (null == callDialog) return;
                    CallHistory callHistory = new CallHistory(callDialog.buildPrimaryKey(),
                        userInfo.getUserID(), userInfo.getNickname(), userInfo.getFaceURL(),
                        signalingInfo.getInvitation().getMediaType(), false, 0, isCallOut,
                        System.currentTimeMillis(), 0);
                    realm.insert(callHistory);
                });
            }
        }, ids);
    }

}


