package com.fongmi.android.tv.ui.dialog;

import androidx.fragment.app.Fragment;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.CinemaDiscovery;
import com.fongmi.android.tv.api.config.CinemaSourceMerger;
import com.fongmi.android.tv.api.config.CinemaSources;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.utils.Notify;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

public final class CinemaSourceDialog {

    private CinemaSourceDialog() {
    }

    public static void catalog(Fragment fragment, ConfigListener listener) {
        if (!attached(fragment)) return;
        List<CinemaSources.Entry> entries = CinemaSources.catalogEntries();
        String[] items = new String[entries.size()];
        for (int index = 0; index < entries.size(); index++) {
            CinemaSources.Entry entry = entries.get(index);
            String state = CinemaSources.isActive(entry) ? " · 当前"
                    : CinemaSources.isAdded(entry) ? " · 已添加" : "";
            items[index] = entry.name() + (entry.isVod() ? " · 点播" : " · 直播") + state;
        }
        new MaterialAlertDialogBuilder(fragment.requireActivity())
                .setTitle(R.string.cinema_sources_catalog)
                .setItems(items, (dialog, which) -> {
                    dialog.dismiss();
                    if (attached(fragment)) listener.setConfig(CinemaSources.activate(entries.get(which)));
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    public static void discovery(Fragment fragment, ConfigListener listener) {
        if (!attached(fragment)) return;
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(fragment.requireActivity())
                .setTitle(R.string.cinema_discover_sources)
                .setMessage(R.string.cinema_discover_notice)
                .setPositiveButton(R.string.cinema_discover_start, (ignored, which) -> start(fragment, listener))
                .setNegativeButton(R.string.dialog_negative, null);
        if (CinemaDiscovery.hasCandidate()) {
            dialog.setNeutralButton(R.string.cinema_discover_use, (ignored, which) -> catalog(fragment, listener));
        }
        dialog.show();
    }

    private static void start(Fragment fragment, ConfigListener listener) {
        if (!attached(fragment)) return;
        boolean started = CinemaDiscovery.start(new CinemaDiscovery.Listener() {
            @Override
            public void onFinished(CinemaSourceMerger.Result result) {
                Notify.dismiss();
                if (!attached(fragment)) return;
                if (!fragment.isVisible()) {
                    Notify.show(R.string.cinema_discover_done);
                    return;
                }
                CinemaSourceMerger.Stats stats = result.stats();
                new MaterialAlertDialogBuilder(fragment.requireActivity())
                        .setTitle(R.string.cinema_discover_done)
                        .setMessage(fragment.getString(R.string.cinema_discover_summary,
                                stats.validInputs, stats.inputs, stats.sitesAdded, stats.sitesDuplicate))
                        .setPositiveButton(R.string.cinema_discover_use, (ignored, which) -> catalog(fragment, listener))
                        .show();
            }

            @Override
            public void onError() {
                Notify.dismiss();
                if (attached(fragment)) Notify.show(R.string.cinema_discover_failed);
            }
        });
        if (started) Notify.progress(fragment.requireActivity());
        else Notify.show(R.string.cinema_discover_busy);
    }

    private static boolean attached(Fragment fragment) {
        return fragment.isAdded() && fragment.getView() != null
                && !fragment.requireActivity().isFinishing() && !fragment.requireActivity().isDestroyed();
    }
}
