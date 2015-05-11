/*
 * Copyright (C) 2015 Kevin Mark
 *
 * This file is part of XHangouts.
 *
 * XHangouts is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * XHangouts is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with XHangouts.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.versobit.kmark.xhangouts.mods;

import android.app.Activity;
import android.content.res.XModuleResources;
import android.content.res.XResources;

import com.versobit.kmark.xhangouts.BuildConfig;
import com.versobit.kmark.xhangouts.Config;
import com.versobit.kmark.xhangouts.Module;
import com.versobit.kmark.xhangouts.R;

import java.lang.reflect.Array;

import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.IXUnhook;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

public class UiQuickSettings extends Module {

    private static final String HANGOUTS_NAV_MENUITEM_BASE = "bev";
    private static final String HANGOUTS_NAV_MENUITEM_HELP = "bbn";

    private static final String HANGOUTS_MENU_POPULATOR = "eaf";

    private static final int HANGOUTS_RES_MENU_TITLE = XResources.getFakeResId(BuildConfig.APPLICATION_ID + ":string/hangouts_menu_title");
    private static final int HANGOUTS_RES_MENU_ICON = XResources.getFakeResId(BuildConfig.APPLICATION_ID + ":drawable/ic_hangouts_menu");
    private static final String ACTUAL_TITLE = "XHangouts v" + BuildConfig.VERSION_NAME.split("-", 2)[0];

    private String modulePath = null;

    private Class classMenuItemBase = null;
    private Class classMenuItemBaseArray = null;

    public UiQuickSettings(Config config) {
        super(UiQuickSettings.class.getSimpleName(), config);
    }

    @Override
    public void init(IXposedHookZygoteInit.StartupParam startup) {
        modulePath = startup.modulePath;
    }

    @Override
    public IXUnhook[] hook(ClassLoader loader) {
        classMenuItemBase = findClass(HANGOUTS_NAV_MENUITEM_BASE, loader);
        classMenuItemBaseArray = Array.newInstance(classMenuItemBase, 0).getClass();
        Class classMenuItemHelp = findClass(HANGOUTS_NAV_MENUITEM_HELP, loader);
        Class classMenuPop = findClass(HANGOUTS_MENU_POPULATOR, loader);

        return new IXUnhook[] {
                // Field corrections
                findAndHookMethod(classMenuItemBase, "a", XC_MethodReplacement.returnConstant(HANGOUTS_RES_MENU_TITLE)),
                findAndHookMethod(classMenuItemBase, "a", Activity.class, onMenuItemClick),
                findAndHookMethod(classMenuItemBase, "b", XC_MethodReplacement.returnConstant(HANGOUTS_RES_MENU_ICON)),
                findAndHookMethod(classMenuItemBase, "c", XC_MethodReplacement.returnConstant(7)),
                findAndHookMethod(classMenuItemBase, "d", XC_MethodReplacement.returnConstant(2)),
                findAndHookMethod(classMenuItemBase, "e", XC_MethodReplacement.returnConstant(7)),

                // Push the Help & feedback entry down
                findAndHookMethod(classMenuItemHelp, "c", XC_MethodReplacement.returnConstant(8)),
                findAndHookMethod(classMenuItemHelp, "e", XC_MethodReplacement.returnConstant(8)),

                // Populate dat menu
                findAndHookMethod(classMenuPop, "a", Class.class, Object[].class, populateMenu)
        };
    }

    private static final XC_MethodReplacement onMenuItemClick = new XC_MethodReplacement() {
        @Override
        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
            Activity act = (Activity)param.args[0];
            act.startActivity(act.getPackageManager().getLaunchIntentForPackage(BuildConfig.APPLICATION_ID));
            return null;
        }
    };

    private final XC_MethodHook populateMenu = new XC_MethodHook() {
        // This method is called by the onAttachBinder method of the NavigationDrawerFragment
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            // If it's not an array of that class we're not interested
            if(!classMenuItemBaseArray.isInstance(param.args[1])) {
                return;
            }

            // This is a var-arg method
            Object[] array = (Object[])param.args[1];

            // Filter out a call we do not want to process
            if (array.length < 4) {
                return;
            }

            // Create a new array to hold the original and our new entry
            Object[] newArray = new Object[array.length + 1];
            System.arraycopy(array, 0, newArray, 0, array.length);
            // Create the base class which is now usable for our purposes
            newArray[newArray.length - 1] = classMenuItemBase.newInstance();
            // Hand it over to the actual method
            param.args[1] = newArray;
        }
    };

    @Override
    public void resources(XResources res) {
        // Get the resources for this module
        XModuleResources xModRes = XModuleResources.createInstance(modulePath, res);

        // Add a new "fake" resource and instantly replace it with the string we actually want
        res.setReplacement(res.addResource(xModRes, R.string.hangouts_menu_title), ACTUAL_TITLE);

        // Add the desired menu icon to the Google Hangouts resources for use like above
        res.addResource(xModRes, R.drawable.ic_hangouts_menu);
    }
}