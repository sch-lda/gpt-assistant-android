package com.skythinker.gptassistant.service;

import android.accessibilityservice.AccessibilityService;
import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.skythinker.gptassistant.ui.MainActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;

public class MyAccessbilityService extends AccessibilityService {

    public static class WidgetNode {
        public enum OperationType {
            CLICK, LONG_CLICK, EDIT, SCROLL_DOWN, SCROLL_UP
        }
        public String className;
        public String text;
        public String description;
        public List<OperationType> operations = new ArrayList<>(); // 支持的操作类型
        public boolean hasOperableChild = false;
        public int operateId = -1; // 用于标记当前节点的操作ID
        public List<WidgetNode> children = new ArrayList<>();
        AccessibilityNodeInfo nodeInfo;
        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            if(className != null) { json.putOpt("class", className.substring(className.lastIndexOf(".") + 1)); }
            if(text != null) { json.putOpt("text", text); }
            if(description != null) { json.putOpt("description", description); }
            if(operations.size() > 0) {
                JSONArray operationsJson = new JSONArray();
                for(OperationType operation : operations) {
                    operationsJson.add(operation.name().toLowerCase());
                }
                json.putOpt("actions", operationsJson);
            }
            if(operateId != -1) { json.putOpt("id", operateId); }
            if(children.size() > 0) {
                JSONArray childrenJson = new JSONArray();
                for(WidgetNode child : children) {
                    childrenJson.add(child.toJson());
                }
                json.putOpt("children", childrenJson);
            }
            return json;
        }
        public WidgetNode fromAccessibilityNodeInfo(AccessibilityNodeInfo node) {
            if (node == null) {
                return null;
            }
            WidgetNode inoperableChildNode = new WidgetNode(); // 用于合并不可操作的子节点
            if (node.getChildCount() > 0) {
                for (int i = 0; i < node.getChildCount(); i++) {
                    WidgetNode childNode = new WidgetNode().fromAccessibilityNodeInfo(node.getChild(i)); // 递归获取子节点信息
                    if (childNode != null) {
                        if (childNode.text == null && childNode.description == null && childNode.operations.size() == 0) {
                            children.addAll(childNode.children); // 若子节点没有任何信息，则将其子节点添加到当前节点
                            hasOperableChild = hasOperableChild || childNode.hasOperableChild; // 合并子节点的可操作性
                        } else {
                            if (childNode.operations.size() > 0 || childNode.hasOperableChild) {
                                hasOperableChild = true; // 子节点可操作即认为本节点可操作
                                children.add(childNode);
                            } else { // 子节点不可操作，则合并到一个节点中
                                if (childNode.description != null)
                                    inoperableChildNode.description = (inoperableChildNode.description == null ? "" : inoperableChildNode.description) + childNode.description + "\n";
                                if (childNode.text != null)
                                    inoperableChildNode.text = (inoperableChildNode.text == null ? "" : inoperableChildNode.text) + childNode.text + "\n";
                            }
                        }
                    }
                }
            }
            if (inoperableChildNode.description != null || inoperableChildNode.text != null) { // 如果有合并的不可操作子节点，则添加到当前子节点
                children.add(inoperableChildNode);
            }
            if (node.getClassName() != null && node.getClassName().length() > 0) {
                className = node.getClassName().toString();
            }
            if (node.getContentDescription() != null && node.getContentDescription().length() > 0) {
                description = node.getContentDescription().toString();
            }
            if (node.getText() != null && node.getText().length() > 0) {
                text = node.getText().toString();
            }
            if(node.isClickable()) operations.add(OperationType.CLICK);
            if(node.isLongClickable()) operations.add(OperationType.LONG_CLICK);
            if(node.isEditable()) operations.add(OperationType.EDIT);
            if(node.isScrollable()) operations.addAll(Arrays.asList(OperationType.SCROLL_DOWN, OperationType.SCROLL_UP));
            nodeInfo = node;
            if(operations.size() > 0) {
                operateId = (int)(Math.random() * 1000000); // 随机生成一个操作ID
            }
            if (!hasOperableChild && children.size() > 0) { // 如果没有可操作的子节点，则将子节点内容合并到当前节点
                if (inoperableChildNode.description != null)
                    description = (description == null ? "" : description) + "\n" + inoperableChildNode.description;
                if (inoperableChildNode.text != null)
                    text = (text == null ? "" : text) + "\n" + inoperableChildNode.text;
                children.clear(); // 清空子节点
            }
            return this;
        }

        public boolean performClick(int operateId) {
            if(operateId == this.operateId) {
                if(nodeInfo != null) {
                    nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    return true;
                }
            } else {
                for(WidgetNode child : children) {
                    if(child.performClick(operateId)) {
                        return true;
                    }
                }
            }
            return false;
        }

        public boolean setText(int operateId, String text) {
            if(operateId == this.operateId) {
                if(nodeInfo != null) {
                    Bundle arguments = new Bundle();
                    arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                    nodeInfo.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                    return true;
                }
            } else {
                for(WidgetNode child : children) {
                    if(child.setText(operateId, text)) {
                        return true;
                    }
                }
            }
            return false;
        }

        public boolean performAction(int operateId, String action, String inputText) {
            if(operateId == this.operateId) {
                if(nodeInfo != null) {
                    if(action.equals(OperationType.CLICK.name().toLowerCase())) {
                        return nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    } else if(action.equals(OperationType.LONG_CLICK.name().toLowerCase())) {
                        return nodeInfo.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
                    } else if(action.equals(OperationType.SCROLL_DOWN.name().toLowerCase())) {
                        return nodeInfo.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                    } else if(action.equals(OperationType.SCROLL_UP.name().toLowerCase())) {
                        return nodeInfo.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
                    } else if(action.equals(OperationType.EDIT.name().toLowerCase()) && inputText != null) {
                        Bundle arguments = new Bundle();
                        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, inputText);
                        return nodeInfo.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                    }
                }
            } else {
                for(WidgetNode child : children) {
                    if(child.performAction(operateId, action, inputText)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private static boolean isConnected = false;
    private Handler handler = new Handler();
    private int keyDownTime = 0, keyUpTime = 0;
    private boolean isWaitingConfirm = false;
    private int pressCount = 0;
    private boolean isPressing = false;
    private boolean isBaned = false;
    private boolean isInStartDelay = false;
    AudioManager audioManager;
    Vibrator vibrator;
    public WidgetNode rootWidgetNode;
    public static MyAccessbilityService staticThis;

    final private int longPressTime = 500; // 长按判定时间
    final private int maxConfirmTime = 3000; // 长按到短按的最长间隔
    final private int banCancelInterval = 2000; // 短按后禁止长按的时间

    public MyAccessbilityService() {
        staticThis = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
//        Log.d("MyAccessbilityService", "onAccessibilityEvent: " + accessibilityEvent.toString());
    }

    @Override
    public void onInterrupt() {

    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        Log.d("MyAccessbilityService", "onKeyEvent: " + event.toString());
        if(event.getKeyCode() != KeyEvent.KEYCODE_VOLUME_DOWN) { // 非音量下键不处理
            return super.onKeyEvent(event);
        }

        int eventTime = (int) event.getEventTime(); // 事件发生的时间戳
        int eventAction = event.getAction();

        if(isBaned) { // 当前处于禁用状态（短按后的禁用时间），不拦截事件
            if(eventAction == KeyEvent.ACTION_DOWN) {
                keyDownTime = eventTime;
                if(eventTime - keyUpTime < banCancelInterval) {
                    return super.onKeyEvent(event);
                } else {
                    isBaned = false; // 超出禁用时间，解除禁用
                }
            } else if(eventAction == KeyEvent.ACTION_UP) {
                keyUpTime = eventTime;
                return super.onKeyEvent(event);
            }
        }

        if(pressCount == 0){ // 当前处于判定周期的第一次按下（标准情况的一次长按+短按为一个判定周期）
            if(eventAction == KeyEvent.ACTION_DOWN) {

//                Log.d("MyAccessbilityService", "widget: " + new WidgetNode().fromAccessibilityNodeInfo(getRootInActiveWindow()).toJson().toStringPretty());

                keyDownTime = eventTime;
                isPressing = true;
                handler.postDelayed(() -> { // 等待长按时间后进行长按判定
                    if(isPressing) { // 长按时间后仍然处于按下状态，判定为一次长按
                        if(!MainActivity.isAlive() || !MainActivity.isRunning()) { // 主活动未运行则唤起
                            Intent intent = new Intent(this, MainActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(intent);
                            Log.d("MyAccessbilityService", "startActivity: MainActivity");
                            isInStartDelay = true;
                            handler.postDelayed(() -> { // 等待500ms后发送广播，开始语音识别
                                if(isInStartDelay) {
                                    Intent broadcastIntent = new Intent("com.skythinker.gptassistant.KEY_SPEECH_START");
                                    LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
                                    Log.d("MyAccessbilityService", "broadcast: KEY_SPEECH_START");
                                }
                            }, 500);
                        } else { // 主活动已在运行， 直接发送广播开始语音识别
                            Intent broadcastIntent = new Intent("com.skythinker.gptassistant.KEY_SPEECH_START");
                            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
                            Log.d("MyAccessbilityService", "broadcast: KEY_SPEECH_START");
                        }
                        vibrator.vibrate(100);
                    }
                }, longPressTime);
                return true;
            } else if(eventAction == KeyEvent.ACTION_UP) {
                keyUpTime = eventTime;
                isPressing = false;
                isInStartDelay = false;
                if(eventTime - keyDownTime < longPressTime) { // 未达到长按时间就松开，用户只是想调音量，进入禁用状态并弹出音量调节界面
                    isBaned = true;
                    audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                    return true;
                } else { // 释放时已经超出了长按时间，表明长按结束
                    pressCount++; // 准备进入判定周期的第二次判定
                    Intent broadcastIntent = new Intent("com.skythinker.gptassistant.KEY_SPEECH_STOP"); //发送广播，停止语音识别
                    LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
                    Log.d("MyAccessbilityService", "broadcast: KEY_SPEECH_STOP");
                    isWaitingConfirm = true;
                    handler.postDelayed(() -> { // 若超出最长短按等待时间，重置判定周期
                        if(isWaitingConfirm) {
                            pressCount = 0;
                        }
                    }, maxConfirmTime);
                    return true;
                }
            }
        } else if (pressCount == 1) { // 当前处于判定周期的第二次按下
            if(eventAction == KeyEvent.ACTION_DOWN) {
                isWaitingConfirm = false;
                keyDownTime = eventTime;
                if(eventTime - keyUpTime < maxConfirmTime) { // 未超出最长短按等待时间，判定为一次短按，发送广播请求向GPT发送提问
                    Intent broadcastIntent = new Intent("com.skythinker.gptassistant.KEY_SEND");
                    LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
                    Log.d("MyAccessbilityService", "broadcast: KEY_SEND");
                    return true;
                } else { // 已超出最长短按等待时间，判定为用户的一次普通点击，不拦截事件
                    return super.onKeyEvent(event);
                }
            } else if(eventAction == KeyEvent.ACTION_UP) {
                if(keyDownTime - keyUpTime < maxConfirmTime) { // 上次按下时被判定为一次成功短按，拦截本次松开事件
                    keyUpTime = eventTime;
                    pressCount = 0;
                    return true;
                } else { // 上次按下时被判定为一次普通点击，不拦截本次松开事件
                    keyUpTime = eventTime;
                    pressCount = 0;
                    return super.onKeyEvent(event);
                }
            }
        }

        return super.onKeyEvent(event);
    }

    public JSONObject getWidgetJson() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if(rootNode != null) {
            rootWidgetNode = new WidgetNode().fromAccessibilityNodeInfo(rootNode);
            return rootWidgetNode.toJson();
        }
        return null;
    }

    public String getCurrentPackageName() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if(rootNode != null) {
            return rootNode.getPackageName() != null ? rootNode.getPackageName().toString() : "";
        }
        return "";
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        isConnected = true;
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        vibrator = (Vibrator) getSystemService(Service.VIBRATOR_SERVICE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isConnected = false;
    }

    public static boolean isConnected() {
        return isConnected;
    }
}