package com.snsapp.api.common;

import java.text.BreakIterator;

/** 文字数は「見た目の1文字」で数える。絵文字や結合文字も1字(画面の Intl.Segmenter と同じ数え方) */
public final class TextLength {

    private TextLength() {
    }

    public static int visible(String text) {
        BreakIterator graphemes = BreakIterator.getCharacterInstance();
        graphemes.setText(text);
        int count = 0;
        while (graphemes.next() != BreakIterator.DONE) {
            count++;
        }
        return count;
    }
}
