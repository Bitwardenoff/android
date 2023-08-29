﻿using System;
using System.Threading.Tasks;
using Bit.App.Abstractions;
using Bit.App.Models;
using Bit.App.Pages;
using Bit.App.Resources;
using Bit.App.Utilities;
using Bit.Core.Abstractions;
using Bit.Core.Enums;
using Bit.Core.Models.Domain;
using Bit.Core.Services;
using Bit.Core.Utilities;
using Bit.iOS.Core.Utilities;
using Bit.iOS.Core.Views;
using Foundation;
using UIKit;
using Xamarin.Forms;

namespace Bit.iOS.Core.Controllers
{
    // TODO: Leaving this here until all inheritance is changed to use BaseLockPasswordViewController instead of UITableViewController
    [Obsolete("Use BaseLockPasswordViewController instead")]
    public abstract class LockPasswordViewController : ExtendedUITableViewController
    {
        private IVaultTimeoutService _vaultTimeoutService;
        private ICryptoService _cryptoService;
        private IDeviceActionService _deviceActionService;
        private IStateService _stateService;
        private IStorageService _secureStorageService;
        private IPlatformUtilsService _platformUtilsService;
        private IBiometricService _biometricService;
        private IUserVerificationService _userVerificationService;
        private PinLockType _pinStatus;
        private bool _pinEnabled;
        private bool _biometricEnabled;
        private bool _biometricIntegrityValid = true;
        private bool _passwordReprompt = false;
        private bool _hasMasterPassword;
        private bool _biometricUnlockOnly = false;

        protected bool autofillExtension = false;

        public LockPasswordViewController(IntPtr handle)
            : base(handle)
        { }

        public abstract UINavigationItem BaseNavItem { get; }
        public abstract UIBarButtonItem BaseCancelButton { get; }
        public abstract UIBarButtonItem BaseSubmitButton { get; }
        public abstract Action Success { get; }
        public abstract Action Cancel { get; }

        public FormEntryTableViewCell MasterPasswordCell { get; set; } = new FormEntryTableViewCell(
            AppResources.MasterPassword, buttonsConfig: FormEntryTableViewCell.ButtonsConfig.One);

        public string BiometricIntegritySourceKey { get; set; }

        public bool HasLoginOrUnlockMethod => _hasMasterPassword || _biometricEnabled || _pinEnabled;

        public UITableViewCell BiometricCell
        {
            get
            {
                var cell = new UITableViewCell();
                cell.BackgroundColor = ThemeHelpers.BackgroundColor;
                if (_biometricIntegrityValid)
                {
                    var biometricButtonText = _deviceActionService.SupportsFaceBiometric() ?
                    AppResources.UseFaceIDToUnlock : AppResources.UseFingerprintToUnlock;
                    cell.TextLabel.TextColor = ThemeHelpers.PrimaryColor;
                    cell.TextLabel.Text = biometricButtonText;
                }
                else
                {
                    cell.TextLabel.TextColor = ThemeHelpers.DangerColor;
                    cell.TextLabel.Font = ThemeHelpers.GetDangerFont();
                    cell.TextLabel.Lines = 0;
                    cell.TextLabel.LineBreakMode = UILineBreakMode.WordWrap;
                    cell.TextLabel.Text = AppResources.AccountBiometricInvalidatedExtension;
                }
                return cell;
            }
        }

        public override async void ViewDidLoad()
        {
            _vaultTimeoutService = ServiceContainer.Resolve<IVaultTimeoutService>("vaultTimeoutService");
            _cryptoService = ServiceContainer.Resolve<ICryptoService>("cryptoService");
            _deviceActionService = ServiceContainer.Resolve<IDeviceActionService>("deviceActionService");
            _stateService = ServiceContainer.Resolve<IStateService>("stateService");
            _secureStorageService = ServiceContainer.Resolve<IStorageService>("secureStorageService");
            _platformUtilsService = ServiceContainer.Resolve<IPlatformUtilsService>("platformUtilsService");
            _biometricService = ServiceContainer.Resolve<IBiometricService>("biometricService");
            _userVerificationService = ServiceContainer.Resolve<IUserVerificationService>();

            // We re-use the lock screen for autofill extension to verify master password
            // when trying to access protected items.
            if (autofillExtension && await _stateService.GetPasswordRepromptAutofillAsync())
            {
                _passwordReprompt = true;
                _pinStatus = PinLockType.Disabled;
                _pinEnabled = false;
                _biometricEnabled = false;
            }
            else
            {
                _pinStatus = await _vaultTimeoutService.GetPinLockTypeAsync();

                var ephemeralPinSet = await _stateService.GetPinKeyEncryptedUserKeyEphemeralAsync()
                    ?? await _stateService.GetPinProtectedKeyAsync();
                _pinEnabled = (_pinStatus == PinLockType.Transient && ephemeralPinSet != null) ||
                    _pinStatus == PinLockType.Persistent;

                _biometricEnabled = await _vaultTimeoutService.IsBiometricLockSetAsync()
                    && await _cryptoService.HasEncryptedUserKeyAsync();
                _biometricIntegrityValid =
                    await _platformUtilsService.IsBiometricIntegrityValidAsync(BiometricIntegritySourceKey);
                _hasMasterPassword = await _userVerificationService.HasMasterPasswordAsync();
                _biometricUnlockOnly = !_hasMasterPassword && _biometricEnabled && !_pinEnabled;
            }

            if (_pinEnabled)
            {
                BaseNavItem.Title = AppResources.VerifyPIN;
            }
            else if (_hasMasterPassword)
            {
                BaseNavItem.Title = AppResources.VerifyMasterPassword;
            }
            else
            {
                BaseNavItem.Title = AppResources.UnlockVault;
            }

            BaseCancelButton.Title = AppResources.Cancel;

            if (_biometricUnlockOnly)
            {
                BaseSubmitButton.Title = null;
                BaseSubmitButton.Enabled = false;
            }
            else
            {
                BaseSubmitButton.Title = AppResources.Submit;
            }

            var descriptor = UIFontDescriptor.PreferredBody;

            if (!_biometricUnlockOnly)
            {
                MasterPasswordCell.Label.Text = _pinEnabled ? AppResources.PIN : AppResources.MasterPassword;
                MasterPasswordCell.TextField.SecureTextEntry = true;
                MasterPasswordCell.TextField.ReturnKeyType = UIReturnKeyType.Go;
                MasterPasswordCell.TextField.ShouldReturn += (UITextField tf) =>
                {
                    CheckPasswordAsync().GetAwaiter().GetResult();
                    return true;
                };
                if (_pinEnabled)
                {
                    MasterPasswordCell.TextField.KeyboardType = UIKeyboardType.NumberPad;
                }
                MasterPasswordCell.ConfigureToggleSecureTextCell();
            }

            TableView.RowHeight = UITableView.AutomaticDimension;
            TableView.EstimatedRowHeight = 70;
            TableView.Source = new TableSource(this);
            TableView.AllowsSelection = true;

            base.ViewDidLoad();

            if (_biometricEnabled)
            {
                if (!_biometricIntegrityValid)
                {
                    return;
                }
                var tasks = Task.Run(async () =>
                {
                    await Task.Delay(500);
                    NSRunLoop.Main.BeginInvokeOnMainThread(async () => await PromptBiometricAsync());
                });
            }
        }

        public override void ViewDidAppear(bool animated)
        {
            base.ViewDidAppear(animated);

            // Users without MP and without biometric or pin need SSO
            if (!_hasMasterPassword)
            {
                if (!(_pinEnabled || _biometricEnabled) ||
                    (_biometricEnabled && !_biometricIntegrityValid))
                {
                    PromptSSO();
                }
            }
            else if (!_biometricEnabled || !_biometricIntegrityValid)
            {
                MasterPasswordCell.TextField.BecomeFirstResponder();
            }
        }

        protected async Task CheckPasswordAsync()
        {
            if (string.IsNullOrWhiteSpace(MasterPasswordCell.TextField.Text))
            {
                var alert = Dialogs.CreateAlert(AppResources.AnErrorHasOccurred,
                    string.Format(AppResources.ValidationFieldRequired,
                        _pinEnabled ? AppResources.PIN : AppResources.MasterPassword),
                    AppResources.Ok);
                PresentViewController(alert, true, null);
                return;
            }

            var email = await _stateService.GetEmailAsync();
            var kdfConfig = await _stateService.GetActiveUserCustomDataAsync(a => new KdfConfig(a?.Profile));
            var inputtedValue = MasterPasswordCell.TextField.Text;

            if (_pinEnabled)
            {
                var failed = true;
                try
                {
                    EncString userKeyPin = null;
                    EncString oldPinProtected = null;
                    if (_pinStatus == PinLockType.Persistent)
                    {
                        userKeyPin = await _stateService.GetPinKeyEncryptedUserKeyAsync();
                        var oldEncryptedKey = await _stateService.GetPinProtectedAsync();
                        oldPinProtected = oldEncryptedKey != null ? new EncString(oldEncryptedKey) : null;
                    }
                    else if (_pinStatus == PinLockType.Transient)
                    {
                        userKeyPin = await _stateService.GetPinKeyEncryptedUserKeyEphemeralAsync();
                        oldPinProtected = await _stateService.GetPinProtectedKeyAsync();
                    }

                    UserKey userKey;
                    if (oldPinProtected != null)
                    {
                        userKey = await _cryptoService.DecryptAndMigrateOldPinKeyAsync(
                            _pinStatus == PinLockType.Transient,
                            inputtedValue,
                            email,
                            kdfConfig,
                            oldPinProtected
                        );
                    }
                    else
                    {
                        userKey = await _cryptoService.DecryptUserKeyWithPinAsync(
                            inputtedValue,
                            email,
                            kdfConfig,
                            userKeyPin
                        );
                    }

                    var protectedPin = await _stateService.GetProtectedPinAsync();
                    var decryptedPin = await _cryptoService.DecryptToUtf8Async(new EncString(protectedPin), userKey);
                    failed = decryptedPin != inputtedValue;
                    if (!failed)
                    {
                        await AppHelpers.ResetInvalidUnlockAttemptsAsync();
                        await SetKeyAndContinueAsync(userKey);
                    }
                }
                catch
                {
                    failed = true;
                }
                if (failed)
                {
                    var invalidUnlockAttempts = await AppHelpers.IncrementInvalidUnlockAttemptsAsync();
                    if (invalidUnlockAttempts >= 5)
                    {
                        await LogOutAsync();
                        return;
                    }
                    InvalidValue();
                }
            }
            else
            {
                var masterKey = await _cryptoService.MakeMasterKeyAsync(inputtedValue, email, kdfConfig);

                var storedPasswordHash = await _cryptoService.GetMasterKeyHashAsync();
                if (storedPasswordHash == null)
                {
                    var oldKey = await _secureStorageService.GetAsync<string>("oldKey");
                    if (masterKey.KeyB64 == oldKey)
                    {
                        var localPasswordHash = await _cryptoService.HashMasterKeyAsync(inputtedValue, masterKey, HashPurpose.LocalAuthorization);
                        await _secureStorageService.RemoveAsync("oldKey");
                        await _cryptoService.SetMasterKeyHashAsync(localPasswordHash);
                    }
                }
                var passwordValid = await _cryptoService.CompareAndUpdateKeyHashAsync(inputtedValue, masterKey);
                if (passwordValid)
                {
                    await AppHelpers.ResetInvalidUnlockAttemptsAsync();

                    var userKey = await _cryptoService.DecryptUserKeyWithMasterKeyAsync(masterKey);
                    await _cryptoService.SetMasterKeyAsync(masterKey);
                    await SetKeyAndContinueAsync(userKey, true);
                }
                else
                {
                    var invalidUnlockAttempts = await AppHelpers.IncrementInvalidUnlockAttemptsAsync();
                    if (invalidUnlockAttempts >= 5)
                    {
                        await LogOutAsync();
                        return;
                    }
                    InvalidValue();
                }
            }
        }

        public async Task PromptBiometricAsync()
        {
            if (!_biometricEnabled || !_biometricIntegrityValid)
            {
                return;
            }
            var success = await _platformUtilsService.AuthenticateBiometricAsync(null,
                _pinEnabled ? AppResources.PIN : AppResources.MasterPassword,
                () => MasterPasswordCell.TextField.BecomeFirstResponder());
            await _stateService.SetBiometricLockedAsync(!success);
            if (success)
            {
                DoContinue();
            }
        }

        public void PromptSSO()
        {
            var loginPage = new LoginSsoPage();
            var app = new App.App(new AppOptions { IosExtension = true });
            ThemeManager.SetTheme(app.Resources);
            ThemeManager.ApplyResourcesTo(loginPage);
            if (loginPage.BindingContext is LoginSsoPageViewModel vm)
            {
                vm.SsoAuthSuccessAction = () => DoContinue();
                vm.CloseAction = Cancel;
            }

            var navigationPage = new NavigationPage(loginPage);
            var loginController = navigationPage.CreateViewController();
            loginController.ModalPresentationStyle = UIModalPresentationStyle.FullScreen;
            PresentViewController(loginController, true, null);
        }

        private async Task SetKeyAndContinueAsync(UserKey userKey, bool masterPassword = false)
        {
            var hasKey = await _cryptoService.HasUserKeyAsync();
            if (!hasKey)
            {
                await _cryptoService.SetUserKeyAsync(userKey);
            }
            DoContinue(masterPassword);
        }

        private async void DoContinue(bool masterPassword = false)
        {
            if (masterPassword)
            {
                await _stateService.SetPasswordVerifiedAutofillAsync(true);
            }
            await EnableBiometricsIfNeeded();
            await _stateService.SetBiometricLockedAsync(false);
            MasterPasswordCell.TextField.ResignFirstResponder();
            Success();
        }

        private async Task EnableBiometricsIfNeeded()
        {
            // Re-enable biometrics if initial use
            if (_biometricEnabled & !_biometricIntegrityValid)
            {
                await _biometricService.SetupBiometricAsync(BiometricIntegritySourceKey);
            }
        }

        private void InvalidValue()
        {
            var alert = Dialogs.CreateAlert(AppResources.AnErrorHasOccurred,
                string.Format(null, _pinEnabled ? AppResources.PIN : AppResources.InvalidMasterPassword),
                AppResources.Ok, (a) =>
                    {

                        MasterPasswordCell.TextField.Text = string.Empty;
                        MasterPasswordCell.TextField.BecomeFirstResponder();
                    });
            PresentViewController(alert, true, null);
        }

        private async Task LogOutAsync()
        {
            await AppHelpers.LogOutAsync(await _stateService.GetActiveUserIdAsync());
            var authService = ServiceContainer.Resolve<IAuthService>("authService");
            authService.LogOut(() =>
            {
                Cancel?.Invoke();
            });
        }

        public class TableSource : ExtendedUITableViewSource
        {
            private LockPasswordViewController _controller;

            public TableSource(LockPasswordViewController controller)
            {
                _controller = controller;
            }

            public override UITableViewCell GetCell(UITableView tableView, NSIndexPath indexPath)
            {
                if (indexPath.Section == 0)
                {
                    if (indexPath.Row == 0)
                    {
                        if (_controller._biometricUnlockOnly)
                        {
                            return _controller.BiometricCell;
                        }
                        else
                        {
                            return _controller.MasterPasswordCell;
                        }
                    }
                }
                else if (indexPath.Section == 1)
                {
                    if (indexPath.Row == 0)
                    {
                        if (_controller._passwordReprompt)
                        {
                            var cell = new ExtendedUITableViewCell();
                            cell.TextLabel.TextColor = ThemeHelpers.DangerColor;
                            cell.TextLabel.Font = ThemeHelpers.GetDangerFont();
                            cell.TextLabel.Lines = 0;
                            cell.TextLabel.LineBreakMode = UILineBreakMode.WordWrap;
                            cell.TextLabel.Text = AppResources.PasswordConfirmationDesc;
                            return cell;
                        }
                        else if (!_controller._biometricUnlockOnly)
                        {
                            return _controller.BiometricCell;
                        }
                    }
                }
                return new ExtendedUITableViewCell();
            }

            public override nfloat GetHeightForRow(UITableView tableView, NSIndexPath indexPath)
            {
                return UITableView.AutomaticDimension;
            }

            public override nint NumberOfSections(UITableView tableView)
            {
                return (!_controller._biometricUnlockOnly && _controller._biometricEnabled) ||
                    _controller._passwordReprompt
                    ? 2
                    : 1;
            }

            public override nint RowsInSection(UITableView tableview, nint section)
            {
                if (section <= 1)
                {
                    return 1;
                }
                return 0;
            }

            public override nfloat GetHeightForHeader(UITableView tableView, nint section)
            {
                return section == 1 ? 0.00001f : UITableView.AutomaticDimension;
            }

            public override string TitleForHeader(UITableView tableView, nint section)
            {
                return null;
            }

            public override void RowSelected(UITableView tableView, NSIndexPath indexPath)
            {
                tableView.DeselectRow(indexPath, true);
                tableView.EndEditing(true);
                if (indexPath.Row == 0 &&
                    ((_controller._biometricUnlockOnly && indexPath.Section == 0) ||
                    indexPath.Section == 1))
                {
                    var task = _controller.PromptBiometricAsync();
                    return;
                }
                var cell = tableView.CellAt(indexPath);
                if (cell == null)
                {
                    return;
                }
                if (cell is ISelectable selectableCell)
                {
                    selectableCell.Select();
                }
            }
        }
    }
}
