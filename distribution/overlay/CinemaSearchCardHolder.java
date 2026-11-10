package com.fongmi.android.tv.ui.holder;

import android.view.View;

import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.CinemaSearchCardBinding;
import com.fongmi.android.tv.ui.base.BaseVodHolder;
import com.fongmi.android.tv.ui.custom.PosterTitleMarquee;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;

/** Search metadata stays on the poster; only the title occupies the area below it. */
public final class CinemaSearchCardHolder extends BaseVodHolder {
    private final CinemaSearchCardBinding binding;
    private final VodPresenter.OnClickListener listener;
    private final PosterTitleMarquee titleMarquee;

    public CinemaSearchCardHolder(CinemaSearchCardBinding binding, VodPresenter.OnClickListener listener, int[] size) {
        super(binding.getRoot());
        this.binding = binding;
        this.listener = listener;
        binding.getRoot().getLayoutParams().width = size[0];
        binding.poster.getLayoutParams().height = size[1];
        binding.site.setMaxWidth(Math.max(1, size[0] - ResUtil.dp2px(16)));
        titleMarquee = PosterTitleMarquee.bindWithFixedHeight(binding.name);
    }

    @Override public void initView(Vod item) {
        binding.name.setText(item.getName());
        titleMarquee.refresh();
        binding.site.setText(item.getSiteName());
        binding.site.setVisibility(item.getSiteName().isEmpty() ? View.GONE : View.VISIBLE);
        String score = item.getScore();
        String year = item.getYear();
        String info = score.isEmpty() ? year : "★ " + score + (year.isEmpty() ? "" : "  ·  " + year);
        binding.info.setText(info);
        binding.info.setVisibility(info.isEmpty() ? View.GONE : View.VISIBLE);
        updateSourceSummary(item);
        binding.getRoot().setOnClickListener(view -> listener.onItemClick(item, binding.image));
        binding.getRoot().setOnLongClickListener(view -> listener.onLongClick(item));
        ImgUtil.load(item.getName(), item.getPic(), binding.image);
    }

    @Override public void updateSourceSummary(Vod item) {
        String summary = item.getSourceSummary();
        binding.remark.setText(summary);
        binding.remark.setVisibility(summary.isEmpty() ? View.GONE : View.VISIBLE);
        binding.metadata.setVisibility(binding.info.getVisibility() == View.VISIBLE || !summary.isEmpty()
                ? View.VISIBLE : View.GONE);
        binding.getRoot().setContentDescription(item.getName() + " " + item.getSiteName()
                + " " + binding.info.getText() + " " + summary);
    }

    @Override public void unbind() {
        ImgUtil.clear(binding.image);
    }
}
