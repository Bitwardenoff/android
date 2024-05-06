﻿using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using Bit.Core.Abstractions;
using Bit.Core.Models.Data;
using Bit.Core.Models.View;
using Bit.Core.Utilities;

namespace Bit.Core.Models.Domain
{
    public class Login : Domain
    {
        public Login() { }

        public Login(LoginData obj, bool alreadyEncrypted = false)
        {
            PasswordRevisionDate = obj.PasswordRevisionDate;
            Uris = obj.Uris?.Select(u => new LoginUri(u, alreadyEncrypted)).ToList();
            Fido2Credentials = obj.Fido2Credentials?.Select(f => new Fido2Credential(f, alreadyEncrypted)).ToList();
            BuildDomainModel(this, obj, new HashSet<string>
            {
                "Username",
                "Password",
                "Totp"
            }, alreadyEncrypted);
        }

        public List<LoginUri> Uris { get; set; }
        public EncString Username { get; set; }
        public EncString Password { get; set; }
        public DateTime? PasswordRevisionDate { get; set; }
        public EncString Totp { get; set; }
        public List<Fido2Credential> Fido2Credentials { get; set; }

        public async Task<LoginView> DecryptAsync(string orgId, bool bypassUriChecksumValidation, SymmetricCryptoKey key = null)
        {
            var view = await DecryptObjAsync(new LoginView(this), this, new HashSet<string>
            {
                "Username",
                "Password",
                "Totp"
            }, orgId, key);
            if (Uris != null)
            {
                var cryptoService = ServiceContainer.Resolve<ICryptoService>();
                view.Uris = new List<LoginUriView>();
                foreach (var uri in Uris)
                {
                    var loginUriView = await uri.DecryptAsync(orgId, key);
                    if (bypassUriChecksumValidation || await cryptoService.ValidateUriChecksumAsync(uri.UriChecksum, loginUriView.Uri, orgId, key))
                    {
                        view.Uris.Add(loginUriView);
                    }
                }
            }
            if (Fido2Credentials != null)
            {
                view.Fido2Credentials = new List<Fido2CredentialView>();
                foreach (var fido2Credential in Fido2Credentials)
                {
                    view.Fido2Credentials.Add(await fido2Credential.DecryptAsync(orgId, key));
                }
            }
            return view;
        }

        public LoginData ToLoginData()
        {
            var l = new LoginData();
            l.PasswordRevisionDate = PasswordRevisionDate;
            BuildDataModel(this, l, new HashSet<string>
            {
                "Username",
                "Password",
                "Totp"
            });
            if (Uris?.Any() ?? false)
            {
                l.Uris = Uris.Select(u => u.ToLoginUriData()).ToList();
            }
            if (Fido2Credentials != null)
            {
                l.Fido2Credentials = Fido2Credentials.Select(f => f.ToFido2CredentialData()).ToList();
            }
            return l;
        }
    }
}
