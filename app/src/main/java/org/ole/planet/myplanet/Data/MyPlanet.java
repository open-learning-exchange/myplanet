package org.ole.planet.myplanet.Data;

public class MyPlanet
{
    private String planetVersion;

    private String latestapk;

    private String minapk;

    private int minVersionCode;

    private String apkpath;

    private String appname;

    public String getPlanetVersion ()
    {
        return planetVersion;
    }

    public void setPlanetVersion (String planetVersion)
    {
        this.planetVersion = planetVersion;
    }

    public String getLatestapk ()
    {
        return latestapk;
    }

    public void setLatestapk (String latestapk)
    {
        this.latestapk = latestapk;
    }

    public String getMinapk ()
    {
        return minapk;
    }

    public void setMinapk (String minapk)
    {
        this.minapk = minapk;
    }

    public String getApkpath ()
    {
        return apkpath;
    }

    public void setApkpath (String apkpath)
    {
        this.apkpath = apkpath;
    }

    public String getAppname ()
    {
        return appname;
    }

    public void setAppname (String appname)
    {
        this.appname = appname;
    }

    public int getMinVersionCode() {
        return minVersionCode;
    }

    public void setMinVersionCode(int minVersionCode) {
        this.minVersionCode = minVersionCode;
    }

    @Override
    public String toString()
    {
        return "ClassPojo [planetVersion = "+planetVersion+", latestapk = "+latestapk+", minapk = "+minapk+", apkpath = "+apkpath+", appname = "+appname+"]";
    }
}
