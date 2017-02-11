﻿using System;
using System.Collections.Generic;

namespace Bit.App.Models.Api
{
    public class TokenRequest
    {
        public string Email { get; set; }
        public string MasterPasswordHash { get; set; }
        public string Token { get; set; }
        public int? Provider { get; set; }
        [Obsolete]
        public string OldAuthBearer { get; set; }
        public DeviceRequest Device { get; set; }

        public IDictionary<string, string> ToIdentityTokenRequest()
        {
            var dict = new Dictionary<string, string>
            {
                { "grant_type", "password" },
                { "username", Email },
                { "password", MasterPasswordHash },
                { "scope", "api offline_access" },
                { "client_id", "mobile" }
            };

            if(OldAuthBearer != null)
            {
                dict.Add("OldAuthBearer", OldAuthBearer);
            }

            if(Device != null)
            {
                dict.Add("DeviceType", Device.Type.ToString());
                dict.Add("DeviceIdentifier", Device.Identifier);
                dict.Add("DeviceName", Device.Name);
                dict.Add("DevicePushToken", Device.PushToken);
            }

            if(Token != null && Provider.HasValue)
            {
                dict.Add("TwoFactorToken", Token);
                dict.Add("TwoFactorProvider", Provider.Value.ToString());
            }

            return dict;
        }
    }
}
