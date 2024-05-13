﻿using Bit.App.Models;

namespace Bit.App.Pages
{
    public partial class RegisterPage : BaseContentPage
    {
        private readonly RegisterPageViewModel _vm;

        private bool _inputFocused;

        public RegisterPage(HomePage homePage, AppOptions appOptions = null)
        {
            InitializeComponent();
            _vm = BindingContext as RegisterPageViewModel;
            _vm.Page = this;
            _vm.FromIosExtension = appOptions?.IosExtension ?? false;
            _vm.RegistrationSuccess = () => MainThread.BeginInvokeOnMainThread(async () => await RegistrationSuccessAsync(homePage));
            _vm.CloseAction = async () =>
            {
                await Navigation.PopModalAsync();
            };
            MasterPasswordEntry = _masterPassword;
            ConfirmMasterPasswordEntry = _confirmMasterPassword;

#if ANDROID
            ToolbarItems.RemoveAt(0);
#endif

            _email.ReturnType = ReturnType.Next;
            _email.ReturnCommand = new Command(() => _masterPassword.Focus());
            _masterPassword.ReturnType = ReturnType.Next;
            _masterPassword.ReturnCommand = new Command(() => _confirmMasterPassword.Focus());
            _confirmMasterPassword.ReturnType = ReturnType.Next;
            _confirmMasterPassword.ReturnCommand = new Command(() => _hint.Focus());
        }

        public Entry MasterPasswordEntry { get; set; }
        public Entry ConfirmMasterPasswordEntry { get; set; }

        protected override bool ShouldCheckToPreventOnNavigatedToCalledTwice => true;

        protected override Task InitOnNavigatedToAsync()
        {
            if (!_inputFocused)
            {
                RequestFocus(_email);
                _inputFocused = true;
            }

            return Task.CompletedTask;
        }

        private async void Submit_Clicked(object sender, EventArgs e)
        {
            if (DoOnce())
            {
                await _vm.SubmitAsync();
            }
        }

        private async Task RegistrationSuccessAsync(HomePage homePage)
        {
            if (homePage != null)
            {
                await homePage.DismissRegisterPageAndLogInAsync(_vm.Email);
            }
        }

        private void Close_Clicked(object sender, EventArgs e)
        {
            if (DoOnce())
            {
                _vm.CloseAction();
            }
        }
    }
}
