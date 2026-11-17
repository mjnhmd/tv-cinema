package com.fongmi.android.tv.source;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Puts a source's own episodes into a predictable order. It never renames or recreates an
 * {@link Episode}: the current item and its URL stay the same objects, and playback navigation
 * therefore keeps pointing at the row the user can see.
 */
public final class CinemaEpisodeOrder {

    private static final int RELIABLE_PERCENT = 80;

    private CinemaEpisodeOrder() {}

    /**
     * @return true when enough labels carried a trustworthy coordinate for a stable reorder.
     *         A false result leaves the source order untouched.
     */
    public static boolean apply(Flag flag, boolean reversed) {
        if (flag == null) return false;
        List<Episode> episodes = flag.getEpisodes();
        if (episodes == null || episodes.size() < 2) return false;
        Episode current = current(flag);
        int total = 0;
        int numbered = 0;
        int dated = 0;
        for (Episode episode : episodes) {
            if (episode == null) continue;
            total++;
            EpisodeLabel label = episode.getLabel();
            if (!episode.isGeneratedLabel() && isNumbered(label)) numbered++;
            if (isDated(label)) dated++;
        }
        if (total < 2) return false;
        boolean byNumber = numbered * 100 >= total * RELIABLE_PERCENT;
        boolean byDate = !byNumber && dated * 100 >= total * RELIABLE_PERCENT;
        if (!byNumber && !byDate) return false;
        List<Episode> sorted = new ArrayList<>(episodes.size());
        for (Episode episode : episodes) if (episode != null) sorted.add(episode);
        if (sorted.size() < 2) return false;
        int direction = reversed ? -1 : 1;
        Comparator<Item> comparator = byNumber
                ? (first, second) -> compareNumber(first, second, direction)
                : (first, second) -> compareDate(first, second, direction);
        List<Item> items = new ArrayList<>(sorted.size());
        for (int index = 0; index < sorted.size(); index++) items.add(new Item(sorted.get(index), index));
        items.sort(comparator);
        sorted.clear();
        for (Item item : items) sorted.add(item.episode);
        flag.reorderEpisodes(sorted);
        if (current != null) {
            int position = indexOf(flag.getEpisodes(), current);
            if (position >= 0) flag.setPosition(position);
        }
        return true;
    }

    private static int compareNumber(Item first, Item second, int direction) {
        // A dated extra (花絮/发布会) has no issue number; it belongs after the numbered run
        // instead of sorting ahead of episode 1 as a zero.
        if (first.numbered != second.numbered) return first.numbered ? -1 : 1;
        if (!first.numbered) return Integer.compare(first.origin, second.origin);
        int compared = direction * Integer.compare(first.season, second.season);
        if (compared != 0) return compared;
        compared = direction * Integer.compare(first.number, second.number);
        if (compared != 0) return compared;
        // Reversing seasons/episodes must not reverse "上/中/下" or bonus clips inside one issue.
        compared = Integer.compare(first.part, second.part);
        if (compared != 0) return compared;
        compared = Integer.compare(first.variant, second.variant);
        if (compared != 0) return compared;
        return Integer.compare(first.origin, second.origin);
    }

    private static int compareDate(Item first, Item second, int direction) {
        if (first.dated != second.dated) return first.dated ? -1 : 1;
        if (!first.dated) return Integer.compare(first.origin, second.origin);
        int compared = direction * first.date.compareTo(second.date);
        if (compared != 0) return compared;
        compared = direction * Integer.compare(first.season, second.season);
        if (compared != 0) return compared;
        compared = direction * Integer.compare(first.number, second.number);
        if (compared != 0) return compared;
        compared = Integer.compare(first.part, second.part);
        if (compared != 0) return compared;
        compared = Integer.compare(first.variant, second.variant);
        if (compared != 0) return compared;
        return Integer.compare(first.origin, second.origin);
    }

    private static boolean isNumbered(EpisodeLabel label) {
        return label.isSingleEpisode() && !label.isAmbiguous() && !label.isPreview() && label.number() != null;
    }

    private static boolean isDated(EpisodeLabel label) {
        return label.hasAirDate() && !label.isAmbiguous();
    }

    private static Episode current(Flag flag) {
        List<Episode> episodes = flag.getEpisodes();
        if (episodes == null || episodes.isEmpty()) return null;
        int position = flag.getPosition();
        if (position >= 0 && position < episodes.size()) return episodes.get(position);
        for (Episode episode : episodes) if (episode != null && episode.isSelected()) return episode;
        return null;
    }

    private static int indexOf(List<Episode> episodes, Episode target) {
        for (int index = 0; index < episodes.size(); index++) if (episodes.get(index) == target) return index;
        return -1;
    }

    private static int partRank(String part) {
        if (part == null || part.isEmpty()) return 0;
        String value = part.toLowerCase(Locale.ROOT);
        if (value.startsWith("part")) {
            try {
                return 10 + Integer.parseInt(value.substring(4).replaceFirst("^0+(?=\\d)", ""));
            } catch (NumberFormatException ignored) {
                return 9;
            }
        }
        return switch (value) {
            case "upper", "top" -> 1;
            case "middle", "mid" -> 2;
            case "lower", "bottom" -> 3;
            default -> 8;
        };
    }

    private static int variantRank(String variant) {
        if (variant == null || variant.isEmpty()) return 0;
        if (variant.contains("bonus")) return 1;
        if (variant.contains("member")) return 2;
        if (variant.contains("pure")) return 3;
        if (variant.contains("preview")) return 4;
        return 5;
    }

    private static final class Item {
        final Episode episode;
        final int origin;
        final boolean numbered;
        final boolean dated;
        final int season;
        final int number;
        final int part;
        final int variant;
        final String date;

        Item(Episode episode, int origin) {
            this.episode = episode;
            this.origin = origin;
            EpisodeLabel label = episode.getLabel();
            EpisodeLabel numberLabel = label != null && isNumbered(label) ? label : null;
            EpisodeLabel dateLabel = label != null && isDated(label) ? label : null;
            this.numbered = numberLabel != null;
            this.dated = dateLabel != null;
            EpisodeLabel usable = numberLabel != null ? numberLabel : dateLabel;
            this.season = usable != null && usable.season() != null ? usable.season() : 0;
            this.number = numberLabel != null ? numberLabel.number() : 0;
            this.part = usable != null ? partRank(usable.part()) : 0;
            this.variant = usable != null ? variantRank(usable.variant()) : 0;
            this.date = dateLabel != null ? dateLabel.airDate() : "";
        }
    }
}
