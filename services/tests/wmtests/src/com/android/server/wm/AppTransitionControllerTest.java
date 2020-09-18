/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.server.wm;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.view.WindowManager.LayoutParams.TYPE_BASE_APPLICATION;
import static android.view.WindowManager.TRANSIT_OLD_ACTIVITY_OPEN;
import static android.view.WindowManager.TRANSIT_OLD_TASK_CHANGE_WINDOWING_MODE;
import static android.view.WindowManager.TRANSIT_OLD_TASK_CLOSE;
import static android.view.WindowManager.TRANSIT_OLD_TASK_OPEN;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doCallRealMethod;

import android.platform.test.annotations.Presubmit;
import android.util.ArraySet;
import android.view.WindowManager;

import androidx.test.filters.FlakyTest;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Build/Install/Run:
 *  atest WmTests:AppTransitionControllerTest
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class AppTransitionControllerTest extends WindowTestsBase {

    private AppTransitionController mAppTransitionController;

    @Before
    public void setUp() throws Exception {
        mAppTransitionController = new AppTransitionController(mWm, mDisplayContent);
    }

    @Override
    ActivityRecord createActivityRecord(DisplayContent dc, int windowingMode, int activityType) {
        final ActivityRecord r = super.createActivityRecord(dc, windowingMode, activityType);
        // Ensure that ActivityRecord#setOccludesParent takes effect.
        doCallRealMethod().when(r).fillsParent();
        return r;
    }

    @Test
    @FlakyTest(bugId = 131005232)
    public void testTranslucentOpen() {
        final ActivityRecord behind = createActivityRecord(mDisplayContent,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        final ActivityRecord translucentOpening = createActivityRecord(mDisplayContent,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        translucentOpening.setOccludesParent(false);
        translucentOpening.setVisible(false);
        mDisplayContent.mOpeningApps.add(behind);
        mDisplayContent.mOpeningApps.add(translucentOpening);
        assertEquals(WindowManager.TRANSIT_OLD_TRANSLUCENT_ACTIVITY_OPEN,
                mAppTransitionController.maybeUpdateTransitToTranslucentAnim(
                        TRANSIT_OLD_TASK_OPEN));
    }

    @Test
    @FlakyTest(bugId = 131005232)
    public void testTranslucentClose() {
        final ActivityRecord behind = createActivityRecord(mDisplayContent,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        final ActivityRecord translucentClosing = createActivityRecord(mDisplayContent,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        translucentClosing.setOccludesParent(false);
        mDisplayContent.mClosingApps.add(translucentClosing);
        assertEquals(WindowManager.TRANSIT_OLD_TRANSLUCENT_ACTIVITY_CLOSE,
                mAppTransitionController.maybeUpdateTransitToTranslucentAnim(
                        TRANSIT_OLD_TASK_CLOSE));
    }

    @Test
    @FlakyTest(bugId = 131005232)
    public void testChangeIsNotOverwritten() {
        final ActivityRecord behind = createActivityRecord(mDisplayContent,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        final ActivityRecord translucentOpening = createActivityRecord(mDisplayContent,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        translucentOpening.setOccludesParent(false);
        translucentOpening.setVisible(false);
        mDisplayContent.mOpeningApps.add(behind);
        mDisplayContent.mOpeningApps.add(translucentOpening);
        assertEquals(TRANSIT_OLD_TASK_CHANGE_WINDOWING_MODE,
                mAppTransitionController.maybeUpdateTransitToTranslucentAnim(
                        TRANSIT_OLD_TASK_CHANGE_WINDOWING_MODE));
    }

    @Test
    @FlakyTest(bugId = 131005232)
    public void testTransitWithinTask() {
        final ActivityRecord opening = createActivityRecord(mDisplayContent,
                WINDOWING_MODE_FREEFORM, ACTIVITY_TYPE_STANDARD);
        opening.setOccludesParent(false);
        final ActivityRecord closing = createActivityRecord(mDisplayContent,
                WINDOWING_MODE_FREEFORM, ACTIVITY_TYPE_STANDARD);
        closing.setOccludesParent(false);
        final Task task = opening.getTask();
        mDisplayContent.mOpeningApps.add(opening);
        mDisplayContent.mClosingApps.add(closing);
        assertFalse(mAppTransitionController.isTransitWithinTask(TRANSIT_OLD_ACTIVITY_OPEN, task));
        closing.getTask().removeChild(closing);
        task.addChild(closing, 0);
        assertTrue(mAppTransitionController.isTransitWithinTask(TRANSIT_OLD_ACTIVITY_OPEN, task));
        assertFalse(mAppTransitionController.isTransitWithinTask(TRANSIT_OLD_TASK_OPEN, task));
    }

    @Test
    public void testGetAnimationTargets_visibilityAlreadyUpdated() {
        // [DisplayContent] -+- [TaskStack1] - [Task1] - [ActivityRecord1] (opening, visible)
        //                   +- [TaskStack2] - [Task2] - [ActivityRecord2] (closing, invisible)
        final Task stack1 = createTaskStackOnDisplay(mDisplayContent);
        final ActivityRecord activity1 = createTestActivityRecord(stack1);

        final Task stack2 = createTaskStackOnDisplay(mDisplayContent);
        final ActivityRecord activity2 = createTestActivityRecord(stack2);
        activity2.setVisible(false);
        activity2.mVisibleRequested = false;

        final ArraySet<ActivityRecord> opening = new ArraySet<>();
        opening.add(activity1);
        final ArraySet<ActivityRecord> closing = new ArraySet<>();
        closing.add(activity2);

        // No animation, since visibility of the opening and closing apps are already updated
        // outside of AppTransition framework.
        assertEquals(
                new ArraySet<>(),
                AppTransitionController.getAnimationTargets(
                        opening, closing, true /* visible */));
        assertEquals(
                new ArraySet<>(),
                AppTransitionController.getAnimationTargets(
                        opening, closing, false /* visible */));
    }

    @Test
    public void testGetAnimationTargets_visibilityAlreadyUpdated_butForcedTransitionRequested() {
        // [DisplayContent] -+- [TaskStack1] - [Task1] - [ActivityRecord1] (closing, invisible)
        //                   +- [TaskStack2] - [Task2] - [ActivityRecord2] (opening, visible)
        final Task stack1 = createTaskStackOnDisplay(mDisplayContent);
        final ActivityRecord activity1 = createTestActivityRecord(stack1);
        activity1.setVisible(true);
        activity1.mVisibleRequested = true;
        activity1.mRequestForceTransition = true;

        final Task stack2 = createTaskStackOnDisplay(mDisplayContent);
        final ActivityRecord activity2 = createTestActivityRecord(stack2);
        activity2.setVisible(false);
        activity2.mVisibleRequested = false;
        activity2.mRequestForceTransition = true;

        final ArraySet<ActivityRecord> opening = new ArraySet<>();
        opening.add(activity1);
        final ArraySet<ActivityRecord> closing = new ArraySet<>();
        closing.add(activity2);

        // The visibility are already updated, but since forced transition is requested, it will
        // be included.
        assertEquals(
                new ArraySet<>(new WindowContainer[]{activity1.getStack()}),
                AppTransitionController.getAnimationTargets(
                        opening, closing, true /* visible */));
        assertEquals(
                new ArraySet<>(new WindowContainer[]{activity2.getStack()}),
                AppTransitionController.getAnimationTargets(
                        opening, closing, false /* visible */));
    }

    @Test
    public void testGetAnimationTargets_exitingBeforeTransition() {
        // Create another non-empty task so the animation target won't promote to task display area.
        createTestActivityRecord(
                mDisplayContent.getDefaultTaskDisplayArea().getOrCreateRootHomeTask());
        final Task stack = createTaskStackOnDisplay(mDisplayContent);
        final ActivityRecord activity = createTestActivityRecord(stack);
        activity.setVisible(false);
        activity.mIsExiting = true;

        final ArraySet<ActivityRecord> closing = new ArraySet<>();
        closing.add(activity);

        // Animate closing apps even if it's not visible when it is exiting before we had a chance
        // to play the transition animation.
        assertEquals(
                new ArraySet<>(new WindowContainer[]{stack}),
                AppTransitionController.getAnimationTargets(
                        new ArraySet<>(), closing, false /* visible */));
    }

    @Test
    public void testGetAnimationTargets_windowsAreBeingReplaced() {
        // [DisplayContent] -+- [TaskStack1] - [Task1] - [ActivityRecord1] (opening, visible)
        //                                                      +- [AppWindow1] (being-replaced)
        //                   +- [TaskStack2] - [Task2] - [ActivityRecord2] (closing, invisible)
        //                                                      +- [AppWindow2] (being-replaced)
        final Task stack1 = createTaskStackOnDisplay(mDisplayContent);
        final ActivityRecord activity1 = createTestActivityRecord(stack1);
        final WindowManager.LayoutParams attrs = new WindowManager.LayoutParams(
                TYPE_BASE_APPLICATION);
        attrs.setTitle("AppWindow1");
        final TestWindowState appWindow1 = createWindowState(attrs, activity1);
        appWindow1.mWillReplaceWindow = true;
        activity1.addWindow(appWindow1);

        final Task stack2 = createTaskStackOnDisplay(mDisplayContent);
        final ActivityRecord activity2 = createTestActivityRecord(stack2);
        activity2.setVisible(false);
        activity2.mVisibleRequested = false;
        attrs.setTitle("AppWindow2");
        final TestWindowState appWindow2 = createWindowState(attrs, activity2);
        appWindow2.mWillReplaceWindow = true;
        activity2.addWindow(appWindow2);

        final ArraySet<ActivityRecord> opening = new ArraySet<>();
        opening.add(activity1);
        final ArraySet<ActivityRecord> closing = new ArraySet<>();
        closing.add(activity2);

        // Animate opening apps even if it's already visible in case its windows are being replaced.
        // Don't animate closing apps if it's already invisible even though its windows are being
        // replaced.
        assertEquals(
                new ArraySet<>(new WindowContainer[]{stack1}),
                AppTransitionController.getAnimationTargets(
                        opening, closing, true /* visible */));
        assertEquals(
                new ArraySet<>(new WindowContainer[]{}),
                AppTransitionController.getAnimationTargets(
                        opening, closing, false /* visible */));
    }

    @Test
    public void testGetAnimationTargets_openingClosingInDifferentTask() {
        // [DisplayContent] -+- [TaskStack1] - [Task1] -+- [ActivityRecord1] (opening, invisible)
        //                   |                          +- [ActivityRecord2] (invisible)
        //                   |
        //                   +- [TaskStack2] - [Task2] -+- [ActivityRecord3] (closing, visible)
        //                                              +- [ActivityRecord4] (invisible)
        final Task stack1 = createTaskStackOnDisplay(mDisplayContent);
        final Task task1 = createTaskInStack(stack1, 0 /* userId */);
        final ActivityRecord activity1 = createActivityRecordInTask(mDisplayContent, task1);
        activity1.setVisible(false);
        activity1.mVisibleRequested = true;
        final ActivityRecord activity2 = createActivityRecordInTask(mDisplayContent, task1);
        activity2.setVisible(false);
        activity2.mVisibleRequested = false;

        final Task stack2 = createTaskStackOnDisplay(mDisplayContent);
        final Task task2 = createTaskInStack(stack2, 0 /* userId */);
        final ActivityRecord activity3 = createActivityRecordInTask(mDisplayContent, task2);
        final ActivityRecord activity4 = createActivityRecordInTask(mDisplayContent, task2);
        activity4.setVisible(false);
        activity4.mVisibleRequested = false;

        final ArraySet<ActivityRecord> opening = new ArraySet<>();
        opening.add(activity1);
        final ArraySet<ActivityRecord> closing = new ArraySet<>();
        closing.add(activity3);

        // Promote animation targets to TaskStack level. Invisible ActivityRecords don't affect
        // promotion decision.
        assertEquals(
                new ArraySet<>(new WindowContainer[]{stack1}),
                AppTransitionController.getAnimationTargets(
                        opening, closing, true /* visible */));
        assertEquals(
                new ArraySet<>(new WindowContainer[]{stack2}),
                AppTransitionController.getAnimationTargets(
                        opening, closing, false /* visible */));
    }

    @Test
    public void testGetAnimationTargets_openingClosingInSameTask() {
        // [DisplayContent] - [TaskStack] - [Task] -+- [ActivityRecord1] (opening, invisible)
        //                                          +- [ActivityRecord2] (closing, visible)
        final Task stack = createTaskStackOnDisplay(mDisplayContent);
        final Task task = createTaskInStack(stack, 0 /* userId */);
        final ActivityRecord activity1 = createActivityRecordInTask(mDisplayContent, task);
        activity1.setVisible(false);
        activity1.mVisibleRequested = true;
        final ActivityRecord activity2 = createActivityRecordInTask(mDisplayContent, task);

        final ArraySet<ActivityRecord> opening = new ArraySet<>();
        opening.add(activity1);
        final ArraySet<ActivityRecord> closing = new ArraySet<>();
        closing.add(activity2);

        // Don't promote an animation target to Task level, since the same task contains both
        // opening and closing app.
        assertEquals(
                new ArraySet<>(new WindowContainer[]{activity1}),
                AppTransitionController.getAnimationTargets(
                        opening, closing, true /* visible */));
        assertEquals(
                new ArraySet<>(new WindowContainer[]{activity2}),
                AppTransitionController.getAnimationTargets(
                        opening, closing, false /* visible */));
    }

    @Test
    public void testGetAnimationTargets_animateOnlyTranslucentApp() {
        // [DisplayContent] -+- [TaskStack1] - [Task1] -+- [ActivityRecord1] (opening, invisible)
        //                   |                          +- [ActivityRecord2] (visible)
        //                   |
        //                   +- [TaskStack2] - [Task2] -+- [ActivityRecord3] (closing, visible)
        //                                              +- [ActivityRecord4] (visible)

        final Task stack1 = createTaskStackOnDisplay(mDisplayContent);
        final Task task1 = createTaskInStack(stack1, 0 /* userId */);
        final ActivityRecord activity1 = createActivityRecordInTask(mDisplayContent, task1);
        activity1.setVisible(false);
        activity1.mVisibleRequested = true;
        activity1.setOccludesParent(false);

        final ActivityRecord activity2 = createActivityRecordInTask(mDisplayContent, task1);

        final Task stack2 = createTaskStackOnDisplay(mDisplayContent);
        final Task task2 = createTaskInStack(stack2, 0 /* userId */);
        final ActivityRecord activity3 = createActivityRecordInTask(mDisplayContent, task2);
        activity3.setOccludesParent(false);
        final ActivityRecord activity4 = createActivityRecordInTask(mDisplayContent, task2);

        final ArraySet<ActivityRecord> opening = new ArraySet<>();
        opening.add(activity1);
        final ArraySet<ActivityRecord> closing = new ArraySet<>();
        closing.add(activity3);

        // Don't promote an animation target to Task level, since opening (closing) app is
        // translucent and is displayed over other non-animating app.
        assertEquals(
                new ArraySet<>(new WindowContainer[]{activity1}),
                AppTransitionController.getAnimationTargets(
                        opening, closing, true /* visible */));
        assertEquals(
                new ArraySet<>(new WindowContainer[]{activity3}),
                AppTransitionController.getAnimationTargets(
                        opening, closing, false /* visible */));
    }

    @Test
    public void testGetAnimationTargets_animateTranslucentAndOpaqueApps() {
        // [DisplayContent] -+- [TaskStack1] - [Task1] -+- [ActivityRecord1] (opening, invisible)
        //                   |                          +- [ActivityRecord2] (opening, invisible)
        //                   |
        //                   +- [TaskStack2] - [Task2] -+- [ActivityRecord3] (closing, visible)
        //                                              +- [ActivityRecord4] (closing, visible)

        final Task stack1 = createTaskStackOnDisplay(mDisplayContent);
        final Task task1 = createTaskInStack(stack1, 0 /* userId */);
        final ActivityRecord activity1 = createActivityRecordInTask(mDisplayContent, task1);
        activity1.setVisible(false);
        activity1.mVisibleRequested = true;
        activity1.setOccludesParent(false);

        final ActivityRecord activity2 = createActivityRecordInTask(mDisplayContent, task1);
        activity2.setVisible(false);
        activity2.mVisibleRequested = true;

        final Task stack2 = createTaskStackOnDisplay(mDisplayContent);
        final Task task2 = createTaskInStack(stack2, 0 /* userId */);
        final ActivityRecord activity3 = createActivityRecordInTask(mDisplayContent, task2);
        activity3.setOccludesParent(false);
        final ActivityRecord activity4 = createActivityRecordInTask(mDisplayContent, task2);

        final ArraySet<ActivityRecord> opening = new ArraySet<>();
        opening.add(activity1);
        opening.add(activity2);
        final ArraySet<ActivityRecord> closing = new ArraySet<>();
        closing.add(activity3);
        closing.add(activity4);

        // Promote animation targets to TaskStack level even though opening (closing) app is
        // translucent as long as all visible siblings animate at the same time.
        assertEquals(
                new ArraySet<>(new WindowContainer[]{stack1}),
                AppTransitionController.getAnimationTargets(
                        opening, closing, true /* visible */));
        assertEquals(
                new ArraySet<>(new WindowContainer[]{stack2}),
                AppTransitionController.getAnimationTargets(
                        opening, closing, false /* visible */));
    }

    @Test
    public void testGetAnimationTargets_stackContainsMultipleTasks() {
        // [DisplayContent] - [TaskStack] -+- [Task1] - [ActivityRecord1] (opening, invisible)
        //                                 +- [Task2] - [ActivityRecord2] (closing, visible)
        final Task stack = createTaskStackOnDisplay(mDisplayContent);
        final Task task1 = createTaskInStack(stack, 0 /* userId */);
        final ActivityRecord activity1 = createActivityRecordInTask(mDisplayContent, task1);
        activity1.setVisible(false);
        activity1.mVisibleRequested = true;
        final Task task2 = createTaskInStack(stack, 0 /* userId */);
        final ActivityRecord activity2 = createActivityRecordInTask(mDisplayContent, task2);

        final ArraySet<ActivityRecord> opening = new ArraySet<>();
        opening.add(activity1);
        final ArraySet<ActivityRecord> closing = new ArraySet<>();
        closing.add(activity2);

        // Promote animation targets up to Task level, not beyond.
        assertEquals(
                new ArraySet<>(new WindowContainer[]{task1}),
                AppTransitionController.getAnimationTargets(
                        opening, closing, true /* visible */));
        assertEquals(
                new ArraySet<>(new WindowContainer[]{task2}),
                AppTransitionController.getAnimationTargets(
                        opening, closing, false /* visible */));
    }
}
