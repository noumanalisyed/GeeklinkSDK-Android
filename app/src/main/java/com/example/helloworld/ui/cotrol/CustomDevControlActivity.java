package com.example.helloworld.ui.cotrol;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.helloworld.R;
import com.example.helloworld.adapter.CommonAdapter;
import com.example.helloworld.adapter.holder.ViewHolder;
import com.example.helloworld.impl.OnItemClickListenerImp;
import com.example.helloworld.impl.RecyclerItemClickListener;
import com.example.helloworld.utils.DialogUtils;
import com.example.helloworld.view.CommonToolbar;
import com.geeklink.smartpisdk.api.SmartPiApiManager;
import com.geeklink.smartpisdk.listener.OnControlDeviceListener;
import com.geeklink.smartpisdk.listener.OnDeviceEntryCodeListener;
import com.geeklink.smartpisdk.listener.OnSetDeviceKeyListener;
import com.gl.ActionFullType;
import com.gl.CarrierType;
import com.gl.DeviceMainType;
import com.gl.KeyInfo;
import com.gl.KeyStudyType;
import com.gl.StateType;
import com.gl.SubDevInfo;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

public class CustomDevControlActivity extends AppCompatActivity implements OnDeviceEntryCodeListener, OnControlDeviceListener, OnSetDeviceKeyListener {

    private CommonToolbar toolbar;
    private Context context;
    private RecyclerView recyclerView;

    private CommonAdapter<Integer> adapter;

    private List<Integer> keyInfoList = new ArrayList<>();
    private String md5 = "";
    private int subId ;
    private String mKeyIdListStr;
    private int mainType;
    private SubDevInfo subDevInfo;
    private boolean isReStudy = false;
    private int keyId = 0;
    private int clickPosition = 0;

    private AlertDialog alertDialog;
    private Handler handler = new Handler();
    private Runnable cancelDialogRunnable = new Runnable() {
        @Override
        public void run() {
            if(alertDialog != null && alertDialog.isShowing()){
                alertDialog.dismiss();
            }
        }
    };

    private static final String TAG = "CustomKeyActivity";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_key);
        context = this;
        Intent intent = getIntent();
        md5 = intent.getStringExtra("md5").toLowerCase();
        subId = intent.getIntExtra("subId",0);
        mainType = intent.getIntExtra("mainType",0);
        mKeyIdListStr = intent.getStringExtra("mKeyIdListStr");

        subDevInfo = new SubDevInfo(subId, DeviceMainType.CUSTOM_DEV,null,0,0,0, CarrierType.CARRIER_38,new ArrayList<Integer>(),md5,"");
        toolbar = findViewById(R.id.title);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        adapter = new CommonAdapter<Integer>(context, R.layout.item_add_sub_dev, keyInfoList) {
            @Override
            public void convert(ViewHolder holder, Integer keyId, int position) {
                holder.setText(R.id.nameTv,String.valueOf(keyId));
            }
        };
        recyclerView.setAdapter(adapter);
        recyclerView.addOnItemTouchListener(new RecyclerItemClickListener(context, recyclerView, new OnItemClickListenerImp() {
            @Override
            public void onItemClick(View view, int position) {
                super.onItemClick(view, position);

                List<String> actions = new ArrayList<>();
                actions.add(context.getString(R.string.text_control));
                actions.add(context.getString(R.string.text_study_again));
                actions.add(context.getString(R.string.text_delete));
                DialogUtils.showItemDialog(CustomDevControlActivity.this, actions, new OnItemClickListenerImp(){
                    @Override
                    public void onItemClick(View view, int position) {
                        super.onItemClick(view, position);
                        clickPosition = position;
                        if(position == 0){
                            //发码控制
                            SmartPiApiManager.getInstance().controlSubDeviceKeyWithMd5(md5,subDevInfo,null, keyInfoList.get(position));
                        }else if(position == 1){
                            //重新录码
                            isReStudy = true;
                            startStudyKeyCode();
                        }else{
                            //删除控制码
                            KeyInfo keyInfo = new KeyInfo(keyId, KeyStudyType.KEY_STUDY_IR.ordinal(),"");
                            SmartPiApiManager.getInstance().setSubDeviceKeyWithMd5(md5,subId, ActionFullType.DELETE,keyInfo);
                        }
                    }
                });

            }
        }));

        setListener();


        toolbar.setRightClick(new CommonToolbar.RightListener() {
            @Override
            public void rightClick() {
                isReStudy = false;
                startStudyKeyCode();
            }
        });
        initData();
    }

    private void setListener() {
        //发码控制回调
        SmartPiApiManager.getInstance().setOnControlDeviceListener(this);
        //录码回调
        SmartPiApiManager.getInstance().setOnDeviceEntryCodeListener(this);
        //设置控制码回调
        SmartPiApiManager.getInstance().setOnSetDeviceKeyListener(this);
    }


    private void startStudyKeyCode(){
        if(alertDialog == null){
            alertDialog = new AlertDialog.Builder(context).setMessage(context.getString(R.string.text_study_code_notes))
                    .setNegativeButton(context.getString(R.string.text_cancel), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            //停止录码
                            SmartPiApiManager.getInstance().getCodeFromDeviceWithMd5(md5, KeyStudyType.KEY_STUDY_CANCEL);
                        }
                    }).create();
        }
        alertDialog.show();
        //开始录码
        SmartPiApiManager.getInstance().getCodeFromDeviceWithMd5(md5, KeyStudyType.KEY_STUDY_IR);
        handler.postDelayed(cancelDialogRunnable, 20 * 1000);
    }

    private void initData() {
        keyInfoList.clear();
        List<Integer> ketList = new Gson().fromJson(mKeyIdListStr,new TypeToken<List<Integer>>() { }.getType());
        keyInfoList.addAll(ketList);
        adapter.notifyDataSetChanged();
    }


    @Override
    public void onCotrolDevice(StateType state, String md5, int deviceSubId) {
        if(state == StateType.OK){
            Toast.makeText(context, context.getString(R.string.text_control_successed), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDeviceEntryCode(StateType state, String md5, KeyStudyType type, String code) {
        if(state == StateType.OK){
            if(alertDialog != null && alertDialog.isShowing()){
                alertDialog.dismiss();
                if(isReStudy){
                    KeyInfo keyInfo = new KeyInfo(keyId,type.ordinal(),code);
                    //录码成功后，设置、保存控制码
                    SmartPiApiManager.getInstance().setSubDeviceKeyWithMd5(md5,subId, ActionFullType.UPDATE,keyInfo);
                }else{
                    KeyInfo keyInfo = new KeyInfo(0,type.ordinal(),code);
                    //录码成功后，设置、保存控制码
                    SmartPiApiManager.getInstance().setSubDeviceKeyWithMd5(md5,subId, ActionFullType.INSERT,keyInfo);
                }
            }

        }
    }

    @Override
    public void onSetDeviceKey(StateType state, String md5, ActionFullType action, int subId, int keyId) {
        if(state == StateType.OK){
            if(action == ActionFullType.INSERT) {
                keyInfoList.add(keyId);
                adapter.notifyDataSetChanged();
            }else if(action == ActionFullType.DELETE){
                keyInfoList.remove(clickPosition);
                adapter.notifyDataSetChanged();
            }
            Toast.makeText(context, context.getString(R.string.text_operate_successed), Toast.LENGTH_SHORT).show();
        }else{
            Toast.makeText(context, context.getString(R.string.text_operate_failed), Toast.LENGTH_SHORT).show();
        }

    }


}
