﻿using System;
using System.Threading.Tasks;
using Acr.UserDialogs;
using Bit.App.Abstractions;
using Bit.App.Resources;
using Xamarin.Forms;
using XLabs.Ioc;
using Plugin.Settings.Abstractions;
using Bit.App.Models.Page;
using Bit.App.Controls;

namespace Bit.App.Pages
{
    public class LockPinPage : BaseLockPage
    {
        private readonly IAuthService _authService;
        private readonly ISettings _settings;

        public LockPinPage()
        {
            _authService = Resolver.Resolve<IAuthService>();
            _settings = Resolver.Resolve<ISettings>();

            Init();
        }

        public PinPageModel Model { get; set; } = new PinPageModel();
        public PinControl PinControl { get; set; }

        public void Init()
        {
            var instructionLabel = new Label
            {
                Text = AppResources.EnterPIN,
                LineBreakMode = LineBreakMode.WordWrap,
                FontSize = Device.GetNamedSize(NamedSize.Small, typeof(Label)),
                HorizontalTextAlignment = TextAlignment.Center,
                Style = (Style)Application.Current.Resources["text-muted"]
            };

            PinControl = new PinControl();
            PinControl.OnPinEntered += PinEntered;
            PinControl.Label.SetBinding<PinPageModel>(Label.TextProperty, s => s.LabelText);
            PinControl.Entry.SetBinding<PinPageModel>(Entry.TextProperty, s => s.PIN);

            var logoutButton = new ExtendedButton
            {
                Text = AppResources.LogOut,
                Command = new Command(async () => await LogoutAsync()),
                VerticalOptions = LayoutOptions.End,
                Style = (Style)Application.Current.Resources["btn-primaryAccent"],
                BackgroundColor = Color.Transparent,
                Uppercase = false
            };

            var stackLayout = new StackLayout
            {
                Padding = new Thickness(30, 40),
                Spacing = 20,
                Children = { PinControl.Label, instructionLabel, logoutButton, PinControl.Entry }
            };

            var tgr = new TapGestureRecognizer();
            tgr.Tapped += Tgr_Tapped;
            PinControl.Label.GestureRecognizers.Add(tgr);
            instructionLabel.GestureRecognizers.Add(tgr);

            Title = AppResources.VerifyPIN;
            Content = stackLayout;
            Content.GestureRecognizers.Add(tgr);
            BindingContext = Model;
        }

        private void Tgr_Tapped(object sender, EventArgs e)
        {
            PinControl.Entry.Focus();
        }

        protected override void OnAppearing()
        {
            base.OnAppearing();
            PinControl.Entry.FocusWithDelay();
        }

        protected void PinEntered(object sender, EventArgs args)
        {
            if(Model.PIN == _authService.PIN)
            {
                _settings.AddOrUpdateValue(Constants.Locked, false);
                PinControl.Entry.Unfocus();
                Navigation.PopModalAsync();
            }
            else
            {
                // TODO: keep track of invalid attempts and logout?

                UserDialogs.Alert(AppResources.InvalidPIN);
                Model.PIN = string.Empty;
                PinControl.Entry.Focus();
            }
        }
    }
}
