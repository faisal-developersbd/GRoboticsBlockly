package faisal.grobotics.com.groboticsblockly;

import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.Toast;

import com.google.blockly.android.AbstractBlocklyActivity;
import com.google.blockly.android.BlocklySectionsActivity;
import com.google.blockly.android.codegen.CodeGenerationRequest;
import com.google.blockly.android.codegen.LoggingCodeGeneratorCallback;
import com.google.blockly.android.control.BlocklyController;
import com.google.blockly.model.DefaultBlocks;
import com.google.blockly.utils.BlockLoadingException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class MainActivity extends BlocklySectionsActivity {
    private static final String TAG = "TurtleActivity";


    private static final String SAVE_FILENAME = "turtle_workspace.xml";
    private static final String AUTOSAVE_FILENAME = "turtle_workspace_temp.xml";
    BluetoothAdapter myBluetooth = null;
    BluetoothSocket btSocket = null;
    private boolean isBtConnected = false;
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    String user_id;
    SharedPreferences sharedPreferences;
    boolean disconnected = false;
    String address = null;
    private ProgressDialog progress;
    private Button runBtn;
    private Button bluetoothBtn;
    static final List<String> TURTLE_BLOCK_DEFINITIONS = Arrays.asList(
            DefaultBlocks.COLOR_BLOCKS_PATH,
            DefaultBlocks.LOGIC_BLOCKS_PATH,
            DefaultBlocks.LOOP_BLOCKS_PATH,
            DefaultBlocks.MATH_BLOCKS_PATH,
            DefaultBlocks.TEXT_BLOCKS_PATH,
            DefaultBlocks.VARIABLE_BLOCKS_PATH,
            "turtle/turtle_blocks.json"
    );

    static final List<String> TURTLE_BLOCK_GENERATORS = Arrays.asList(
            "turtle/generators.js"
    );

    private static final int MAX_LEVELS = 0;
    private static final String[] LEVEL_TOOLBOX = new String[1];

    static {
        LEVEL_TOOLBOX[0] = "toolbox_basic.xml";

    }

    private final Handler mHandler = new Handler();
    private WebView mTurtleWebview;
    private final CodeGenerationRequest.CodeGeneratorCallback mCodeGeneratorCallback =
            new CodeGenerationRequest.CodeGeneratorCallback() {

                @Override
                public void onFinishCodeGeneration(final String generatedCode) {

                    // Sample callback.
                    //Log.i(TAG, "generatedCode:\n" + generatedCode);
                    Toast.makeText(getBaseContext(),"Generated code: "+generatedCode,Toast.LENGTH_LONG).show();
                    String chk = generatedCode.substring(0, 3);
                    String data = "";
                    if (chk.equals("for")) {
                        String nb = generatedCode.substring(28, 29);
                        int mx = Integer.valueOf(nb);
                        data = generatedCode.substring(generatedCode.indexOf('{') + 1, generatedCode.indexOf('}') - 1);
                        String end = generatedCode.substring(generatedCode.indexOf('}') + 1);
                        data = data.replace('\n' + "  ", '\n' + "");
                        data = data.substring(1);
                        String data1 = "";
                        for (int i = 0; i < mx; i++) {
                            if (i != mx - 1)
                                data1 += data + '\n';
                            else
                                data1 += data;

                        }
                        data = data1 + end;
                        Toast.makeText(getApplicationContext(), data,
                                Toast.LENGTH_LONG).show();


                    } else
                        data = generatedCode + '\n';

                    if (btSocket != null) {
                        try {
                            btSocket.getOutputStream().write(data.getBytes());
                        } catch (IOException e) {
                            msg("Process Failed");
                        }
                    }

                    Toast.makeText(getApplicationContext(), "RUNNING",
                            Toast.LENGTH_LONG).show();

                   /* mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            String encoded = "Turtle.execute("
                                    + JavascriptUtil.makeJsString(generatedCode) + ")";
                            mTurtleWebview.loadUrl("javascript:" + encoded);
                        }
                    });*/

                }
            };

    @Override
    public void onLoadWorkspace() {
        mBlocklyActivityHelper.loadWorkspaceFromAppDirSafely(SAVE_FILENAME);
    }

    @Override
    public void onSaveWorkspace() {
        mBlocklyActivityHelper.saveWorkspaceToAppDirSafely(SAVE_FILENAME);
    }


    @NonNull
    @Override
    protected void playClicked(View view) {
        runCode();
    }

    @NonNull
    @Override
    protected void blueToothPress(View view) {
        bluetoothFunction();
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return onDemoItemSelected(item, this) || super.onOptionsItemSelected(item);
    }


    static boolean onDemoItemSelected(MenuItem item, AbstractBlocklyActivity activity) {
        BlocklyController controller = activity.getController();
        int id = item.getItemId();
        boolean loadWorkspace = false;
        String filename = "";

//        if(id==R.id.action_blue){
//            if(this.getController().getWorkspace().hasBlocks()) {
//                this.onRunCode();
//            } else {
//                Log.i("AbstractBlocklyActivity", "No blocks in workspace. Skipping run request.");
//            }
//        }


        if (loadWorkspace) {
            String assetFilename = "/demo_workspaces/" + filename;
            try {
                controller.loadWorkspaceContents(activity.getAssets().open(assetFilename));
            } catch (IOException | BlockLoadingException e) {
                throw new IllegalStateException(
                        "Couldn't load demo workspace from assets: " + assetFilename, e);
            }
            addDefaultVariables(controller);
            return true;
        }

        return false;
    }

    @NonNull
    @Override
    protected List<String> getBlockDefinitionsJsonPaths() {
        // Use the same blocks for all the levels. This lets the user's block code carry over from
        // level to level. The set of blocks shown in the toolbox for each level is defined by the
        // toolbox path below.
        return TURTLE_BLOCK_DEFINITIONS;
    }

    @Override
    protected int getActionBarMenuResId() {
        return R.menu.turtle_actionbar;
    }

    @NonNull
    @Override
    protected List<String> getGeneratorsJsPaths() {

        return TURTLE_BLOCK_GENERATORS;
    }

    @NonNull
    @Override
    protected String getToolboxContentsXmlPath() {
        // Expose a different set of blocks to the user at each level.
        return "turtle/" + LEVEL_TOOLBOX[0];
    }

    @Override
    protected void onInitBlankWorkspace() {
        addDefaultVariables(getController());
    }

    @NonNull
    @Override
    protected void playButtonClicked(View view) {
             if (getController().getWorkspace().hasBlocks()) {
        onRunCode();
    } else {
        Log.i(TAG, "No blocks in workspace. Skipping run request.");
    }
    }

    @NonNull
    @Override
    protected ListAdapter onCreateSectionsListAdapter() {
        // Create the game levels with the labels "Level 1", "Level 2", etc., displaying
        // them as simple text items in the sections drawer.
        String[] levelNames = new String[MAX_LEVELS];
        for (int i = 0; i < MAX_LEVELS; ++i) {
            levelNames[i] = "Level " + (i + 1);
        }
        return new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_activated_1,
                android.R.id.text1,
                levelNames);
    }

    @Override
    protected boolean onSectionChanged(int oldSection, int newSection) {
        reloadToolbox();
        return true;
    }

    View decorView;

    @Override
    protected View onCreateContentView(int parentId) {
        View root = getLayoutInflater().inflate(R.layout.turtle_content, null);
        decorView = root;
        mTurtleWebview = (WebView) root.findViewById(R.id.turtle_runtime);
        mTurtleWebview.getSettings().setJavaScriptEnabled(true);
        mTurtleWebview.setWebChromeClient(new WebChromeClient());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        mTurtleWebview.loadUrl("file:///android_asset/turtle/turtle.html");
        Intent newint = getIntent();
        address = newint.getStringExtra(BluetoothConnection.EXTRA_ADDRESS); //receive the address of the bluetooth device
        if (address != null) {
            new ConnectBT().execute();
        }

//        View someView = findViewById(R.id.screen);
//       View  root2= someView.getRootView();
        // root.setBackgroundColor(getResources().getColor(android.R.color.black));
        return root;
    }

    public void runCode() {
        final AbstractBlocklyActivity activity = this;
        //BlocklyController controller = activity.getController();
        if (this.getController().getWorkspace().hasBlocks()) {
            this.onRunCode();
        } else {
            Log.i("AbstractBlocklyActivity", "No blocks in workspace. Skipping run request.");
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);



    }

    public void bluetoothFunction() {
        Intent intent = new Intent(MainActivity.this, BluetoothConnection.class);
        startActivity(intent);
    }

    @NonNull
    @Override
    protected CodeGenerationRequest.CodeGeneratorCallback getCodeGenerationCallback() {
        return mCodeGeneratorCallback;
    }

    static void addDefaultVariables(BlocklyController controller) {
        // TODO: (#22) Remove this override when variables are supported properly
        controller.addVariable("item");
        controller.addVariable("count");
        controller.addVariable("marshmallow");
        controller.addVariable("lollipop");
        controller.addVariable("kitkat");
        controller.addVariable("android");
    }

    @Override
    @NonNull
    protected String getWorkspaceSavePath() {
        return SAVE_FILENAME;
    }

    @Override
    @NonNull
    protected String getWorkspaceAutosavePath() {
        return AUTOSAVE_FILENAME;
    }

    private void msg(String s) {
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show();
    }


    public void connect(View view) {
        if (btSocket != null)
            disconnect();
        Intent intent = new Intent(MainActivity.this, BluetoothConnection.class);
        startActivity(intent);
    }


    public void disconnect() {
        try {
            disconnected = true;
            Thread.sleep(1000);
            if (btSocket != null) {
                try {
                    btSocket.close();
                } catch (IOException e) {
                    msg("Error");
                }
            }
            finish();

        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    public void bluetooth(MenuItem item) {
        Intent intent = new Intent(MainActivity.this, BluetoothConnection.class);
        startActivity(intent);
    }

    public void runableC(View view) {


    }


    private class ConnectBT extends AsyncTask<Void, Void, Void> {
        private boolean ConnectSuccess = true;

        @Override
        protected void onPreExecute() {
            progress = ProgressDialog.show(MainActivity.this, "Connecting...", "Please wait!!!");
        }

        @Override
        protected Void doInBackground(Void... devices) {
            try {
                if (btSocket == null || !isBtConnected) {
                    myBluetooth = BluetoothAdapter.getDefaultAdapter();//get the mobile bluetooth device
                    BluetoothDevice dispositivo = myBluetooth.getRemoteDevice(address);//connects to the device's address and checks if it's available
                    btSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(myUUID);//create a RFCOMM (SPP) connection
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    btSocket.connect();//start connection

                }
            } catch (IOException e) {
                ConnectSuccess = false;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);

            if (!ConnectSuccess) {
                msg("Connection Failed.  Try again.");
                finish();
            } else {
                msg("Connected.");

                isBtConnected = true;
            }
            progress.dismiss();
        }




    }
}