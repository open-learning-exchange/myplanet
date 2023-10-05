package org.ole.planet.myplanet.ui.references;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.databinding.FragmentReferenceBinding;
import org.ole.planet.myplanet.databinding.RowReferenceBinding;
import org.ole.planet.myplanet.model.Reference;
import org.ole.planet.myplanet.ui.dictionary.DictionaryActivity;
import org.ole.planet.myplanet.ui.map.OfflineMapActivity;

import java.util.ArrayList;
import java.util.List;

public class ReferenceFragment extends Fragment {
    private FragmentReferenceBinding fragmentReferenceBinding;
    private RowReferenceBinding rowReferenceBinding;
    OnHomeItemClickListener homeItemClickListener;

    public ReferenceFragment() {
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnHomeItemClickListener)
            homeItemClickListener = (OnHomeItemClickListener) context;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentReferenceBinding = FragmentReferenceBinding.inflate(inflater, container, false);
        List<Reference> list = new ArrayList<>();
        list.add(new Reference(getString(R.string.maps), android.R.drawable.ic_dialog_map));
        list.add(new Reference(getString(R.string.engilsh_dictionary), R.drawable.ic_dictionary));
        fragmentReferenceBinding.rvReferences.setLayoutManager(new GridLayoutManager(getActivity(), 3));
        setRecyclerAdapter(list);
        return fragmentReferenceBinding.getRoot();
    }

    private void setRecyclerAdapter(List<Reference> list) {
        fragmentReferenceBinding.rvReferences.setAdapter(new RecyclerView.Adapter() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                rowReferenceBinding = RowReferenceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
                return new ViewHolderReference(rowReferenceBinding.getRoot());
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                rowReferenceBinding.title.setText(list.get(position).getTitle());
                rowReferenceBinding.icon.setImageResource(list.get(position).getIcon());
                rowReferenceBinding.getRoot().setOnClickListener(view -> {
                    if (position == 0)
                        startActivity(new Intent(getActivity(), OfflineMapActivity.class));
                    else {
                        startActivity(new Intent(getActivity(), DictionaryActivity.class));
                    }
                });
            }

            @Override
            public int getItemCount() {
                return list.size();
            }
        });
    }

    static class ViewHolderReference extends RecyclerView.ViewHolder {

        public ViewHolderReference(View itemView) {
            super(itemView);
        }
    }
}
