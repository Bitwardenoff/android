﻿using System.Windows.Input;
using Bit.Core.Abstractions;
using Bit.Core.Utilities;
using CommunityToolkit.Mvvm.Input;

namespace Bit.App.Controls
{
    public partial class AccountSwitchingOverlayView : ContentView
    {
        public static readonly BindableProperty MainPageProperty = BindableProperty.Create(
            nameof(MainPage),
            typeof(ContentPage),
            typeof(AccountSwitchingOverlayView),
            defaultBindingMode: BindingMode.OneWay);

        public static readonly BindableProperty MainFabProperty = BindableProperty.Create(
            nameof(MainFab),
            typeof(View),
            typeof(AccountSwitchingOverlayView),
            defaultBindingMode: BindingMode.OneWay);

        public ContentPage MainPage
        {
            get => (ContentPage)GetValue(MainPageProperty);
            set => SetValue(MainPageProperty, value);
        }

        public View MainFab
        {
            get => (View)GetValue(MainFabProperty);
            set => SetValue(MainFabProperty, value);
        }

        readonly LazyResolve<ILogger> _logger = new LazyResolve<ILogger>("logger");

        public AccountSwitchingOverlayView()
        {
            InitializeComponent();

            ToggleVisibililtyCommand = new AsyncRelayCommand(ToggleVisibilityAsync,
                AsyncRelayCommandOptions.None);

            SelectAccountCommand = new AsyncRelayCommand<AccountViewCellViewModel>(SelectAccountAsync,
                AsyncRelayCommandOptions.None);

            LongPressAccountCommand = new AsyncRelayCommand<AccountViewCellViewModel>(LongPressAccountAsync,
                AsyncRelayCommandOptions.None);
        }

        public AccountSwitchingOverlayViewModel ViewModel => BindingContext as AccountSwitchingOverlayViewModel;

        public ICommand ToggleVisibililtyCommand { get; }

        public ICommand SelectAccountCommand { get; }

        public ICommand LongPressAccountCommand { get; }

#if IOS
        public int AccountListRowHeight => 70;
#else
        public int AccountListRowHeight => 74;
#endif

        public bool LongPressAccountEnabled { get; set; } = true;

        public Action AfterHide { get; set; }

        public async Task ToggleVisibilityAsync()
        {
            try
            {
                if (IsVisible)
                {
                    await HideAsync();
                }
                else
                {
                    await ShowAsync();
                }
            }
            catch (Exception ex)
            {
                _logger.Value.Exception(ex);
            }
        }

        public async Task ShowAsync()
        {
            if (ViewModel == null)
            {
                return;
            }

            await ViewModel.RefreshAccountViewsAsync();

            await MainThread.InvokeOnMainThreadAsync(async () =>
            {
                // start listView in default (off-screen) position
                await _accountListContainer.TranslateTo(0, _accountListContainer.Height * -1, 0);

                // re-measure in case accounts have been removed without changing screens
                if (ViewModel.AccountViews != null)
                {
                    _accountListView.HeightRequest = AccountListRowHeight * ViewModel.AccountViews.Count;
                }

                // set overlay opacity to zero before making visible and start fade-in
                Opacity = 0;
                IsVisible = true;
                this.FadeTo(1, 100);

#if ANDROID
                // start fab fade-out
                MainFab?.FadeTo(0, 200);
#endif

                // slide account list into view
                await _accountListContainer.TranslateTo(0, 0, 200, Easing.SinOut);
            });
        }

        public async Task HideAsync()
        {
            if (!IsVisible)
            {
                // already hidden, don't animate again
                return;
            }
            // Not all animations are awaited. This is intentional to allow multiple simultaneous animations.
            await MainThread.InvokeOnMainThreadAsync(async () =>
            {
                // start overlay fade-out
                this.FadeTo(0, 200);

#if ANDROID
                // start fab fade-in
                MainFab?.FadeTo(1, 200);
#endif

                // slide account list out of view
                await _accountListContainer.TranslateTo(0, _accountListContainer.Height * -1, 200, Easing.SinIn);

                // remove overlay
                IsVisible = false;

                AfterHide?.Invoke();
            });
        }

        private async void FreeSpaceOverlay_Tapped(object sender, EventArgs e)
        {
            try
            {
                await HideAsync();
            }
            catch (Exception ex)
            {
                _logger.Value.Exception(ex);
            }
        }

        private async Task SelectAccountAsync(AccountViewCellViewModel item)
        {
            try
            {
                await Task.Delay(100);
                await HideAsync();

                ViewModel?.SelectAccountCommand?.Execute(item);
            }
            catch (Exception ex)
            {
                _logger.Value.Exception(ex);
            }
        }

        private async Task LongPressAccountAsync(AccountViewCellViewModel item)
        {
            try
            {
                if (!LongPressAccountEnabled || item == null || !item.IsAccount)
                {
                    return;
                }

                await Task.Delay(100);
                await HideAsync();

                ViewModel?.LongPressAccountCommand?.Execute(
                    new Tuple<ContentPage, AccountViewCellViewModel>(MainPage, item));
            }
            catch (Exception ex)
            {
                _logger.Value.Exception(ex);
            }
        }
    }
}
