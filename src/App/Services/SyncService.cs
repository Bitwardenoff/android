﻿using System;
using System.Linq;
using System.Threading.Tasks;
using Bit.App.Abstractions;
using Bit.App.Models.Data;
using Plugin.Settings.Abstractions;
using Bit.App.Models.Api;
using System.Collections.Generic;
using Xamarin.Forms;
using Newtonsoft.Json;

namespace Bit.App.Services
{
    public class SyncService : ISyncService
    {
        private readonly ICipherApiRepository _cipherApiRepository;
        private readonly IFolderApiRepository _folderApiRepository;
        private readonly ILoginApiRepository _loginApiRepository;
        private readonly IAccountsApiRepository _accountsApiRepository;
        private readonly ISettingsApiRepository _settingsApiRepository;
        private readonly IFolderRepository _folderRepository;
        private readonly ILoginRepository _loginRepository;
        private readonly ISettingsRepository _settingsRepository;
        private readonly IAuthService _authService;
        private readonly ISettings _settings;

        public SyncService(
            ICipherApiRepository cipherApiRepository,
            IFolderApiRepository folderApiRepository,
            ILoginApiRepository loginApiRepository,
            IAccountsApiRepository accountsApiRepository,
            ISettingsApiRepository settingsApiRepository,
            IFolderRepository folderRepository,
            ILoginRepository loginRepository,
            ISettingsRepository settingsRepository,
            IAuthService authService,
            ISettings settings)
        {
            _cipherApiRepository = cipherApiRepository;
            _folderApiRepository = folderApiRepository;
            _loginApiRepository = loginApiRepository;
            _accountsApiRepository = accountsApiRepository;
            _settingsApiRepository = settingsApiRepository;
            _folderRepository = folderRepository;
            _loginRepository = loginRepository;
            _settingsRepository = settingsRepository;
            _authService = authService;
            _settings = settings;
        }

        public bool SyncInProgress { get; private set; }

        public async Task<bool> SyncAsync(string id)
        {
            if(!_authService.IsAuthenticated)
            {
                return false;
            }

            SyncStarted();

            var cipher = await _cipherApiRepository.GetByIdAsync(id).ConfigureAwait(false);
            if(!cipher.Succeeded)
            {
                SyncCompleted(false);

                if(Application.Current != null && (cipher.StatusCode == System.Net.HttpStatusCode.Forbidden
                    || cipher.StatusCode == System.Net.HttpStatusCode.Unauthorized))
                {
                    MessagingCenter.Send(Application.Current, "Logout", (string)null);
                }

                return false;
            }

            switch(cipher.Result.Type)
            {
                case Enums.CipherType.Folder:
                    var folderData = new FolderData(cipher.Result, _authService.UserId);
                    var existingLocalFolder = _folderRepository.GetByIdAsync(id);
                    if(existingLocalFolder == null)
                    {
                        await _folderRepository.InsertAsync(folderData).ConfigureAwait(false);
                    }
                    else
                    {
                        await _folderRepository.UpdateAsync(folderData).ConfigureAwait(false);
                    }
                    break;
                case Enums.CipherType.Login:
                    var loginData = new LoginData(cipher.Result, _authService.UserId);
                    var existingLocalLogin = _loginRepository.GetByIdAsync(id);
                    if(existingLocalLogin == null)
                    {
                        await _loginRepository.InsertAsync(loginData).ConfigureAwait(false);
                    }
                    else
                    {
                        await _loginRepository.UpdateAsync(loginData).ConfigureAwait(false);
                    }
                    break;
                default:
                    SyncCompleted(false);
                    return false;
            }

            SyncCompleted(true);
            return true;
        }

        public async Task<bool> SyncDeleteFolderAsync(string id, DateTime revisionDate)
        {
            if(!_authService.IsAuthenticated)
            {
                return false;
            }

            SyncStarted();

            await _folderRepository.DeleteWithLoginUpdateAsync(id, revisionDate).ConfigureAwait(false);
            SyncCompleted(true);
            return true;
        }

        public async Task<bool> SyncDeleteLoginAsync(string id)
        {
            if(!_authService.IsAuthenticated)
            {
                return false;
            }

            SyncStarted();

            await _loginRepository.DeleteAsync(id).ConfigureAwait(false);
            SyncCompleted(true);
            return true;
        }

        public async Task<bool> FullSyncAsync(TimeSpan syncThreshold, bool forceSync = false)
        {
            DateTime? lastSync = _settings.GetValueOrDefault<DateTime?>(Constants.LastSync, null);
            if(lastSync != null && DateTime.UtcNow - lastSync.Value < syncThreshold)
            {
                return false;
            }

            return await FullSyncAsync(forceSync).ConfigureAwait(false);
        }

        public async Task<bool> FullSyncAsync(bool forceSync = false)
        {
            if(!_authService.IsAuthenticated)
            {
                return false;
            }

            if(!forceSync && !(await NeedsToSyncAsync()))
            {
                _settings.AddOrUpdateValue(Constants.LastSync, DateTime.UtcNow);
                return false;
            }

            SyncStarted();

            var now = DateTime.UtcNow;
            var ciphers = await _cipherApiRepository.GetAsync().ConfigureAwait(false);
            var domains = await _settingsApiRepository.GetDomains(false).ConfigureAwait(false);

            if(!ciphers.Succeeded || !domains.Succeeded)
            {
                SyncCompleted(false);
                if(Application.Current == null)
                {
                    return false;
                }

                if(ciphers.StatusCode == System.Net.HttpStatusCode.Forbidden ||
                    ciphers.StatusCode == System.Net.HttpStatusCode.Unauthorized ||
                    domains.StatusCode == System.Net.HttpStatusCode.Forbidden ||
                    domains.StatusCode == System.Net.HttpStatusCode.Unauthorized)
                {
                    MessagingCenter.Send(Application.Current, "Logout", (string)null);
                }

                return false;
            }

            var logins = ciphers.Result.Data.Where(c => c.Type == Enums.CipherType.Login).ToDictionary(s => s.Id);
            var folders = ciphers.Result.Data.Where(c => c.Type == Enums.CipherType.Folder).ToDictionary(f => f.Id);

            var loginTask = SyncLoginsAsync(logins, true);
            var folderTask = SyncFoldersAsync(folders, true);
            var domainsTask = SyncDomainsAsync(domains.Result);
            await Task.WhenAll(loginTask, folderTask, domainsTask).ConfigureAwait(false);

            if(folderTask.Exception != null || loginTask.Exception != null || domainsTask.Exception != null)
            {
                SyncCompleted(false);
                return false;
            }

            _settings.AddOrUpdateValue(Constants.LastSync, now);
            SyncCompleted(true);
            return true;
        }

        private async Task<bool> NeedsToSyncAsync()
        {
            DateTime? lastSync = _settings.GetValueOrDefault<DateTime?>(Constants.LastSync, null);
            if(!lastSync.HasValue)
            {
                return true;
            }

            var accountRevisionDate = await _accountsApiRepository.GetAccountRevisionDate();
            if(accountRevisionDate.Succeeded && accountRevisionDate.Result.HasValue &&
                accountRevisionDate.Result.Value > lastSync)
            {
                return true;
            }

            if(Application.Current != null && (accountRevisionDate.StatusCode == System.Net.HttpStatusCode.Forbidden
                || accountRevisionDate.StatusCode == System.Net.HttpStatusCode.Unauthorized))
            {
                MessagingCenter.Send(Application.Current, "Logout", (string)null);
            }

            return false;
        }

        private async Task SyncFoldersAsync(IDictionary<string, CipherResponse> serverFolders, bool deleteMissing)
        {
            if(!_authService.IsAuthenticated)
            {
                return;
            }

            var localFolders = (await _folderRepository.GetAllByUserIdAsync(_authService.UserId).ConfigureAwait(false))
                .ToDictionary(f => f.Id);

            foreach(var serverFolder in serverFolders)
            {
                if(!_authService.IsAuthenticated)
                {
                    return;
                }

                var existingLocalFolder = localFolders.ContainsKey(serverFolder.Key) ? localFolders[serverFolder.Key] : null;
                if(existingLocalFolder == null)
                {
                    var data = new FolderData(serverFolder.Value, _authService.UserId);
                    await _folderRepository.InsertAsync(data).ConfigureAwait(false);
                }
                else if(existingLocalFolder.RevisionDateTime != serverFolder.Value.RevisionDate)
                {
                    var data = new FolderData(serverFolder.Value, _authService.UserId);
                    await _folderRepository.UpdateAsync(data).ConfigureAwait(false);
                }
            }

            if(!deleteMissing)
            {
                return;
            }

            foreach(var folder in localFolders.Where(localFolder => !serverFolders.ContainsKey(localFolder.Key)))
            {
                await _folderRepository.DeleteAsync(folder.Value.Id).ConfigureAwait(false);
            }
        }

        private async Task SyncLoginsAsync(IDictionary<string, CipherResponse> serverLogins, bool deleteMissing)
        {
            if(!_authService.IsAuthenticated)
            {
                return;
            }

            var localLogins = (await _loginRepository.GetAllByUserIdAsync(_authService.UserId).ConfigureAwait(false))
                .ToDictionary(s => s.Id);

            foreach(var serverLogin in serverLogins)
            {
                if(!_authService.IsAuthenticated)
                {
                    return;
                }

                var existingLocalLogin = localLogins.ContainsKey(serverLogin.Key) ? localLogins[serverLogin.Key] : null;
                if(existingLocalLogin == null)
                {
                    var data = new LoginData(serverLogin.Value, _authService.UserId);
                    await _loginRepository.InsertAsync(data).ConfigureAwait(false);
                }
                else if(existingLocalLogin.RevisionDateTime != serverLogin.Value.RevisionDate)
                {
                    var data = new LoginData(serverLogin.Value, _authService.UserId);
                    await _loginRepository.UpdateAsync(data).ConfigureAwait(false);
                }
            }

            if(!deleteMissing)
            {
                return;
            }

            foreach(var login in localLogins.Where(localLogin => !serverLogins.ContainsKey(localLogin.Key)))
            {
                await _loginRepository.DeleteAsync(login.Value.Id).ConfigureAwait(false);
            }
        }

        private async Task SyncDomainsAsync(DomainsResponse serverDomains)
        {
            var eqDomains = new List<IEnumerable<string>>();
            if(serverDomains.EquivalentDomains != null)
            {
                eqDomains.AddRange(serverDomains.EquivalentDomains);
            }

            if(serverDomains.GlobalEquivalentDomains != null)
            {
                eqDomains.AddRange(serverDomains.GlobalEquivalentDomains.Select(d => d.Domains));
            }

            await _settingsRepository.UpsertAsync(new SettingsData
            {
                Id = _authService.UserId,
                EquivalentDomains = JsonConvert.SerializeObject(eqDomains)
            });
        }

        private void SyncStarted()
        {
            if(Application.Current == null)
            {
                return;
            }

            SyncInProgress = true;
            MessagingCenter.Send(Application.Current, "SyncStarted");
        }

        private void SyncCompleted(bool successfully)
        {
            if(Application.Current == null)
            {
                return;
            }

            SyncInProgress = false;
            MessagingCenter.Send(Application.Current, "SyncCompleted", successfully);
        }
    }
}
