package com.led_on_off.led;

import android.hardware.Camera;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.AsyncTask;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import static java.lang.Thread.sleep;

public class ledControl extends ActionBarActivity {
    private int inactive = 0x8056B3CD;
    private int active = 0x8056cdab;
    private boolean pressedDown = false;
   // Button btnOn, btnOff, btnDis;
    ImageButton Play, Discnt;

    String address = null;
    private ProgressDialog progress;
    BluetoothAdapter myBluetooth = null;
    BluetoothSocket btSocket = null;
    private boolean isBtConnected = false;
    private Camera c = null;
    private CameraPreview mPreview = null;
    ImageView top = null;
    ImageView bot = null;
    public static int currentTopY = 0;
    public static int currentBotY = 0;
    boolean topBarBlack = false;
    boolean botBarBlack = false;
    boolean openCamera = false;
    boolean isMoving = false;
    boolean playing = false;
    boolean safePic = true;
    //SPP UUID. Look for it
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    Handler handler = new Handler();

    static public void decodeYUV420SP(int[] rgb, byte[] yuv420sp, int width, int height) {
        final int frameSize = width * height;

        for (int j = 0, yp = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
            for (int i = 0; i < width; i++, yp++) {
                int y = (0xff & ((int) yuv420sp[yp])) - 16;
                if (y < 0) y = 0;
                if ((i & 1) == 0) {
                    v = (0xff & yuv420sp[uvp++]) - 128;
                    u = (0xff & yuv420sp[uvp++]) - 128;
                }

                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);

                if (r < 0) r = 0; else if (r > 262143) r = 262143;
                if (g < 0) g = 0; else if (g > 262143) g = 262143;
                if (b < 0) b = 0; else if (b > 262143) b = 262143;

                rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Intent newint = getIntent();
        address = newint.getStringExtra(DeviceList.EXTRA_ADDRESS); //receive the address of the bluetooth device

        //view of the ledControl
        setContentView(R.layout.activity_led_control);

        //call the widgets
        Play = (ImageButton)findViewById(R.id.play);
        Discnt = (ImageButton)findViewById(R.id.discnt);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);


        new ConnectBT().execute(); //Call the class to connect

        //commands to be sent to bluetooth
        Play.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                playFunc();      //method to turn on
            }
        });

        Discnt.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Disconnect(); //close connection
            }
        });

        try{
            c = Camera.open();
            c.setDisplayOrientation(90);
        }
        catch(Exception e){

        }
        Camera.Parameters params = c.getParameters();
        params.setPictureSize(1920,1080);
        c.setParameters(params);
        mPreview = new CameraPreview(this, c, ledControl.this);
        FrameLayout preview = (FrameLayout)findViewById(R.id.camera_preview);
        preview.addView(mPreview);
        top = (ImageView) findViewById(R.id.topBox);
        bot = (ImageView) findViewById(R.id.bottomBox);
        Camera.Size dimensions = c.getParameters().getPreviewSize();
        setMargins(top,0,100,0,0);
        setMargins(bot,0,dimensions.width-400,0,0);
        currentTopY = 100;
        currentBotY = dimensions.width-400;
        handler.post(runnableCode);
        handler.post(movingCode);
        File folder = new File(Environment.getExternalStorageDirectory() +
                File.separator + "ScannerApp");
        boolean success = true;
        if (!folder.exists()) {
            success = folder.mkdirs();
        }
    }
    private Runnable runnableCode = new Runnable() {
        @Override
        public void run() {
            Log.d("Handlers", "Called on main thread");
            if(playing)
                openCamera = true;
            handler.postDelayed(runnableCode, 1000);
        }
    };
    private Runnable movingCode = new Runnable() {
        @Override
        public void run() {
            Log.d("Handlers", "Moving reel");
            if(isMoving)
                try {
                    btSocket.getOutputStream().write('1');
                } catch (IOException e) {
                    e.printStackTrace();
                }
            handler.postDelayed(movingCode, 480);
        }
    };

    private void Disconnect()
    {
        if (btSocket!=null) //If the btSocket is busy
        {
            try
            {
                btSocket.close(); //close connection
            }
            catch (IOException e)
            { msg("Error");}
        }
        finish(); //return to the first layout

    }

    private void playFunc()
    {
        if (btSocket!=null)
        {
            isMoving = true;
            playing = true;
        }
    }

    // fast way to call Toast
    private void msg(String s)
    {
        Toast.makeText(getApplicationContext(),s,Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_led_control, menu);
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

    public void setMargins(View v,int l, int t, int r, int b) {
        if (v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            p.setMargins(l, t, r, b);
            v.requestLayout();
        }
    }

    public void barsChanged(boolean topIsBlack, boolean botIsBlack){
        if(topIsBlack != topBarBlack){
            topBarBlack = topIsBlack;
            if(topBarBlack){
                top.setBackgroundColor(active);
            }
            else {
                top.setBackgroundColor(inactive);
            }
        }

        if(botIsBlack != botBarBlack){
            botBarBlack = topIsBlack;
            if(botBarBlack){
                bot.setBackgroundColor(active);
            }
            else {
                bot.setBackgroundColor(inactive);
            }
        }
        if(topIsBlack && botIsBlack && openCamera) {
            isMoving = false;
            openCamera = false;
            boolean picturenottaken = true;
            while(picturenottaken) {
                try {
                    c.takePicture(null, null, rawPic);
                    picturenottaken = false;
                } catch (Exception e) {
                    Log.e("Camera", e.toString());
                }
            }
            msg("Picture taken");
            isMoving = true;
        }
    }

    Camera.PictureCallback rawPic = new Camera.PictureCallback(){
        public void onPictureTaken(byte[] data, Camera camera) {
            new SavePhotoTask().execute(data);
        }
    };

    private class SavePhotoTask extends AsyncTask<byte[], String, String> {
        @Override
        protected String doInBackground(byte[]... jpeg) {
            safePic = false;
            File photo = new File(Environment.getExternalStorageDirectory()+"/ScannerApp",
                    (new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date()))+".jpg");
            Log.d("picture", (Environment.getExternalStorageDirectory().toString()+"/Download"));
            if (photo.exists()) {
                photo.delete();
            }

            try {
                FileOutputStream fos=new FileOutputStream(photo.getPath());

                fos.write(jpeg[0]);
                fos.close();
            }
            catch (Exception e) {
                Log.e("PictureDemo", "Exception in photoCallback", e);
            }

            return(null);
        }
    }

    private class ConnectBT extends AsyncTask<Void, Void, Void>  // UI thread
    {
        private boolean ConnectSuccess = true; //if it's here, it's almost connected

        @Override
        protected void onPreExecute()
        {
            progress = ProgressDialog.show(ledControl.this, "Connecting...", "Please wait!!!");  //show a progress dialog
        }

        @Override
        protected Void doInBackground(Void... devices) //while the progress dialog is shown, the connection is done in background
        {
            try
            {
                if (btSocket == null || !isBtConnected)
                {
                 myBluetooth = BluetoothAdapter.getDefaultAdapter();//get the mobile bluetooth device
                 BluetoothDevice dispositivo = myBluetooth.getRemoteDevice(address);//connects to the device's address and checks if it's available
                 btSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(myUUID);//create a RFCOMM (SPP) connection
                 BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                 btSocket.connect();//start connection
                }
            }
            catch (IOException e)
            {

                ConnectSuccess = false;//if the try failed, you can check the exception here
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) //after the doInBackground, it checks if everything went fine
        {
            super.onPostExecute(result);

            if (!ConnectSuccess) {
                msg("Connection Failed. Is it a SPP Bluetooth? Try again.");
                finish();
            } else {
                msg("Connected.");
                isBtConnected = true;
            }
            progress.dismiss();
        }
    }
}


