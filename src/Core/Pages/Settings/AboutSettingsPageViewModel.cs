﻿using System.Windows.Input;
using Bit.App.Abstractions;
using Bit.Core.Resources.Localization;
using Bit.Core;
using Bit.Core.Abstractions;
using Bit.Core.Utilities;
using CommunityToolkit.Mvvm.Input;
using Sentry;

namespace Bit.App.Pages
{
    public class AboutSettingsPageViewModel : BaseViewModel
    {
        private readonly IPlatformUtilsService _platformUtilsService;
        private readonly IDeviceActionService _deviceActionService;
        private readonly ILogger _logger;

        private bool _inited;
        private bool _shouldSubmitCrashLogs;

        public AboutSettingsPageViewModel()
        {
            _platformUtilsService = ServiceContainer.Resolve<IPlatformUtilsService>();
            _deviceActionService = ServiceContainer.Resolve<IDeviceActionService>();
            _logger = ServiceContainer.Resolve<ILogger>();

            var environmentService = ServiceContainer.Resolve<IEnvironmentService>();
            var clipboardService = ServiceContainer.Resolve<IClipboardService>();

            ToggleSubmitCrashLogsCommand = CreateDefaultAsyncRelayCommand(ToggleSubmitCrashLogsAsync, allowsMultipleExecutions: false);

            GoToHelpCenterCommand = CreateDefaultAsyncRelayCommand(
                () => LaunchUriAsync(AppResources.LearnMoreAboutHowToUseBitwardenOnTheHelpCenter,
                                     AppResources.ContinueToHelpCenter,
                                     ExternalLinksConstants.HELP_CENTER), allowsMultipleExecutions: false);

            ContactBitwardenSupportCommand = CreateDefaultAsyncRelayCommand(
                () => LaunchUriAsync(AppResources.ContactSupportDescriptionLong,
                                     AppResources.ContinueToContactSupport,
                                     ExternalLinksConstants.CONTACT_SUPPORT), allowsMultipleExecutions: false);

            GoToWebVaultCommand = CreateDefaultAsyncRelayCommand(
                () => LaunchUriAsync(AppResources.ExploreMoreFeaturesOfYourBitwardenAccountOnTheWebApp,
                                     AppResources.ContinueToWebApp,
                                     environmentService.GetWebVaultUrl()), allowsMultipleExecutions: false);

            GoToLearnAboutOrgsCommand = CreateDefaultAsyncRelayCommand(
                () => LaunchUriAsync(AppResources.LearnAboutOrganizationsDescriptionLong,
                                     string.Format(AppResources.ContinueToX, ExternalLinksConstants.BITWARDEN_WEBSITE),
                                     ExternalLinksConstants.HELP_ABOUT_ORGANIZATIONS), allowsMultipleExecutions: false);

            RateTheAppCommand = CreateDefaultAsyncRelayCommand(RateAppAsync, allowsMultipleExecutions: false);

            CopyAppInfoCommand = CreateDefaultAsyncRelayCommand(
                () => clipboardService.CopyTextAsync(AppInfo), allowsMultipleExecutions: false);

            ThrowAndCaptureCommand = CreateDefaultAsyncRelayCommand(
                OnCapturedExceptionClicked, allowsMultipleExecutions: false);

            ThrowUnhandledCommand = CreateDefaultAsyncRelayCommand(
                () =>
                {
#pragma warning disable CS0618 // These are test Crash methods!
                    SentrySdk.CauseCrash(CrashType.Managed);
                    return Task.CompletedTask;
                }, allowsMultipleExecutions: false);

            ThrowBackgroundUnhandledCommand = CreateDefaultAsyncRelayCommand(

                () =>
                {
                    SentrySdk.CauseCrash(CrashType.ManagedBackgroundThread);
                    return Task.CompletedTask;
                }, allowsMultipleExecutions: false);

            JavaCrashCommand = CreateDefaultAsyncRelayCommand(
                () =>
                {
#if ANDROID
                    SentrySdk.CauseCrash(CrashType.Java);
#endif
                    return Task.CompletedTask;
                }, allowsMultipleExecutions: false);

            NativeCrashCommand = CreateDefaultAsyncRelayCommand(
                () =>
                {
                    SentrySdk.CauseCrash(CrashType.Native);
                    return Task.CompletedTask;
#pragma warning restore CS0618 // Type or member is obsolete
                }, allowsMultipleExecutions: false);
        }

        public bool ShouldSubmitCrashLogs
        {
            get => _shouldSubmitCrashLogs;
            set
            {
                SetProperty(ref _shouldSubmitCrashLogs, value);
                ((ICommand)ToggleSubmitCrashLogsCommand).Execute(null);
            }
        }

        public string AppInfo
        {
            get
            {
                var appInfo = string.Format("{0}: {1} ({2})",
                    AppResources.Version,
                    _platformUtilsService.GetApplicationVersion(),
                    _deviceActionService.GetBuildNumber());

                return $"© Bitwarden Inc. 2015-{DateTime.Now.Year}\n\n{appInfo}";
            }
        }

        public AsyncRelayCommand ToggleSubmitCrashLogsCommand { get; }
        public ICommand GoToHelpCenterCommand { get; }
        public ICommand ContactBitwardenSupportCommand { get; }
        public ICommand GoToWebVaultCommand { get; }
        public ICommand GoToLearnAboutOrgsCommand { get; }
        public ICommand RateTheAppCommand { get; }
        public ICommand CopyAppInfoCommand { get; }
        public ICommand ThrowAndCaptureCommand { get; }
        public ICommand ThrowUnhandledCommand { get; }
        public ICommand ThrowBackgroundUnhandledCommand { get; }
        public ICommand JavaCrashCommand { get; }
        public ICommand NativeCrashCommand { get; }

        public async Task InitAsync()
        {
            _shouldSubmitCrashLogs = await _logger.IsEnabled();

            _inited = true;

            MainThread.BeginInvokeOnMainThread(() =>
            {
                TriggerPropertyChanged(nameof(ShouldSubmitCrashLogs));
                ToggleSubmitCrashLogsCommand.NotifyCanExecuteChanged();
            });
        }

        private async Task ToggleSubmitCrashLogsAsync()
        {
            await _logger.SetEnabled(ShouldSubmitCrashLogs);
            _shouldSubmitCrashLogs = await _logger.IsEnabled();

            MainThread.BeginInvokeOnMainThread(() => TriggerPropertyChanged(nameof(ShouldSubmitCrashLogs)));
        }

        private async Task LaunchUriAsync(string dialogText, string dialogTitle, string uri)
        {
            if (await _platformUtilsService.ShowDialogAsync(dialogText, dialogTitle, AppResources.Continue, AppResources.Cancel))
            {
                _platformUtilsService.LaunchUri(uri);
            }
        }

        private async Task RateAppAsync()
        {
            if (await _platformUtilsService.ShowDialogAsync(AppResources.RateAppDescriptionLong, AppResources.ContinueToAppStore, AppResources.Continue, AppResources.Cancel))
            {
                await MainThread.InvokeOnMainThreadAsync(_deviceActionService.RateApp);
            }
        }

        private Task OnCapturedExceptionClicked()
        {
            try
            {
                throw new ApplicationException("This exception was thrown and captured manually, without crashing the app.");
            }
            catch (Exception ex)
            {
                SentrySdk.CaptureException(ex);
            }

            return Task.CompletedTask;
        }

        /// INFO: Left here in case we need to debug push notifications
        /// <summary>
        /// Sets up app info plus debugging information for push notifications.
        /// Useful when trying to solve problems regarding push notifications.
        /// </summary>
        /// <example>
        /// Add an IniAsync() method to be called on view appearing, change the AppInfo to be a normal property with setter
        /// and set the result of this method in the main thread to that property to show that in the UI.
        /// </example>
        //        public async Task<string> GetAppInfoForPushNotificationsDebugAsync()
        //        {
        //            var stateService = ServiceContainer.Resolve<IStateService>();

        //            var appInfo = string.Format("{0}: {1} ({2})", AppResources.Version,
        //                _platformUtilsService.GetApplicationVersion(), _deviceActionService.GetBuildNumber());

        //#if DEBUG
        //            var pushNotificationsRegistered = ServiceContainer.Resolve<IPushNotificationService>("pushNotificationService").IsRegisteredForPush;
        //            var pnServerRegDate = await stateService.GetPushLastRegistrationDateAsync();
        //            var pnServerError = await stateService.GetPushInstallationRegistrationErrorAsync();

        //            var pnServerRegDateMessage = default(DateTime) == pnServerRegDate ? "-" : $"{pnServerRegDate.GetValueOrDefault().ToShortDateString()}-{pnServerRegDate.GetValueOrDefault().ToShortTimeString()} UTC";
        //            var errorMessage = string.IsNullOrEmpty(pnServerError) ? string.Empty : $"Push Notifications Server Registration error: {pnServerError}";

        //            var text = string.Format("© Bitwarden Inc. 2015-{0}\n\n{1}\nPush Notifications registered:{2}\nPush Notifications Server Last Date :{3}\n{4}", DateTime.Now.Year, appInfo, pushNotificationsRegistered, pnServerRegDateMessage, errorMessage);
        //#else
        //            var text = string.Format("© Bitwarden Inc. 2015-{0}\n\n{1}", DateTime.Now.Year, appInfo);
        //#endif
        //            return text;
        //        }
    }
}
