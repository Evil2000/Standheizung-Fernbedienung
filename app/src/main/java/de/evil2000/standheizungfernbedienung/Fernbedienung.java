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
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Date;
import java.util.LinkedList;

public class Fernbedienung extends Activity {
    private SharedPreferences settings;
    private StatesAdapter stateLogAdapter;
    private MqttAndroidClient mqttClient = null;
    private String mqttClientId;
    private LinkedList<String> mqttMessageQ = null;
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
            if (messages.getMessageBody().startsWith("AH"))
                abortBroadcast();

            // Split the SMS message by space and treat the second argument as "command"
            String command;
            try {
                command = messages.getMessageBody().split(" ")[1];
            } catch (ArrayIndexOutOfBoundsException e) {
                Log.w(H.thisFunc(getClass()), "SMS message is not in format 'AH (on|off)'! Ignoring.");
                return;
            }

            if (command.equals("on")) {
                StateItem stItm = new StateItem();
                stItm.setStateString(getString(R.string.ah_on));
                stItm.setState(true);
                stItm.setTimestamp(new Date(messages.getTimestampMillis()));
                stateLogAdapter.insert(0, stItm);
            } else if (command.equals("off")) {
                StateItem stItm = new StateItem();
                stItm.setStateString(getString(R.string.ah_off));
                stItm.setState(false);
                stItm.setTimestamp(new Date(messages.getTimestampMillis()));
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

        final Switch swtchUseMqttTransport = findViewById(R.id.swtchUseMqttTransport);
        final EditText txtMqttBrokerUri = findViewById(R.id.txtMqttBrokerUri);
        final EditText txtPhoneNumber = findViewById(R.id.txtPhoneNumber);

        txtPhoneNumber.setText(settings.getString("receiverPhoneNumber", ""));
        txtPhoneNumber.addTextChangedListener(new PhoneNumberFormattingTextWatcher());
        txtPhoneNumber.setOnFocusChangeListener(new TextView.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus)
                    return;
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
        txtMqttBrokerUri.setText(settings.getString("mqttBrokerUri", ""));
        txtPhoneNumber.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                Log.d(H.thisFunc(getClass()), "editable=" + charSequence.toString());
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
        txtMqttBrokerUri.setOnFocusChangeListener(new TextView.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus)
                    return;
                settings.edit().putString("mqttBrokerUri", ((TextView) v).getText().toString())
                        .apply();
            }
        });
        txtMqttBrokerUri.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                        actionId == EditorInfo.IME_ACTION_DONE ||
                        event.getAction() == KeyEvent.ACTION_DOWN &&
                                event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    // the user is done typing and clicked "Done"
                    settings.edit().putString("mqttBrokerUri", view.getText().toString()).apply();
                    return true; // consume.
                }
                return false;
            }
        });
        CompoundButton.OnCheckedChangeListener l = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                settings.edit().putBoolean("useMqttTransport", checked).apply();
                if (checked) {
                    txtPhoneNumber.setVisibility(View.GONE);
                    findViewById(R.id.lblPhoneNumber).setVisibility(View.GONE);
                    txtMqttBrokerUri.setVisibility(View.VISIBLE);
                    findViewById(R.id.lblMqttBrokerUri).setVisibility(View.VISIBLE);
                } else {
                    txtPhoneNumber.setVisibility(View.VISIBLE);
                    findViewById(R.id.lblPhoneNumber).setVisibility(View.VISIBLE);
                    txtMqttBrokerUri.setVisibility(View.GONE);
                    findViewById(R.id.lblMqttBrokerUri).setVisibility(View.GONE);
                }
            }
        };
        swtchUseMqttTransport.setOnCheckedChangeListener(l);
        swtchUseMqttTransport.setChecked(settings.getBoolean("useMqttTransport", false));
        l.onCheckedChanged(swtchUseMqttTransport, settings.getBoolean("useMqttTransport", false));

        ToggleButton btnSwitchState = findViewById(R.id.btnSwitchState);
        btnSwitchState.setText(getString(R.string.ah_off));
        btnSwitchState.setTextOn(getString(R.string.ah_on));
        btnSwitchState.setTextOff(getString(R.string.ah_off));

        btnSwitchState.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ToggleButton btn = (ToggleButton) view;
                String cmd = (btn.isChecked() ? "on" : "off") + " app";
                if (settings.getBoolean("useMqttTransport", false)) {
                    sendMqttMessage(cmd);
                } else {
                    if (!sendSms("AH " + cmd))
                        btn.setChecked(false);
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
            Date thirtyMinutesAfterLastState = new Date(lastState.getTimestamp().getTime() + 30 * 60 * 1000); // 30 Minutes
            //Date thirtyMinutesBeforeNow = new Date((new Date()).getTime() - 30 * 60 * 1000); // 30 Minutes
            if (lastState.getState() == true) {
                if ((new Date()).before(thirtyMinutesAfterLastState)) {
                    btnSwitchState.setChecked(true);
                } else {
                    // Zeit abgelaufen
                    btnSwitchState.setChecked(false);
                    StateItem stItm = new StateItem();
                    stItm.setStateString(getString(R.string.ah_off));
                    stItm.setState(false);
                    stItm.setTimestamp(thirtyMinutesAfterLastState);
                    stateLogAdapter.insert(0, stItm);
                    stateLogAdapter.notifyDataSetChanged();
                }
            }
        }

        IntentFilter mIntentFilter = new IntentFilter();
        mIntentFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        mIntentFilter.addAction("android.provider.Telephony.SMS_RECEIVED");
        registerReceiver(smsReceiver, mIntentFilter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(H.thisFunc(getClass()), "===");
        connectMqtt();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(H.thisFunc(getClass()), "===");
        try {
            mqttClient.unsubscribe("/AH/from");
            mqttClient.disconnect();
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(H.thisFunc(getClass()), "===");
        writeStatesToFile();
        unregisterReceiver(smsReceiver);
        mqttClient.unregisterResources();
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

    /**
     * Send a SMS message to the trusted sender.
     *
     * @param message The message to send.
     */
    private boolean sendSms(String message) {
        return sendSms(settings.getString("receiverPhoneNumber", ""), message);
    }

    /**
     * Send a SMS message to a number.
     *
     * @param toNumber Receiver of the SMS message
     * @param message  The message to send.
     */
    private boolean sendSms(String toNumber, String message) {
        if (toNumber == null || toNumber.isEmpty())
            return false;
        SmsManager smsMgr = SmsManager.getDefault();
        smsMgr.sendTextMessage(toNumber, null, message, null, null);
        return true;
    }

    private void sendMqttMessage(String message) {
        connectMqtt();
        mqttMessageQ.add(message);
        peakMqttMessageQ();
    }

    private void connectMqtt() {
        final String brokerUri = settings.getString("mqttBrokerUri", "");
        if (isAlreadyConnected()) {
            return;
        }
        mqttMessageQ = new LinkedList<>();
        if (brokerUri.isEmpty()) {
            //txtMqttBrokerUri.setBackgroundColor();
            return;
        }
        mqttClient = new MqttAndroidClient(getBaseContext(), brokerUri, getClientId());
        mqttClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean isReconnect, String serverURI) {
                Log.d("connectComplete", "isReconnect=" + isReconnect + " serverURI=" + serverURI);
                if (serverURI.equals(brokerUri)) {
                    try {
                        IMqttToken res = mqttClient.subscribe("/AH/from", 0);
                        res.setActionCallback(new IMqttActionListener() {
                            @Override
                            public void onSuccess(IMqttToken token) {
                                Log.d("IMqttActionListener", "Subscription complete.");
                            }

                            @Override
                            public void onFailure(IMqttToken iMqttToken, Throwable cause) {
                                Log.d("IMqttActionListener", "Subscription failed. cause=" + cause.toString());
                            }
                        });
                    } catch (MqttException e) {
                        e.printStackTrace();
                    }
                }
                peakMqttMessageQ();
            }

            @Override
            public void connectionLost(Throwable cause) {
                Log.d("connectionLost", "cause=" + (cause != null ? cause.toString() : "null"));
            }

            @Override
            public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
                Log.d("messageArrived", "topic=" + topic + " message=" + mqttMessage.toString());
                if (mqttMessage.toString().equals("on")) {
                    StateItem stItm = new StateItem();
                    stItm.setStateString(getString(R.string.ah_on));
                    stItm.setState(true);
                    stItm.setTimestamp(new Date());
                    stateLogAdapter.insert(0, stItm);
                    ((ToggleButton) findViewById(R.id.btnSwitchState)).setChecked(true);
                } else if (mqttMessage.toString().equals("off")) {
                    StateItem stItm = new StateItem();
                    stItm.setStateString(getString(R.string.ah_off));
                    stItm.setState(false);
                    stItm.setTimestamp(new Date());
                    stateLogAdapter.insert(0, stItm);
                    ((ToggleButton) findViewById(R.id.btnSwitchState)).setChecked(false);
                } else {
                    StateItem stItm = new StateItem();
                    stItm.setStateString(mqttMessage.toString());
                    stItm.setState(false);
                    stItm.setTimestamp(new Date());
                    stateLogAdapter.insert(0, stItm);
                    ((ToggleButton) findViewById(R.id.btnSwitchState)).setChecked(false);
                }
                stateLogAdapter.notifyDataSetChanged();
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                Log.d("deliveryComplete", "token=" + token.toString());
            }
        });
        try {
            mqttClient.connect();
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private synchronized void peakMqttMessageQ() {
        if (!isAlreadyConnected()) {
            return;
        }
        try {
            while (mqttMessageQ.size() > 0)
                mqttClient.publish("/AH/to", mqttMessageQ.poll().getBytes(), 0, false);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private boolean isAlreadyConnected() {
        return ((mqttClient != null) && mqttClient.isConnected());
    }

    private String getClientId() {
        if (mqttClientId != null)
            return mqttClientId;
        mqttClientId = MqttClient.generateClientId();
        Log.d(H.thisFunc(getClass()), "mqttClientId=" + mqttClientId);
        return mqttClientId;
    }

}
