﻿using Bit.Core.Abstractions;
using Bit.Core.Utilities;
using System;
using System.Threading.Tasks;
using Bit.App.Abstractions;
using Bit.App.Controls;
using Bit.App.Utilities;
using Bit.Core.Enums;
using Xamarin.Forms;
using Xamarin.Forms.PlatformConfiguration;
using Xamarin.Forms.PlatformConfiguration.iOSSpecific;

namespace Bit.App.Pages
{
    public class BaseContentPage : ContentPage
    {
        private IStateService _stateService;
        private IDeviceActionService _deviceActionService;
        private IMessagingService _messagingService;

        protected int ShowModalAnimationDelay = 400;
        protected int ShowPageAnimationDelay = 100;

        public BaseContentPage()
        {
            if (Device.RuntimePlatform == Device.iOS)
            {
                On<iOS>().SetUseSafeArea(true);
                On<iOS>().SetModalPresentationStyle(UIModalPresentationStyle.FullScreen);
            }
        }

        public DateTime? LastPageAction { get; set; }

        public bool IsThemeDirty { get; set; }

        protected async override void OnAppearing()
        {
            base.OnAppearing();
            
            if (IsThemeDirty)
            {
                UpdateOnThemeChanged();
            }
            
            await SaveActivityAsync();
        }

        public bool DoOnce(Action action = null, int milliseconds = 1000)
        {
            if (LastPageAction.HasValue && (DateTime.UtcNow - LastPageAction.Value).TotalMilliseconds < milliseconds)
            {
                // Last action occurred recently.
                return false;
            }
            LastPageAction = DateTime.UtcNow;
            action?.Invoke();
            return true;
        }

        public virtual Task UpdateOnThemeChanged()
        {
            IsThemeDirty = false;
            return Task.CompletedTask;
        }

        protected void SetActivityIndicator(ContentView targetView = null)
        {
            var indicator = new ActivityIndicator
            {
                IsRunning = true,
                VerticalOptions = LayoutOptions.CenterAndExpand,
                HorizontalOptions = LayoutOptions.Center,
                Color = ThemeManager.GetResourceColor("PrimaryColor"),
            };
            if (targetView != null)
            {
                targetView.Content = indicator;
            }
            else
            {
                Content = indicator;
            }
        }

        protected async Task LoadOnAppearedAsync(View sourceView, bool fromModal, Func<Task> workFunction,
            ContentView targetView = null)
        {
            async Task DoWorkAsync()
            {
                await workFunction.Invoke();
                if (sourceView != null)
                {
                    if (targetView != null)
                    {
                        targetView.Content = sourceView;
                    }
                    else
                    {
                        Content = sourceView;
                    }
                }
            }
            if (Device.RuntimePlatform == Device.iOS)
            {
                await DoWorkAsync();
                return;
            }
            await Task.Run(async () =>
            {
                await Task.Delay(fromModal ? ShowModalAnimationDelay : ShowPageAnimationDelay);
                Device.BeginInvokeOnMainThread(async () => await DoWorkAsync());
            });
        }

        protected void RequestFocus(InputView input)
        {
            Task.Run(async () =>
            {
                await Task.Delay(ShowModalAnimationDelay);
                Device.BeginInvokeOnMainThread(() => input.Focus());
            });
        }

        protected async Task<bool> ShowAccountSwitcherAsync()
        {
            return await _stateService.HasMultipleAccountsAsync();
        }

        protected async Task RefreshAccountViewsAsync(Xamarin.Forms.ListView accountListView, bool allowAddAccountRow)
        {
            await _stateService.RefreshAccountViewsAsync(allowAddAccountRow);
            // Property change trigger on account listview is yielding inconsistent results, using a hammer instead
            accountListView.ItemsSource = null;
            accountListView.ItemsSource = _stateService.AccountViews;
        }
        protected async Task<AvatarImageSource> GetAvatarImageSourceAsync(bool useCurrentActiveAccount = true)
        {
            if (useCurrentActiveAccount)
            {
                return new AvatarImageSource(await _stateService.GetNameAsync(), await _stateService.GetEmailAsync());
            }
            return new AvatarImageSource();
        }

        protected async Task ShowAccountListAsync(bool isVisible, View listContainer, View overlay, View fab = null)
        {
            Device.BeginInvokeOnMainThread(async () =>
            {
                // Not all animations are awaited. This is intentional to allow multiple simultaneous animations.
                if (isVisible)
                {
                    // start listView in default (off-screen) position
                    await listContainer.TranslateTo(0, listContainer.Height * -1, 0);

                    // set overlay opacity to zero before making visible and start fade-in
                    overlay.Opacity = 0;
                    overlay.IsVisible = true;
                    overlay.FadeTo(1, 100);

                    if (Device.RuntimePlatform == Device.Android && fab != null)
                    {
                        // start fab fade-out
                        fab.FadeTo(0, 200);
                    }

                    // slide account list into view
                    await listContainer.TranslateTo(0, 0, 200, Easing.SinOut);
                }
                else
                {
                    // start overlay fade-out
                    overlay.FadeTo(0, 200);

                    if (Device.RuntimePlatform == Device.Android && fab != null)
                    {
                        // start fab fade-in
                        fab.FadeTo(1, 200);
                    }

                    // slide account list out of view
                    await listContainer.TranslateTo(0, listContainer.Height * -1, 200, Easing.SinIn);

                    // remove overlay
                    overlay.IsVisible = false;
                }
            });
        }

        protected async Task AccountRowSelectedAsync(object sender, SelectedItemChangedEventArgs e, View listContainer,
            View overlay, View fab = null, bool? allowActiveAccountSelection = false)
        {
            if (!DoOnce())
            {
                return;
            }
            if (!(e.SelectedItem is AccountViewCellViewModel item))
            {
                return;
            }

            ((Xamarin.Forms.ListView)sender).SelectedItem = null;
            await Task.Delay(100);
            await ShowAccountListAsync(false, listContainer, overlay, fab);

            if (item.AccountView.IsAccount)
            {
                if (!item.AccountView.IsActive)
                {
                    await _stateService.SetActiveUserAsync(item.AccountView.UserId);
                    _messagingService.Send("switchedAccount");
                }
                else if (allowActiveAccountSelection ?? false)
                {
                    _messagingService.Send("switchedAccount");
                }
            }
            else
            {
                _messagingService.Send("addAccount");
            }
        }

        private void SetServices()
        {
            if (_stateService == null)
            {
                _stateService = ServiceContainer.Resolve<IStateService>("stateService");
            }
            if (_deviceActionService == null)
            {
                _deviceActionService = ServiceContainer.Resolve<IDeviceActionService>("deviceActionService");
            }
            if (_messagingService == null)
            {
                _messagingService = ServiceContainer.Resolve<IMessagingService>("messagingService");
            }
        }

        private async Task SaveActivityAsync()
        {
            SetServices();
            await _stateService.SetLastActiveTimeAsync(_deviceActionService.GetActiveTime());
        }
    }
}
