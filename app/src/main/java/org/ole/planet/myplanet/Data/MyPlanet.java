package org.ole.planet.myplanet.Data;

public class MyPlanet {
    private String planetVersion;

    private String latestapk;

    private String minapk;

    private int minapkcode;

    private int latestapkcode;

    private String apkpath;

    private String appname;

    public String getPlanetVersion() {
        return planetVersion;
    }

    public void setPlanetVersion(String planetVersion) {
        this.planetVersion = planetVersion;
    }

    public String getLatestapk() {
        return latestapk;
    }

    public void setLatestapk(String latestapk) {
        this.latestapk = latestapk;
    }

    public String getMinapk() {
        return minapk;
    }

    public void setMinapk(String minapk) {
        this.minapk = minapk;
    }

    public String getApkpath() {
        return apkpath;
    }

    public void setApkpath(String apkpath) {
        this.apkpath = apkpath;
    }

    public String getAppname() {
        return appname;
    }

    public void setAppname(String appname) {
        this.appname = appname;
    }

    public int getMinapkcode() {
        return minapkcode;
    }

    public void setMinapkcode(int minapkcode) {
        this.minapkcode = minapkcode;
    }

    public int getLatestapkcode() {
        return latestapkcode;
    }

    public void setLatestapkcode(int latestapkcode) {
        this.latestapkcode = latestapkcode;
    }

    @Override
    public String toString() {
        return "ClassPojo [planetVersion = " + planetVersion + ", latestapk = " + latestapk + ", minapk = " + minapk + ", apkpath = " + apkpath + ", appname = " + appname + "]";
    }
}
