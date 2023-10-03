package org.ole.planet.myplanet.ui.enterprises;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.RowFinanceBinding;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import io.realm.RealmResults;

public class AdapterFinance extends RecyclerView.Adapter<AdapterFinance.ViewHolderFinance> {
    private RowFinanceBinding rowFinanceBinding;
    private Context context;
    private RealmResults<RealmMyTeam> list;

    public AdapterFinance(Context context, RealmResults<RealmMyTeam> list) {
        this.context = context;
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolderFinance onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        rowFinanceBinding = RowFinanceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolderFinance(rowFinanceBinding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolderFinance holder, int position) {
        rowFinanceBinding.date.setText(TimeUtils.formatDate(list.get(position).getDate(), "MMM dd, yyyy"));
        rowFinanceBinding.note.setText(list.get(position).getDescription());
            Utilities.log("Type " + list.get(position).getDate());
            if (TextUtils.equals(list.get(position).getType().toLowerCase(), "debit")) {
                rowFinanceBinding.debit.setText(list.get(position).getAmount() + "");
                rowFinanceBinding.credit.setText(" -");
                rowFinanceBinding.credit.setTextColor(Color.BLACK);
            } else {
                rowFinanceBinding.credit.setText(list.get(position).getAmount() + "");
                rowFinanceBinding.debit.setText(" -");
                rowFinanceBinding.debit.setTextColor(Color.BLACK);
            }
            rowFinanceBinding.balance.setText(getBalance(position) + "");
            updateBackgroundColor(rowFinanceBinding.llayout, position);

    }

    private String getBalance(int position) {
        int i = 0;
        int balance = 0;
        Utilities.log(position + "");
        for (RealmMyTeam team : list) {
            if ("debit".equalsIgnoreCase(team.getType().toLowerCase())) balance -= team.getAmount();
            else balance += team.getAmount();
            if (i == position) break;
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

    public static class ViewHolderFinance extends RecyclerView.ViewHolder {
        RowFinanceBinding rowFinanceBinding;

        public ViewHolderFinance(RowFinanceBinding rowFinanceBinding) {
            super(rowFinanceBinding.getRoot());
            this.rowFinanceBinding = rowFinanceBinding;
        }
    }
}
