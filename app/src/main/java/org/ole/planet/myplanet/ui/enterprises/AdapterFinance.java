package org.ole.planet.myplanet.ui.enterprises;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.utilities.TimeUtils;

import java.util.List;

public class AdapterFinance extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private Context context;
    private List<RealmMyTeam> list;


    public AdapterFinance(Context context, List<RealmMyTeam> list) {
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
            ((ViewHolderFinance) holder).date.setText(TimeUtils.formatDate(list.get(position).getDate()));
            ((ViewHolderFinance) holder).note.setText(list.get(position).getDescription());
            if (TextUtils.equals(list.get(position).getType(), "debit")) {
                ((ViewHolderFinance) holder).debit.setText(list.get(position).getAmount() + "");
                ((ViewHolderFinance) holder).credit.setText("");
            } else {
                ((ViewHolderFinance) holder).credit.setText(list.get(position).getAmount() + "");
                ((ViewHolderFinance) holder).debit.setText("");
            }
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    class ViewHolderFinance extends RecyclerView.ViewHolder {
        TextView date, note, credit, debit, balance;

        public ViewHolderFinance(View itemView) {
            super(itemView);
            date = itemView.findViewById(R.id.date);
            note = itemView.findViewById(R.id.note);
            credit = itemView.findViewById(R.id.credit);
            debit = itemView.findViewById(R.id.debit);
            balance = itemView.findViewById(R.id.balance);
        }
    }
}
