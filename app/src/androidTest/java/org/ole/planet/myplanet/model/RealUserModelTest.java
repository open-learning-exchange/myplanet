package org.ole.planet.myplanet.model;


import android.content.Context;

import androidx.test.filters.LargeTest;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.gson.JsonObject;

import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.utilities.NetworkUtils;
import org.ole.planet.myplanet.utilities.VersionUtils;

import io.realm.Realm;
import io.realm.RealmConfiguration;

import static org.hamcrest.MatcherAssert.assertThat;

@LargeTest
@RunWith(AndroidJUnit4ClassRunner.class)
public class RealUserModelTest {

    private RealmUserModel mTestUser;
    private JsonObject mJsonObject;
    private Realm realm;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Realm.init(mContext);
        RealmConfiguration testConfig =
                new RealmConfiguration.Builder().
                        inMemory().
                        name("test-realm").build();
        realm = Realm.getInstance(testConfig);

        realm.beginTransaction();
        mTestUser = realm.createObject(RealmUserModel.class, "1");
        mTestUser.set_id("");
//        mTestUser.setId("1");
        mTestUser.set_rev("rev");
        mTestUser.setName("name");
//        mTestUser.setRoles(new RealmList<>());
        mTestUser.setUserAdmin(true);
        mTestUser.setJoinDate(1111111);
        mTestUser.setFirstName("firstName");
        mTestUser.setMiddleName("middleName");
        mTestUser.setLastName("lastName");
        mTestUser.setEmail("email");
        mTestUser.setPlanetCode("planetCode");
        mTestUser.setParentCode("parentCode");
        mTestUser.setPhoneNumber("phoneNumber");
        mTestUser.setPassword_scheme("password_scheme");
        mTestUser.setIterations("3");
        mTestUser.setDerived_key("derivedKey");
        mTestUser.setLevel("level");
        mTestUser.setLanguage("language");
        mTestUser.setGender("gender");
        mTestUser.setSalt("salt");
        mTestUser.setDob("dob");
        mTestUser.setBirthPlace("birthplace");
        mTestUser.setCommunityName("communityName");
        mTestUser.setUserImage("userImage");
        mTestUser.setKey("key");
        mTestUser.setIv("iv");
        mTestUser.setPassword("password");
        mTestUser.setUpdated(true);
        mTestUser.setShowTopbar(true);

        mJsonObject = new JsonObject();
        mJsonObject.addProperty("name", mTestUser.getName());
        mJsonObject.add("roles", mTestUser.getRoles());
        mJsonObject.addProperty("isUserAdmin", mTestUser.getUserAdmin());
        mJsonObject.addProperty("joinDate", mTestUser.getJoinDate());
        mJsonObject.addProperty("firstName", mTestUser.getFirstName());
        mJsonObject.addProperty("lastName", mTestUser.getLastName());
        mJsonObject.addProperty("middleName", mTestUser.getMiddleName());
        mJsonObject.addProperty("email", mTestUser.getEmail());
        mJsonObject.addProperty("language", mTestUser.getLanguage());
        mJsonObject.addProperty("level", mTestUser.getLevel());
        mJsonObject.addProperty("type", "user");
        mJsonObject.addProperty("gender", mTestUser.getGender());
        mJsonObject.addProperty("phoneNumber", mTestUser.getPhoneNumber());
        mJsonObject.addProperty("birthDate", mTestUser.getDob());
        try {
            mJsonObject.addProperty("iterations", Integer.parseInt(mTestUser.getIterations()));
        }catch (Exception e){
            mJsonObject.addProperty("iterations", 10);
        }
        mJsonObject.addProperty("parentCode", mTestUser.getParentCode());
        mJsonObject.addProperty("planetCode", mTestUser.getPlanetCode());
        mJsonObject.addProperty("birthPlace", mTestUser.getBirthPlace());
        realm.commitTransaction();
    }

    @After
    public void tearDown() throws Exception {
        mTestUser = null;
        mJsonObject = null;
        realm.close();
        mContext = null;
    }

    @Test
    public void serializeUser() {
        // Properties added when _id is Empty
        mJsonObject.addProperty("password", mTestUser.getPassword());
        mJsonObject.addProperty("macAddress", NetworkUtils.getMacAddr());
        mJsonObject.addProperty("androidId",NetworkUtils.getMacAddr());
        mJsonObject.addProperty("uniqueAndroidId", VersionUtils.getAndroidId(MainApplication.context));
        mJsonObject.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(MainApplication.context));

        // Check the result of our serialization
        assertThat(mTestUser.serialize(mContext), CoreMatchers.is(mJsonObject));
    }

//    @Test
//    public void serializeUserWith_idTest() {
//
//        // Properties added when _id is NOT Empty
//        mJsonObject.addProperty("_id", mTestUser.get_id());
//        mJsonObject.addProperty("_rev", mTestUser.get_rev());
//        mJsonObject.addProperty("derived_key", mTestUser.getDerived_key());
//        mJsonObject.addProperty("salt", mTestUser.getSalt());
//        mJsonObject.addProperty("password_scheme", mTestUser.getPassword_scheme());
//
//        // Check the result of our serialization
//        assertThat(mTestUser.serialize(mContext), CoreMatchers.is(mJsonObject));
//    }
}