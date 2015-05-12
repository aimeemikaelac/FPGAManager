package edu.colorado.cs.ngn.fpgamanager;

import android.content.Context;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;


public class MainActivity extends ActionBarActivity {
    private static String DEFAULT_DEVCFG_DIR = "/sys/class/xdevcfg/xdevcfg/device/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        EditText devcfgDirText = ((EditText)findViewById(R.id.devcfg_dir));
        devcfgDirText.setText(DEFAULT_DEVCFG_DIR);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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

    public void writeBitstreamOnClick(View view){
        boolean checked = ((ToggleButton) view).isChecked();
        if(checked) {
            String absoluteFileName = getFileName();
            boolean isPartialBitstream = checkIsPartialBox();

            CharSequence devcfgDir = ((EditText) findViewById(R.id.devcfg_dir)).getText();
            boolean written;
            if (DEFAULT_DEVCFG_DIR.compareTo(devcfgDir.toString()) != 0) {
                written = programBitstreamToFPGA(absoluteFileName, isPartialBitstream, devcfgDir.toString());
            } else {
                written = programBitstreamToFPGA(absoluteFileName, isPartialBitstream);
            }

            if (written) {
                Toast.makeText(getApplicationContext(), "Bitstream written successfully", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), "Bitstream written unsuccessfully", Toast.LENGTH_SHORT).show();
            }

            ((ToggleButton) view).setChecked(false);
        }
    }

    private boolean programBitstreamToFPGA(String absoluteFileName, boolean isPartialBitstream) {
        programBitstreamToFPGA(absoluteFileName, isPartialBitstream, DEFAULT_DEVCFG_DIR);
        return false;
    }

    private boolean programBitstreamToFPGA(String absoluteFileName, boolean isPartialBitstream, String currentDevcfgDir) {
        DataOutputStream rootStream = getRootOutputstream();
        if(rootStream == null){
            return false;
        }

        File devcfgFolder = new File(currentDevcfgDir);
        if(!devcfgFolder.exists()){
            writeErrorMessage("The devcfg driver folder does not exist");
            try {
                rootStream.close();
            } catch (IOException e) {
                writeErrorMessage("Could not close outputstream");
                writeErrorMessage("Stacktrace:\n"+e.toString());
            }
            return false;
        }
        if(!devcfgFolder.isDirectory()){
            writeErrorMessage("The devcfg driver path is not a folder");
            try {
                rootStream.close();
            } catch (IOException e) {
                writeErrorMessage("Could not close outputstream");
                writeErrorMessage("Stacktrace:\n" + e.toString());
            }
            return false;
        }
        return writeBitstream(absoluteFileName, isPartialBitstream, devcfgFolder, rootStream);
    }

    private boolean writeBitstream(String absoluteFileName, boolean isPartialBitstream, File devcfgDir, DataOutputStream rootStream) {
        ArrayList<String> cmds = new ArrayList<String>();
        File bitstreamFile = new File(absoluteFileName);
        if (!bitstreamFile.exists() || !bitstreamFile.isFile()) {
            writeErrorMessage("Cannot access bitstream file");
        }
        if (!bitstreamFile.isAbsolute()) {
            writeErrorMessage("Bitstream file path is not absolute");
        }
        File isPartialFile = new File(devcfgDir.getAbsolutePath() + File.separator + "is_partial_bitstream");
        if (!isPartialFile.exists() || !isPartialFile.isFile()) {
            writeErrorMessage("Could not access is_partial_bitstream file in the devcfg driver folder: " + devcfgDir.getAbsolutePath());
            return false;
        }
        File devcfg = new File("/dev/xdevcfg");
        if (!isPartialFile.exists() || !isPartialFile.isFile()) {
            writeErrorMessage("Could not access /dev/xdevcfg");
            return false;
        }
        String setPartialBitstream;
        if (isPartialBitstream) {
            setPartialBitstream = "echo 1 >> " + isPartialFile.getAbsoluteFile();
        } else {
            setPartialBitstream = "echo 0 >> " + isPartialFile.getAbsoluteFile();
        }
        cmds.add(setPartialBitstream);
        String writeBitstream = "cat " + bitstreamFile.getAbsoluteFile() + " >> " + devcfg.getAbsolutePath();
        cmds.add(writeBitstream);

        for (String cmd : cmds) {
            try {
                rootStream.writeBytes(cmd + "\n");
            } catch (IOException e) {
                writeErrorMessage("IOError encountered when writing command: " + cmd);
                writeErrorMessage("Stacktrace:\n" + e.toString());
                return false;
            }
        }
        try {
            rootStream.writeBytes("exit\n");
            rootStream.flush();
        } catch (IOException e) {
            writeErrorMessage("IOError encountered when flushing and exiting");
            writeErrorMessage("Stacktrace:\n" + e.toString());
        }
        return true;
    }

    private DataOutputStream getRootOutputstream(){
        Process p = null;
        try {
            p = Runtime.getRuntime().exec("su");
        } catch (IOException e) {
            e.printStackTrace();
            writeErrorMessage("IOException encountered executing \"su\"");
            writeErrorMessage("Stacktrace:\n"+e.toString());
            return null;
        }
        DataOutputStream os = new DataOutputStream(p.getOutputStream());
        return os;
    }

    private boolean checkIsPartialBox() {
        CheckBox isPartialBox = (CheckBox) findViewById(R.id.isPartialBitstreamBox);
        return isPartialBox.isChecked();
    }

    private void writeErrorMessage(String s) {
        TextView errorTextView = (TextView)findViewById(R.id.errorMessageDisplay);
        CharSequence currentText = errorTextView.getText();
        errorTextView.setText(currentText + "\n" + s);
        System.out.println("ERROR: "+s);
    }

    private String getFileName(){
        EditText filePathText = (EditText)findViewById(R.id.bitstreamFilePath);
        return filePathText.getText().toString();
    }
}
