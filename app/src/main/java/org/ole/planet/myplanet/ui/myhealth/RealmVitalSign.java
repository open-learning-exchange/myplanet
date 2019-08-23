package org.ole.planet.myplanet.ui.myhealth;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class RealmVitalSign extends RealmObject {
    @PrimaryKey
    private String id;
    private float bodyTemp;
    private String method;
    private int pulseRate;
    private int respirationRate;
    private int bloodPressureSystolic;
    private int bloodPressureDiastolic;
    private String userId;



    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public float getBodyTemp() {
        return bodyTemp;
    }

    public void setBodyTemp(float bodyTemp) {
        this.bodyTemp = bodyTemp;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public int getPulseRate() {
        return pulseRate;
    }

    public void setPulseRate(int pulseRate) {
        this.pulseRate = pulseRate;
    }

    public int getRespirationRate() {
        return respirationRate;
    }

    public void setRespirationRate(int respirationRate) {
        this.respirationRate = respirationRate;
    }

    public int getBloodPressureSystolic() {
        return bloodPressureSystolic;
    }

    public void setBloodPressureSystolic(int bloodPressureSystolic) {
        this.bloodPressureSystolic = bloodPressureSystolic;
    }

    public int getBloodPressureDiastolic() {
        return bloodPressureDiastolic;
    }

    public void setBloodPressureDiastolic(int bloodPressureDiastolic) {
        this.bloodPressureDiastolic = bloodPressureDiastolic;
    }
}
