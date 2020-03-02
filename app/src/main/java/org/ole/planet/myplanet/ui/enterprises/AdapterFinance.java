package org.ole.planet.myplanet.ui.enterprises;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import io.realm.RealmResults;

public class AdapterFinance extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private Context context;
    private RealmResults<RealmMyTeam> list;


    public AdapterFinance(Context context, RealmResults<RealmMyTeam> list) {
        this.context = context;
        this.list = list;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.row_finance, parent, false);
        return new ViewHolderFinance(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ViewHolderFinance) {
            ((ViewHolderFinance) holder).date.setText(TimeUtils.formatDate(list.get(position).getDate(), "MMM dd, yyyy"));
            ((ViewHolderFinance) holder).note.setText(list.get(position).getDescription());
            Utilities.log("Type " + list.get(position).getDate());
            if (TextUtils.equals(list.get(position).getType().toLowerCase(), "debit")) {
                ((ViewHolderFinance) holder).debit.setText(list.get(position).getAmount() + "");
                ((ViewHolderFinance) holder).credit.setText(" -");
                ((ViewHolderFinance) holder).credit.setTextColor(Color.BLACK);
            } else {
                ((ViewHolderFinance) holder).credit.setText(list.get(position).getAmount() + "");
                ((ViewHolderFinance) holder).debit.setText(" -");
                ((ViewHolderFinance) holder).debit.setTextColor(Color.BLACK);
            }
            ((ViewHolderFinance) holder).balance.setText(getBalance(position) + "");
            updateBackgroundColor(((ViewHolderFinance) holder).row, position);
        }
    }

    private String getBalance(int position) {
        int i = 0;
        int balance = 0;
        Utilities.log(position + "");
        for (RealmMyTeam team : list) {
            if ("debit".equalsIgnoreCase(team.getType().toLowerCase())) balance -= team.getAmount();
            else balance += team.getAmount();
            if (i == position)
                break;
            i++;
        }
        return balance + "";
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public void updateBackgroundColor(LinearLayout layout, int position) {
        if (position % 2 < 1) {
            GradientDrawable border = new GradientDrawable();
            border.setColor(0xFFFFFFFF); //white background
            border.setStroke(1, context.getResources().getColor(R.color.black_overlay));
            border.setGradientType(GradientDrawable.LINEAR_GRADIENT);
            Drawable[] layers = {border};
            LayerDrawable layerDrawable = new LayerDrawable(layers);
            layerDrawable.setLayerInset(0, -10, 0, -10, 0);
            layout.setBackground(layerDrawable);
        }
    }

    class ViewHolderFinance extends RecyclerView.ViewHolder {
        TextView date, note, credit, debit, balance;
        LinearLayout row;

        public ViewHolderFinance(View itemView) {
            super(itemView);
            row = itemView.findViewById(R.id.llayout);
            date = itemView.findViewById(R.id.date);
            note = itemView.findViewById(R.id.note);
            credit = itemView.findViewById(R.id.credit);
            debit = itemView.findViewById(R.id.debit);
            balance = itemView.findViewById(R.id.balance);
        }
    }
}
