package io.openim.android.ouiconversation.ui;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alibaba.android.arouter.facade.annotation.Route;
import com.alibaba.android.arouter.launcher.ARouter;
import com.yanzhenjie.recyclerview.widget.DefaultItemDecoration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.openim.android.ouiconversation.R;
import io.openim.android.ouiconversation.adapter.MessageAdapter;
import io.openim.android.ouiconversation.databinding.ActivityChatBinding;
import io.openim.android.ouiconversation.vm.ChatVM;
import io.openim.android.ouiconversation.widget.BottomInputCote;
import io.openim.android.ouicore.base.BaseActivity;
import io.openim.android.ouicore.base.BaseApp;
import io.openim.android.ouicore.entity.MsgExpand;
import io.openim.android.ouicore.entity.NotificationMsg;
import io.openim.android.ouicore.im.IMUtil;
import io.openim.android.ouicore.utils.Common;
import io.openim.android.ouicore.utils.Constant;
import io.openim.android.ouicore.utils.L;
import io.openim.android.ouicore.utils.OnDedrepClickListener;
import io.openim.android.ouicore.utils.Routes;
import io.openim.android.ouicore.widget.BottomPopDialog;
import io.openim.android.sdk.OpenIMClient;
import io.openim.android.sdk.models.Message;
import io.openim.android.sdk.models.OfflinePushInfo;
import io.openim.android.sdk.models.SignalingInfo;
import io.openim.android.sdk.models.SignalingInvitationInfo;

@Route(path = Routes.Conversation.CHAT)
public class ChatActivity extends BaseActivity<ChatVM, ActivityChatBinding> implements ChatVM.ViewAction {

    private MessageAdapter messageAdapter;
    private BottomInputCote bottomInputCote;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //userId 与 GROUP_ID 互斥
        String userId = getIntent().getStringExtra(Constant.K_ID);
        String groupId = getIntent().getStringExtra(Constant.K_GROUP_ID);
        String name = getIntent().getStringExtra(io.openim.android.ouicore.utils.Constant.K_NAME);
        NotificationMsg notificationMsg = (NotificationMsg) getIntent().getSerializableExtra(Constant.K_NOTICE);
        bindVM(ChatVM.class, true);
        if (null != userId)
            vm.otherSideID = userId;
        if (null != groupId) {
            vm.isSingleChat = false;
            vm.groupID = groupId;
        }
        if (null != notificationMsg)
            vm.notificationMsg.setValue(notificationMsg);
        super.onCreate(savedInstanceState);

        bindViewDataBinding(ActivityChatBinding.inflate(getLayoutInflater()));
        sink();
        view.setChatVM(vm);

        initView(name);
        listener();

        setTouchClearFocus(false);
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(mGlobalLayoutListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getWindow().getDecorView().getViewTreeObserver().removeOnGlobalLayoutListener(mGlobalLayoutListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isFinishing())
            removeCacheVM();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initView(String name) {
        bottomInputCote = new BottomInputCote(this, view.layoutInputCote);
        bottomInputCote.setChatVM(vm);

        view.nickName.setText(name);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        //倒叙
        linearLayoutManager.setStackFromEnd(true);
        linearLayoutManager.setReverseLayout(true);

        view.recyclerView.setLayoutManager(linearLayoutManager);
        view.recyclerView.addItemDecoration(new DefaultItemDecoration(this.getResources().getColor(android.R.color.transparent), 1, Common.dp2px(16)));
        messageAdapter = new MessageAdapter();
        messageAdapter.bindRecyclerView(view.recyclerView);

        vm.setMessageAdapter(messageAdapter);
        view.recyclerView.setAdapter(messageAdapter);
        vm.messages.observe(this, v -> {
            if (null == v) return;
            messageAdapter.setMessages(v);
            messageAdapter.notifyDataSetChanged();
        });
        view.recyclerView.setOnTouchListener((v, event) -> {
            bottomInputCote.clearFocus();
            Common.hideKeyboard(this, v);
            bottomInputCote.setExpandHide();
            return false;
        });
        view.recyclerView.post(() -> scrollToPosition(0));
        view.recyclerView.addOnLayoutChangeListener((v, i, i1, i2, i3, i4, i5, i6, i7) -> {
            if (i3 < i7) { // bottom < oldBottom
                scrollToPosition(0);
            }
        });

    }

    //记录原始窗口高度
    private int mWindowHeight = 0;

    private ViewTreeObserver.OnGlobalLayoutListener mGlobalLayoutListener = () -> {
        Rect r = new Rect();
        //获取当前窗口实际的可见区域
        getWindow().getDecorView().getWindowVisibleDisplayFrame(r);
        int height = r.height();
        if (mWindowHeight == 0) {
            //一般情况下，这是原始的窗口高度
            mWindowHeight = height;
        } else {
            RelativeLayout.LayoutParams inputLayoutParams = (RelativeLayout.LayoutParams) view.layoutInputCote.getRoot().getLayoutParams();
            if (mWindowHeight == height) {
                inputLayoutParams.bottomMargin = 0;
            } else {
                //两次窗口高度相减，就是软键盘高度
                int softKeyboardHeight = mWindowHeight - height;
                inputLayoutParams.bottomMargin = softKeyboardHeight;
            }
            view.layoutInputCote.getRoot().setLayoutParams(inputLayoutParams);
        }
    };

    private void listener() {
        view.call.setOnClickListener(v -> {
            if (!vm.isSingleChat) return;
            BottomPopDialog dialog = new BottomPopDialog(this);
            dialog.show();
            dialog.getMainView().menu3.setOnClickListener(v1 -> dialog.dismiss());
            dialog.getMainView().menu1.setText(io.openim.android.ouicore.R.string.voice_calls);
            dialog.getMainView().menu2.setText(io.openim.android.ouicore.R.string.video_calls);
            List<String> ids = new ArrayList<>();
            ids.add(vm.otherSideID);
            dialog.getMainView().menu1.setOnClickListener(v1 -> {
                toCall(false, ids, null);
                dialog.dismiss();

            });
            dialog.getMainView().menu2.setOnClickListener(v1 -> {
                toCall(true, ids, null);
                dialog.dismiss();
            });

        });
        view.delete.setOnClickListener(v -> {
            List<Message> selectMsg = getSelectMsg();
            for (Message message : selectMsg) {
                vm.deleteMessageFromLocalStorage(message);
            }
        });
        view.mergeForward.setOnClickListener(v -> {
            ARouter.getInstance().build(Routes.Contact.FORWARD)
                .navigation(this, Constant.Event.FORWARD);
        });
        vm.enableMultipleSelect.observe(this, o -> {
            int px = Common.dp2px(22);
            if (o) {
                view.choiceMenu.setVisibility(View.VISIBLE);
                view.layoutInputCote.getRoot().setVisibility(View.INVISIBLE);
                view.cancel.setVisibility(View.VISIBLE);
                view.back.setVisibility(View.GONE);
                view.recyclerView.setPadding(0, 0, px, 0);
            } else {
                view.choiceMenu.setVisibility(View.GONE);
                view.layoutInputCote.getRoot().setVisibility(View.VISIBLE);
                view.cancel.setVisibility(View.GONE);
                view.back.setVisibility(View.VISIBLE);
                view.recyclerView.setPadding(px, 0, px, 0);
                messageAdapter.notifyDataSetChanged();
            }
        });
        view.cancel.setOnClickListener(v -> {
            vm.enableMultipleSelect.setValue(false);
            for (Message message : vm.messages.getValue()) {
                MsgExpand msgExpand = (MsgExpand) message.getExt();
                if (null != msgExpand)
                    msgExpand.isChoice = false;
            }
        });

        view.notice.setOnClickListener(v -> ARouter.getInstance().build(Routes.Group.NOTICE_DETAIL)
            .withSerializable(Constant.K_NOTICE, vm.notificationMsg.getValue()).navigation());

        view.back.setOnClickListener(v -> finish());

        view.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                LinearLayoutManager linearLayoutManager = (LinearLayoutManager) view.recyclerView.getLayoutManager();
                int firstVisiblePosition = linearLayoutManager.findFirstCompletelyVisibleItemPosition();
                int lastVisiblePosition = linearLayoutManager.findLastCompletelyVisibleItemPosition();
                if (lastVisiblePosition == vm.messages.getValue().size() - 1
                    && vm.messages.getValue().size() >= vm.count) {
                    vm.loadHistoryMessage();
                }
                if (vm.isSingleChat)
                    vm.sendMsgReadReceipt(firstVisiblePosition, lastVisiblePosition);
            }
        });

        view.more.setOnClickListener(new OnDedrepClickListener() {
            @Override
            public void click(View v) {
                if (vm.isSingleChat) {

                } else {
                    ARouter.getInstance().build(Routes.Group.MATERIAL)
                        .withString(Constant.K_GROUP_ID, vm.groupID).navigation();
                }
            }
        });
    }

    private void toCall(boolean isVideoCalls, List<String> inviteeUserIDs, String groupID) {
        if (null == callingService) return;
        SignalingInfo signalingInfo = new SignalingInfo();
        String inId = BaseApp.inst().loginCertificate.userID;
        signalingInfo.setOpUserID(inId);
        SignalingInvitationInfo signalingInvitationInfo = new SignalingInvitationInfo();
        signalingInvitationInfo.setInviterUserID(inId);
        signalingInvitationInfo.setInviteeUserIDList(inviteeUserIDs);
        signalingInvitationInfo.setRoomID(String.valueOf(UUID.randomUUID()));
        signalingInvitationInfo.setTimeout(30);
        signalingInvitationInfo.setMediaType(isVideoCalls ? "video" : "audio");
        signalingInvitationInfo.setPlatformID(IMUtil.PLATFORM_ID);
        signalingInvitationInfo.setSessionType(vm.isSingleChat?1:2);
        signalingInvitationInfo.setGroupID(groupID);

        signalingInfo.setInvitation(signalingInvitationInfo);
        signalingInfo.setOfflinePushInfo(new OfflinePushInfo());
        callingService.call(isVideoCalls, signalingInfo);

    }

    @NonNull
    private List<Message> getSelectMsg() {
        List<Message> selectMsg = new ArrayList<>();
        for (Message message : messageAdapter.getMessages()) {
            MsgExpand msgExpand = (MsgExpand) message.getExt();
            if (null != msgExpand && msgExpand.isChoice)
                selectMsg.add(message);
        }
        return selectMsg;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        bottomInputCote.dispatchTouchEvent(event);
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void scrollToPosition(int position) {
        view.recyclerView.scrollToPosition(position);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        if (requestCode == Constant.Event.FORWARD && null != data) {
            //在这里转发
            String id = data.getStringExtra(Constant.K_ID);
            String otherSideNickName = data.getStringExtra(Constant.K_NAME);

            String groupId = data.getStringExtra(Constant.K_GROUP_ID);

            Message forwardMsg;
            if (null == vm.forwardMsg)//表示合并转发
                forwardMsg = IMUtil.createMergerMessage(vm.isSingleChat, otherSideNickName,
                    getSelectMsg());
            else
                forwardMsg = vm.forwardMsg;
            vm.aloneSendMsg(forwardMsg, id, groupId);
        }
    }
}
