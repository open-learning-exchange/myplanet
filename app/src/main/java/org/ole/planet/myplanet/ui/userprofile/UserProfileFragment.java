package org.ole.planet.myplanet.ui.userprofile;


import static android.app.Activity.RESULT_OK;
import static org.ole.planet.myplanet.MainApplication.context;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.snackbar.Snackbar;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.EditProfileDialogBinding;
import org.ole.planet.myplanet.databinding.FragmentUserProfileBinding;
import org.ole.planet.myplanet.databinding.RowStatBinding;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.sync.SyncActivity;
import org.ole.planet.myplanet.utilities.FileUtils;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.realm.Realm;

public class UserProfileFragment extends Fragment {
    private FragmentUserProfileBinding fragmentUserProfileBinding;
    private RowStatBinding rowStatBinding;
    UserProfileDbHandler handler;
    private SharedPreferences settings;
    DatabaseService realmService;
    Realm mRealm;
    RealmUserModel model;
    File output;
    static final int IMAGE_TO_USE = 100;
    static String imageUrl = "";
    int type = 0;
    String selectedGender = null;
    String selectedLevel = null;
    String selectedLanguage = null;
    String date = null;

    public UserProfileFragment() {
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mRealm != null) mRealm.close();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentUserProfileBinding = FragmentUserProfileBinding.inflate(inflater, container, false);
        handler = new UserProfileDbHandler(getActivity());
        realmService = new DatabaseService(getActivity());
        settings = context.getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE);
        mRealm = realmService.getRealmInstance();
        fragmentUserProfileBinding.rvStat.setLayoutManager(new LinearLayoutManager(getActivity()));
        fragmentUserProfileBinding.rvStat.setNestedScrollingEnabled(false);

        fragmentUserProfileBinding.btProfilePic.setOnClickListener(v1 -> searchForPhoto());
        model = handler.getUserModel();
        fragmentUserProfileBinding.txtName.setText(String.format("%s %s %s", model.firstName, model.middleName, model.lastName));
        fragmentUserProfileBinding.txtGender.setText("Gender: " + Utilities.checkNA(model.gender));
        fragmentUserProfileBinding.txtEmail.setText(getString(R.string.email_colon) + Utilities.checkNA(model.email));
        String dob = TextUtils.isEmpty(model.dob) ? "N/A" : TimeUtils.getFormatedDate(model.dob, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        fragmentUserProfileBinding.txtLanguage.setText(getString(R.string.language_colon) + Utilities.checkNA(model.language));
        fragmentUserProfileBinding.txtLevel.setText("Level: " + Utilities.checkNA(model.level));
        fragmentUserProfileBinding.txtDob.setText(getString(R.string.date_of_birth) + dob);
        if (!TextUtils.isEmpty(model.userImage)) {
            Glide.with(context)
                .load(model.userImage)
                .apply(new RequestOptions()
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile))
                .listener(new RequestListener<>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        fragmentUserProfileBinding.image.setImageResource(R.drawable.profile);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        return false;
                    }
                })
                .into(fragmentUserProfileBinding.image);
        } else {
            fragmentUserProfileBinding.image.setImageResource(R.drawable.profile);
        }

        fragmentUserProfileBinding.btEditProfile.setOnClickListener(v -> {
            Dialog dialog = new Dialog(requireContext());
            dialog.setCancelable(false);
            EditProfileDialogBinding editProfileDialogBinding = EditProfileDialogBinding.inflate(LayoutInflater.from(requireContext()));
            dialog.setContentView(editProfileDialogBinding.getRoot());
            Objects.requireNonNull(dialog.getWindow()).setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            editProfileDialogBinding.firstName.setText(model.firstName);
            editProfileDialogBinding.middleName.setText(model.middleName);
            editProfileDialogBinding.lastName.setText(model.lastName);
            editProfileDialogBinding.email.setText(model.email);
            editProfileDialogBinding.phoneNumber.setText(model.phoneNumber);
            String dob1 = TextUtils.isEmpty(model.dob) ? "N/A" : TimeUtils.getFormatedDate(model.dob, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            editProfileDialogBinding.dateOfBirth.setText(dob1);

            String[] languages = getResources().getStringArray(R.array.language);
            List<CharSequence> languageList = new ArrayList<>(Arrays.asList(languages));
            languageList.add(0, "Language");

            ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, languageList);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            editProfileDialogBinding.language.setAdapter(adapter);

            if (model.language != null) {
                String[] language = getResources().getStringArray(R.array.language);
                List<String> languageLists = Arrays.asList(language);

                int languagePosition = languageLists.indexOf(model.language);

                if (languagePosition >= 0) {
                    editProfileDialogBinding.language.setSelection(languagePosition + 1);
                }
            } else {
                editProfileDialogBinding.language.setSelection(0);
            }

            editProfileDialogBinding.language.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    selectedLanguage = parent.getItemAtPosition(position).toString();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // Handle the case when nothing is selected
                }
            });

            String[] levels = getResources().getStringArray(R.array.subject_level);
            List<String> levelList = new ArrayList<>(Arrays.asList(levels));
            levelList.remove("All");

            levelList.add(0, "Level");
            ArrayAdapter<String> levelAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, levelList);
            levelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            editProfileDialogBinding.level.setAdapter(levelAdapter);

            if (model.level != null) {
                int levelPosition = levelList.indexOf(model.level);
                if (levelPosition >= 0) {
                    editProfileDialogBinding.level.setSelection(levelPosition + 1);
                }
            } else {
                editProfileDialogBinding.level.setSelection(0);
            }

            editProfileDialogBinding.level.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    selectedLevel = parent.getItemAtPosition(position).toString();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // Handle the case when nothing is selected
                }
            });

            if ("male".equalsIgnoreCase(model.gender)) {
                editProfileDialogBinding.rbMale.setChecked(true);
            } else if ("female".equalsIgnoreCase(model.gender)) {
                editProfileDialogBinding.rbFemale.setChecked(true);
            }

            editProfileDialogBinding.dateOfBirth.setOnClickListener(v14 -> {
                Calendar now = Calendar.getInstance();
                DatePickerDialog dpd = new DatePickerDialog(requireContext(), (view, year, monthOfYear, dayOfMonth) -> {
                    String dob2 = String.format(Locale.US, "%04d-%02d-%02d", year, monthOfYear + 1, dayOfMonth);
                    date = String.format(Locale.US, "%04d-%02d-%02dT00:00:00.000Z", year, monthOfYear + 1, dayOfMonth);
                    editProfileDialogBinding.dateOfBirth.setText(dob2);
                }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)
                );
                dpd.getDatePicker().setMaxDate(now.getTimeInMillis());
                dpd.show();
            });

            editProfileDialogBinding.btnSave.setOnClickListener(v13 -> {
                if (TextUtils.isEmpty(editProfileDialogBinding.firstName.getText().toString().trim())) {
                    editProfileDialogBinding.firstName.setError("first name required");
                } else if (TextUtils.isEmpty(editProfileDialogBinding.middleName.getText().toString().trim())) {
                    editProfileDialogBinding.middleName.setError("middle name is required");
                } else if (TextUtils.isEmpty(editProfileDialogBinding.lastName.getText().toString().trim())) {
                    editProfileDialogBinding.lastName.setError("last name is required");
                } else if (TextUtils.isEmpty(editProfileDialogBinding.email.getText().toString().trim())) {
                    editProfileDialogBinding.email.setError("email name is required");
                } else if (TextUtils.isEmpty(editProfileDialogBinding.phoneNumber.getText().toString().trim())) {
                    editProfileDialogBinding.phoneNumber.setError("phone number is required");
                } else if (getResources().getString(R.string.birth_date).equals(editProfileDialogBinding.dateOfBirth.getText().toString())) {
                    editProfileDialogBinding.dateOfBirth.setError("date of birth is required");
                } else if (editProfileDialogBinding.rdGender.getCheckedRadioButtonId() == -1) {
                    Snackbar.make(editProfileDialogBinding.getRoot(), "gender not picked", Snackbar.LENGTH_SHORT).show();
                } else {
                    if (editProfileDialogBinding.rbMale.isChecked()) {
                        selectedGender = "male";
                    } else if (editProfileDialogBinding.rbFemale.isChecked()) {
                        selectedGender = "female";
                    }
                    Realm realm = Realm.getDefaultInstance();
                    String userId = settings.getString("userId", "");

                    RealmUserModel.updateUserDetails(realm, userId, editProfileDialogBinding.firstName.getText().toString(),
                            editProfileDialogBinding.lastName.getText().toString(), editProfileDialogBinding.middleName.getText().toString(),
                            editProfileDialogBinding.email.getText().toString(), editProfileDialogBinding.phoneNumber.getText().toString(),
                            selectedLevel, selectedLanguage, selectedGender, date);
                    realm.close();
                    dialog.dismiss();
                }
            });

            editProfileDialogBinding.btnCancel.setOnClickListener(v12 -> dialog.dismiss());
            dialog.show();
        });
        final LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
        map.put("Community Name", Utilities.checkNA(model.planetCode));
        map.put("Last Login : ", Utilities.getRelativeTime(handler.getLastVisit()));
        map.put("Total Visits : ", handler.getOfflineVisits() + "");
        map.put("Most Opened Resource : ", Utilities.checkNA(handler.getMaxOpenedResource()));
        map.put("Number of Resources Opened : ", Utilities.checkNA(handler.getNumberOfResourceOpen()));

        final LinkedList<String> keys = new LinkedList<>(map.keySet());
        fragmentUserProfileBinding.rvStat.setAdapter(new RecyclerView.Adapter() {
            @NonNull
            @Override
            public ViewHolderRowStat onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                rowStatBinding = RowStatBinding.inflate(LayoutInflater.from(getActivity()), parent, false);
                return new ViewHolderRowStat(rowStatBinding);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                if (holder instanceof ViewHolderRowStat) {
                    rowStatBinding.tvTitle.setText(keys.get(position));
                    rowStatBinding.tvTitle.setVisibility(View.VISIBLE);
                    rowStatBinding.tvDescription.setText(map.get(keys.get(position)));
                    if (position % 2 == 0) {
                        rowStatBinding.getRoot().setBackgroundColor(getResources().getColor(R.color.bg_white));
                        rowStatBinding.getRoot().setBackgroundColor(getResources().getColor(R.color.md_grey_300));
                    }
                }
            }

            @Override
            public int getItemCount() {
                return keys.size();
            }

            class ViewHolderRowStat extends RecyclerView.ViewHolder {
                public RowStatBinding rowStatBinding;

                public ViewHolderRowStat(RowStatBinding rowStatBinding) {
                    super(rowStatBinding.getRoot());
                    this.rowStatBinding = rowStatBinding;
                }
            }
        });
        return fragmentUserProfileBinding.getRoot();
    }

    public void searchForPhoto() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI);
        startActivityForResult(intent, IMAGE_TO_USE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == IMAGE_TO_USE && resultCode == RESULT_OK) {
            Uri url = data.getData();
            imageUrl = url.toString();

            if (!mRealm.isInTransaction()) {
                mRealm.beginTransaction();
            }
            String path = FileUtils.getRealPathFromURI(requireActivity(), url);
            if (TextUtils.isEmpty(path)) {
                path = FileUtils.getImagePath(requireActivity(), url);
            }
            model.userImage = path;
            mRealm.commitTransaction();
            fragmentUserProfileBinding.image.setImageURI(url);
            Utilities.log("Image Url = " + imageUrl);
        }
             /*   getRealm().executeTransactionAsync(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                model.setUserImage(imageUrl);
            }
        });*/
    }

    private void populateUserData(View v) {
        model = handler.getUserModel();
        ((TextView) v.findViewById(R.id.txt_name)).setText(String.format("%s %s %s", model.firstName, model.middleName, model.lastName));
        ((TextView) v.findViewById(R.id.txt_email)).setText(getString(R.string.email_colon)
                + Utilities.checkNA(model.email));
        String dob = TextUtils.isEmpty(model.dob) ? "N/A" : TimeUtils.getFormatedDate(model.dob, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        ((TextView) v.findViewById(R.id.txt_dob)).setText(getString(R.string.date_of_birth) + dob);

        if (!TextUtils.isEmpty(model.userImage)) {
            Glide.with(context)
                    .load(model.userImage)
                    .apply(new RequestOptions()
                            .placeholder(R.drawable.profile)
                            .error(R.drawable.profile))
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            fragmentUserProfileBinding.image.setImageResource(R.drawable.profile);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            return false;
                        }
                    })
                    .into(fragmentUserProfileBinding.image);
        } else {
            fragmentUserProfileBinding.image.setImageResource(R.drawable.profile);
        }

        final LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
        map.put("Community Name", Utilities.checkNA(model.planetCode));
        map.put("Last Login : ", Utilities.getRelativeTime(handler.getLastVisit()));
        map.put("Total Visits : ", handler.getOfflineVisits() + "");
        map.put("Most Opened Resource : ", Utilities.checkNA(handler.getMaxOpenedResource()));
        map.put("Number of Resources Opened : ", Utilities.checkNA(handler.getNumberOfResourceOpen()));

        final LinkedList<String> keys = new LinkedList<>(map.keySet());
        fragmentUserProfileBinding.rvStat.setAdapter(new RecyclerView.Adapter() {
            @NonNull
            @Override
            public ViewHolderRowStat onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                rowStatBinding = RowStatBinding.inflate(LayoutInflater.from(getActivity()), parent, false);
                return new ViewHolderRowStat(rowStatBinding);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                if (holder instanceof ViewHolderRowStat) {
                    rowStatBinding.tvTitle.setText(keys.get(position));
                    rowStatBinding.tvTitle.setVisibility(View.VISIBLE);
                    rowStatBinding.tvDescription.setText(map.get(keys.get(position)));
                    if (position % 2 == 0) {
                        rowStatBinding.getRoot().setBackgroundColor(getResources().getColor(R.color.bg_white));
                        rowStatBinding.getRoot().setBackgroundColor(getResources().getColor(R.color.md_grey_300));
                    }
                }
            }

            @Override
            public int getItemCount() {
                return keys.size();
            }

            class ViewHolderRowStat extends RecyclerView.ViewHolder {
                public RowStatBinding rowStatBinding;

                public ViewHolderRowStat(RowStatBinding rowStatBinding) {
                    super(rowStatBinding.getRoot());
                    this.rowStatBinding = rowStatBinding;
                }
            }
        });
    }

    private void showDatePickerDialog() {

    }


}
