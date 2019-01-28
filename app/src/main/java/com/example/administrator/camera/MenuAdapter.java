package com.example.administrator.camera;

import android.content.Context;
import android.graphics.Color;
import android.support.v7.widget.RecyclerView;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

/**
 * Created by jianglei on 2/4/17.
 */

public class MenuAdapter extends RecyclerView.Adapter<MenuAdapter.ItemViewHolder> implements AutoLocateHorizontalView.IAutoLocateHorizontalView {
    private Context context;
    private View view;
    private List<String> ages;

    public MenuAdapter(Context context, List<String> ages){
        this.context = context;
        this.ages = ages;
    }

    @Override
    public ItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        view = LayoutInflater.from(context).inflate(R.layout.item_age,parent,false);
        return new ItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ItemViewHolder holder, int position) {
        holder.tvAge.setText(ages.get(position));
    }

    @Override
    public int getItemCount() {
        return  ages.size();
    }

    @Override
    public View getItemView() {
        return view;
    }

    @Override
    public void onViewSelected(boolean isSelected,int pos, RecyclerView.ViewHolder holder,int itemWidth) {
        if(isSelected) {
            ((ItemViewHolder) holder).tvAge.setTextSize(TypedValue.COMPLEX_UNIT_SP,16);
            ((ItemViewHolder) holder).tvAge.setTextColor(Color.WHITE);
        }else{
            ((ItemViewHolder) holder).tvAge.setTextSize(TypedValue.COMPLEX_UNIT_SP,15);
            ((ItemViewHolder) holder).tvAge.setTextColor(Color.rgb(0xfe,0xfe,0xfe));
        }
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder{
        TextView tvAge;
        ItemViewHolder(View itemView) {
            super(itemView);
            tvAge = (TextView)itemView.findViewById(R.id.tv_age);
        }
    }
}
