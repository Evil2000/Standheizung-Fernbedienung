package de.evil2000.standheizungfernbedienung;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.TwoLineListItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Created by dave on 08.12.17.
 */

public class StatesAdapter extends BaseAdapter {
    private Context context;
    private ArrayList<StateItem> states;

    public StatesAdapter(Context context) {
        this.context = context;
        this.states = new ArrayList<StateItem>();
    }

    public StatesAdapter(Context context, ArrayList<StateItem> states) {
        this.context = context;
        this.states = states;
    }

    public void add(StateItem item) {
        this.states.add(item);
        //this.notifyDataSetChanged();
    }

    public void insert(int index, StateItem item) {
        this.states.add(index, item);
        //this.notifyDataSetChanged();
    }

    public void remove(StateItem item) {
        this.states.remove(item);
        //this.notifyDataSetChanged();
    }

    public void remove(int index) {
        this.states.remove(index);
        //this.notifyDataSetChanged();
    }

    public void sortByTimestamp() {
        sortByTimestamp(false);
    }
    public void sortByTimestamp(final boolean sortReverse) {
        Collections.sort(states, new Comparator<StateItem>() {
            @Override
            public int compare(StateItem item, StateItem item1) {
                return item.getTimestamp().compareTo(item1.getTimestamp())*(sortReverse?-1:1);
            }
        });
    }

    @Override
    public int getCount() {
        return states.size();
    }

    @Override
    public StateItem getItem(int position) {
        return states.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        TwoLineListItem twoLineListItem;

        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            twoLineListItem = (TwoLineListItem) inflater.inflate(
                    android.R.layout.simple_list_item_2, null);
        } else {
            twoLineListItem = (TwoLineListItem) convertView;
        }

        if (getCount() > 0) {
            TextView text1 = twoLineListItem.getText1();
            TextView text2 = twoLineListItem.getText2();
            text1.setText(states.get(position).getStateString());
            text2.setText(states.get(position).getFormatedTimestamp());
        }

        return twoLineListItem;
    }
}
