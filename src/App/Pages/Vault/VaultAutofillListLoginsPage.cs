﻿using System;
using System.Linq;
using System.Threading.Tasks;
using Acr.UserDialogs;
using Bit.App.Abstractions;
using Bit.App.Controls;
using Bit.App.Models.Page;
using Bit.App.Resources;
using Xamarin.Forms;
using XLabs.Ioc;
using Bit.App.Utilities;
using System.Threading;
using Bit.App.Models;
using System.Collections.Generic;

namespace Bit.App.Pages
{
    public class VaultAutofillListLoginsPage : ExtendedContentPage
    {
        private readonly ILoginService _loginService;
        private readonly IDeviceInfoService _deviceInfoService;
        private readonly IUserDialogs _userDialogs;
        private readonly IClipboardService _clipboardService;
        private readonly ISettingsService _settingsService;
        private CancellationTokenSource _filterResultsCancellationTokenSource;
        private readonly string _name;

        public VaultAutofillListLoginsPage(string uriString)
            : base(true)
        {
            Uri = uriString;

            Uri uri;
            DomainName domainName;
            if(!System.Uri.TryCreate(uriString, UriKind.Absolute, out uri) ||
                !DomainName.TryParse(uri.Host, out domainName))
            {
                if(uriString != null && uriString.StartsWith(Constants.AndroidAppProtocol))
                {
                    _name = uriString.Substring(Constants.AndroidAppProtocol.Length);
                }
            }
            else
            {
                _name = domainName.BaseDomain;
            }

            _loginService = Resolver.Resolve<ILoginService>();
            _deviceInfoService = Resolver.Resolve<IDeviceInfoService>();
            _userDialogs = Resolver.Resolve<IUserDialogs>();
            _clipboardService = Resolver.Resolve<IClipboardService>();
            _settingsService = Resolver.Resolve<ISettingsService>();
            GoogleAnalyticsService = Resolver.Resolve<IGoogleAnalyticsService>();

            Init();
        }

        public ExtendedObservableCollection<VaultListPageModel.Login> PresentationLogins { get; private set; }
            = new ExtendedObservableCollection<VaultListPageModel.Login>();
        public StackLayout NoDataStackLayout { get; set; }
        public ListView ListView { get; set; }
        public ActivityIndicator LoadingIndicator { get; set; }
        private IGoogleAnalyticsService GoogleAnalyticsService { get; set; }
        private string Uri { get; set; }

        private void Init()
        {
            var noDataLabel = new Label
            {
                Text = string.Format(AppResources.NoLoginsForUri, _name ?? "--"),
                HorizontalTextAlignment = TextAlignment.Center,
                FontSize = Device.GetNamedSize(NamedSize.Small, typeof(Label)),
                Style = (Style)Application.Current.Resources["text-muted"]
            };

            var addLoginButton = new ExtendedButton
            {
                Text = AppResources.AddALogin,
                Command = new Command(() => AddLoginAsync()),
                Style = (Style)Application.Current.Resources["btn-primaryAccent"]
            };

            NoDataStackLayout = new StackLayout
            {
                Children = { noDataLabel, addLoginButton },
                VerticalOptions = LayoutOptions.CenterAndExpand,
                Padding = new Thickness(20, 0),
                Spacing = 20
            };

            ToolbarItems.Add(new AddLoginToolBarItem(this));
            ToolbarItems.Add(new CloseToolBarItem(this));

            ListView = new ListView(ListViewCachingStrategy.RecycleElement)
            {
                ItemsSource = PresentationLogins,
                HasUnevenRows = true,
                ItemTemplate = new DataTemplate(() => new VaultListViewCell(
                    (VaultListPageModel.Login l) => MoreClickedAsync(l)))
            };

            if(Device.OS == TargetPlatform.iOS)
            {
                ListView.RowHeight = -1;
            }

            ListView.ItemSelected += LoginSelected;

            Title = string.Format(AppResources.LoginsForUri, _name ?? "--");

            LoadingIndicator = new ActivityIndicator
            {
                IsRunning = true,
                VerticalOptions = LayoutOptions.CenterAndExpand,
                HorizontalOptions = LayoutOptions.Center
            };

            Content = LoadingIndicator;
        }

        protected override void OnAppearing()
        {
            base.OnAppearing();
            _filterResultsCancellationTokenSource = FetchAndLoadVault();
        }

        protected override bool OnBackButtonPressed()
        {
            GoogleAnalyticsService.TrackExtensionEvent("BackClosed", Uri.StartsWith("http") ? "Website" : "App");
            MessagingCenter.Send(Application.Current, "Autofill", (VaultListPageModel.Login)null);
            return true;
        }

        private void AdjustContent()
        {
            if(PresentationLogins.Count > 0)
            {
                Content = ListView;
            }
            else
            {
                Content = NoDataStackLayout;
            }
        }

        private CancellationTokenSource FetchAndLoadVault()
        {
            var cts = new CancellationTokenSource();
            _filterResultsCancellationTokenSource?.Cancel();

            Task.Run(async () =>
            {
                var logins = await _loginService.GetAllAsync(Uri);
                var sortedLogins = logins.Select(l => new VaultListPageModel.Login(l))
                    .OrderBy(s => s.Name)
                    .ThenBy(s => s.Username);

                Device.BeginInvokeOnMainThread(() =>
                {
                    PresentationLogins.ResetWithRange(sortedLogins);
                    AdjustContent();
                });
            }, cts.Token);

            return cts;
        }

        private void LoginSelected(object sender, SelectedItemChangedEventArgs e)
        {
            var login = e.SelectedItem as VaultListPageModel.Login;

            if(Uri.StartsWith("http") && _deviceInfoService.Version < 21)
            {
                MoreClickedAsync(login);
                return;
            }

            GoogleAnalyticsService.TrackExtensionEvent("AutoFilled", Uri.StartsWith("http") ? "Website" : "App");
            MessagingCenter.Send(Application.Current, "Autofill", login);
        }

        private async void AddLoginAsync()
        {
            var page = new VaultAddLoginPage(Uri, _name, true);
            await Navigation.PushForDeviceAsync(page);
        }

        private async void MoreClickedAsync(VaultListPageModel.Login login)
        {
            var buttons = new List<string> { AppResources.View, AppResources.Edit };
            if(!string.IsNullOrWhiteSpace(login.Password.Value))
            {
                buttons.Add(AppResources.CopyPassword);
            }
            if(!string.IsNullOrWhiteSpace(login.Username))
            {
                buttons.Add(AppResources.CopyUsername);
            }

            var selection = await DisplayActionSheet(login.Name, AppResources.Cancel, null, buttons.ToArray());

            if(selection == AppResources.View)
            {
                var page = new VaultViewLoginPage(login.Id);
                await Navigation.PushForDeviceAsync(page);
            }
            else if(selection == AppResources.Edit)
            {
                var page = new VaultEditLoginPage(login.Id);
                await Navigation.PushForDeviceAsync(page);
            }
            else if(selection == AppResources.CopyPassword)
            {
                Copy(login.Password.Value, AppResources.Password);
            }
            else if(selection == AppResources.CopyUsername)
            {
                Copy(login.Username, AppResources.Username);
            }
        }

        private void Copy(string copyText, string alertLabel)
        {
            _clipboardService.CopyToClipboard(copyText);
            _userDialogs.Toast(string.Format(AppResources.ValueHasBeenCopied, alertLabel));
        }

        private class AddLoginToolBarItem : ToolbarItem
        {
            private readonly VaultAutofillListLoginsPage _page;

            public AddLoginToolBarItem(VaultAutofillListLoginsPage page)
            {
                _page = page;
                Text = AppResources.Add;
                Icon = "plus";
                Clicked += ClickedItem;
                Priority = 1;
            }

            private void ClickedItem(object sender, EventArgs e)
            {
                _page.AddLoginAsync();
            }
        }

        private class CloseToolBarItem : ToolbarItem
        {
            private readonly VaultAutofillListLoginsPage _page;

            public CloseToolBarItem(VaultAutofillListLoginsPage page)
            {
                _page = page;
                Text = AppResources.Close;
                Icon = "close";
                Clicked += ClickedItem;
                Priority = 2;
            }

            private void ClickedItem(object sender, EventArgs e)
            {
                _page.GoogleAnalyticsService.TrackExtensionEvent("Closed", _page.Uri.StartsWith("http") ? "Website" : "App");
                MessagingCenter.Send(Application.Current, "SetMainPage");
            }
        }
    }
}
