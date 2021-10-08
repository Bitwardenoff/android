using Bit.Core.Abstractions;
using Bit.Core.Utilities;
using System;
using System.Threading.Tasks;
using Bit.App.Resources;
using Xamarin.Forms;

namespace Bit.App.Pages
{
    public partial class UpdateTempPasswordPage : BaseContentPage
    {
        private readonly IMessagingService _messagingService;
        private readonly IPlatformUtilsService _platformUtilsService;
        private readonly IBroadcasterService _broadcasterService;
        private readonly UpdateTempPasswordPageViewModel _vm;
        private readonly string _pageName;

        public UpdateTempPasswordPage()
        {  
            // Service Init
            _messagingService = ServiceContainer.Resolve<IMessagingService>("messagingService");
            _platformUtilsService = ServiceContainer.Resolve<IPlatformUtilsService>("platformUtilsService");
            _broadcasterService = ServiceContainer.Resolve<IBroadcasterService>("broadcasterService");
            
            // Service Use
            _messagingService.Send("showStatusBar", true);
            
            // Binding
            InitializeComponent();
            _pageName = string.Concat(nameof(UpdateTempPasswordPage), "_", DateTime.UtcNow.Ticks);
            _vm = BindingContext as UpdateTempPasswordPageViewModel;
            _vm.Page = this;
            
            // Actions Declaration
            _vm.LogOutAction = () =>
            {
                _messagingService.Send("logout");
            };
            _vm.UpdateTempPasswordSuccessAction = () => Device.BeginInvokeOnMainThread(UpdateTempPasswordSuccess);
            
            // Link fields that will be referenced in codebehind
            MasterPasswordEntry = _masterPassword;
            ConfirmMasterPasswordEntry = _confirmMasterPassword;
            
            // Return Types and Commands
            _masterPassword.ReturnType = ReturnType.Next;
            _masterPassword.ReturnCommand = new Command(() => _confirmMasterPassword.Focus());
            _confirmMasterPassword.ReturnType = ReturnType.Next;
            _confirmMasterPassword.ReturnCommand = new Command(() => _hint.Focus());
        }

        public Entry MasterPasswordEntry { get; set; }
        public Entry ConfirmMasterPasswordEntry { get; set; }

        protected override async void OnAppearing()
        {
            _broadcasterService.Subscribe(_pageName, async (message) =>
            {
                if (message.Command == "syncStarted")
                {
                    Device.BeginInvokeOnMainThread(() => IsBusy = true);
                }
                else if (message.Command == "syncCompleted")
                {
                    await Task.Delay(500);
                    Device.BeginInvokeOnMainThread(async () =>
                    {
                        IsBusy = false;
                        await _vm.InitAsync();
                    });
                }
            });
            base.OnAppearing();
            await _vm.InitAsync(true);
            RequestFocus(_masterPassword);
        }

        private async void Submit_Clicked(object sender, EventArgs e)
        {
            if (DoOnce())
            {
                await _vm.SubmitAsync();
            }
        }

        private async void LogOut_Clicked(object sender, EventArgs e)
        {
            if (DoOnce())
            {
                var confirmed = await _platformUtilsService.ShowDialogAsync(AppResources.LogoutConfirmation,
                    AppResources.LogOut, AppResources.Yes, AppResources.Cancel);
                if (confirmed)
                {
                    _vm.LogOutAction();
                }
            }
        }
        
        private void UpdateTempPasswordSuccess()
        {
            _messagingService.Send("logout");
        }
    }
}
