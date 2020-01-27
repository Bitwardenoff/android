﻿using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using Android.App;
using Android.Content;
using Android.Graphics;
using Android.OS;
using Android.Provider;
using Android.Runtime;
using Android.Views;
using Android.Views.Accessibility;
using Android.Widget;
using Bit.App.Resources;
using Bit.Core;
using Bit.Core.Abstractions;
using Bit.Core.Utilities;
using Java.Util;

namespace Bit.Droid.Accessibility
{
    [Service(Permission = Android.Manifest.Permission.BindAccessibilityService, Label = "Bitwarden")]
    [IntentFilter(new string[] { "android.accessibilityservice.AccessibilityService" })]
    [MetaData("android.accessibilityservice", Resource = "@xml/accessibilityservice")]
    [Register("com.x8bit.bitwarden.Accessibility.AccessibilityService")]
    public class AccessibilityService : Android.AccessibilityServices.AccessibilityService
    {
        private const string BitwardenPackage = "com.x8bit.bitwarden";
        private const string BitwardenWebsite = "vault.bitwarden.com";

        private AccessibilityNodeInfo _anchorNode = null;
        private int _lastAnchorX, _lastAnchorY = 0;
        private static bool _overlayAnchorObserverRunning = false;
        private IWindowManager _windowManager = null;
        private LinearLayout _overlayView = null;
        private long _lastAutoFillTime = 0;
        private Java.Lang.Runnable _overlayAnchorObserverRunnable = null;
        private Handler _handler = new Handler(Looper.MainLooper);

        private HashSet<string> _launcherPackageNames = null;
        private DateTime? _lastLauncherSetBuilt = null;
        private TimeSpan _rebuildLauncherSpan = TimeSpan.FromHours(1);

        public override void OnAccessibilityEvent(AccessibilityEvent e)
        {
            try
            {
                var powerManager = GetSystemService(PowerService) as PowerManager;
                if(Build.VERSION.SdkInt > BuildVersionCodes.KitkatWatch && !powerManager.IsInteractive)
                {
                    return;
                }
                else if(Build.VERSION.SdkInt < BuildVersionCodes.Lollipop && !powerManager.IsScreenOn)
                {
                    return;
                }

                if(SkipPackage(e?.PackageName))
                {
                    if(e?.PackageName != "com.android.systemui")
                    {
                        CancelOverlayPrompt();
                    }
                    return;
                }

                // AccessibilityHelpers.PrintTestData(root, e);

                AccessibilityNodeInfo root = null;

                switch(e.EventType)
                {
                    case EventTypes.ViewFocused:
                    case EventTypes.ViewClicked:
                        if(e.Source == null || e.PackageName == BitwardenPackage)
                        {
                            CancelOverlayPrompt();
                            e.Recycle();
                            break;
                        }

                        root = RootInActiveWindow;
                        if(root == null)
                        {
                            e.Recycle();
                            return;
                        }
                        if(root.PackageName != e.PackageName)
                        {
                            e.Recycle();
                            break;
                        }
                        var isKnownBroswer = AccessibilityHelpers.SupportedBrowsers.ContainsKey(root.PackageName);
                        if(e.EventType == EventTypes.ViewClicked && isKnownBroswer)
                        {
                            e.Recycle();
                            break;
                        }
                        if(!(e.Source?.Password ?? false) && !AccessibilityHelpers.IsUsernameEditText(root, e))
                        {
                            CancelOverlayPrompt();
                            e.Recycle();
                            break;
                        }
                        if(ScanAndAutofill(root, e))
                        {
                            CancelOverlayPrompt();
                            e.Recycle();
                        }
                        else
                        {
                            OverlayPromptToAutofill(root, e);
                        }
                        break;
                    case EventTypes.WindowContentChanged:
                    case EventTypes.WindowStateChanged:
                        if(AccessibilityHelpers.LastCredentials == null)
                        {
                            e.Recycle();
                            break;
                        }
                        if(e.PackageName == BitwardenPackage)
                        {
                            CancelOverlayPrompt();
                            e.Recycle();
                            break;
                        }

                        root = RootInActiveWindow;
                        if(ScanAndAutofill(root, e))
                        {
                            CancelOverlayPrompt();
                        }
                        e.Recycle();
                        break;
                    default:
                        break;
                }

                if(root != null)
                {
                    root.Recycle();
                }
            }
            // Suppress exceptions so that service doesn't crash.
            catch(Exception ex)
            {
                System.Diagnostics.Debug.WriteLine(">>> {0}: {1}", ex.GetType(), ex.StackTrace);
            }
        }

        public override void OnInterrupt()
        {
            // Do nothing.
        }

        public bool ScanAndAutofill(AccessibilityNodeInfo root, AccessibilityEvent e)
        {
            var filled = false;
            var passwordNodes = AccessibilityHelpers.GetWindowNodes(root, e, n => n.Password, false);
            if(passwordNodes.Count > 0)
            {
                var uri = AccessibilityHelpers.GetUri(root);
                if(uri != null && !uri.Contains(BitwardenWebsite))
                {
                    if(AccessibilityHelpers.NeedToAutofill(AccessibilityHelpers.LastCredentials, uri))
                    {
                        AccessibilityHelpers.GetNodesAndFill(root, e, passwordNodes);
                        filled = true;
                        _lastAutoFillTime = Java.Lang.JavaSystem.CurrentTimeMillis();
                    }
                }
                AccessibilityHelpers.LastCredentials = null;
            }
            else if(AccessibilityHelpers.LastCredentials != null)
            {
                Task.Run(async () =>
                {
                    await Task.Delay(1000);
                    AccessibilityHelpers.LastCredentials = null;
                });
            }
            passwordNodes.Dispose();
            return filled;
        }

        private void CancelOverlayPrompt()
        {
            _overlayAnchorObserverRunning = false;

            if(_windowManager != null && _overlayView != null)
            {
                _windowManager.RemoveViewImmediate(_overlayView);
                System.Diagnostics.Debug.WriteLine(">>> Accessibility Overlay View Removed");
            }

            _overlayView = null;
            _lastAnchorX = 0;
            _lastAnchorY = 0;

            if(_anchorNode != null)
            {
                _anchorNode.Recycle();
                _anchorNode = null;
            }
        }

        private void OverlayPromptToAutofill(AccessibilityNodeInfo root, AccessibilityEvent e)
        {
            if(!AccessibilityHelpers.OverlayPermitted())
            {
                System.Diagnostics.Debug.WriteLine(">>> Overlay Permission not granted");
                Toast.MakeText(this, AppResources.AccessibilityOverlayPermissionAlert, ToastLength.Long).Show();
                e.Recycle();
                return;
            }

            if(_overlayView != null || _anchorNode != null || _overlayAnchorObserverRunning)
            {
                CancelOverlayPrompt();
            }

            if(Java.Lang.JavaSystem.CurrentTimeMillis() - _lastAutoFillTime < 1000)
            {
                e.Recycle();
                return;
            }

            var uri = AccessibilityHelpers.GetUri(root);
            if(string.IsNullOrWhiteSpace(uri))
            {
                e.Recycle();
                return;
            }

            var layoutParams = AccessibilityHelpers.GetOverlayLayoutParams();
            var anchorPosition = AccessibilityHelpers.GetOverlayAnchorPosition(root, e.Source);
            layoutParams.X = anchorPosition.X;
            layoutParams.Y = anchorPosition.Y;

            if(_windowManager == null)
            {
                _windowManager = GetSystemService(WindowService).JavaCast<IWindowManager>();
            }

            var intent = new Intent(this, typeof(AccessibilityActivity));
            intent.PutExtra("uri", uri);
            intent.SetFlags(ActivityFlags.NewTask | ActivityFlags.SingleTop | ActivityFlags.ClearTop);

            _overlayView = AccessibilityHelpers.GetOverlayView(this);
            _overlayView.Click += (sender, eventArgs) =>
            {
                CancelOverlayPrompt();
                StartActivity(intent);
            };

            _anchorNode = e.Source;
            _lastAnchorX = anchorPosition.X;
            _lastAnchorY = anchorPosition.Y;

            _windowManager.AddView(_overlayView, layoutParams);

            System.Diagnostics.Debug.WriteLine(">>> Accessibility Overlay View Added at X:{0} Y:{1}",
                layoutParams.X, layoutParams.Y);

            StartOverlayAnchorObserver();
        }

        private void StartOverlayAnchorObserver()
        {
            if(_overlayAnchorObserverRunning)
            {
                return;
            }
            _overlayAnchorObserverRunning = true;

            _overlayAnchorObserverRunnable = new Java.Lang.Runnable(() =>
            {
                if(_overlayAnchorObserverRunning)
                {
                    AdjustOverlayForScroll();
                    _handler.PostDelayed(_overlayAnchorObserverRunnable, 250);
                }
            });

            _handler.PostDelayed(_overlayAnchorObserverRunnable, 250);
        }

        private void AdjustOverlayForScroll()
        {
            if(_overlayView == null || _anchorNode == null)
            {
                CancelOverlayPrompt();
                return;
            }

            var root = RootInActiveWindow;
            IEnumerable<AccessibilityWindowInfo> windows = null;
            if(Build.VERSION.SdkInt > BuildVersionCodes.Kitkat)
            {
                windows = Windows;
            }
            var anchorPosition = AccessibilityHelpers.GetOverlayAnchorPosition(_anchorNode, root, windows);
            root.Recycle();

            if(anchorPosition == null)
            {
                CancelOverlayPrompt();
                return;
            }
            else if(anchorPosition.X == -1 && anchorPosition.Y == -1)
            {
                if(_overlayView.Visibility != ViewStates.Gone)
                {
                    _overlayView.Visibility = ViewStates.Gone;
                }
                return;
            }
            else if(anchorPosition.X == _lastAnchorX && anchorPosition.Y == _lastAnchorY)
            {
                return;
            }

            var layoutParams = AccessibilityHelpers.GetOverlayLayoutParams();
            layoutParams.X = anchorPosition.X;
            layoutParams.Y = anchorPosition.Y;

            _lastAnchorX = anchorPosition.X;
            _lastAnchorY = anchorPosition.Y;

            _windowManager.UpdateViewLayout(_overlayView, layoutParams);

            if(_overlayView.Visibility != ViewStates.Visible)
            {
                _overlayView.Visibility = ViewStates.Visible;
            }

            System.Diagnostics.Debug.WriteLine(">>> Accessibility Overlay View Updated to X:{0} Y:{1}",
                    layoutParams.X, layoutParams.Y);
        }

        private bool SkipPackage(string eventPackageName)
        {
            if(string.IsNullOrWhiteSpace(eventPackageName) ||
                AccessibilityHelpers.FilteredPackageNames.Contains(eventPackageName) ||
                eventPackageName.Contains("launcher"))
            {
                return true;
            }
            if(_launcherPackageNames == null || _lastLauncherSetBuilt == null ||
                (DateTime.Now - _lastLauncherSetBuilt.Value) > _rebuildLauncherSpan)
            {
                // refresh launcher list every now and then
                _lastLauncherSetBuilt = DateTime.Now;
                var intent = new Intent(Intent.ActionMain);
                intent.AddCategory(Intent.CategoryHome);
                var resolveInfo = PackageManager.QueryIntentActivities(intent, 0);
                _launcherPackageNames = resolveInfo.Select(ri => ri.ActivityInfo.PackageName).ToHashSet();
            }
            return _launcherPackageNames.Contains(eventPackageName);
        }
    }
}
