package io.openim.android.ouicore.vm;

import static io.openim.android.ouicore.utils.Common.UIHandler;

import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import io.openim.android.ouicore.base.BaseApp;
import io.openim.android.ouicore.base.BaseViewModel;
import io.openim.android.ouicore.base.IView;
import io.openim.android.ouicore.entity.MsgConversation;
import io.openim.android.ouicore.im.IMEvent;
import io.openim.android.ouicore.im.IMUtil;
import io.openim.android.ouicore.net.bage.GsonHel;
import io.openim.android.ouicore.utils.Constant;
import io.openim.android.ouicore.utils.L;
import io.openim.android.ouicore.utils.Obs;
import io.openim.android.sdk.OpenIMClient;
import io.openim.android.sdk.listener.OnAdvanceMsgListener;
import io.openim.android.sdk.listener.OnBase;
import io.openim.android.sdk.listener.OnConversationListener;
import io.openim.android.sdk.models.ConversationInfo;
import io.openim.android.sdk.models.Message;
import io.openim.android.sdk.models.ReadReceiptInfo;
import io.openim.android.sdk.models.RevokedInfo;
import io.openim.android.sdk.models.UserInfo;
import io.realm.RealmList;
import io.realm.RealmResults;

public class ContactListVM extends BaseViewModel<ContactListVM.ViewAction> implements OnConversationListener, OnAdvanceMsgListener {
    public MutableLiveData<List<MsgConversation>> conversations = new MutableLiveData<>(new ArrayList<>());
    public MutableLiveData<List<UserInfo>> frequentContacts = new MutableLiveData<>(new ArrayList<>());

    @Override
    protected void viewCreate() {
        IMEvent.getInstance().addConversationListener(this);
        IMEvent.getInstance().addAdvanceMsgListener(this);
        updataConversation();

        UIHandler.postDelayed(this::updataConversation, 5 * 1000);
        Obs.newMessage(Constant.Event.CONTACT_LIST_VM_INIT);
    }

    public void deleteConversationFromLocalAndSvr(String conversationId) {
        OpenIMClient.getInstance().conversationManager.deleteConversationFromLocalAndSvr(new OnBase<String>() {
            @Override
            public void onError(int code, String error) {

            }

            @Override
            public void onSuccess(String data) {
                updataConversation();
            }
        }, conversationId);
    }

    private void updataConversation() {
        OpenIMClient.getInstance().conversationManager.getAllConversationList(new OnBase<List<ConversationInfo>>() {
            @Override
            public void onError(int code, String error) {
                IView.onErr(error);
            }

            @Override
            public void onSuccess(List<ConversationInfo> data) {
                L.e("AllConversationList---size--" + data.size());
                conversations.getValue().clear();
                for (ConversationInfo datum : data) {
                    Message msg = GsonHel.fromJson(datum.getLatestMsg(), Message.class);
                    if (null == msg)
                        continue;
                    conversations.getValue().add(new MsgConversation(msg, datum));
                }
                conversations.setValue(conversations.getValue());
                updataFrequentContacts(data);
            }
        });
    }
    public  void setOneConversationPrivateChat(IMUtil.OnSuccessListener<String> onSuccessListener,
                                               String cid, boolean isChecked){
        OpenIMClient.getInstance().conversationManager.setOneConversationPrivateChat(new OnBase<String>() {
            @Override
            public void onError(int code, String error) {
                IView.onErr(error);
            }

            @Override
            public void onSuccess(String data) {
                onSuccessListener.onSuccess(data);
            }
        },cid,isChecked);
    }

    /**
     * 更新常联系
     *
     * @param data
     */
    private void updataFrequentContacts(List<ConversationInfo> data) {
        List<UserInfo> uList = new ArrayList<>();
        for (ConversationInfo datum : data) {
            if (datum.getConversationType() == Constant.SessionType.SINGLE_CHAT) {
                UserInfo u = new UserInfo();
                u.setUserID(datum.getUserID());
                u.setNickname(datum.getShowName());
                u.setFaceURL(datum.getFaceURL());
                uList.add(u);
            }
        }
        if (uList.isEmpty()) return;
        frequentContacts.setValue(uList.size() > 15 ? uList.subList(0, 15) : uList);
    }

//    @RequiresApi(api = Build.VERSION_CODES.N)
//    private void insertDBContact(List<ConversationInfo> data) {
//        RealmList<UserInfoDB> uList = new RealmList<>();
//        for (ConversationInfo datum : data) {
//            if (datum.getConversationType() == Constant.SessionType.SINGLE_CHAT) {
//                UserInfoDB u = new UserInfoDB();
//                u.setUserID(datum.getUserID());
//                u.setNickname(datum.getShowName());
//                u.setFaceURL(datum.getFaceURL());
//                uList.add(u);
//            }
//        }
//        if (uList.isEmpty()) return;
//        BaseApp.inst().realm.executeTransactionAsync(realm -> {
//            RealmResults<UserInfoDB> realmResults = realm.where(UserInfoDB.class).findAll();
//            if (realmResults.isEmpty()) {
//                realm.insert(uList.size() > 15 ? uList.subList(0, 15) : uList);
//            } else {
//                realm.where(UserInfoDB.class)
//                    .in("userID", (String[]) uList.stream()
//                        .map(UserInfoDB::getUserID)
//                        .distinct().toArray()).findAll().deleteAllFromRealm();
//
//                for (UserInfoDB userInfoDB : uList) {
//                    UserInfoDB task = realm.where(UserInfoDB.class)
//                        .equalTo("userID", userInfoDB.getUserID()).findFirst();
//                    if (null != task)
//                        task.deleteFromRealm();
//                }
//            }
//        });
//    }

    @Override
    public void onConversationChanged(List<ConversationInfo> list) {
        updataConversation();
    }


    private void sortConversation(List<ConversationInfo> list) {
        List<MsgConversation> msgConversations = new ArrayList<>();
        for (ConversationInfo info : list) {
            msgConversations.add(new MsgConversation(GsonHel.fromJson(info.getLatestMsg(), Message.class), info));
            Iterator<MsgConversation> iterator = conversations.getValue().iterator();
            while (iterator.hasNext()) {
                if (iterator.next().conversationInfo.getConversationID().equals(info.getConversationID()))
                    iterator.remove();
            }
        }
        conversations.getValue().addAll(msgConversations);
        Collections.sort(conversations.getValue(), IMUtil.simpleComparator());
        conversations.setValue(conversations.getValue());
    }

    @Override
    public void onNewConversation(List<ConversationInfo> list) {
        sortConversation(list);
    }

    @Override
    public void onSyncServerFailed() {

    }

    @Override
    public void onSyncServerFinish() {

    }

    @Override
    public void onSyncServerStart() {

    }

    @Override
    public void onTotalUnreadMessageCountChanged(int i) {
        L.e("");
    }

    @Override
    public void onRecvNewMessage(Message msg) {

    }

    @Override
    public void onRecvC2CReadReceipt(List<ReadReceiptInfo> list) {

    }

    @Override
    public void onRecvGroupMessageReadReceipt(List<ReadReceiptInfo> list) {

    }

    @Override
    public void onRecvMessageRevoked(String msgId) {

    }

    @Override
    public void onRecvMessageRevokedV2(RevokedInfo info) {

    }

    //置顶/取消置顶 会话
    public void pinConversation(ConversationInfo conversationInfo, boolean isPinned) {
        OpenIMClient.getInstance().conversationManager.pinConversation(new OnBase<String>() {
            @Override
            public void onError(int code, String error) {
            }

            @Override
            public void onSuccess(String data) {
                conversationInfo.setPinned(isPinned);
            }
        }, conversationInfo.getConversationID(), isPinned);
    }

    public interface ViewAction extends IView {
        void onErr(String msg);
    }
}
