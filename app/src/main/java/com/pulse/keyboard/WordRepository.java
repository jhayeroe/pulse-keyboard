package com.pulse.keyboard;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Local seed dictionary, with opt-in manually added personal words. */
public final class WordRepository {
    public static final String PREFS = "pulse_keyboard";
    private static final String KEY_PERSONAL = "personal_words";

    // Common short words first improve early suggestions; this list is an alpha starter.
    private static final String[] STARTER = (
        "ano na pare koy pareng kuya ate ka kamusta kumusta salamat " +
        "gusto pano paano sige pwede po opo nga okay boss pre bro " +
        "the and you for are not but can all was her his she him has had " +
        "have from this that with your will just one two three good great " +
        "hello help home house hope how what when where why who there their " +
        "they them then these those thank thanks time today tomorrow yesterday " +
        "work world word words want went come came here yes no okay ok please " +
        "sorry love like look best better about after again always before " +
        "because between even every family friend friends first five four " +
        "give go going got group happy high important in into is it its " +
        "keep know last later learn let life live long make many may me " +
        "message more most much my name need new next now of off on only " +
        "open or our out over people phone place plan play project put " +
        "read really right run said same say see send set should show " +
        "small some start still stop take talk team tell test than text " +
        "there thing think through to together too try turn use used " +
        "very wait way we well were which while why with yes yet " +
        "ako ikaw tayo kami kayo sila ito iyan iyon dito diyan doon " +
        "ang mga na ng sa para kay kung pero kasi dahil kapag " +
        "may meron wala mayroon naman lang din rin pa po opo " +
        "hindi oo tama mali bakit paano kailan saan sino ano " +
        "maganda mabuti salamat maraming kamusta kumusta umaga tanghali " +
        "hapon gabi bukas ngayon kahapon mamaya sandali sige sure " +
        "pwede puwede kailangan gusto mahal kita kita mo niya nila " +
        "natin natin lang natin ito siya siya ay aking atin " +
        "trabaho opisina kasama tao empleyado araw buwan taon " +
        "meeting report update boss pare office ready done tapos " +
        "schedule task urgent review approve approved pending complete " +
        "payment order items stock sales hr manager salary leave " +
        "company team status minutes number department employee employees"
    ).split("\\s+");

    private WordRepository() {}

    public static List<String> all(Context context) {
        LinkedHashSet<String> words = new LinkedHashSet<>(Arrays.asList(STARTER));
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        words.addAll(prefs.getStringSet(KEY_PERSONAL, new LinkedHashSet<>()));
        return new ArrayList<>(words);
    }

    public static boolean addPersonal(Context context, String raw) {
        if (raw == null) return false;
        String word = raw.trim().toLowerCase(Locale.ROOT);
        if (word.length() < 2 || word.length() > 32 || !word.matches("[a-z]+")) {
            return false;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> copy = new LinkedHashSet<>(prefs.getStringSet(KEY_PERSONAL, new LinkedHashSet<>()));
        copy.add(word);
        prefs.edit().putStringSet(KEY_PERSONAL, copy).apply();
        return true;
    }
}
