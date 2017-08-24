/*
 * Copyright (C) 2017 Hazuki
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.hazuki.yuzubrowser.settings.container.custom;

import android.content.SharedPreferences;

import java.lang.reflect.Field;
import java.util.ArrayList;

import jp.hazuki.yuzubrowser.settings.container.Containable;
import jp.hazuki.yuzubrowser.settings.container.IntContainer;
import jp.hazuki.yuzubrowser.utils.ErrorReport;

public class FontSizeContainer implements Containable {
    public final ArrayList<Containable> mPreferenceList = new ArrayList<>();
    public final IntContainer bookmark;
    public final IntContainer history;
    public final IntContainer menu;

    public FontSizeContainer() {
        bookmark = new IntContainer("font_size_bookmark", -1);
        history = new IntContainer("font_size_history", -1);
        menu = new IntContainer("font_size_menu", -1);

        checkPreferenceList();
    }

    @Override
    public void read(SharedPreferences shared_preference) {
        for (Containable pref : mPreferenceList) {
            pref.read(shared_preference);
        }
    }

    @Override
    public void write(SharedPreferences.Editor editor) {
        for (Containable pref : mPreferenceList) {
            pref.write(editor);
        }
    }

    private void checkPreferenceList() {
        try {
            Field[] fields = FontSizeContainer.class.getDeclaredFields();
            for (Field field : fields) {
                Object obj = field.get(this);
                if (obj instanceof Containable) {
                    mPreferenceList.add((Containable) obj);
                }
            }
        } catch (IllegalArgumentException | IllegalAccessException e) {
            ErrorReport.printAndWriteLog(e);
        }
    }
}
