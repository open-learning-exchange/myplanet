package org.ole.planet.takeout.utilities;

import org.ole.planet.takeout.Data.realm_meetups;
import org.ole.planet.takeout.Data.realm_myCourses;
import org.ole.planet.takeout.Data.realm_myLibrary;
import org.ole.planet.takeout.Data.realm_myTeams;

import java.util.ArrayList;
import java.util.List;

public class Constants {
    public static List<ShelfData> shelfDataList;

    static {
        shelfDataList = new ArrayList<>();
        shelfDataList.add(new ShelfData("resourceIds", "resources", "resourceId", realm_myLibrary.class));
        shelfDataList.add(new ShelfData("meetupIds", "meetups", "meetupId", realm_meetups.class));
        shelfDataList.add(new ShelfData("courseIds", "courses", "courseId", realm_myCourses.class));
        shelfDataList.add(new ShelfData("myTeamIds", "teams", "teamId", realm_myTeams.class));
    }

    public static class ShelfData {
        public String key;
        public String type;
        public String categoryKey;
        public Class aClass;

        public ShelfData(String key, String type, String categoryKey, Class aClass) {
            this.key = key;
            this.type = type;
            this.categoryKey = categoryKey;
            this.aClass = aClass;
        }
    }
}
