package de.evil2000.standheizungfernbedienung;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.telephony.PhoneNumberFormattingTextWatcher;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsMessage;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Date;

public class Fernbedienung extends Activity {
    private SharedPreferences settings;
    private StatesAdapter stateLogAdapter;
    private BroadcastReceiver smsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Read the SMS from the intent.
            Bundle pudsBundle = intent.getExtras();
            Object[] pdus = (Object[]) pudsBundle.get("pdus");
            SmsMessage messages = SmsMessage.createFromPdu((byte[]) pdus[0]);
            // @formatter:off
            Log.i("onReceive", "Received SMS message:\n"
                    + "From    : " + messages.getOriginatingAddress() + "\n"
                    + "ICCidx  : " + messages.getIndexOnIcc() + "\n"
                    + "Prot-ID : " + messages.getProtocolIdentifier() + "\n"
                    + "Subject : " + messages.getPseudoSubject() + "\n"
                    + "SMSC    : " + messages.getServiceCenterAddress() + "\n"
                    + "Status  : " + messages.getStatus() + "\n"
                    + "ICC-Stat: " + messages.getStatusOnIcc() + "\n"
                    + "Timestmp: " + messages.getTimestampMillis() + "\n"
                    + "Message : " + messages.getMessageBody() + "\n"
            );
            // @formatter:on

            // Check if message is received from a trusted sender.
            if (!PhoneNumberUtils.compare(messages.getOriginatingAddress(), settings.getString("receiverPhoneNumber", "")))
                return;

            // TODO:
            // Check if AH is on by parsing the SMS message
            if (true) {
                StateItem stItm = new StateItem();
                stItm.setStateString("Standheizung ein");
                stItm.setState(true);
                stateLogAdapter.insert(0, stItm);
            } else {
                StateItem stItm = new StateItem();
                stItm.setStateString("Standheizung aus");
                stItm.setState(true);
                stateLogAdapter.insert(0, stItm);
            }
            stateLogAdapter.notifyDataSetChanged();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fernbedienung);

        settings = getSharedPreferences("settings", MODE_PRIVATE);

        EditText txtPhoneNumber = findViewById(R.id.txtPhoneNumber);
        txtPhoneNumber.setText(settings.getString("receiverPhoneNumber", ""));
        txtPhoneNumber.addTextChangedListener(new PhoneNumberFormattingTextWatcher());
        txtPhoneNumber.setOnFocusChangeListener(new TextView.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) return;
                TextView view = (TextView) v;
                String number = view.getText().toString().replace(" ", "");
                if (!PhoneNumberUtils.isGlobalPhoneNumber(number)) {
                    view.setTextColor(Color.rgb(191, 0, 0));
                } else {
                    view.setTextColor(Color.rgb(96, 191, 0));
                    settings.edit().putString("receiverPhoneNumber", view.getText().toString()).apply();
                }
            }
        });
        txtPhoneNumber.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                        actionId == EditorInfo.IME_ACTION_DONE ||
                        event.getAction() == KeyEvent.ACTION_DOWN &&
                                event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    // the user is done typing and clicked "Done"
                    String number = view.getText().toString().replace(" ", "");
                    if (!PhoneNumberUtils.isGlobalPhoneNumber(number)) {
                        view.setTextColor(Color.rgb(191, 0, 0));
                    } else {
                        view.setTextColor(Color.rgb(96, 191, 0));
                        settings.edit().putString("receiverPhoneNumber", view.getText().toString()).apply();
                    }
                    return true; // consume.
                }
                return false;
            }
        });

        ToggleButton btnSwitchState = findViewById(R.id.btnSwitchState);
        btnSwitchState.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ToggleButton btn = (ToggleButton) view;
                if (btn.isChecked()) {
                    // Todo:
                    // Send SMS AH on and register new StateItem AFTER confirmation SMS is received

                } else {
                    // Todo:
                    // Send SMS AH off and register new StateItem AFTER confirmation SMS is received

                }
            }
        });

        stateLogAdapter = new StatesAdapter(this);
        ListView lstStateLog = findViewById(R.id.lstStateLog);
        lstStateLog.setAdapter(stateLogAdapter);
        readStatesFromFile();
        stateLogAdapter.sortByTimestamp(true);
        // Keep only the last 20 State Log entries.
        while (stateLogAdapter.getCount() > 20) {
            stateLogAdapter.remove(stateLogAdapter.getCount() - 1);
        }
        stateLogAdapter.notifyDataSetChanged();

        if (stateLogAdapter.getCount() > 0) {
            StateItem lastState = stateLogAdapter.getItem(0);
            Date thirtyMinutesBeforeNow = new Date((new Date()).getTime() - 30 * 60 * 1000); // 30 Minutes
            if (lastState.getState() == true && lastState.getTimestamp().after(thirtyMinutesBeforeNow)) {
                btnSwitchState.setChecked(true);
            }
        }

        IntentFilter mIntentFilter = new IntentFilter();
        mIntentFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        mIntentFilter.addAction("android.provider.Telephony.SMS_RECEIVED");
        registerReceiver(smsReceiver,mIntentFilter);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        writeStatesToFile();
    }

    private void writeStatesToFile() {
        for (int i = 0; i < stateLogAdapter.getCount(); i++) {
            try {
                ObjectOutput out = new ObjectOutputStream(openFileOutput("states" + i + ".bin", MODE_PRIVATE));
                out.writeObject(stateLogAdapter.getItem(i));
                out.close();
                Log.d(H.thisFunc(getClass()), "wrote states" + i + ".bin");
            } catch (java.io.IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void readStatesFromFile() {
        File intStorageDir = getFilesDir();
        File[] fileList = intStorageDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                return filename.startsWith("states") && filename.endsWith(".bin");
            }
        });
        for (File f : fileList)
            try {
                ObjectInput in = new ObjectInputStream(new FileInputStream(f));
                stateLogAdapter.add((StateItem) in.readObject());
                in.close();
                Log.d(H.thisFunc(getClass()), "read " + f.toString());
                f.delete();
            } catch (java.io.IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
    }
}
