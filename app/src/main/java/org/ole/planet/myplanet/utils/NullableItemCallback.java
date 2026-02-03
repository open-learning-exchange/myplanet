package org.ole.planet.myplanet.utils;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;

public abstract class NullableItemCallback<T> extends DiffUtil.ItemCallback<T> {
    @Override
    public boolean areItemsTheSame(@Nullable T oldItem, @Nullable T newItem) {
        return areItemsTheSameNullable(oldItem, newItem);
    }

    @Override
    public boolean areContentsTheSame(@Nullable T oldItem, @Nullable T newItem) {
        return areContentsTheSameNullable(oldItem, newItem);
    }

    @Nullable
    @Override
    public Object getChangePayload(@Nullable T oldItem, @Nullable T newItem) {
        return getChangePayloadNullable(oldItem, newItem);
    }

    public abstract boolean areItemsTheSameNullable(@Nullable T oldItem, @Nullable T newItem);

    public abstract boolean areContentsTheSameNullable(@Nullable T oldItem, @Nullable T newItem);

    @Nullable
    public Object getChangePayloadNullable(@Nullable T oldItem, @Nullable T newItem) {
        return null;
    }
}
