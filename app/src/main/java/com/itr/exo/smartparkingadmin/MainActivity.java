package com.itr.exo.smartparkingadmin;

import android.app.PendingIntent;
import android.content.Intent;
import android.nfc.FormatException;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.support.v4.widget.NestedScrollView;
import android.util.Log;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.nxp.crypto.CRC32Calculator;
import com.nxp.listeners.WriteSRAMListener;
import com.nxp.reader.I2C_Enabled_Commands;
import com.nxp.reader.Ntag_I2C_Commands;
import com.nxp.ByteUtils;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;


public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    NfcAdapter nfcAdapter;

    PendingIntent pendingIntent;

    private TextView myText = null;
    private TextView estadoNFCText = null;

    private NestedScrollView consoleScroll = null;

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
            return true;
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
        //Timber.e("NEW INTENT %s", intent.getAction());
        setIntent(intent);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(getIntent().getAction())) {
            processIntent(getIntent());
        }

        if (nfcAdapter != null) {
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
        }
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
        // only one message sent during the beam
        //logger.send("DISCOVERED", tag.toString());
        //nfcStateSubject.discovered();
        addLineToConsole("Tag descubierto "+tag.toString());
        //Toast.makeText(getApplicationContext(), "Tag descubierto", Toast.LENGTH_SHORT).show();
        connect(tag);
    }

    //NfcStateSubject nfcStateSubject;

    private Ntag_I2C_Commands channel;

    public void addLineToConsole(String line){
        myText.append("\n"+line);
        consoleScroll.post(new Runnable() {
            @Override
            public void run() {
                consoleScroll.fullScroll(View.FOCUS_DOWN);
            }
        });
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
        readBlock();
    }

    public void CMD_RESET(View v){
        addLineToConsole("CMD_RESET");
        if (isConnected()) {
            final byte[] command = new byte[64];
            command[0] = (byte) 0x00;
            command[1] = (byte) (~( 0x00) + 1);

            try {
                channel.waitforI2Cread(100);
                channel.writeSRAMBlock(command, null);

            } catch (TimeoutException | IOException | FormatException e) {
                //Timber.e(e);
            }
        }
    }

    public void CMD_PS_MODE_WORKING(View v){
        addLineToConsole("CMD_PS_MODE_WORKING");
        if (isConnected()) {
            final byte[] command = new byte[64];
            command[0] = (byte) 0x01;
            command[1] = (byte) (~( 0x01) + 1);

            try {
                channel.waitforI2Cread(100);
                channel.writeSRAMBlock(command, null);

            } catch (TimeoutException | IOException | FormatException e) {
                //Timber.e(e);
            }
        }
    }

    public void CMD_PS_MODE_STORAGE(View v){
        addLineToConsole("CMD_PS_MODE_STORAGE");
        if (isConnected()) {
            final byte[] command = new byte[64];
            command[0] = (byte) 0x02;
            command[1] = (byte) (~( 0x02) + 1);

            try {
                channel.waitforI2Cread(100);
                channel.writeSRAMBlock(command, null);

            } catch (TimeoutException | IOException | FormatException e) {
                //Timber.e(e);
            }
        }
    }

    public void CMD_PS_TEST_ALL(View v){
        addLineToConsole("CMD_PS_TEST_ALL");
        if (isConnected()) {
            final byte[] command = new byte[64];
            command[0] = (byte) 0x04;
            command[1] = (byte) (~( 0x04) + 1);

            try {
                channel.waitforI2Cread(100);
                channel.writeSRAMBlock(command, null);

            } catch (TimeoutException | IOException | FormatException e) {
                //Timber.e(e);
            }
        }
    }

    public void CMD_SET_CFG_RADIO(View v){
        addLineToConsole("CMD_SET_CFG_RADIO");
        if (isConnected()) {
            final byte[] command = new byte[64];
            command[0] = (byte) 0xB5;
            command[1] = (byte) (~( 0xB5) + 1);

            try {
                channel.waitforI2Cread(100);
                channel.writeSRAMBlock(command, null);

            } catch (TimeoutException | IOException | FormatException e) {
                //Timber.e(e);
            }
        }
    }

    public void connect(Tag tag) {
        try {
            channel = new Ntag_I2C_Commands(tag);
            channel.connect();
            //Toast.makeText(getApplicationContext(), "Conectado a tag", Toast.LENGTH_SHORT).show();
            addLineToConsole("Conectado a tag");
            //addLineToConsole("fast write " + channel.checkPTwritePossible());
            estadoNFCText.setText("CONNECTED");
        } catch (Exception e) {
            estadoNFCText.setText("ERROR");
            addLineToConsole(e.getMessage());
        }
    }

    public boolean isConnected() {
        boolean connected = channel != null && channel.isConnected();
        estadoNFCText.setText(connected ? "CONNECTED":"DISCONNECTED");
//        if(!connected)
//            Toast.makeText(getApplicationContext(), "No esta conectado", Toast.LENGTH_SHORT).show();
        return connected;
    }

    private I2C_Enabled_Commands.R_W_Methods method;
    private static final int DELAY_TIME 		= 100;

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

                // file checksum
                command[9] = (byte) 0x04;
                command[10] = (byte) 0x00;

                byte packageChecksum = 0x00;
                List<byte[]> blocks = new ArrayList<byte[]>();
                final byte[] ba = new byte[64];
                final byte[] bb = new byte[64];

                ba[0] = (byte) 0x68;
                ba[1] = (byte) 0x6f;
                ba[2] = (byte) 0x6c;
                ba[3] = (byte) 0x61;


                bb[0] = 0x71;
                bb[1] = 0x75;
                bb[2] = 0x65;
                bb[3] = 0x20;
                bb[4] = 0x74;
                bb[5] = 0x61;
                bb[6] = 0x6c;
                CRC32Calculator crc = new CRC32Calculator();
//                blocks.add(crc.CRC32(createMockFile()));
//                blocks.add(crc.CRC32(createMockFile()));
                blocks.add((createMockFile()));
                blocks.add((createMockFile()));

                for (int i = 0; i < 11; i++) {
                    packageChecksum += command[i];
                }

                packageChecksum = (byte) (~packageChecksum + 1);

                command[11] = packageChecksum;

                channel.waitforI2Cread(DELAY_TIME);

                channel.writeSRAMBlock(command, null);

                for (int i = 0; i < blocks.size(); i++) {
                    addLineToConsole("escribiendo paquete " + i);
                    channel.waitforI2Cread(DELAY_TIME);
                    channel.writeSRAMBlock(blocks.get(i), new WriteSRAMListener() {
                        @Override
                        public void onWriteSRAM(){
                            readBlock();
                        }
                    });
                }
            } catch (Exception e) {

            }
        }

    }

    public void sendFile() {
        addLineToConsole("Sending file Channel connected "+ isConnected());
        //Log.v("Sending file","Channel connected "+ (channel!=null && channel.isConnected()));
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

            byte packageChecksum = 0x00;

            for (int i=0; i < 11; i++) {
                packageChecksum += command[i];
            }

            packageChecksum = (byte) (~packageChecksum +1);

            command[11] = packageChecksum;

            try {
                addLineToConsole("SENDING " + ByteUtils.bytesToHex(command));
                Log.v("SENDING", "enviando");
                channel.waitforI2Cwrite(100);
                channel.writeSRAMBlock(command, null);
                Thread.sleep(10);

                channel.waitforI2Cwrite(100); // @Eric - Tiempo de timeot a respuesta del tag (decia 100)
                channel.writeSRAMBlock(createMockFile(), new WriteSRAMListener() {
                    @Override
                    public void onWriteSRAM(){
                        readBlock();
                    }
                });


            } catch (InterruptedException | TimeoutException | IOException | FormatException e) {
                Log.v("error","volo todo");
                //Timber.e(e);
            }
        }
    }

    public byte[] createMockFile() {
        final byte[] command = new byte[64];
        command[0] = (byte) 0x01;
        command[1] = (byte) 0x01;
        command[2] = (byte) 0x01;
        command[3] = (byte) 0x01;

        return command;
    }

    public boolean readBlock() {
        addLineToConsole("trying to read");
        if (isConnected()) {
            final byte[] command = new byte[64];
            command[0] = (byte) 0xf2;
            command[1] = (byte) (~( 0xf2) + 1);

            try {
                channel.waitforI2Cread(100);
                channel.writeSRAMBlock(command, null);
                Thread.sleep(10);

                channel.waitforI2Cread(100);

                byte[] dataRead = channel.readSRAMBlock();

                addLineToConsole("RECEIVED "+ ByteUtils.bytesToHex(dataRead));
//                addLineToConsole("RECEIVED "+ (dataRead[0] == 0x31));

            } catch (InterruptedException | TimeoutException | IOException | FormatException e) {
                //Timber.e(e);
            }
        } else {
            return false;
        }
        return true;
    }

    public void readFile() {
        //Timber.d("Sending file. Channel connected %b", channel.isConnected());
        if (channel.isConnected()) {
            final byte[] command = new byte[64];
            command[0] = (byte) 0xf1;
            command[1] = (byte) (~( 0xf1) + 1);

            try {
                channel.waitforI2Cread(100);
                channel.writeSRAMBlock(command, null);
                Thread.sleep(10);

                channel.waitforI2Cread(100);

                byte[] dataRead = channel.readSRAMBlock();

                //Timber.d("RECEIVED %s", ByteUtils.bytesToHex(dataRead));
                //logger.send("RECEIVED", command);

            } catch (InterruptedException | TimeoutException | IOException | FormatException e) {
                //Timber.e(e);
            }
        }
    }
}
