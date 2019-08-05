package org.ole.planet.myplanet.ui.calendar;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import io.realm.Realm;

public class CalendarFragment extends Fragment {

    private DatabaseHelper myDb;
    private CalendarView calendarView;

    OnHomeItemClickListener listener;
    TextView schedule;
    Button scheduleButton, deleteButton;
    String userInput, deleteInput;
    EditText inputText, deleteText;
    String selectedDate;
    RealmUserModel user;
    Date mDate;
    private StringBuffer buffer;
    String username;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnHomeItemClickListener)
            listener = (OnHomeItemClickListener) context;
    }

    public CalendarFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_calendar, container, false);

        schedule = v.findViewById(R.id.scheduleCalendar);
        schedule.setMovementMethod(new ScrollingMovementMethod());
        scheduleButton = v.findViewById(R.id.scheduleButton);
        calendarView = v.findViewById(R.id.calendarView);
        //deleteButton = v.findViewById(R.id.deleteButton);

        myDb = new DatabaseHelper(getContext());

        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        calendarView.setOnDateChangeListener(new CalendarView.OnDateChangeListener() {
            @Override
            public void onSelectedDayChange(CalendarView calendarView, int year, int month, int dayOfMonth) {
                if (month < 10 && dayOfMonth < 10)
                    selectedDate = year + "-0" + month + "-0" + dayOfMonth;
                else if (month < 10 && dayOfMonth >= 10)
                    selectedDate = year + "-0" + month + "-" + dayOfMonth;
                else if (month >= 10 && dayOfMonth < 10)
                    selectedDate = year + "-" + month + "-0" + dayOfMonth;
                else
                    selectedDate = year + "-" + month + "-" + dayOfMonth;


                //Get Date type
                try {
                    mDate = new SimpleDateFormat("YYYY-MM-DD").parse(selectedDate);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
        });

        scheduleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setCancelable(false);
                builder.setTitle("SCHEDULE");
                builder.setMessage("Please enter detail of your schedule");
                builder.setIcon(R.drawable.ole_logo);
                inputText = new EditText(getContext());
                inputText.setSingleLine();
                builder.setView(inputText);

                builder.setPositiveButton("SAVE", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        userInput = inputText.getText().toString();

                        //Identify user's current identification
                        user = new UserProfileDbHandler(MainApplication.context).getUserModel();
                        username = user.getId();

                        if (selectedDate == null) {
                            Date date = Calendar.getInstance().getTime();
                            SimpleDateFormat dateFormat = new SimpleDateFormat("YYYY-MM-dd");
                            selectedDate = dateFormat.format(date);
                        }

                        //Add events to SQLite database
                        boolean isInserted = myDb.insertData(username, selectedDate, userInput);
                        if (isInserted)
                            Toast.makeText(getContext(), "Add event successfully", Toast.LENGTH_LONG).show();
                        else
                            Toast.makeText(getContext(), "Fail to add event", Toast.LENGTH_LONG).show();

                        //Show database
                        showData();

                    }
                });

                builder.setNegativeButton("CANCEL", null);
                builder.show();
            }
        });

        /*//Delete Button
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setCancelable(false);
                builder.setTitle("DELETE");
                builder.setMessage("Please enter the id of the schedule");
                builder.setIcon(R.drawable.ole_logo);
                deleteText = new EditText(getContext());
                deleteText.setSingleLine();
                builder.setView(deleteText);

                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        deleteInput = deleteText.getText().toString();
                        Integer deleteRow = myDb.deleteData(deleteInput);

                        if (deleteRow > 0)
                            Toast.makeText(getContext(), "Delete Successfully", Toast.LENGTH_LONG).show();
                        else
                            Toast.makeText(getContext(), "Fail to delete", Toast.LENGTH_LONG).show();

                        showData();
                    }
                });

                builder.setNegativeButton("CANCEL", null);
                builder.show();

            }
        });*/

        //Show database
        showData();

    }

    //Show all data in database
    public void showData() {
        Cursor cursor = myDb.getAllData();
        user = new UserProfileDbHandler(MainApplication.context).getUserModel();
        username = user.getId();

        if (cursor.getCount() != 0) {
            buffer = new StringBuffer();

            while(cursor.moveToNext()) {
                if (cursor.getString(1).equals(username)) {
                    buffer.append(cursor.getString(2));
                    buffer.append(": " + cursor.getString(3) + "\n");
                }

            }
        }

        if (buffer == null || buffer.length()==0)
            schedule.setText("No available schedule");
        else
            schedule.setText(buffer);
    }

}
