package com.skythinker.gptassistant.service;

import android.accessibilityservice.AccessibilityService;
import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Vibrator;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;

public class AgentAccessibilityService extends AccessibilityService {

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
    public WidgetNode rootWidgetNode;
    public static AgentAccessibilityService staticThis;

    public AgentAccessibilityService() { staticThis = this; }

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
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {

    }

    @Override
    public void onInterrupt() {

    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        isConnected = true;
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