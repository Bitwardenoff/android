﻿using Bit.App.Abstractions;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using Windows.Security.Credentials;

namespace Bit.UWP.Services
{
    public class SecureStorageService : ISecureStorageService
    {
        private const string ResourceName = "bitwarden";
        private PasswordVault _vault = new PasswordVault();

        public bool Contains(string key)
        {
            try
            {
                var entry = _vault.Retrieve(ResourceName, key);
                return entry != null;
            }
            catch
            {
                return false;
            }
        }

        public void Delete(string key)
        {
            var entry = _vault.Retrieve(ResourceName, key);
            if (entry != null)
                _vault.Remove(entry);
        }

        public byte[] Retrieve(string key)
        {
            try
            {
                var entry = _vault.Retrieve(ResourceName, key);
                if (entry != null)
                    return Convert.FromBase64String(entry.Password);
                else
                    return null;
            }
            catch
            {
                return null;
            }
        }

        public void Store(string key, byte[] dataBytes)
        {
            var data = Convert.ToBase64String(dataBytes);
            _vault.Add(new PasswordCredential(ResourceName, key, data));
        }
    }
}
