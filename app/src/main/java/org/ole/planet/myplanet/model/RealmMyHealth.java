package org.ole.planet.myplanet.model;

public class RealmMyHealth {
    private RealmMyHealthProfile profile;
    private String userKey;
    private long lastExamination;

    public RealmMyHealthProfile getProfile() {
        return profile;
    }

    public void setProfile(RealmMyHealthProfile profile) {
        this.profile = profile;
    }

    public String getUserKey() {
        return userKey;
    }

    public void setUserKey(String userKey) {
        this.userKey = userKey;
    }

    public long getLastExamination() {
        return lastExamination;
    }

    public void setLastExamination(long lastExamination) {
        this.lastExamination = lastExamination;
    }

    public static class RealmMyHealthProfile {
        private String emergencyContactName = "", emergencyContactType = "", emergencyContact = "", specialNeeds = "", notes = "";

        public String getEmergencyContactName() {
            return emergencyContactName;
        }

        public void setEmergencyContactName(String emergencyContactName) {
            this.emergencyContactName = emergencyContactName;
        }


        public String getEmergencyContactType() {
            return emergencyContactType;
        }

        public void setEmergencyContactType(String emergencyContactType) {
            this.emergencyContactType = emergencyContactType;
        }

        public String getEmergencyContact() {
            return emergencyContact;
        }

        public void setEmergencyContact(String emergencyContact) {
            this.emergencyContact = emergencyContact;
        }

        public String getSpecialNeeds() {
            return specialNeeds;
        }

        public void setSpecialNeeds(String specialNeeds) {
            this.specialNeeds = specialNeeds;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }
    }
}
