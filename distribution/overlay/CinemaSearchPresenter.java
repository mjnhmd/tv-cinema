package com.fongmi.android.tv.ui.presenter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.databinding.CinemaSearchCardBinding;
import com.fongmi.android.tv.ui.holder.CinemaSearchCardHolder;

/** Keeps ordinary search styling independent of library and home posters. */
public final class CinemaSearchPresenter extends VodPresenter {
    private final OnClickListener listener;
    private final int[] size;

    public CinemaSearchPresenter(OnClickListener listener, int[] size) {
        super(listener, Style.rect(), size);
        this.listener = listener;
        this.size = size;
    }

    @NonNull @Override public Presenter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        return new CinemaSearchCardHolder(CinemaSearchCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false), listener, size);
    }
}
