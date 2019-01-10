package com.Simoorg.EasyTrackManipulator;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import java.util.ArrayList;

import abak.tr.com.boxedverticalseekbar.BoxedVertical;

public class EQActivity extends AppCompatActivity {

    static int eqVals[] = {0, 0, 0, 0, 0, 0, 0};
    ArrayList<BoxedVertical> EqBands = new ArrayList<>();

    static ArrayList<int[]> presets = new ArrayList<int[]>() {{
        add(new int[]{0, 0, 0, 0, 0, 0, 0});
        add(new int[]{-6, -4, -1, 0, 0, 0, 0});
        add(new int[]{3, 2, 0, -1, 0, 2, 3});
        add(new int[]{0, 0, 1, 3, 2, 0, 0});
    }};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_eq);
        PrepareEqSeekbars();
        PrepareSpinner();
    }

    //region Preparation methods
    private void PrepareEqSeekbars() {

        EqBands.add((BoxedVertical) findViewById(R.id.boxed_vertical));
        EqBands.add((BoxedVertical) findViewById(R.id.boxed_vertical2));
        EqBands.add((BoxedVertical) findViewById(R.id.boxed_vertical3));
        EqBands.add((BoxedVertical) findViewById(R.id.boxed_vertical4));
        EqBands.add((BoxedVertical) findViewById(R.id.boxed_vertical5));
        EqBands.add((BoxedVertical) findViewById(R.id.boxed_vertical6));
        EqBands.add((BoxedVertical) findViewById(R.id.boxed_vertical7));

        for (int i = 0; i < EqBands.size(); i++) {
            EqBands.get(i).setOnBoxedPointsChangeListener(getListener(i));
            EqBands.get(i).setValue(eqVals[i]);
        }
    }

    private BoxedVertical.OnValuesChangeListener getListener(final int index) {
        return new BoxedVertical.OnValuesChangeListener() {
            @Override
            public void onPointsChanged(BoxedVertical boxedPoints, final int value) {
                eqVals[index] = value;
                SetEQBand(index, eqVals[index]);
            }

            @Override
            public void onStartTrackingTouch(BoxedVertical boxedVertical) {
            }

            @Override
            public void onStopTrackingTouch(BoxedVertical boxedPoints) {

            }
        };
    }

    private void PrepareSpinner() {
        Spinner spinner = findViewById(R.id.spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.eq_presets, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                for (int i = 0; i < EqBands.size(); i++) {
                    EqBands.get(i).setValue(presets.get(position)[i]);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    //endregion

    public void ResetEQClick(View view) {
        Spinner spinner = findViewById(R.id.spinner);
        if (spinner.getSelectedItemPosition() == 0)
            for (int i = 0; i < eqVals.length; i++) {
                EqBands.get(i).setValue(0);
                SetEQBand(i, eqVals[i]);
            }
        else
            spinner.setSelection(0);
    }

    private native void SetEQBand(int index, int gain);
}
