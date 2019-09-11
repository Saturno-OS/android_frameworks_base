/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License.
 */

package com.android.systemui.statusbar;

import android.service.notification.NotificationListenerService.Ranking;

import com.android.internal.annotations.VisibleForTesting;
import com.android.systemui.statusbar.notification.collection.NotificationEntry;

@VisibleForTesting
public class NotificationEntryHelper {
    public static ModifiedRankingBuilder modifyRanking(NotificationEntry entry) {
        return new ModifiedRankingBuilder(entry);
    }

    public static class ModifiedRankingBuilder extends RankingBuilder {
        private final NotificationEntry mTarget;

        private ModifiedRankingBuilder(NotificationEntry target) {
            super(target.ranking());
            mTarget = target;
        }

        @Override
        public Ranking build() {
            final Ranking ranking = super.build();
            mTarget.setRanking(ranking);
            return ranking;
        }
    }
}