package org.ole.planet.takeout.Data;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class realm_UserModel extends RealmObject {
    @PrimaryKey
    private String id;
    private String _rev;
    private String name;
    private String roles;
    private Boolean isUserAdmin;
    private int joinDate;
    private String firstName;
    private String lastName;
    private String middleName;
    private String email;
    private String phoneNumber;
    private String password_scheme;
    private String iterations;
    private String derived_key;
    private String salt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String get_rev() {
        return _rev;
    }

    public void set_rev(String _rev) {
        this._rev = _rev;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRoles() {
        return roles;
    }

    public void setRoles(String roles) {
        this.roles = roles;
    }

    public Boolean getUserAdmin() {
        return isUserAdmin;
    }

    public void setUserAdmin(Boolean userAdmin) {
        isUserAdmin = userAdmin;
    }

    public int getJoinDate() {
        return joinDate;
    }

    public void setJoinDate(int joinDate) {
        this.joinDate = joinDate;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getPassword_scheme() {
        return password_scheme;
    }

    public void setPassword_scheme(String password_scheme) {
        this.password_scheme = password_scheme;
    }

    public String getIterations() {
        return iterations;
    }

    public void setIterations(String iterations) {
        this.iterations = iterations;
    }

    public String getDerived_key() {
        return derived_key;
    }

    public void setDerived_key(String derived_key) {
        this.derived_key = derived_key;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }
}
