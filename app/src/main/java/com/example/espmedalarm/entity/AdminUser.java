package com.example.espmedalarm.entity;

/** Row shown in the Admin Panel's user list. Populated manually by
 *  AdminRepository (not a Firestore @DocumentId model) since it's read-only
 *  summary data derived from the users/{uid} doc. */
public class AdminUser {
    public String uid;
    public String email;
    public long createdAtMillis;
    public long lastLoginAtMillis;
    public boolean disabled;
}
