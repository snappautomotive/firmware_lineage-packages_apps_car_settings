/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package com.android.car.settings.sound;

import android.annotation.DrawableRes;
import android.annotation.StringRes;
import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.media.CarAudioManager;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.database.ContentObserver;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.util.Xml;

import androidx.car.widget.ListItem;
import androidx.car.widget.ListItemAdapter;
import androidx.car.widget.ListItemProvider.ListProvider;
import androidx.car.widget.PagedListView;

import com.android.car.settings.R;
import com.android.car.settings.common.BaseFragment;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Activity hosts sound related settings.
 */
public class SoundSettingsFragment extends BaseFragment {
    private static final String TAG = "SoundSettingsFragment";

    private static final String XML_TAG_VOLUME_ITEMS = "carVolumeItems";
    private static final String XML_TAG_VOLUME_ITEM = "item";

    private final SparseArray<VolumeItem> mVolumeItems = new SparseArray<>();

    private final List<ListItem> mVolumeLineItems = new ArrayList<>();

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                mCarAudioManager = (CarAudioManager) mCar.getCarManager(Car.AUDIO_SERVICE);
                int volumeGroupCount = mCarAudioManager.getVolumeGroupCount();
                // Populates volume slider items from volume groups to UI.
                for (int groupId = 0; groupId < volumeGroupCount; groupId++) {
                    final VolumeItem volumeItem = getVolumeItemForUsages(
                            mCarAudioManager.getUsagesForVolumeGroupId(groupId));
                    mVolumeLineItems.add(new VolumeLineItem(
                            getContext(),
                            mCarAudioManager,
                            groupId,
                            volumeItem.usage,
                            volumeItem.icon,
                            volumeItem.title,
                            new VolumeLineItem.SeekbarListener(getContext(),
                                mCarAudioManager,
                                groupId,
                                volumeItem.usage)));
                }
                // if list is already initiated, update it's content.
                if (mPagedListAdapter != null) {
                    mPagedListAdapter.notifyDataSetChanged();
                }
                mCarAudioManager.registerVolumeChangeObserver(mVolumeChangeObserver);
            } catch (CarNotConnectedException e) {
                Log.e(TAG, "Car is not connected!", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mCarAudioManager.unregisterVolumeChangeObserver(mVolumeChangeObserver);
            mVolumeLineItems.clear();
            mCarAudioManager = null;
        }
    };

    private final ContentObserver mVolumeChangeObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            mPagedListAdapter.notifyDataSetChanged();
        }
    };

    private Car mCar;
    private CarAudioManager mCarAudioManager;
    private PagedListView mListView;
    private ListItemAdapter mPagedListAdapter;

    public static SoundSettingsFragment getInstance() {
        SoundSettingsFragment soundSettingsFragment = new SoundSettingsFragment();
        Bundle bundle = BaseFragment.getBundle();
        bundle.putInt(EXTRA_TITLE_ID, R.string.sound_settings);
        bundle.putInt(EXTRA_LAYOUT, R.layout.list);
        bundle.putInt(EXTRA_ACTION_BAR_LAYOUT, R.layout.action_bar);
        soundSettingsFragment.setArguments(bundle);
        return soundSettingsFragment;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        loadAudioUsageItems();
        mCar = Car.createCar(getContext(), mServiceConnection);
        mListView = getView().findViewById(R.id.list);
        mPagedListAdapter = new ListItemAdapter(getContext(), new ListProvider(mVolumeLineItems));
        mListView.setAdapter(mPagedListAdapter);
        mListView.setMaxPages(PagedListView.UNLIMITED_PAGES);
    }

    @Override
    public void onStart() {
        super.onStart();
        mCar.connect();
    }

    @Override
    public void onStop() {
        super.onStop();
        for (ListItem item : mVolumeLineItems) {
            ((VolumeLineItem) item).stop();
        }
        mCar.disconnect();
    }

    private void loadAudioUsageItems() {
        try (XmlResourceParser parser = getResources().getXml(R.xml.car_volume_items)) {
            AttributeSet attrs = Xml.asAttributeSet(parser);
            int type;
            // Traverse to the first start tag
            while ((type=parser.next()) != XmlResourceParser.END_DOCUMENT
                    && type != XmlResourceParser.START_TAG) {
            }

            if (!XML_TAG_VOLUME_ITEMS.equals(parser.getName())) {
                throw new RuntimeException("Meta-data does not start with carVolumeItems tag");
            }
            int outerDepth = parser.getDepth();
            int rank = 0;
            while ((type=parser.next()) != XmlResourceParser.END_DOCUMENT
                    && (type != XmlResourceParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlResourceParser.END_TAG) {
                    continue;
                }
                if (XML_TAG_VOLUME_ITEM.equals(parser.getName())) {
                    TypedArray item = getResources().obtainAttributes(
                            attrs, R.styleable.carVolumeItems_item);
                    int usage = item.getInt(R.styleable.carVolumeItems_item_usage, -1);
                    if (usage >= 0) {
                        mVolumeItems.put(usage, new VolumeItem(
                                usage, rank,
                                item.getResourceId(R.styleable.carVolumeItems_item_title, 0),
                                item.getResourceId(R.styleable.carVolumeItems_item_icon, 0)));
                        rank++;
                    }
                    item.recycle();
                }
            }
        } catch (XmlPullParserException | IOException e) {
            Log.e(TAG, "Error parsing volume groups configuration", e);
        }
    }

    private VolumeItem getVolumeItemForUsages(int[] usages) {
        int rank = Integer.MAX_VALUE;
        VolumeItem result = null;
        for (int usage : usages) {
            VolumeItem volumeItem = mVolumeItems.get(usage);
            if (volumeItem.rank < rank) {
                rank = volumeItem.rank;
                result = volumeItem;
            }
        }
        return result;
    }

    /**
     * Wrapper class which contains information to render volume item on UI.
     */
    private static class VolumeItem {
        private final @AudioAttributes.AttributeUsage int usage;
        private final int rank;
        private final @StringRes int title;
        private final @DrawableRes int icon;

        private VolumeItem(@AudioAttributes.AttributeUsage int usage, int rank,
                @StringRes int title, @DrawableRes int icon) {
            this.usage = usage;
            this.rank = rank;
            this.title = title;
            this.icon = icon;
        }
    }
}
