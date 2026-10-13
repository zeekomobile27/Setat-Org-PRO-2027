package com.zeekoltd.setat.pro;

public class SupportRoom {
    public String uid;
    public String userName;
    public String userProfileImage;
    public String lastMessage;
    public long lastMessageTime;
    public int unreadCountAdmin;
    public int unreadCountUser;

    public SupportRoom() {}

    public SupportRoom(String uid, String userName, String userProfileImage, String lastMessage, long lastMessageTime, int unreadCountAdmin, int unreadCountUser) {
        this.uid = uid;
        this.userName = userName;
        this.userProfileImage = userProfileImage;
        this.lastMessage = lastMessage;
        this.lastMessageTime = lastMessageTime;
        this.unreadCountAdmin = unreadCountAdmin;
        this.unreadCountUser = unreadCountUser;
    }
}