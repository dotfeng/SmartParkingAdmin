package com.itr.exo.smartparkingadmin;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.nfc.FormatException;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.widget.NestedScrollView;
import android.util.Log;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.nxp.listeners.ReadSRAMListener;
import com.nxp.listeners.WriteSRAMListener;
import com.nxp.reader.I2C_Enabled_Commands;
import com.nxp.reader.Ntag_I2C_Commands;
import com.nxp.ByteUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    NfcAdapter nfcAdapter;

    private Ntag_I2C_Commands channel;

    PendingIntent pendingIntent;

    private TextView myText = null;
    private TextView estadoNFCText = null;

    private NestedScrollView consoleScroll = null;
    SharedPreferences prefs;
    private static int DELAY_TIME;
    private static int READ_ID_DELAY_TIME;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        //        DrawerLayout drawer = findViewById(R.id.drawer_layout);
//        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
//                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
//        drawer.addDrawerListener(toggle);
//        toggle.syncState();
//
//        NavigationView navigationView = findViewById(R.id.nav_view);
//        navigationView.setNavigationItemSelectedListener(this);

        myText = findViewById(R.id.consoleTextView);
        estadoNFCText = findViewById(R.id.estadoNFCText);

        consoleScroll = findViewById(R.id.consoleScroll);

        addLineToConsole("Iniciando aplicacion");

        pendingIntent = PendingIntent.getActivity(this, 0, new Intent(
                getApplicationContext(), getClass())
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        setupNfcAdapter();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        DELAY_TIME = Integer.parseInt(prefs.getString("write_timeout","100"));
        READ_ID_DELAY_TIME = Integer.parseInt(prefs.getString("id_read_timeout","2000"));

    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            showSettingsFragment();
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_camera) {
            // Handle the camera action
        } else if (id == R.id.nav_gallery) {

        } else if (id == R.id.nav_slideshow) {

        } else if (id == R.id.nav_manage) {

        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onNewIntent(Intent intent) {
        // onResume gets called after this to handle the intent
        setIntent(intent);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(getIntent().getAction())) {
            processIntent(getIntent());
        }
        DELAY_TIME = Integer.parseInt(prefs.getString("write_timeout","100"));
        READ_ID_DELAY_TIME = Integer.parseInt(prefs.getString("id_read_timeout","2000"));

        if (nfcAdapter != null) {
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
        }
    }

    private void showSettingsFragment() {
        Intent goToNextActivity = new Intent(getApplicationContext(), SettingsActivity.class);
        startActivity(goToNextActivity);
    }

    private void setupNfcAdapter() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC no disponible", Toast.LENGTH_LONG).show();
            finish();
        }
        PermissionUtils.checkNfcPermissions(this, nfcAdapter);
    }

    void processIntent(Intent intent) {
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        addLineToConsole("Tag descubierto "+tag.toString());
        connect(tag);
    }

    public void addLineToConsole(String line){
        new Thread(new Runnable() {
            @Override
            public void run()
            {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run()
                    {
                        myText.append("\n"+line);
                        Log.d("action",line != null ? line : "");
                        consoleScroll.post(new Runnable() {
                            @Override
                            public void run() {
                                consoleScroll.fullScroll(View.FOCUS_DOWN);
                            }
                        });
                    }
                });
            }
        }).start();

    }

    public void send64k(View v){
        addLineToConsole("Enviando 64B");
        sendFile();
    }

    public void send128k(View v){
        addLineToConsole("Enviando 128B");
        fastWrite();
    }

    public void read64Block(View v){
        readBlock(null);
    }

    public void CMD_RESET(View v){
        addLineToConsole("CMD_RESET");
        sendCommand(ExoCommands.CMD_RESET.getBytes(),null);
    }

    public void CMD_PS_MODE_WORKING(View v){
        addLineToConsole("CMD_PS_MODE_WORKING");
        sendCommand(ExoCommands.CMD_PS_MODE_WORKING.getBytes(),null);
    }

    public void CMD_PS_MODE_STORAGE(View v){
        addLineToConsole("CMD_PS_MODE_STORAGE");
        sendCommand(ExoCommands.CMD_PS_MODE_STORAGE.getBytes(),null);
    }

    public void CMD_PS_TEST_ALL(View v){
        addLineToConsole("CMD_PS_TEST_ALL");
        sendCommand(ExoCommands.CMD_PS_TEST_ALL.getBytes(),null);
    }

    public void CMD_SET_CFG_RADIO(View v){
        addLineToConsole("CMD_SET_CFG_RADIO");
        sendCommand(ExoCommands.CMD_SET_CFG_RADIO.getBytes(),null);
    }

//    final byte[] command = new byte[64];
//    command[0] = (byte) 0x15;
//    command[1] = (byte) (~( 0x15) + 1);

    public void CMD_DEVICE_INFORMATION(View v){
        addLineToConsole("CMD_DEVICE_INFORMATION");
        sendCommand(ExoCommands.CMD_DEVICE_INFORMATION.getBytes(), new WriteSRAMListener() {
            @Override
            public void onWriteSRAM() {
                readBlock(READ_ID_DELAY_TIME);
            }
        });
    }

    public void CMD_FILE_RECEIVE(View v){
        readFile();
    }

    public void sendCommand(byte[] cmd, WriteSRAMListener listener){
        if (isConnected()) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        channel.waitforI2Cread(100);
                        channel.writeSRAMBlock(cmd, listener != null ? listener : new WriteSRAMListener() {
                            @Override
                            public void onWriteSRAM() {
                                readBlock(null);
                            }
                        });
                    } catch (TimeoutException | IOException | FormatException e) {
                        addLineToConsole(e.getMessage());
                    }
                }
        }).start();
        }else
            addLineToConsole("Tag disconnected.");
    }


    public void connect(Tag tag) {
        try {
            channel = new Ntag_I2C_Commands(tag);
            channel.connect();
            addLineToConsole("Conectado a tag");
            estadoNFCText.setText("CONECTADO");
        } catch (Exception e) {
            estadoNFCText.setText("ERROR");
            addLineToConsole(e.getMessage());
        }
    }

    public boolean isConnected() {
        boolean connected = channel != null && channel.isConnected();

        new Thread(new Runnable() {
            @Override
            public void run()
            {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run()
                    {
                        estadoNFCText.setText(connected ? "CONECTADO":"DESCONECTADO");
                    }
                });
            }
        }).start();
        return connected;
    }

    private I2C_Enabled_Commands.R_W_Methods method;

    public void fastWrite(){
        method = I2C_Enabled_Commands.R_W_Methods.Fast_Mode;
        if (isConnected()) {
            try {
                final byte[] command = new byte[64];
                command[0] = (byte) 0xf0;

                // package number
                command[1] = (byte) 0x01;
                command[2] = (byte) 0x00;
                command[3] = (byte) 0x00;
                command[4] = (byte) 0x00;

                // Amount of transfer
                command[5] = (byte) 0x02;
                command[6] = (byte) 0x00;
                command[7] = (byte) 0x00;
                command[8] = (byte) 0x00;

                List<byte[]> blocks = new ArrayList<>();

                blocks.add(ByteUtils.hexToBytes("686F6C61000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"));
                blocks.add(ByteUtils.hexToBytes("7175652074616C000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"));
                byte[] aa = ByteUtils.hexToBytes("686F6C610000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000007175652074616C000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");

                appendFileCrc(aa,command);

                byte packageChecksum = 0x00;
                for (int i = 0; i < 11; i++) {
                    packageChecksum += command[i];
                }

                packageChecksum = (byte) (~packageChecksum + 1);

                command[11] = packageChecksum;

                addLineToConsole("SENDING CMD " + ByteUtils.bytesToHex(command));

                channel.waitforI2Cread(DELAY_TIME);

                channel.writeSRAMBlock(command, null);

                for (int i = 0; i < blocks.size(); i++) {
                    addLineToConsole("escribiendo paquete " + i);
                    channel.waitforI2Cread(DELAY_TIME);
                    addLineToConsole("SENDING " + ByteUtils.bytesToHex(blocks.get(i)));
                    channel.writeSRAMBlock(blocks.get(i), null);
                }
                readBlock(null);
            } catch (Exception e) {
                addLineToConsole(e.getMessage());
            }
        }

    }

    public void sendFile() {
        if (isConnected()) {
            final byte[] command = new byte[64];
            command[0] = (byte) 0xf0;

            // package number
            command[1] = (byte) 0x01;
            command[2] = (byte) 0x00;
            command[3] = (byte) 0x00;
            command[4] = (byte) 0x00;

            // Amount of transfer
            command[5] = (byte) 0x01;
            command[6] = (byte) 0x00;
            command[7] = (byte) 0x00;
            command[8] = (byte) 0x00;

            // file checksum
            command[9] = (byte) 0x04;
            command[10] = (byte) 0x00;

            appendFileCrc(createMockFile(),command);

            byte packageChecksum = 0x00;

            for (int i=0; i < 11; i++) {
                packageChecksum += command[i];
            }

            packageChecksum = (byte) (~packageChecksum +1);

            command[11] = packageChecksum;

            try {
                addLineToConsole("SENDING CMD " + ByteUtils.bytesToHex(command));

                channel.waitforI2Cwrite(DELAY_TIME);
                channel.writeSRAMBlock(command, null);

                channel.waitforI2Cwrite(DELAY_TIME);
                addLineToConsole("SENDING " + ByteUtils.bytesToHex(createMockFile()));
                channel.writeSRAMBlock(createMockFile(), new WriteSRAMListener() {
                    @Override
                    public void onWriteSRAM(){
                        readBlock(null);
                    }
                });


            } catch (TimeoutException | IOException | FormatException e) {
                addLineToConsole(e.getMessage());
            }
        }
    }

    public void appendFileCrc(byte[] file, byte[] command) {
        int crc = 0x0000;

        for(byte b : file)
        {
            crc +=b;
        }

        command[9] = (byte) ((crc & 0x000000ff));
        command[10] = (byte) ((crc & 0x0000ff00) >>> 8);
    }

    public byte[] createMockFile() {
        final byte[] command = new byte[64];
        command[0] = (byte) 0x01;
        command[1] = (byte) 0x01;
        command[2] = (byte) 0x01;
        command[3] = (byte) 0x01;

        return command;
    }

    public boolean readBlock(Integer timeout) {
        addLineToConsole("READING BLOCK");
        if (isConnected()) {
            try {
                channel.writeSRAMBlock(ExoCommands.CMD_64BYTES_RECEIVE.getBytes(), new WriteSRAMListener() {
                    @Override
                    public void onWriteSRAM() {
                        try{
                            channel.waitforI2Cread(timeout != null ? timeout:DELAY_TIME);
                            byte[] dataRead = channel.readSRAMBlock(null);
                            addLineToConsole("RECEIVED "+ ByteUtils.bytesToHex(dataRead));
                        } catch(Exception e){
                            addLineToConsole(e.getMessage());
                        }

                    }
                });
            } catch (IOException | FormatException e) {
                addLineToConsole(e.getMessage());
            }
        } else {
            return false;
        }
        return true;
    }

    public void readFile() {
        addLineToConsole("READING FILE");
        if (isConnected()) {
            try {
                channel.waitforI2Cread(DELAY_TIME);
                channel.writeSRAMBlock(ExoCommands.CMD_FILE_RECEIVE.getBytes(), new WriteSRAMListener() {
                    @Override
                    public void onWriteSRAM() {
                        try{
                            channel.waitforI2Cread(DELAY_TIME);
                            channel.readSRAMBlock(new ReadSRAMListener() {
                                @Override
                                public void onReadSRAM(byte[] dataRead) {
                                    try {
                                        addLineToConsole("RECEIVED "+ ByteUtils.bytesToHex(dataRead));
                                        addLineToConsole("SENDING OK CMD");
                                        channel.writeSRAMBlock(ExoCommands.CMD_SEND_OK.getBytes(), null);
                                    } catch(Exception e){
                                        addLineToConsole(e.getMessage());
                                    }
                                }
                            });

                        } catch(Exception e){
                            addLineToConsole(e.getMessage());
                        }
                    }
                });

            } catch (TimeoutException | IOException | FormatException e) {
                addLineToConsole(e.getMessage());
            }
        }
    }

}
